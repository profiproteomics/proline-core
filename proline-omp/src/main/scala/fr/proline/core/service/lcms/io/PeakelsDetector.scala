package fr.proline.core.service.lcms.io

import com.almworks.sqlite4java.SQLiteConnection
import com.github.davidmoten.rtree._
import com.typesafe.scalalogging.LazyLogging
import fr.profi.chemistry.model.MolecularConstants
import fr.profi.ms.algo.IsotopePatternEstimator
import fr.profi.mzdb.algo.PeakelsPatternPredictor
import fr.profi.mzdb.model.{PutativeFeature, SpectrumHeader, Feature => MzDbFeature, Peakel => MzDbPeakel}
import fr.profi.mzdb.peakeldb.PeakelDbHelper
import fr.profi.mzdb.peakeldb.io.{PeakelDbReader, PeakelDbWriter}
import fr.profi.mzdb.util.ms.MsUtils
import fr.profi.mzdb.{FeatureDetectorConfig, MzDbFeatureDetector, MzDbReader, SmartPeakelFinderConfig}
import fr.profi.util.collection._
import fr.profi.util.metrics.Metric
import fr.profi.util.ms.massToMoz
import fr.proline.context.LcMsDbConnectionContext
import fr.proline.core.Settings
import fr.proline.core.algo.lcms._
import fr.proline.core.algo.lcms.alignment.AlignmentResult
import fr.proline.core.algo.msq.config._
import fr.proline.core.om.model.lcms.{Feature => LcMsFeature, _}
import fr.proline.core.om.model.msi.{Peptide, PeptideMatch}
import fr.proline.core.om.model.msq.ExperimentalDesignSetup
import fr.proline.core.om.storer.lcms.impl.{PgPeakelWriter, SQLPeakelWriter}
import fr.proline.core.service.lcms.CreateMapSet
import rx.lang.scala.subjects.PublishSubject
import rx.lang.scala.{Observable, Scheduler, Subject, schedulers}

import java.io.File
import java.util.concurrent.{Executors, LinkedBlockingQueue}
import scala.collection.mutable
import scala.collection.mutable.{ArrayBuffer, HashMap, HashSet, LongMap}
import scala.concurrent._
import scala.concurrent.duration.Duration
import scala.util.control.Breaks._

object PeakelsDetector {

  val ISOTOPE_PATTERN_HALF_MZ_WINDOW = 5
  val RUN_SLICE_MAX_PARALLELISM = { // Defines how many run slices we want to process in parallel
    val nbProcessors = Runtime.getRuntime().availableProcessors()
    Some( math.max( 1, (nbProcessors / 2).toInt ) )
  }

  val BACK_PRESSURE_BUFFER_SIZE = 50000

  private var mzdbMaxParallelism = 2 // Defines how many mzDB files we want to process in parallel

  // Synchronized method used to change the mzDB maximum parallelism value
  def setMzdbMaxParallelism(maxParallelism: Int) = this.synchronized {
    mzdbMaxParallelism = maxParallelism
  }

  private var tempDir = new File(System.getProperty("java.io.tmpdir"))

  // Synchronized method used to change the temp directory
  def setTempDirectory(tempDirectory: File) = this.synchronized {
    require( tempDir.isDirectory(), "tempDir must be a directory")

    tempDir = tempDirectory
  }

  protected val threadCount = new java.util.concurrent.atomic.AtomicInteger()
  protected val terminatedThreadCount = new java.util.concurrent.atomic.AtomicInteger()

  // Create a custom ThreadFactory to customize the name of threads in the pool
  // TODO: do the same thing in mzDB-processing
  protected val rtreeThreadFactory = new java.util.concurrent.ThreadFactory {

    def newThread(r: Runnable): Thread = {
      val threadName = s"ExtractMapSet-RTree-Thread-${PeakelsDetector.threadCount.incrementAndGet()}"
      new Thread(r, threadName)
    }
  }
}

class PeakelsDetector(
  lcmsDbCtx: LcMsDbConnectionContext,
  mapSetName: String,
  lcMsRuns: Seq[LcMsRun],
  val masterQcExpDesign: ExperimentalDesignSetup,
  quantConfig: ILcMsQuantConfig,
  val peptideMatchByRunIdAndScanNumber: LongMap[LongMap[ArrayBuffer[PeptideMatch]]]
) extends AbstractMapSetDetector(lcmsDbCtx, mapSetName, lcMsRuns, quantConfig) with LazyLogging {
  
  // Do some requirements
  require(quantConfig.extractionParams.mozTolUnit matches "(?i)PPM")
  require(
    lcMsRuns.map(_.number).filter(_ > 0).distinct.length == lcMsRuns.length,
    "Invalid LC-MS run numbers: numbers should distinct and strictly positive"
  )
  require(quantConfig.detectionParams.isDefined, "detect peakels method requires PSM matching parameter")
  require(quantConfig.detectionParams.get.psmMatchingParams.isDefined, "detect peakels method requires PSM matching parameter")
  require(quantConfig.detectionParams.get.isotopeMatchingParams.isDefined, "detect peakels method requires isotope matching parameter")

  protected val avgIsotopeMassDiff = MolecularConstants.AVERAGE_PEPTIDE_ISOTOPE_MASS_DIFF
  protected val peakelWriter = rawMapStorer.peakelWriter.get

  protected val pps = new PeakPickingSoftware(
    id = -1,
    name = "Proline",
    version = new fr.proline.core.service.Version().getVersion.split("-").head,
    algorithm = "ExtractMapSet"
  )

  class LcMsMapDetectorEntityCache(
    val lcMsRuns: Seq[LcMsRun],
    val mzDbFileByLcMsRunId: LongMap[File],
    val mapSetId: Long
  ) {
    private type PeakelRTree = RTree[java.lang.Integer,geometry.Point]
    
    val runsCount = lcMsRuns.length
    
    // Create some immutable maps => they don't need to be synchronized
    private val m_metricsByRunId = new LongMap[Metric](runsCount)
    private val m_peakelIdByMzDbPeakelIdByRunId = new LongMap[LongMap[Long]](runsCount) // contains only assigned peakels
    private val m_mzDbPeakelIdsByPeptideAndChargeByRunId = new LongMap[HashMap[(Peptide, Int), ArrayBuffer[Int]]](runsCount)
    for (lcMsRun <- lcMsRuns)  {
      m_metricsByRunId += lcMsRun.id -> new Metric("LCMSRun_"+lcMsRun.id)
      m_peakelIdByMzDbPeakelIdByRunId += (lcMsRun.id, new LongMap[Long]())
      m_mzDbPeakelIdsByPeptideAndChargeByRunId += (lcMsRun.id -> new HashMap[(Peptide, Int), ArrayBuffer[Int]]())
    }
    val metricsByRunId = m_metricsByRunId.toMap
    val peakelIdByMzDbPeakelIdByRunId = m_peakelIdByMzDbPeakelIdByRunId.toMap
    val mzDbPeakelIdsByPeptideAndChargeByRunId = m_mzDbPeakelIdsByPeptideAndChargeByRunId.toMap
    
    // Create some mutable maps => they need to be synchronized
    private val peakelFileByRunId_lock = new Object()
    private val peakelFileByRunId = new LongMap[File]()
    
    def getPeakelFile(runId: Long): Option[File] = peakelFileByRunId.get(runId)
    def addPeakelFile(runId: Long, peakelFile: File) {
      peakelFileByRunId_lock.synchronized {
        peakelFileByRunId += runId -> peakelFile
      }
    }
    
    private val rTreeByRunId_lock = new Object()
    private val rTreeByRunId = new LongMap[PeakelRTree](runsCount)

    def getRTree(lcMsRunId: Long): Option[PeakelRTree] = rTreeByRunId.get(lcMsRunId)
    def addRTree(runId: Long, rTree: PeakelRTree) {
      rTreeByRunId_lock.synchronized {
        rTreeByRunId += runId -> rTree
      }
    }
    
    private val featureTuples_lock = new Object()
    val featureTuples = new ArrayBuffer[(LcMsFeature, Peptide, LcMsRun)]()
    
    def addFeatureTuple(featureTuple: (LcMsFeature, Peptide, LcMsRun)) {
      featureTuples_lock.synchronized {
        featureTuples += featureTuple
      }
    }

    val isomericPeptidesMap = new HashMap[Peptide, Array[Peptide]]()

    def buildIsomericPeptidesMap(peptides: Seq[Peptide]): Unit = {
      val peptidesBySeqPtmsKey = peptides.groupBy(peptide => peptide.sequence + peptide.ptms.map(_.definition.names.shortName).sorted.mkString)
      for((key, isomericPeptides) <- peptidesBySeqPtmsKey) {
        isomericPeptides.foreach(peptide => isomericPeptidesMap += (peptide -> isomericPeptides.toArray))
      }
    }

    private val conflictingPeptidesMap_lock = new Object()
    val conflictingPeptidesMap = new HashMap[(Peptide, Int), ArrayBuffer[Peptide]]()

    def getConflictingPeptides(peptideAndCharge: (Peptide, Int)): Option[Array[Peptide]] = {
      val r = conflictingPeptidesMap.getOrElse(peptideAndCharge, ArrayBuffer.empty[Peptide]).toArray ++ isomericPeptidesMap.getOrElse(peptideAndCharge._1, Array.empty[Peptide])
      if (r.isEmpty){ None } else { Some(r) }
    }

    def addConflictingPeptides(peptideAndCharge: (Peptide, Int), peptides: Seq[Peptide]) {
      conflictingPeptidesMap_lock.synchronized {
        conflictingPeptidesMap.getOrElseUpdate(peptideAndCharge, ArrayBuffer[Peptide]()) ++= peptides
      }
    }

  }

  def detectMapSetFromPeakels(
    mzDbFileByLcMsRunId: LongMap[File],
    mapSetId: Long
  ): (MapSet, AlignmentResult) = {

    assert(sortedLcMsRuns.length == this.masterQcExpDesign.runGroupNumbers.length)

    logger.info("Low level Configuration used :: \n {} ", Settings.renderConfigAsString())

    val intensityComputationMethod = ClusterIntensityComputation.withName(
      clusteringParams.intensityComputation.toUpperCase()
    )
    val timeComputationMethod = ClusterTimeComputation.withName(
      clusteringParams.timeComputation.toUpperCase()
    )

    val detectorEntityCache = new LcMsMapDetectorEntityCache(
      sortedLcMsRuns,
      mzDbFileByLcMsRunId,
      mapSetId
    )
    // Customize how many files we want to process in parallel
    // We need one thread for the whole map set detection put 2 threads per mzDB file
    val ioThreadPool = Executors.newFixedThreadPool(
      1 + PeakelsDetector.mzdbMaxParallelism * 2,
      Executors.defaultThreadFactory()
    )
    implicit val ioExecCtx = ExecutionContext.fromExecutor(ioThreadPool)

    // Customize a thread pool for R*Tree computation
    val computationThreadPool = Executors.newFixedThreadPool(
      PeakelsDetector.mzdbMaxParallelism,
      PeakelsDetector.rtreeThreadFactory
    )
    val rxCompScheduler = rx.lang.scala.JavaConversions.javaSchedulerToScalaScheduler(
      rx.schedulers.Schedulers.from(computationThreadPool)
    )


    if (Settings.isomericPeptidesSharePeakels) {
      detectorEntityCache.buildIsomericPeptidesMap(peptideMatchByRunIdAndScanNumber.values.flatten.flatMap(_._2).map(_.peptide).toSeq.distinct)
    }

    try {

      // Create a peakel publisher that will be used to insert a stream of peakels in the LCMSdb
      // Important note: the toSerialized method enables thread-safety
      // Sources:
      // - http://reactivex.io/RxJava/javadoc/rx/subjects/SerializedSubject.html
      // - http://stackoverflow.com/questions/31841809/is-serializedsubject-necessary-for-thread-safety-in-rxjava
      // - http://tomstechnicalblog.blogspot.fr/2016/03/rxjava-problem-with-subjects.html
      // - http://davesexton.com/blog/post/To-Use-Subject-Or-Not-To-Use-Subject.aspx
      val peakelPublisher = PublishSubject[(Peakel,Long)]().toSerialized

      logger.info(s"Inserting ${detectorEntityCache.runsCount} raw maps before peakel detection...")
      val rawMaps = sortedLcMsRuns.map { lcMsRun =>

        // Create and insert the raw map
        val rawMap = RawMap(
          id = RawMap.generateNewId(),
          name = lcMsRun.rawFile.name,
          isProcessed = false,
          creationTimestamp = new java.util.Date,
          features = Array.empty[LcMsFeature],
          //peakels = Option.empty[Array[Peakel]],
          runId = lcMsRun.id,
          peakPickingSoftware = pps
        )

        // Store the raw map
        rawMapStorer.storeRawMap(rawMap, storeFeatures = false, storePeakels = false)

        rawMap
      }

      // To see: http://ashkrit.blogspot.fr/2014/01/java-queues-bad-practices.html
      val publishedPeakelQueue = new LinkedBlockingQueue[Option[Peakel]]()
      var isLastPublishedPeakel = false

      val mapSetDetectionFuture = Future { // TODO: why do we need a future here ?

        // Observe the peakel publisher in a separate thread
        peakelPublisher.subscribe({ case (peakel,rawMapId) =>
          // Update raw map id
          // TODO: do this before publishing the peakel
          peakel.rawMapId = rawMapId
          publishedPeakelQueue.put(Some(peakel))
        }, { e =>
          throw new Exception("Caught error in peakelPublisher", e)
        }, { () =>
          isLastPublishedPeakel = true
          publishedPeakelQueue.put(None) // add a None peakel to stop the queue monitoring
          logger.info("Peakel stream successfully published !")
        })

        logger.info("Detecting LC-MS maps...")
        val processedMaps = this._detectMapsFromPeakels(rawMaps, detectorEntityCache, peakelPublisher, rxCompScheduler)

        // Create some new mappings between LC-MS runs and the processed maps
        val processedMapByRunId = new LongMap[ProcessedMap](processedMaps.length)
        val lcmsRunByProcMapId = new LongMap[LcMsRun](detectorEntityCache.runsCount)

        for ( (lcMsRun,processedMap) <- sortedLcMsRuns.zip(processedMaps) ) {
          assert(
            lcMsRun.number == processedMap.number,
            "Invalid mapping between LC-MS run and corresponding processed map"
          )

          lcmsRunByProcMapId += processedMap.id -> lcMsRun
          processedMapByRunId += lcMsRun.id -> processedMap
        }

        // Retrieve some mappings
        val metricsByRunId = detectorEntityCache.metricsByRunId
        val mzDbPeakelIdsByPeptideAndChargeByRunId = detectorEntityCache.mzDbPeakelIdsByPeptideAndChargeByRunId

        // Define some data structures
        val mftBuilderByPeptideAndCharge = new HashMap[(Peptide, Int), MasterFeatureBuilder]()

        this.logger.info("Building the master map...")

        // Group found features by peptide and charge to build master features
        val featureTuplesByPepAndCharge = detectorEntityCache.featureTuples.groupBy { ft => (ft._2, ft._1.charge) }
        for (((peptide, charge), masterFeatureTuples) <- featureTuplesByPepAndCharge) {

          val masterFtChildren = new ArrayBuffer[LcMsFeature](masterFeatureTuples.length)
          val featureTuplesByLcMsRun = masterFeatureTuples.groupBy(_._3)

          // Iterate over each LC-MS run
          val runsWithMissingFt = new ArrayBuffer[LcMsRun]()
          for (lcMsRun <- sortedLcMsRuns) {

            val lcMsRunId = lcMsRun.id

            // Check if a feature corresponding to this peptide has been found in this run
            if (!featureTuplesByLcMsRun.contains(lcMsRun)) {
              runsWithMissingFt += lcMsRun
            } else {
              val runFeatureTuples = featureTuplesByLcMsRun(lcMsRun)
              val runFeatures = runFeatureTuples.map(_._1)
              // If a single feature has been mapped to a given peptide ion in a single run
              if (runFeatures.length == 1) masterFtChildren += runFeatures.head
              // Else if multiple features have been mapped to the same peptide ion in a single run
              else {
                // TODO: maybe we should cluster features only if they are close in time dimension ???
                // CBy : YES !!
                val clusterFeature = ClusterizeFeatures.buildFeatureCluster(
                  runFeatures,
                  rawMapId = runFeatures.head.relations.rawMapId,
                  procMapId = runFeatures.head.relations.processedMapId,
                  intensityComputationMethod,
                  timeComputationMethod,
                  lcMsRun.scanSequence.get.scanById
                )
                masterFtChildren += clusterFeature
                metricsByRunId(lcMsRunId).storeValue("cluster feature duration", clusterFeature.duration)
              }
            }
          }

          // Create TMP master feature builders
          //VDS Warning maxBy may return wrong value if NaN
          val bestFt = masterFtChildren.maxBy(_.intensity)

          val mftBuilder = new MasterFeatureBuilder(
            bestFeature = bestFt,
            children = masterFtChildren,
            peptideId = peptide.id // attach the peptide id to the master feature
          )
          mftBuilderByPeptideAndCharge += (peptide, charge) -> mftBuilder

        } // ends (peptide, charge) loop


        this.logger.info("Aligning maps moz ...")
        val featureTuplesByRuns = detectorEntityCache.featureTuples.groupBy { ft => (ft._3) }

        if (alignmentConfig.isDefined) {

          val alignmentSmoother = AlnSmoother(method = alignmentConfig.get.smoothingMethodName)

          for ((lcMsRun, tuples) <- featureTuplesByRuns) {
            val processedMap = processedMapByRunId(lcMsRun.id)
            val landmarks = new ArrayBuffer[Landmark]
            tuples.foreach { t =>
              val theoreticalMoz = massToMoz(t._2.calculatedMass, t._1.charge)
              val rt = t._1.elutionTime
              landmarks += Landmark(rt.toDouble, 1e6 * (theoreticalMoz - t._1.moz) / theoreticalMoz)
            }

//            Landmark._toCSVFile(List("moz", lcMsRun.getRawFileName).mkString("_") + ".csv", landmarks)

            val smoothedLandmarks = alignmentSmoother.smoothLandmarks(landmarks, alignmentConfig.get.smoothingMethodParams)

            val mozCalibration = MapMozCalibration(
                mozList = smoothedLandmarks.map(_.x).toArray,
                deltaMozList = smoothedLandmarks.map(_.dx).toArray,
                mapId = -1L,
                scanId = -1L
              )
              processedMap.mozCalibrations = Some(Array(mozCalibration))
          }
        }

        this.logger.info("Aligning maps RT and creating new map set...")

        // Align maps
        var tmpMapSet = _computeMapAlignment(mapSetId, processedMaps, mftBuilderByPeptideAndCharge)

        // Define some data structures
        val putativeFtsByRunId = new LongMap[ArrayBuffer[PutativeFeature]]()
        val putativeFtsByPeptideAndRunId = new HashMap[(Peptide, Long), ArrayBuffer[PutativeFeature]]()
        val peptideByPutativeFtId = new LongMap[Peptide]()
        val multiMatchedMzDbPeakelIdsByPutativeFtId = new LongMap[ArrayBuffer[Int]]()

        this.logger.info("Predicting missing features coordinates...")

        // Iterate create master features to predict missing ones
        for (((peptide, charge), mftBuilder) <- mftBuilderByPeptideAndCharge) {

          val masterFtChildren = mftBuilder.children
          val bestFt = mftBuilder.bestFeature
          val bestFtProcMapId = bestFt.relations.processedMapId
          val bestFtLcMsRun = lcmsRunByProcMapId(bestFtProcMapId)

          // Compute prediction Stats
          for (feature <- masterFtChildren) {
            if (feature != bestFt) {
              val bestFtProcMapId = bestFt.relations.processedMapId
              val ftProcMapId = feature.relations.processedMapId
              val predictedRtOpt = tmpMapSet.convertElutionTime(bestFt.elutionTime, bestFtProcMapId, ftProcMapId)

              if (predictedRtOpt.isEmpty || predictedRtOpt.get._1 <= 0) {
                metricsByRunId(bestFtLcMsRun.id).incr("unpredictable peptide elution time")
              } else {
                val deltaRt = feature.elutionTime - predictedRtOpt.get._1
                metricsByRunId(bestFtLcMsRun.id).storeValue("matched feature vs predicted RT", (deltaRt))
              }

            // Compute moz stats

//              val targetProcMap = processedMapByRunId(lcmsRunByProcMapId(feature.relations.processedMapId).id)
//
//              val predictedRT = tmpMapSet.convertElutionTime(
//                bestFt.elutionTime,
//                bestFt.relations.processedMapId,
//                feature.relations.processedMapId)
//
//              val dmass = targetProcMap.mozCalibrations.get.head.calcDeltaMoz(predictedRT.getOrElse(bestFt.elutionTime).toDouble)
//              val predictedMoz = massToMoz(peptide.calculatedMass, charge) - MsUtils.ppmToDa(bestFt.moz, dmass)
//              val predictedDeltaMass = MsUtils.DaToPPM(feature.moz, predictedMoz - feature.moz)
//              val bestDeltaMass = MsUtils.DaToPPM(feature.moz, bestFt.moz - feature.moz)
//              metricsByRunId(lcmsRunByProcMapId(ftProcMapId).id).addValue("predicted.moz delta", predictedDeltaMass)
//              metricsByRunId(lcmsRunByProcMapId(ftProcMapId).id).addValue("best.moz delta", bestDeltaMass)

            }
          }

          val missingRunAndRefFtPairs = _buildMissingRunAndRefFtPairs(processedMapByRunId, masterFtChildren, bestFt)

          // Create a putative feature for each missing one
          for ((lcMsRun, refFt, childrenFt) <- missingRunAndRefFtPairs) {

            val lcMsRunId = lcMsRun.id
            val currentProcMapId = processedMapByRunId(lcMsRunId).id

            val predictedTimes = childrenFt.map(ft => tmpMapSet.convertElutionTime(ft.elutionTime, ft.relations.processedMapId, currentProcMapId)).flatten
            val meanPredictedRt = if (predictedTimes.isEmpty) {
              None
            } else {
              Some((predictedTimes.map(_._1).sum / predictedTimes.size, predictedTimes.map(_._2).sum / predictedTimes.size))
            }

            val predictedRT = tmpMapSet.convertElutionTime(refFt.elutionTime, refFt.relations.processedMapId, currentProcMapId)

            val predictedTimeOpt = if (Settings.meanPredictedRetentionTime) {
              meanPredictedRt
            } else {
              predictedRT
            }

            if (predictedRT.isDefined && meanPredictedRt.isDefined) {
              metricsByRunId(lcMsRunId).storeValue("predicted vs mean predicted RT", (predictedRT.get._1 - meanPredictedRt.get._1))
            }


            // Fix negative predicted times and remove decimals. If elution cannot be
            // predicted, use the reference feature elution time instead
            val predictedTime = if (predictedTimeOpt.getOrElse((refFt.elutionTime, 0))._1 <= 0) 1f
            else math.round(predictedTimeOpt.getOrElse((refFt.elutionTime, 0))._1).toFloat

            // predict experimental mz of the putative feature

            val putativeMoz = if (Settings.useMozCalibration) {
                  val targetProcMap = processedMapByRunId(lcMsRunId)
                  val theoreticalMoz = massToMoz(peptide.calculatedMass, charge)
                  val dmass = targetProcMap.mozCalibrations.get.head.calcDeltaMoz(predictedTime)
                  theoreticalMoz - MsUtils.ppmToDa(theoreticalMoz, dmass)
                } else {
                  refFt.moz
                }

            val pf = new PutativeFeature(
                id = PutativeFeature.generateNewId,
                mz = putativeMoz,
                charge = refFt.charge,
                elutionTime = predictedTime,
                evidenceMsLevel = 2,
                isPredicted = true
            )

            pf.elutionTimeTolerance = predictedTimeOpt.getOrElse((refFt.elutionTime, 0.0f))._2
            pf.maxObservedIntensity = refFt.apexIntensity

            val multiMatchedMzDbPeakelIds = ArrayBuffer.empty[Int]
            val conflictingPeptides = detectorEntityCache.getConflictingPeptides((peptide, charge)).orNull
              val mzDbPeakelIdsByPeptideAndCharge = mzDbPeakelIdsByPeptideAndChargeByRunId.getOrElse(lcMsRunId, null)

              if (conflictingPeptides != null && mzDbPeakelIdsByPeptideAndCharge != null) {
                for (conflictingPeptide <- conflictingPeptides) {
                  val mzDbPeakelIds = mzDbPeakelIdsByPeptideAndCharge.getOrElse((conflictingPeptide, charge), null)
                  if (mzDbPeakelIds != null) {
                    multiMatchedMzDbPeakelIds ++= mzDbPeakelIds
                  }
                }
              }

            putativeFtsByRunId.getOrElseUpdate(lcMsRunId, new ArrayBuffer[PutativeFeature]) += pf
            putativeFtsByPeptideAndRunId.getOrElseUpdate((peptide, lcMsRunId), new ArrayBuffer[PutativeFeature]) += pf
            peptideByPutativeFtId(pf.id) = peptide
            multiMatchedMzDbPeakelIdsByPutativeFtId(pf.id) = multiMatchedMzDbPeakelIds

          }

        } // ends for (((peptide, charge), mftBuilder) <- mftBuilderByPeptideAndCharge)

        //
        //    Then search for missing features
        //
        val x2RawMaps = this._searchForUnidentifiedFeatures(
          processedMapByRunId,
          peakelPublisher,
          mftBuilderByPeptideAndCharge,
          putativeFtsByRunId,
          putativeFtsByPeptideAndRunId,
          peptideByPutativeFtId,
          multiMatchedMzDbPeakelIdsByPutativeFtId,
          detectorEntityCache
        )

        // Complete the peakel publisher
        peakelPublisher.onCompleted()

        // Define some mappings
        val x2RawMapByRunId = x2RawMaps.mapByLong(_.runId)

        // --- Build a temporary master map and update corrected elution times ---
        val alnRefMapId = tmpMapSet.getAlnReferenceMapId

        val masterFeatures = mftBuilderByPeptideAndCharge.values.map { mftBuilder =>

          def updateCorrectedElutionTime(ft: LcMsFeature) {
            val predictedTimeOpt = tmpMapSet.convertElutionTime(ft.elutionTime, ft.relations.processedMapId, alnRefMapId)
            ft.correctedElutionTime = predictedTimeOpt.map(_._1)
          }

          for (ft <- mftBuilder.children) {
            updateCorrectedElutionTime(ft)
            if (ft.isCluster) ft.subFeatures.foreach(updateCorrectedElutionTime(_))
          }

          val mft = mftBuilder.toMasterFeature()
          require(mft.children.length <= sortedLcMsRuns.length, "master feature contains more child features than maps")

          mft
        } toArray

        val alnRefMap = tmpMapSet.getAlnReferenceMap.get
        val curTime = new java.util.Date()
        tmpMapSet.masterMap = ProcessedMap(
          id = ProcessedMap.generateNewId(),
          number = 0,
          name = alnRefMap.name,
          features = masterFeatures,
          isMaster = true,
          isAlnReference = false,
          isProcessed = true,
          creationTimestamp = curTime,
          modificationTimestamp = curTime,
          mapSetId = tmpMapSet.id,
          rawMapReferences = tmpMapSet.getRawMapIds().map(RawMapIdentifier(_))
        )

        // Re-build child maps in order to be sure they contain master feature children (including clusters)
        tmpMapSet = tmpMapSet.rebuildChildMaps()

        // Link re-built processedMap to corresponding x2RawMap
        for (processedMap <- tmpMapSet.childMaps) {
          val runId = processedMap.runId.get
          processedMap.rawMapReferences = Array(x2RawMapByRunId(runId))
        }

        (tmpMapSet,x2RawMaps)
      } // End of Future

      var mapSetDetectionExceptionOpt = Option.empty[Throwable]
      mapSetDetectionFuture.onFailure { case t: Throwable =>

        // Terminate the peakel queue
        isLastPublishedPeakel = true
        publishedPeakelQueue.put(None)

        mapSetDetectionExceptionOpt = Some(t)
      }

      logger.info("Streaming peakel insertion in the LCMSdb...")

      // Convert the peakel queue into a lambda stream that will be passed to the peakel storer
      val forEachPeakel = { onEachPeakel: (Peakel => Unit) =>

        while (!isLastPublishedPeakel || !publishedPeakelQueue.isEmpty()) {
          val peakelOpt = publishedPeakelQueue.take()
          if (peakelOpt.isDefined) {
            onEachPeakel(peakelOpt.get)

            val otherPeakelOpts = new java.util.ArrayList[Option[Peakel]]
            publishedPeakelQueue.drainTo(otherPeakelOpts)

            val otherPeakelOptsIter = otherPeakelOpts.iterator()
            while (otherPeakelOptsIter.hasNext()) {
              otherPeakelOptsIter.next.map(onEachPeakel)
            }
          }
        }

        logger.debug("Exiting forEachPeakel loop...")

        // Throw exception if defined
        mapSetDetectionExceptionOpt.map(t => throw t)

        ()
      }

      // Insert the peakel stream
      val peakelIdByTmpIdByRawMapId = peakelWriter match {
        case pgPeakelWriter: PgPeakelWriter => {
          pgPeakelWriter.insertPeakelStream(forEachPeakel, None)
        }
        case sqlPeakelWriter: SQLPeakelWriter => {
          sqlPeakelWriter.insertPeakelStream(forEachPeakel, None)
        }
        case _ => throw new Exception("This peakel writer do not implement the required \"insertPeakelStream\" method")
      }


      /**
       * We are now returning to the MAIN thread :)
       *
       * */
      // Synchronize map set detection operations
      val (tmpMapSet,x2RawMaps) = Await.result(mapSetDetectionFuture, Duration.Inf)


      // Update feature peakel item IDs
      for (
        x2RawMap <- x2RawMaps;
        rawMapId = x2RawMap.id;
        peakelIdByTmpId = peakelIdByTmpIdByRawMapId(rawMapId);
        ft <- x2RawMap.features;
        peakelItem <- ft.relations.peakelItems
      ) {
        val peakelRef = peakelItem.peakelReference
        val tmpPeakelId = peakelRef.id

        // FIXME: figure out why some peakel ids are already > 0
        // Remarks:
        // - problem not observed with Pg...
        // - it might be related to peakels coming from cross assignment ?
        if (tmpPeakelId < 0) {
          val newPeakelId = peakelIdByTmpId(tmpPeakelId)
          peakelRef.asInstanceOf[PeakelIdentifier].id = newPeakelId
        }
      }

      // Storing features of each detected raw map
      // VDS: create Map RawMapId-> a ScanId for mapMozCalibration (scanId not used but require  !)
      val scanIdByRawMapId = new mutable.HashMap[Long, Long]()

      for ((x2RawMap,idx) <- x2RawMaps.zipWithIndex) {
        logger.info(s"Storing features of raw map #${idx+1} (id=${x2RawMap.id}, name=${x2RawMap.name})...")

        rawMapStorer.featureWriter.insertFeatures(x2RawMap.features, x2RawMap.id, linkToPeakels = true)
        if(x2RawMap.features.length>0){
          scanIdByRawMapId.put(x2RawMap.id, x2RawMap.features(0).relations.apexScanId)
        }
        // Log some raw map information
        val rawMapPeakelsCount = peakelIdByTmpIdByRawMapId(x2RawMap.id).size
        logger.info("Raw map peakels count = "+rawMapPeakelsCount)
        logger.info(detectorEntityCache.metricsByRunId(x2RawMap.runId).toString)
      }

      // Memorize processed maps temporary ID and upadte MozCalib if necessary
      val procMapTmpIdByRawMapId = tmpMapSet.childMaps.toLongMapWith { processedMap =>

        if(processedMap.mozCalibrations.isDefined && scanIdByRawMapId.contains(processedMap.getRawMapIds.head)){
          val currentMozArr  = processedMap.mozCalibrations.get
          val newMozArr = new Array[MapMozCalibration](currentMozArr.length)

          for(i <- 0 until currentMozArr.length ) {
            newMozArr(i) = currentMozArr(i).copy(scanId = scanIdByRawMapId(processedMap.getRawMapIds.head))
          }
          processedMap.mozCalibrations = Some(newMozArr) //update mozCalibration with a valid scan id
        }

        (processedMap.getRawMapIds.head, processedMap.id)
      }

      // --- Persist the corresponding map set ---
      val x2MapSet = CreateMapSet(lcmsDbCtx, mapSetName, tmpMapSet.childMaps)

      // Map processed map id by corresponding temp id
      val procMapIdByTmpId = new LongMap[Long](sortedLcMsRuns.length)
      for (processedMap <- tmpMapSet.childMaps) {
        val rawMapId = processedMap.getRawMapIds.head
        val oldProcessedMapId = procMapTmpIdByRawMapId.get(rawMapId).get
        procMapIdByTmpId.put(oldProcessedMapId,processedMap.id )
      }

      // Attach the computed master map to the newly created map set
      val tmpMasterMap = tmpMapSet.masterMap
      tmpMasterMap.mapSetId = x2MapSet.id
      tmpMasterMap.rawMapReferences = x2RawMaps
      x2MapSet.masterMap = tmpMasterMap

      // Update the map ids in the alnResult
      val finalMapAlnSets = new ArrayBuffer[MapAlignmentSet](tmpMapSet.mapAlnSets.length)
      for (alnSet <- tmpMapSet.mapAlnSets) {

        val finalRefMapId = procMapIdByTmpId.get(alnSet.refMapId).get
        val finalTargetMapId = procMapIdByTmpId.get(alnSet.targetMapId).get
        val finalMapAlignments = new ArrayBuffer[MapAlignment](alnSet.mapAlignments.length)

        for (aln <- alnSet.mapAlignments) {
          val finalMapAln = new MapAlignment(
            procMapIdByTmpId.get(aln.refMapId).get,
            procMapIdByTmpId.get(aln.targetMapId).get,
            aln.massRange,
            aln.timeList,
            aln.deltaTimeList,
            aln.properties
          )
          finalMapAlignments += finalMapAln
        }

        finalMapAlnSets += new MapAlignmentSet(
          finalRefMapId,
          finalTargetMapId,
          finalMapAlignments.toArray
        )
      }

      val finalAlnResult = new AlignmentResult(
        procMapIdByTmpId.get(tmpMapSet.getAlnReferenceMapId).get,
        finalMapAlnSets.toArray
      )

      (x2MapSet, finalAlnResult)

    } finally {

      this.logger.debug("Closing ExtractMapSet thread pools...")

      // Shutdown the thread pools
      if (ioThreadPool.isShutdown() == false ) ioThreadPool.shutdownNow()
      if (computationThreadPool.isShutdown() == false ) computationThreadPool.shutdownNow()

      // Update terminatedThreadCount and reset the ThreadCount
      val terminatedThreadCount = PeakelsDetector.terminatedThreadCount.addAndGet(PeakelsDetector.mzdbMaxParallelism)
      if (terminatedThreadCount == PeakelsDetector.threadCount) {
        PeakelsDetector.threadCount.set(0)
      }

      this.logger.debug("ExtractMapSet thread pools have been closed!")
    }

  }

  private def _computeMapAlignment(mapSetId: Long, processedMaps: Array[ProcessedMap], mftBuilderByPeptideAndCharge: mutable.HashMap[(Peptide, Int), MasterFeatureBuilder]): MapSet = {

    val alnResult = if (alignmentConfig.isDefined) {
      alignmentConfig.get.ftMappingMethodName match {
        case FeatureMappingMethod.FEATURE_COORDINATES => {
          mapAligner.get.computeMapAlignments(processedMaps, alignmentConfig.get)
        }
        case FeatureMappingMethod.PEPTIDE_IDENTITY => {

          // Map mftBuilder by the feature id
          val mftBuilderByFtId = new mutable.LongMap[MasterFeatureBuilder](processedMaps.foldLeft(0)(_ + _.features.length))
          for (
            mftBuilder <- mftBuilderByPeptideAndCharge.values;
            childFt <- mftBuilder.children
          ) {
            if (childFt.isCluster) {
              val bestFt = childFt.subFeatures.maxBy(_.intensity) //VDS Warning maxBy may return wrong value if NaN
              mftBuilderByFtId.put(bestFt.id, mftBuilder)
            } else {
              mftBuilderByFtId.put(childFt.id, mftBuilder)
            }
          }

          logger.debug("peptide identity feature size = " + mftBuilderByFtId.size)

          // Create a new map aligner algo starting from existing master features
          mapAligner.get.computeMapAlignmentsUsingCustomFtMapper(processedMaps, alignmentConfig.get) { (map1Features, map2Features) =>

            val ftMapping = new mutable.LongMap[ArrayBuffer[LcMsFeature]](Math.max(map1Features.length, map2Features.length))

            val map2FtByMftBuilder = Map() ++ (for (
              map2Ft <- map2Features;
              mftBuilderOpt = mftBuilderByFtId.get(map2Ft.id);
              if mftBuilderOpt.isDefined
            ) yield (mftBuilderOpt.get, map2Ft))

            for (
              map1Ft <- map1Features;
              mftBuilderOpt = mftBuilderByFtId.get(map1Ft.id);
              if mftBuilderOpt.isDefined;
              map2FtOpt = map2FtByMftBuilder.get(mftBuilderOpt.get);
              if (map2FtOpt.isDefined && Math.abs(map1Ft.elutionTime - map2FtOpt.get.elutionTime) < alignmentConfig.get.ftMappingMethodParams.timeTol)
            ) {
              ftMapping.getOrElseUpdate(map1Ft.id, new ArrayBuffer[LcMsFeature]) += map2FtOpt.get
            }
            logger.debug("PEPTIDE_IDENTITY custom mapper found {} ft from {} map1 feat. and {} map2 Feat.  ", ftMapping.size, map1Features.size, map2Features.size)

            ftMapping
          }

        }
        case _ => throw new Exception("Unsupported feature mapping method")
      }
    } else {
      // Map Alignment was not requested: build an empty AlignmentResult using the first map as reference without any MapAlignmentSet
      AlignmentResult(processedMaps(0).id, Array.empty[MapAlignmentSet])
    }

    new MapSet(
      id = mapSetId,
      name = mapSetName,
      creationTimestamp = new java.util.Date,
      childMaps = processedMaps.toArray,
      alnReferenceMapId = alnResult.alnRefMapId,
      mapAlnSets = alnResult.mapAlnSets
    )
  }

  private def _buildMissingRunAndRefFtPairs(processedMapByRunId: LongMap[ProcessedMap], masterFtChildren: ArrayBuffer[LcMsFeature], bestFt: LcMsFeature) = {

    val childFtByProcMapId = masterFtChildren.mapByLong { ft => ft.relations.processedMapId }

    if (crossAssignmentConfig.isDefined) {

      if (crossAssignmentConfig.get.methodName == CrossAssignMethod.BETWEEN_ALL_RULS ||
        masterQcExpDesign.groupSetup.biologicalGroups.length == 1
      ) {
        sortedLcMsRuns.withFilter { lcMsRun =>
          childFtByProcMapId.contains(processedMapByRunId(lcMsRun.id).id) == false
        } map { lcMsRun =>
          (lcMsRun, bestFt, masterFtChildren)
        }
      } else {

        val lcMsRunBioGroupNumPairs = this.sortedLcMsRuns.zip(this.masterQcExpDesign.runGroupNumbers)

        val bioGroupNumAndLcMsRunsTuples = lcMsRunBioGroupNumPairs.groupByLong(_._2).toSeq.map { case (groupNum, lcmsRunsBioGroupTuples) =>
          val bioGroupLcMsRuns = lcmsRunsBioGroupTuples.map(_._1)
          (groupNum -> bioGroupLcMsRuns)
        }

        bioGroupNumAndLcMsRunsTuples.flatMap { case (bioGroupNum, lcMsRuns) =>

          // Retrieve detected child features in this group
          val detectedChildFtsInGroup = lcMsRuns.withFilter { lcMsRun =>
            childFtByProcMapId.contains(processedMapByRunId(lcMsRun.id).id)
          } map { lcMsRun =>
            childFtByProcMapId(processedMapByRunId(lcMsRun.id).id)
          }

          // Disable cross assignment if we have not detected features in this group
          if (detectedChildFtsInGroup.isEmpty) {
            logger.debug(s"No Ft found in group ${bioGroupNum}: will not perform cross assignment")
            Seq()
          } else {
            // Else determine a reference for this group
            val refFt = detectedChildFtsInGroup.maxBy(_.intensity) //VDS Warning maxBy may return wrong value if NaN

            lcMsRuns.withFilter { lcMsRun =>
              childFtByProcMapId.contains(processedMapByRunId(lcMsRun.id).id) == false
            } map { lcMsRun =>
              (lcMsRun, refFt, detectedChildFtsInGroup)
            }
          }
        }
      }
    } else {
      Seq.empty[(LcMsRun, LcMsFeature, ArrayBuffer[LcMsFeature])]
    }

  }

  case class PeakelMatch(
    peakel: MzDbPeakel,
    peptide: Peptide,
    spectrumHeader: SpectrumHeader,
    charge: Int,
    peptideMatch: PeptideMatch
  )

  private def _buildPeakelMatches(
    peakelFlow: Observable[Array[MzDbPeakel]],
    scanSequence: LcMsScanSequence,
    ms2SpectrumHeaderMatrix: Array[Array[SpectrumHeader]],
    pepMatchesMatrix: Array[ArrayBuffer[PeptideMatch]]
  ): Future[ArrayBuffer[PeakelMatch]] = {

    val peakelMatches = new ArrayBuffer[PeakelMatch](scanSequence.ms2ScansCount)
    val peakelMatchesPromise = Promise[ArrayBuffer[PeakelMatch]]

    peakelFlow.doOnSubscribe(
      this.logger.info("Mapping peakels to PSMs...")
    ).subscribe( { detectedPeakels =>
      for (detectedPeakel <- detectedPeakels) {

        val peakelMz = detectedPeakel.getMz
        // TODO: define a specific m/z tolerance for this procedure or fix a low hardcoded value ???
        val ms2MatchingMzTolDa = MsUtils.ppmToDa(peakelMz, quantConfig.detectionParams.get.psmMatchingParams.get.mozTol)
        val firstTime = detectedPeakel.getFirstElutionTime()
        val lastTime = detectedPeakel.getLastElutionTime()

        // TODO: do we need to expand the duration with a time tolerance ?
        val minCycle = scanSequence.getScanAtTime(firstTime, 1).cycle
        val maxCycle = scanSequence.getScanAtTime(lastTime, 1).cycle

        // Find identified MS2 scans concurrent with the detected peakel
        var curCycle = minCycle
        while (curCycle <= maxCycle) {

          // Retrieve corresponding MS2 scans for this cycle
          val ms2SpecHeaders = ms2SpectrumHeaderMatrix(curCycle)
          if (ms2SpecHeaders != null) {

            val ms2SpecHeadersCount = ms2SpecHeaders.length
            var ms2ShIdx = 0
            while (ms2ShIdx < ms2SpecHeadersCount) {
              val ms2Sh = ms2SpecHeaders(ms2ShIdx)
              val time = ms2Sh.getElutionTime()

                // Keep only identified MS2 scans
                val pepMatches = pepMatchesMatrix(ms2Sh.getInitialId())

                if (pepMatches != null) {
                  val pepMatchesCount = pepMatches.length

                  var psmIdx = 0
                  while (psmIdx < pepMatchesCount) {
                    val psm = pepMatches(psmIdx)

                    // Filter on m/z difference between the peakel and the precursor PSM
                    if (Math.abs(psm.getExperimentalMoz - peakelMz) <= ms2MatchingMzTolDa) {
                      peakelMatches += PeakelMatch(
                        detectedPeakel,
                        psm.peptide,
                        ms2Sh,
                        psm.charge,
                        psm
                      )
                    }
                    psmIdx += 1
                  }
                }
//              }

              ms2ShIdx += 1
            } // ends while (ms2ShIdx < ms2SpecHeadersCount)
          } // ends while (curCycle <= maxCycle)

          curCycle += 1
        } // ends while (curCycle <= maxCycle)

      } // ends for detectedPeakels

    }, { e =>
      peakelMatchesPromise.failure(e)
    }, { () =>
      peakelMatchesPromise.success(peakelMatches)
    })

    peakelMatchesPromise.future
  }

//  def _testIsotopicPatternPrediction(mzDbFt: MzDbFeature, charge: Int, spectrumId: Option[Long],retentionTime: Option[Float], inMemoryPeakelDb: SQLiteConnection, rTree: RTree[Integer, Point], runMetrics: Metric): Boolean = {
//
//    val coelutingPeakels = PeakelDbHelper.findPeakelsInRange(
//      inMemoryPeakelDb,
//      Some(rTree),
//      mzDbFt.mz - PeakelsDetector.ISOTOPE_PATTERN_HALF_MZ_WINDOW,
//      mzDbFt.mz + PeakelsDetector.ISOTOPE_PATTERN_HALF_MZ_WINDOW,
//      if (retentionTime.isDefined) { retentionTime.get - crossAssignmentConfig.get.ftMappingParams.timeTol } else { mzDbFt.getBasePeakel().getFirstElutionTime() },
//      if (retentionTime.isDefined) { retentionTime.get + crossAssignmentConfig.get.ftMappingParams.timeTol } else { mzDbFt.getBasePeakel().getLastElutionTime() }   //mzDbFt.getElutionTime() + crossAssignmentConfig.get.ftMappingParams.timeTol
//    )
//
//    val mozTolInDa = MsUtils.ppmToDa(mzDbFt.mz, crossAssignmentConfig.get.ftMappingParams.mozTol.get) // crossAssignmentConfig.get.ftMappingParams.mozTol.get)
//    val slicingSspectrumId = if (spectrumId.isDefined) { spectrumId.get } else { mzDbFt.getBasePeakel().getApexSpectrumId() }
//    val (mzList, intensityList) = slicePeakels(coelutingPeakels, slicingSspectrumId)
//    val spectrumData = new SpectrumData(mzList.toArray, intensityList.toArray)
//    val ppmTol = mozTolPPM //math.max(mozTolPPM, MsUtils.DaToPPM(mzDbFt.getMz, mzDbFt.getBasePeakel().leftHwhmMean))
//    val putativePatterns = DotProductPatternScorer.calcIsotopicPatternHypotheses(spectrumData, mzDbFt.mz, ppmTol)
//    val bestPattern = DotProductPatternScorer.selectBestPatternHypothese(putativePatterns)
//
//    (bestPattern._2.charge == charge) && (math.abs(bestPattern._2.monoMz - mzDbFt.mz) <= mozTolInDa)
//  }

  private def _detectMapsFromPeakels(
    rawMaps: Seq[RawMap],
    entityCache: LcMsMapDetectorEntityCache,
    peakelPublisher: Subject[(Peakel,Long)],
    rxCompScheduler: Scheduler
  )(implicit execCtx: ExecutionContext): Array[ProcessedMap] = {

    val rawMapByRunId = rawMaps.mapByLong(_.runId)
    val lcMsRuns = entityCache.lcMsRuns
    val mapSetId = entityCache.mapSetId
    val mzDbFileByLcMsRunId = entityCache.mzDbFileByLcMsRunId
    val metricsByRunId = entityCache.metricsByRunId
    val peakelIdByMzDbPeakelIdByRunId = entityCache.peakelIdByMzDbPeakelIdByRunId
    val mzDbPeakelIdsByPeptideAndChargeByRunId = entityCache.mzDbPeakelIdsByPeptideAndChargeByRunId

    val groupedParLcMsRuns = lcMsRuns.sortBy(_.number).grouped(PeakelsDetector.mzdbMaxParallelism).map(_.par).toList

    val parProcessedMaps = for (parLcMsRuns <- groupedParLcMsRuns; lcMsRun <- parLcMsRuns) yield {

      val mapNumber = lcMsRun.number
      val lcMsRunId = lcMsRun.id
      val rawMap = rawMapByRunId(lcMsRunId)
      val rawMapId = rawMap.id
      require(rawMapId > 0, "the raw map must be persisted")

      // Open mzDB file
      val mzDbFile = mzDbFileByLcMsRunId(lcMsRunId)

      // Create a buffer to store built features
      val rawMapFeatures = new ArrayBuffer[LcMsFeature]()

      // Retrieve the psmByScanNumber mapping for this run
      val pepMatchesBySpecNumber = Some(peptideMatchByRunIdAndScanNumber).flatMap(_.get(lcMsRunId)).getOrElse(LongMap.empty[ArrayBuffer[PeptideMatch]])

      // Create a mapping avoiding the creation of duplicated peakels
      val peakelIdByMzDbPeakelId = peakelIdByMzDbPeakelIdByRunId(lcMsRunId)

      val mzDbPeakelIdsByPeptideAndCharge = mzDbPeakelIdsByPeptideAndChargeByRunId(lcMsRunId)

      val runMetrics = metricsByRunId(lcMsRunId)

      // Search for existing Peakel file
      val existingPeakelFiles = PeakelsDetector.tempDir.listFiles.filter { file =>
        val isFileNameMatching = file.getName.startsWith(s"${lcMsRun.getRawFileName}-")
        val isFileNonEmpty = file.length > 0
        isFileNameMatching && isFileNonEmpty
      }
      val needPeakelDetection = (quantConfig.useLastPeakelDetection == false || existingPeakelFiles.isEmpty)

      val futurePeakelDetectionResult = if (needPeakelDetection) {

        // Remove TMP files if they exist
        existingPeakelFiles.foreach { peakelFile =>
          if (peakelFile.delete() == false) {
            this.logger.warn("Can't delete peakelDB file located at: "+  peakelFile)
          } else {
            this.logger.info("Successfully deleted peakelDB file located at: "+  peakelFile)
          }
        }

        this.logger.info(s"Start peakel detection for run id=$lcMsRunId (map #$mapNumber) from " + mzDbFile.getAbsolutePath())

        // Create TMP file to store orphan peakels which will be deleted after JVM exit
        val peakelFile = File.createTempFile(s"${lcMsRun.getRawFileName}-", ".sqlite",PeakelsDetector.tempDir)
        //peakelFile.deleteOnExit()
        this.logger.debug(s"Creating tmp file for run id=$lcMsRunId at: " + peakelFile)

        // Create a mapping between the TMP file and the LC-MS run
        entityCache.addPeakelFile(lcMsRunId, peakelFile)

        val resultPromise = Promise[(Observable[(Int,Array[MzDbPeakel])],Array[SpectrumHeader],LongMap[Array[SpectrumHeader]])]

        val peakelProducerFuture =  Future {
          this.logger.info(s"Detecting peakels in raw MS survey for run id=$lcMsRunId...")

          val mzDb = new MzDbReader(mzDbFile, true)

          try {
            // Instantiate the feature detector
            val mzdbFtDetector = new MzDbFeatureDetector(
              mzDb,
              FeatureDetectorConfig(
                msLevel = 1,
                mzTolPPM = mozTolPPM,
                minNbOverlappingIPs = Settings.FeatureDetectorConfig.minNbOverlappingIPs,
                intensityPercentile = Settings.FeatureDetectorConfig.intensityPercentile,
                maxConsecutiveGaps = Settings.FeatureDetectorConfig.maxConsecutiveGaps,
                peakelFinderConfig = SmartPeakelFinderConfig(
                  minPeaksCount = Settings.SmartPeakelFinderConfig.minPeaksCount,
                  miniMaxiDistanceThresh = Settings.SmartPeakelFinderConfig.miniMaxiDistanceThresh,
                  maxIntensityRelThresh = Settings.SmartPeakelFinderConfig.maxIntensityRelThresh,
                  useOscillationFactor = Settings.SmartPeakelFinderConfig.useOscillationFactor,
                  maxOscillationFactor = Settings.SmartPeakelFinderConfig.maxOscillationFactor,
                  usePartialSGSmoother = Settings.SmartPeakelFinderConfig.usePartialSGSmoother,
                  useBaselineRemover = Settings.SmartPeakelFinderConfig.useBaselineRemover,
                  useSmoothing = Settings.SmartPeakelFinderConfig.useSmoothing
                )
              )
            )

            // Launch the peakel detection
            val onDetectedPeakels = { observablePeakels: Observable[(Int,Array[MzDbPeakel])] =>
              resultPromise.success(observablePeakels,mzDb.getMs1SpectrumHeaders,mzdbFtDetector.ms2SpectrumHeadersByCycle)
              ()
            }
            val nParRunSlices = PeakelsDetector.RUN_SLICE_MAX_PARALLELISM
            mzdbFtDetector.detectPeakelsAsync( mzDb.getLcMsRunSliceIterator(), onDetectedPeakels, nParRunSlices )

          } finally {
            mzDb.close()
          }

        }

        val futureResult = resultPromise.future.map { case (observableRunSlicePeakels, ms1SpecHeaders, ms2SpecHeadersByCycle) =>

          this.logger.debug(" *************** START resultPromise.future map")
          // TODO: create/manage this scheduler differently ?
          val rxIOScheduler = schedulers.IOScheduler()

          val observablePeakels = Observable[Array[MzDbPeakel]] { subscriber =>
            //Verify peakel producer was successful or not
            peakelProducerFuture.onFailure {
              case t: Throwable => {
                subscriber.onError( t )
              }
            }

            // Re-usable function that will be use to catch and propagate errors to the subscriber
            def tryOrPropagateError( block: => Unit ) = {
              try {
                block
              } catch {
                case t: Throwable => {
                  subscriber.onError( t )
                }
              }
            }

            tryOrPropagateError {
              logger.info("Observing detected peakels to store them in the PeakelDB...")

              // Open TMP SQLite file using a lazy val to perform its instantiation in the appropriate thread
              lazy val peakelFileConnection = PeakelDbWriter.initPeakelStore(peakelFile)

              observableRunSlicePeakels.onBackpressureBuffer(PeakelsDetector.BACK_PRESSURE_BUFFER_SIZE).observeOn(rxIOScheduler).subscribe({ case (rsId,peakels) =>

                logger.trace(s"Storing ${peakels.length} peakels for run slice #$rsId and run #${lcMsRun.id}...")

                // Store peakels in SQLite file
                PeakelDbWriter.storePeakelsInPeakelDB(peakelFileConnection, peakels, ms1SpecHeaders)

                subscriber.onNext(peakels)

              }, { e =>
                subscriber.onError(e)
              }, { () =>
                tryOrPropagateError {
                  logger.debug("All run slices have been analyzed to generate the peakelDB: "+ peakelFile.getName)
                  peakelFileConnection.dispose()
                  subscriber.onCompleted()
                }
              })
            }

          } // ends Observable

          (peakelFile,observablePeakels,ms2SpecHeadersByCycle)
        } // end resultPromise.future.map

        futureResult

      } else {

        val lastPeakelFile = existingPeakelFiles.sortBy( - _.lastModified() ).head
        logger.info(s"Will re-use existing peakelDB for run id=$lcMsRunId (map #$mapNumber): "+lastPeakelFile)

        // Peakel file already exists => reuse it !
        // Create a mapping between the TMP file and the LC-MS run
        entityCache.addPeakelFile(lcMsRunId, lastPeakelFile)

        val mzDb = new MzDbReader(mzDbFile, true)

        try {
          logger.info("Loading scan meta-data from mzDB file: "+mzDbFile)
          val ms2SpectrumHeadersByCycle = mzDb.getMs2SpectrumHeaders().groupByLong(_.getCycle.toInt)
          val observablePeakels = PeakelDbReader.streamPeakels(lastPeakelFile)

          Future.successful( (lastPeakelFile, observablePeakels, ms2SpectrumHeadersByCycle) )

        } finally {
          mzDb.close()
        }

      }

      // Entering the same thread than the Future created above (detection or loading of peakels)
      val peakelRecordingFuture = futurePeakelDetectionResult.flatMap { case (peakelFile, peakelFlow, ms2SpecHeadersByCycle) =>

        //val rxCompScheduler = schedulers.ComputationScheduler() // TODO: remove me => we use now our own thread pool
        val publishedPeakelFlow = peakelFlow.onBackpressureBuffer(PeakelsDetector.BACK_PRESSURE_BUFFER_SIZE).observeOn(rxCompScheduler).publish

        val rTreePromise = Promise[RTree[java.lang.Integer,geometry.Point]]
        //val entriesBuffer = new ArrayBuffer[Entry[java.lang.Integer,geometry.Point]]
        val rTree_lock = new Object()
        var rTree = RTree
          .star()
          .maxChildren(4)
          .create[java.lang.Integer,geometry.Point]()
          //.add(entriesIterable)

        publishedPeakelFlow.doOnSubscribe(
          this.logger.info("Building peakel in-memory R*Tree to provide quick searches...")
        ).onBackpressureBuffer(PeakelsDetector.BACK_PRESSURE_BUFFER_SIZE).subscribe( { peakels =>

          val entriesBuffer = new ArrayBuffer[Entry[java.lang.Integer,geometry.Point]]
          for (peakel <- peakels) {
            val peakelId = new java.lang.Integer(peakel.id)
            val geom = geometry.Geometries.point(peakel.getMz,peakel.getElutionTime())
            entriesBuffer += Entries.entry(peakelId, geom)
          }

          val entriesIterable = collection.JavaConversions.asJavaIterable(entriesBuffer)

          rTree_lock.synchronized {
            rTree = rTree.add(entriesIterable)
          }

        },{ e => // on error
          rTreePromise.failure(e)
        }, { () => // on completed

          rTreePromise.success(rTree)
        })

        val rTreeFuture = rTreePromise.future

        // Optimize meta-data
        logger.debug(s"Optimize meta-data for run id=$lcMsRunId (map #$mapNumber)...")

        val scanSequence = lcMsRun.scanSequence.get

        // Put spectra headers into a matrix structure to optimize the lookup operations
        val lastCycle = scanSequence.scans.last.cycle
        // TODO: replace the ms2SpectrumHeaderMatrix by a LC-MS scan matrix (to avoid re-loading these data from mzDB files)???
        // Note: DBO => if the data are incorrect in the LC-MS db it might be better to always use the mzDB meta-data
        val ms2SpectrumHeaderMatrix = new Array[Array[SpectrumHeader]](lastCycle + 1)
        for ( (cycle,ms2SpectrumHeaders) <- ms2SpecHeadersByCycle) {
          ms2SpectrumHeaderMatrix(cycle.toInt) = ms2SpectrumHeaders
        }

        // Put PSMs into a matrix structure to optimize the lookup operations
        val lastSpecNumber = scanSequence.scans.last.initialId
        val pepMatchesMatrix = new Array[ArrayBuffer[PeptideMatch]](lastSpecNumber + 1)
        logger.debug(s"Last spectrum initial ID of run ${scanSequence.runId} is $lastSpecNumber" )

        for ( (specNum,pepMatches) <- pepMatchesBySpecNumber) {
          try {
            pepMatchesMatrix(specNum.toInt) = pepMatches
          } catch {
            case t: Throwable => {
              throw new Exception(
                s"The spectrum number (${specNum.toInt}) is too high for the considered mzDB file (lastSpecNumber is $lastSpecNumber). " +
                "Please check that the provided mzDB file is matching the MS/MS search result one.",
                t
              )
            }
          }
        }

        // Match detected peakels with validated PSMs
        val peakelMatchesFuture = _buildPeakelMatches(
          publishedPeakelFlow,
          lcMsRun.scanSequence.get,
          ms2SpectrumHeaderMatrix,
          pepMatchesMatrix
        )

        // Execute the peakel flow observable
        val supscription = publishedPeakelFlow.connect

        val combinedFuture = for {
          rTree <- rTreeFuture
          peakelMatches <- peakelMatchesFuture
        } yield {
          // Unsubscribe the connectable observable
          supscription.unsubscribe()
          (peakelFile,rTree,peakelMatches)
        }

        combinedFuture
      }

      // Synchronize all parallel computations that were previously performed
      // We are now returning to single thread execution

      val (peakelFile,rTree,peakelMatches) = Await.result(peakelRecordingFuture, Duration.Inf)

      this.logger.info(s"Total number of MS/MS matched peakels = ${peakelMatches.length} for run id=$lcMsRunId (map #$mapNumber)... ")
      this.logger.info(s"Created R*Tree contains ${rTree.size} peakel indices, for run id=$lcMsRunId (map #$mapNumber)...")

      entityCache.addRTree(lcMsRunId, rTree)

      // Compute some statistics
      if (true) { // Fake test to isolate statistics variables
        val distinctPeptideIds = pepMatchesBySpecNumber.flatMap(_._2.map(_.peptide.id)).toArray.distinct
        runMetrics.setCounter("distinct peptides", distinctPeptideIds.length)

        val assignedPeptideIdMap = peakelMatches.map(_.peptide.id).mapByLong(id => id)
        val orphanPeptideCount = distinctPeptideIds.count(!assignedPeptideIdMap.contains(_))

        runMetrics.setCounter("orphan peptides", orphanPeptideCount)
      }

      // Create a mapping to track peakels already persisted in the LCMSdb
      val mzDbPeakelIdByTmpPeakelId = new LongMap[Int]()
      val peakelCountByPeptideId = new LongMap[Int]()

      val peakelFileConnection = new SQLiteConnection(peakelFile)
      var inMemoryPeakelDb: SQLiteConnection = null
      try {

        // Open the PeakelDB
        peakelFileConnection.openReadonly()
        logger.info(s"Putting the peakelDB in memory...")
        inMemoryPeakelDb = PeakelDbReader.loadPeakelDbInMemory(peakelFileConnection)
        peakelFileConnection.dispose()
        logger.info(s"The peakelDB has been put in memory !")

        // Iterate over peakels mapped with peptides to build features
        this.logger.info("Building features from MS/MS matched peakels...")

        val peakelMatchesByPeakelId = peakelMatches.groupByLong(_.peakel.id)
        for ((peakelId, peakelMatches) <- peakelMatchesByPeakelId) {

          // Re-load the peakel from the peakelDB
          val identifiedPeakel = peakelMatches.head.peakel
          val peakelMatchesByCharge = peakelMatches.groupBy(_.charge)
          val distinctPeptideIds = peakelMatches.map(_.peptide.id).distinct

          if (distinctPeptideIds.length > 1) {
            runMetrics.incr("multi-matched peakels")
          }


          for ((charge, sameChargePeakelMatches) <- peakelMatchesByCharge) {

            var peptides = sameChargePeakelMatches.map(_.peptide).distinct

              val mzDbFt = _createMzDbFeature(
                inMemoryPeakelDb,
                Some(rTree),
                identifiedPeakel,
                charge,
                false,
                sameChargePeakelMatches.map(_.spectrumHeader.getId).distinct.toArray
              )


              // Convert mzDb feature into LC-MS one
              val lcmsFt = this._mzDbFeatureToLcMsFeature(mzDbFt, None, rawMapId, lcMsRun.scanSequence.get, peakelIdByMzDbPeakelId)

              // Store feature peakels
              this._storeLcMsFeaturePeakels(lcmsFt, mzDbFt, rawMapId, peakelPublisher, mzDbPeakelIdByTmpPeakelId)

              // Add feature to the list of raw map features
              rawMapFeatures += lcmsFt

              // Update some mappings

              if (mzDbFt.getPeakelsCount == 1) {
                runMetrics.incr("psm monoisotopic features")
              }

              runMetrics.incr("psm (ions)")

              for (peptide <- peptides) {
                entityCache.addFeatureTuple((lcmsFt, peptide, lcMsRun))

                val peptideAndCharge = (peptide, charge)
                mzDbPeakelIdsByPeptideAndCharge.getOrElseUpdate(peptideAndCharge, ArrayBuffer[Int]()) += identifiedPeakel.id

                if (peptides.length > 1) {
                  entityCache.addConflictingPeptides(peptideAndCharge, peptides)
                }
              }

              //            val goodPrediction = _testIsotopicPatternPrediction(mzDbFt, charge, None, Some(mzDbFt.getElutionTime()), inMemoryPeakelDb, rTree, runMetrics)
              //
              //            if (!goodPrediction) {
              //              // try to predict monoisotope and charge state at the first MSMS rt
              //              val msmsTime = sameChargePeakelMatches.head.spectrumHeader.getElutionTime
              //              val predictionMSMSRt = _testIsotopicPatternPrediction(mzDbFt, charge, Some(mzDbFt.ms2SpectrumIds.head), Some(msmsTime), inMemoryPeakelDb, rTree, runMetrics)
              //              runMetrics.incr("wrong (monoisotope, charge) psm matching")
              //              logger.info("wrong psm peakel match for  {}, {}, {}, {}, {}, {}, {}", mzDbFt.getPeakelsCount(), mzDbFt.mz, mzDbFt.getElutionTime() / 60.0, mzDbFt.getBasePeakel().getApexIntensity(), charge,  peptides.head.sequence, predictionMSMSRt)
              //            }
              //
              //            if (goodPrediction && mzDbFt.getPeakelsCount() == 1) {
              //              logger.info("good psm monoisotopic peakel match for  ({}, {}, {}, {}, {}, {})", mzDbFt.getPeakelsCount(), mzDbFt.mz, mzDbFt.getElutionTime() / 60.0, mzDbFt.getBasePeakel().getApexIntensity(), charge,  peptides.head.sequence)
              //            }

          }
        } // ends for peakelMatchesByPeakelId

        logger.info("(peptide, charge) matched: "+mzDbPeakelIdsByPeptideAndCharge.keySet.size)

      } finally {
        if (inMemoryPeakelDb != null) {
          inMemoryPeakelDb.dispose()
        }
      }

      // Check that the number of built peakels equals the number of persisted ones
      assert(
        peakelIdByMzDbPeakelId.size == mzDbPeakelIdByTmpPeakelId.size,
        s"${mzDbPeakelIdByTmpPeakelId.size} peakels were stored but ${peakelIdByMzDbPeakelId.size} peakels are associated to features!"
      )

      // Attach features to the raw map and convert it to a processed map
      val processedMap = rawMap.copy(features = rawMapFeatures.toArray).toProcessedMap(mapNumber, mapSetId)

      logger.info("Processed map peakels count = "+peakelIdByMzDbPeakelId.size)
      logger.info("Processed map features count = "+processedMap.features.length)

      // Set processed map id of the feature (
      for (procFt <- processedMap.features) {
        procFt.relations.processedMapId = processedMap.id
      }

      // Return the created processed map
      processedMap

    } // ends lcmsRun iteration loop

    parProcessedMaps.toArray.sortBy(_.number)
  }

  private def _storeLcMsFeaturePeakels(
    lcmsFt: LcMsFeature,
    mzDbFt: MzDbFeature,
    rawMapId: Long,
    peakelPublisher: Subject[(Peakel,Long)],
    mzDbPeakelIdByTmpPeakelId: LongMap[Int]
  ) {
    for ( (peakelItem,indexedMzDbPeakel) <- lcmsFt.relations.peakelItems.zip(mzDbFt.indexedPeakels) ) {
      val lcmsPeakel = peakelItem.getPeakel().get
      val lcmsPeakelId = lcmsPeakel.id

      // Do not persist the same peakel twice
      if (lcmsPeakelId < 0 && mzDbPeakelIdByTmpPeakelId.contains(lcmsPeakelId) == false) {
        peakelPublisher.onNext( (lcmsPeakel,rawMapId) )
        mzDbPeakelIdByTmpPeakelId.put(lcmsPeakelId, indexedMzDbPeakel._1.id)
      }

      // Detach peakel from feature (this should decrease memory footprint)
      peakelItem.peakelReference = PeakelIdentifier(peakelItem.peakelReference.id)
    }
  }

  private def _searchForUnidentifiedFeatures(
    processedMapByRunId: LongMap[ProcessedMap],
    peakelPublisher: Subject[(Peakel,Long)],
    mftBuilderByPeptideAndCharge: HashMap[(Peptide, Int), MasterFeatureBuilder],
    putativeFtsByLcMsRunId: LongMap[_ <: Seq[PutativeFeature]],
    putativeFtsByPeptideAndRunId: HashMap[(Peptide, Long), _ <: Seq[PutativeFeature]],
    peptideByPutativeFtId: LongMap[Peptide],
    multiMatchedMzDbPeakelIdsByPutativeFtId: LongMap[ArrayBuffer[Int]],
    entityCache: LcMsMapDetectorEntityCache
  ): Seq[RawMap] = {

    val halfMzWindow = PeakelsDetector.ISOTOPE_PATTERN_HALF_MZ_WINDOW

    // Retrieve some cached entities
    val runsCount = entityCache.runsCount
    val lcMsRuns = entityCache.lcMsRuns
    val metricsByRunId = entityCache.metricsByRunId

    // Iterate lcMsRuns to create raw maps
    for (lcMsRun <- lcMsRuns) yield {

      val lcMsRunId = lcMsRun.id
      val runMetrics = metricsByRunId(lcMsRunId)

      // Retrieve processed an raw maps
      val processedMap = processedMapByRunId(lcMsRunId)
      val rawMap = processedMap.getRawMaps().head.get
      val putativeFtsOpt = putativeFtsByLcMsRunId.get(lcMsRunId)

      val x2RawMap = if (putativeFtsOpt.isEmpty) rawMap
      else {

        // Retrieve putative Features sorted by decreasing intensity
        val putativeFts = putativeFtsOpt.get.sortWith(_.maxObservedIntensity > _.maxObservedIntensity)
        val newLcmsFeatures = new ArrayBuffer[LcMsFeature]()

        this.logger.info(s"Searching for ${putativeFts.length} missing features in run id=$lcMsRunId...")

        // Re-open peakel SQLite file
        val peakelFile = entityCache.getPeakelFile(lcMsRunId).get
        val peakelFileConn = new SQLiteConnection(peakelFile)
        peakelFileConn.openReadonly()
        //peakelFileConn.open(false) // allowCreate = false

        var inMemoryPeakelDb: SQLiteConnection = null

        // Retrieve corresponding R*Tree
        val rTree = entityCache.getRTree(lcMsRunId).get

        // Retrieve the map avoiding the creation of duplicated peakels
        val peakelIdByMzDbPeakelId = entityCache.peakelIdByMzDbPeakelIdByRunId(lcMsRunId)
        val assignedMzDbPeakelIdSet = new HashSet[Int] () ++= peakelIdByMzDbPeakelId.map(_._1.toInt)

        // Create a mapping to track peakels already persisted in the LCMSdb
        val mzDbPeakelIdByTmpPeakelId = peakelIdByMzDbPeakelId.map { case (k,v) => (v,k.toInt) }

        try {
          //val benchmarkMap = new HashMap[String,Long]()
          logger.info(s"Putting the peakelDB in memory...")
          inMemoryPeakelDb = PeakelDbReader.loadPeakelDbInMemory(peakelFileConn)
          peakelFileConn.dispose()
          logger.info(s"The peakelDB has been put in memory !")

          // Perform a first pass search of missing peakels
          logger.info(s"Searching for coeluting peakels of ${putativeFts.length} missing features...")
          val missingFtIdAndCoelutingPeakelIds = putativeFts.par.map { putativeFt =>
            val mz = putativeFt.mz
            val rtTolerance = if (Settings.useAdaptativeRTTolerance) Settings.maxAdaptativeRTTolerance else crossAssignmentConfig.get.ftMappingParams.timeTol
            val peakelIds = PeakelDbHelper.findPeakelIdsInRange(
              rTree,
              mz - halfMzWindow,
              mz + halfMzWindow,
              putativeFt.elutionTime - rtTolerance,
              putativeFt.elutionTime + rtTolerance
            )

            (putativeFt.id.toLong, peakelIds)
          } toArray

          val coelutingPeakelIdsByMissingFtId = missingFtIdAndCoelutingPeakelIds.toLongMap
          val coelutingPeakelIdMap = missingFtIdAndCoelutingPeakelIds.flatMap(_._2).mapByLong(id => id)
          val coelutingPeakelsCount = coelutingPeakelIdMap.size
          val coelutingIdPredicate = { id: Int => coelutingPeakelIdMap.contains(id) }
          val coelutingPeakels = PeakelDbReader.loadManyPeakels(
            inMemoryPeakelDb,
            coelutingPeakelsCount,
            Some(coelutingIdPredicate)
          )

          logger.info(s"Loading $coelutingPeakelsCount coeluting peakels...")
          val coelutingPeakelById = new LongMap[MzDbPeakel](coelutingPeakelsCount)
          for (peakel <- coelutingPeakels) {
            coelutingPeakelById.put(peakel.id, peakel)
          }

          logger.info(s"${coelutingPeakelById.size} coeluting peakels have been loaded !")

          logger.info("Try now to retrieve some unidentified peakels...")

          // Search for missing peakels using parallelized computations
          val putativeFtAndMissingPeakelOptTuples = for (putativeFt <- putativeFts.par) yield {

            val charge = putativeFt.charge
            val peptide = peptideByPutativeFtId(putativeFt.id)
            val mftBuilder = mftBuilderByPeptideAndCharge((peptide, charge))

            val coelutingPeakels = coelutingPeakelIdsByMissingFtId(putativeFt.id).map { peakelId =>
              coelutingPeakelById(peakelId)
            }

            val effectiveRTtolerance = {
              if (Settings.useAdaptativeRTTolerance) {
                val rtTolerance = math.min(math.max(putativeFt.elutionTimeTolerance, crossAssignmentConfig.get.ftMappingParams.timeTol), Settings.maxAdaptativeRTTolerance)
                rtTolerance
              } else {
                crossAssignmentConfig.get.ftMappingParams.timeTol
              }
            }

            Tuple3(putativeFt, mftBuilder, _findUnidentifiedPeakel(
              coelutingPeakels,
              putativeFt.mz,
              putativeFt.charge,
              minTime = putativeFt.elutionTime - effectiveRTtolerance,
              avgTime = putativeFt.elutionTime,
              maxTime = putativeFt.elutionTime + effectiveRTtolerance,
              expectedDuration = mftBuilder.bestFeature.duration,
              assignedMzDbPeakelIdSet = assignedMzDbPeakelIdSet,
              multimatchedMzDbPeakelIds = multiMatchedMzDbPeakelIdsByPutativeFtId(putativeFt.id),
              runMetrics
              //benchmarkMap
            ))
          }

          logger.info("Convert found unidentified peakels into features...")

          for (
            (putativeFt,mftBuilder,missingPeakelOpt) <- putativeFtAndMissingPeakelOptTuples.toArray;
            (missingPeakel, isReliable) <- missingPeakelOpt
          ) {

           if (!crossAssignmentConfig.get.restrainToReliableFeatures || isReliable) {

            val mzDbFt = _createMzDbFeature(
              inMemoryPeakelDb,
              Some(rTree),
              missingPeakel,
              putativeFt.charge,
              true,
              Array.empty[Long]
            )

            if (mzDbFt.getPeakelsCount == 1) runMetrics.incr("missing monoisotopic features")
            if (!isReliable) runMetrics.incr("missing unreliable features count")

            runMetrics.incr("missing feature found")

            // Update putative feature of conflicting peptides in this run to allow peakel re-assignment
            val charge = putativeFt.charge
            val peptide = peptideByPutativeFtId(putativeFt.id)
            val conflictingPeptides = entityCache.getConflictingPeptides((peptide, charge)).orNull
            if (conflictingPeptides != null) {
              for (conflictingPeptide <- conflictingPeptides) {
                for (pft <- putativeFtsByPeptideAndRunId((peptide, lcMsRunId))) {
                  runMetrics.incr("putative peakels list updated")
                  multiMatchedMzDbPeakelIdsByPutativeFtId(pft.id) += missingPeakel.id
                }
              }
            }

            val newLcmsFeature = this._mzDbFeatureToLcMsFeature(
              mzDbFt,
              Some(putativeFt),
              rawMap.id,
              lcMsRun.scanSequence.get,
              peakelIdByMzDbPeakelId
            )
            
            // Store feature peakels
            this._storeLcMsFeaturePeakels(newLcmsFeature,mzDbFt,rawMap.id,peakelPublisher,mzDbPeakelIdByTmpPeakelId)

            if (isReliable) {
              for ( peakel <- mzDbFt.getPeakels() ) {
                assignedMzDbPeakelIdSet += peakel.id
              }
            }
            
            val newFtProps = newLcmsFeature.properties.get
            
            // Set predicted time property
            newFtProps.setPredictedElutionTime(Some(putativeFt.elutionTime))
            
            // Set isReliable property
            newFtProps.setIsReliable(Some(isReliable))
            
            // Set processed map id
            newLcmsFeature.relations.processedMapId = processedMap.id

            // Append newLcmsFt in the buffer to add to it the raw map
            newLcmsFeatures += newLcmsFeature

            // Retrieve master feature builder to append this new feature to its children buffer
            mftBuilder.children += newLcmsFeature

            // Compute some metrics
            val deltaRt = newLcmsFeature.elutionTime - putativeFt.elutionTime
            runMetrics.storeValue("missing predicted retention time", deltaRt) 
            

            }  

          } // end for each found peakel

          logger.info("Missing features search has finished !")

          // Create a new raw map by including the retrieved missing features
          val x2RawMap = rawMap.copy(
            features = rawMap.features ++ newLcmsFeatures
            //peakels = Some(peakelByMzDbPeakelId.values.toArray)
          )

          runMetrics.setCounter("map features count", x2RawMap.features.size)
          
          /*val newMap = benchmarkMap.map(kv => kv._1 -> (kv._2.toFloat / 1e9) )
          logger.debug("benchmarkMap: " +  newMap)*/

          // Return x2RawMap
          x2RawMap
          
        } finally {
          
          if (inMemoryPeakelDb != null) {
            inMemoryPeakelDb.dispose()
          }
          // Release resources
          //peakelFileConn.dispose()
        }
      }

      // Store the raw map (we assume here that peakels have been previously stored)
      //rawMapStorer.storeRawMap(x2RawMap, storePeakels = true)
      
      // Detach peakels from the raw map (this should decrease memory footprint)
      //x2RawMap.peakels = None

      // Detach peakels from features (this should decrease memory footprint)
      //for (ft <- x2RawMap.features; peakelItem <- ft.relations.peakelItems) {
      //  peakelItem.peakelReference = PeakelIdentifier(peakelItem.peakelReference.id)
      //}

      x2RawMap
    } // end for (lcMsRun <- lcMsRuns) => search for missing features
    
  }
  
  private def _createMzDbFeature(
    peakelFileConnection: SQLiteConnection,
    rTreeOpt: Option[RTree[java.lang.Integer,geometry.Point]],
    peakel: MzDbPeakel,
    charge: Int,
    isPredicted: Boolean,
    ms2SpectrumIds: Array[Long]
  ): MzDbFeature = {

    val isotopes = this._findFeatureIsotopes(peakelFileConnection, rTreeOpt, peakel, charge)
    
    //println(s"found ${isotopes.length} isotopes")

    MzDbFeature(
      id = MzDbFeature.generateNewId,
      mz = peakel.getApexMz(),
      charge = charge,
      indexedPeakels = isotopes.zipWithIndex,
      isPredicted = isPredicted,
      ms2SpectrumIds = ms2SpectrumIds
    )
  }

  
  private def _findUnidentifiedPeakel(
    coelutingPeakels: Seq[MzDbPeakel],
    peakelMz: Double,
    charge: Int,
    minTime: Float,
    avgTime: Float,
    maxTime: Float,
    expectedDuration: Float,
    assignedMzDbPeakelIdSet: HashSet[Int],
    multimatchedMzDbPeakelIds: Seq[Int],
    metric: Metric
    //benchmarkMap: HashMap[String,Long]
  ): Option[(MzDbPeakel,Boolean)] = {
    
    val mozTolInDa = MsUtils.ppmToDa(peakelMz, crossAssignmentConfig.get.ftMappingParams.mozTol.get)

    val foundPeakels = coelutingPeakels.filter { peakel =>
      (math.abs(peakelMz - peakel.getApexMz) <= mozTolInDa) && (peakel.getElutionTime() >= minTime) && (peakel.getElutionTime() <= maxTime)
    }

    if (foundPeakels.isEmpty) {
      metric.incr("missing peakel: no peakel found in the peakelDB")
      return None
    }

    // Apply some filters to the found peakels: they must not be already assigned or included in a pool of peakels
    // that could be matched multiple times
    val multimatchedPeakelIdSet = multimatchedMzDbPeakelIds.toSet
    val matchingPeakels = if (Settings.filterAssignedPeakels) {
      foundPeakels.filter { foundPeakel => multimatchedPeakelIdSet.contains(foundPeakel.id) || !assignedMzDbPeakelIdSet.contains(foundPeakel.id) }
    } else {
      foundPeakels
    }

    if ((foundPeakels.size - matchingPeakels.size) > 0) {
      metric.storeValue("missing peakel:more found than unassigned", (foundPeakels.size - matchingPeakels.size))
    }

    val filteredPeakels = PeakelsPatternPredictor.assessReliability(
      mozTolPPM,
      coelutingPeakels,
      matchingPeakels,
      charge,
      mozTolInDa
    )

    if (filteredPeakels.isEmpty) {
      metric.incr("missing peakel: no peakel matching")
      None
    } else if(filteredPeakels.length == 1) {
      metric.incr("missing peakel: only one peakel matching")
      if (filteredPeakels.head._2) { metric.incr("missing peakel: only one peakel matching which is reliable") }
      Some(filteredPeakels.head)
    } else {
      
      var reliablePeakels = filteredPeakels.filter(_._2)
      if (reliablePeakels.isEmpty) { 
        metric.incr("no.reliable.peakel.found") 
        reliablePeakels = filteredPeakels 
      }
      // sort reliablePeakels by mz distance to ensure minBy always returns the same value
      reliablePeakels.sortWith{ (p1,p2) =>  math.abs(peakelMz-p1._1.getApexMz) < math.abs(peakelMz-p2._1.getApexMz) }
      
      val nearestPeakelInTime = reliablePeakels.minBy { case (peakel,isReliable) => 
        math.abs(avgTime - peakel.calcWeightedAverageTime())
      }

      if (true) { // fake condition to isolate metrics computation
        val nearestFilteredPeakelInTime = filteredPeakels.minBy { case (peakel,isReliable) => 
          math.abs(avgTime - peakel.calcWeightedAverageTime())
        }
        if (nearestFilteredPeakelInTime != nearestPeakelInTime) { metric.incr("nearestPeakelInTime.not.reliable") }
        metric.addValue("missing peakel: delta moz", MsUtils.DaToPPM( peakelMz, peakelMz-nearestPeakelInTime._1.getApexMz()))
      }

      Some(nearestPeakelInTime)
    }
  }

  private def _findFeatureIsotopes(
    sqliteConn: SQLiteConnection,
    rTreeOpt: Option[RTree[java.lang.Integer,geometry.Point]],
    peakel: MzDbPeakel,
    charge: Int
  ): Array[MzDbPeakel] = {

    val peakelMz = peakel.getMz
    val mozTolInDa = MsUtils.ppmToDa(peakelMz, quantConfig.detectionParams.get.isotopeMatchingParams.get.mozTol)
    
    val peakelRt = peakel.getElutionTime
    val peakelDurationTol = 1 + math.min(peakel.getElutionTime - peakel.getFirstElutionTime, peakel.getLastElutionTime - peakel.getElutionTime)
    val minRt = peakelRt - peakelDurationTol
    val maxRt = peakelRt + peakelDurationTol
    
    val pattern = IsotopePatternEstimator.getTheoreticalPattern(peakelMz, charge)
    val intensityScalingFactor = peakel.getApexIntensity / pattern.mzAbundancePairs(0)._2
    
    val isotopes = new ArrayBuffer[MzDbPeakel](pattern.isotopeCount)
    isotopes += peakel

    breakable {
      // Note: skip first isotope because it is already included in the isotopes array
      for (isotopeIdx <- 1 until pattern.isotopeCount) {
        
        val prevIsotope = isotopes.last
        val ipMoz = prevIsotope.getMz + (avgIsotopeMassDiff / charge)
        
        // Search for peakels corresponding to second isotope
        val foundPeakels = PeakelDbHelper.findPeakelsInRange(
          sqliteConn,
          rTreeOpt,
          ipMoz - mozTolInDa,
          ipMoz + mozTolInDa,
          minRt,
          maxRt
        )

        if (foundPeakels.nonEmpty) {

          val isotopePeakel = PeakelDbHelper.findCorrelatingPeakel(peakel, foundPeakels)

          val expectedIntensity = pattern.mzAbundancePairs(isotopeIdx)._2 * intensityScalingFactor
          if (isotopePeakel.isDefined) {
            // Gentle constraint on the observed intensity: no more than 4 times the expected intensity
            if (isotopePeakel.get.getApexIntensity() < 4 * expectedIntensity) {
              isotopes += isotopePeakel.get
            } else {
              // TODO: compute statistics of the observed ratios
              logger.trace(s"Isotope intensity is too high: is ${isotopePeakel.get.getApexIntensity()} but expected $expectedIntensity")
              break
            }
          } else {
            logger.trace("Isotope peakel not found")
            break
          }
        } else {
          break
        }
      }
    }

    isotopes.toArray
  }

}

