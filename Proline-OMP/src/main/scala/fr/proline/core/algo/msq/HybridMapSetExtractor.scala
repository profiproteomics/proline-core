package fr.proline.core.algo.msq

import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue

import scala.annotation.elidable
import scala.annotation.elidable.ASSERTION
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.collection.mutable.HashSet
import scala.collection.mutable.LongMap
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration.Duration
import scala.concurrent.forkjoin.ForkJoinPool
import scala.util.control.Breaks.break
import scala.util.control.Breaks.breakable

import com.almworks.sqlite4java.SQLiteConnection
import com.github.davidmoten.rtree.Entries
import com.github.davidmoten.rtree.Entry
import com.github.davidmoten.rtree.RTree
import com.github.davidmoten.rtree.geometry
import com.typesafe.scalalogging.LazyLogging

import fr.profi.ms.algo.IsotopePatternEstimator
import fr.profi.mzdb.FeatureDetectorConfig
import fr.profi.mzdb.MzDbFeatureDetector
import fr.profi.mzdb.MzDbReader
import fr.profi.mzdb.SmartPeakelFinderConfig
import fr.profi.mzdb.algo.PeakelCorrelationScorer
import fr.profi.mzdb.io.peakels.PeakelDbReader
import fr.profi.mzdb.io.peakels.PeakelDbWriter
import fr.profi.mzdb.model.{ Feature => MzDbFeature }
import fr.profi.mzdb.model.{ Peakel => MzDbPeakel }
import fr.profi.mzdb.model.PutativeFeature
import fr.profi.mzdb.model.SpectrumHeader

import fr.profi.mzdb.util.ms.MsUtils

import fr.profi.util.collection.array2longMapBuilder
import fr.profi.util.collection.array2longMapGrouper
import fr.profi.util.collection.filterMonadic2longMapBuilder
import fr.profi.util.collection.traversableOnce2longMapGrouper
import fr.profi.util.collection.tuplesArray2longMapBuilder

import fr.profi.util.metrics.Metric
import fr.proline.context.LcMsDbConnectionContext

import fr.proline.core.algo.lcms.ClusterIntensityComputation
import fr.proline.core.algo.lcms.ClusterTimeComputation
import fr.proline.core.algo.lcms.ClusterizeFeatures
import fr.proline.core.algo.lcms.alignment.AlignmentResult
import fr.proline.core.algo.msq.config.ILcMsQuantConfig

import fr.proline.core.om.model.lcms._
import fr.proline.core.om.model.msi.Peptide
import fr.proline.core.om.model.msi.PeptideMatch
import fr.proline.core.om.storer.lcms.RawMapStorer
import fr.proline.core.om.storer.lcms.impl.PgPeakelWriter
import fr.proline.core.service.lcms.CreateMapSet
import fr.proline.core.service.lcms.io.PeakelsPatternPredictor

import rx.lang.scala.Observable
import rx.lang.scala.Scheduler
import rx.lang.scala.Subject
import rx.lang.scala.schedulers
import rx.lang.scala.subjects.PublishSubject

object HybridMapSetExtractor {
    val ISOTOPE_PATTERN_HALF_MZ_WINDOW = 5
    val MZDB_MAX_PARALLELISM = 2 // Defines how many mzDB files we want to process in parallel

    val threadCount = new java.util.concurrent.atomic.AtomicInteger()
    val terminatedThreadCount = new java.util.concurrent.atomic.AtomicInteger()

    // Create a custom ThreadFactory to provide our customize the name of threads in the pool
    // TODO: do the same thing in mzDB-processing
    val threadFactory = new java.util.concurrent.ThreadFactory {

      def newThread(r: Runnable): Thread = {
        val threadName = s"ExtractMapSet-RTree-Thread-${HybridMapSetExtractor.threadCount.incrementAndGet()}"
        new Thread(r, threadName)
      }
    }

    private var tempDir = new File(System.getProperty("java.io.tmpdir"))

    // Synchronized method used to change the temp directory
    def setTempDirectory(tempDirectory: File) = this.synchronized {
      require(tempDir.isDirectory(), "tempDir must be a directory")

      tempDir = tempDirectory
    }
}


class HybridMapSetExtractor(
  val lcmsDbCtx: LcMsDbConnectionContext,
  mapSetName: String,
  lcMsRuns: Seq[LcMsRun],
  quantConfig: ILcMsQuantConfig,
  val peptideMatchByRunIdAndScanNumber: LongMap[LongMap[ArrayBuffer[PeptideMatch]]]
  ) extends AbstractMapSetExtractor(mapSetName, lcMsRuns, quantConfig) with LazyLogging {

  protected val rawMapStorer = RawMapStorer(lcmsDbCtx)
  protected val peakelWriter = rawMapStorer.peakelWriter.get
  
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
    val featureTuples = new ArrayBuffer[(Feature, Peptide, LcMsRun)]()
    
    def addFeatureTuple(featureTuple: (Feature, Peptide, LcMsRun)) {
      featureTuples_lock.synchronized {
        featureTuples += featureTuple
      }
    }
    
    private val conflictingPeptidesMap_lock = new Object()
    val conflictingPeptidesMap = new HashMap[(Peptide, Int), ArrayBuffer[Peptide]]()

    def getConflictedPeptides(peptideAndCharge: (Peptide, Int)): Option[ArrayBuffer[Peptide]] = {
      conflictingPeptidesMap.get(peptideAndCharge)
    }
    def addConflictedPeptides(peptideAndCharge: (Peptide, Int), peptides: Seq[Peptide]) {
      conflictingPeptidesMap_lock.synchronized {
        conflictingPeptidesMap.getOrElseUpdate(peptideAndCharge, ArrayBuffer[Peptide]()) ++= peptides
      }
    }
  }
   
  def extractMapSet(
    lcMsRuns: Seq[LcMsRun],
    mzDbFileByLcMsRunId: LongMap[File],
    mapSetId: Long): (MapSet, AlignmentResult) = {

    val intensityComputationMethod = ClusterIntensityComputation.withName(
      clusteringParams.intensityComputation.toUpperCase()
    )
    val timeComputationMethod = ClusterTimeComputation.withName(
      clusteringParams.timeComputation.toUpperCase()
    )

    val detectorEntityCache = new LcMsMapDetectorEntityCache(
      lcMsRuns: Seq[LcMsRun],
      mzDbFileByLcMsRunId: LongMap[File],
      mapSetId
    )
    // Customize how many files we want to process in parallel
    val forkJoinPool = new scala.concurrent.forkjoin.ForkJoinPool(HybridMapSetExtractor.MZDB_MAX_PARALLELISM)
    val computationThreadPool = Executors.newFixedThreadPool(
      HybridMapSetExtractor.MZDB_MAX_PARALLELISM,
      HybridMapSetExtractor.threadFactory
    )
    implicit val rxCompScheduler = rx.lang.scala.JavaConversions.javaSchedulerToScalaScheduler(
      rx.schedulers.Schedulers.from(computationThreadPool)
    )

    try {

      // Create a peakel publisher that will be used to insert a stream of peakels in the LCMSdb
      // Important note: the toSerialized method enables thread-safety
      // Sources:
      // - http://reactivex.io/RxJava/javadoc/rx/subjects/SerializedSubject.html
      // - http://stackoverflow.com/questions/31841809/is-serializedsubject-necessary-for-thread-safety-in-rxjava
      // - http://tomstechnicalblog.blogspot.fr/2016/03/rxjava-problem-with-subjects.html
      // - http://davesexton.com/blog/post/To-Use-Subject-Or-Not-To-Use-Subject.aspx
      val peakelPublisher = PublishSubject[(Peakel, Long)]().toSerialized

      logger.info(s"Inserting ${detectorEntityCache.runsCount} raw maps before peakel detection...")
      val rawMaps = lcMsRuns.map { lcMsRun =>

        // Create and insert the raw map
        val rawMap = RawMap(
          id = RawMap.generateNewId(),
          name = lcMsRun.rawFile.name,
          isProcessed = false,
          creationTimestamp = new java.util.Date,
          features = Array.empty[Feature],
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

      val mapSetDectionFuture = Future {

        // Observe the peakel publisher in a separate thread
        peakelPublisher.subscribe({
          case (peakel, rawMapId) =>
            // Update raw map id
            // TODO: do this before publishing the peakel
            peakel.rawMapId = rawMapId
            publishedPeakelQueue.put(Some(peakel))
        }, { e =>
          throw e
        }, { () =>
          isLastPublishedPeakel = true
          publishedPeakelQueue.put(None) // add a None peakel to stop the queue monitoring
          logger.info("Peakel stream successfully published !")
        })

        logger.info("Detecting LC-MS maps...")
        val processedMaps = this._detectMapsFromPeakels(rawMaps, detectorEntityCache, peakelPublisher, forkJoinPool, rxCompScheduler)

        // Create some new mappings between LC-MS runs and the processed maps
        val processedMapByRunId = new LongMap[ProcessedMap]()
        val lcmsRunByProcMapId = new LongMap[LcMsRun](detectorEntityCache.runsCount)

        for ((lcMsRun, processedMap) <- lcMsRuns.zip(processedMaps)) {
          lcmsRunByProcMapId += processedMap.id -> lcMsRun
          processedMapByRunId += lcMsRun.id -> processedMap
        }

        this.logger.info("Creating new map set...")

        // Align maps
        // TODO: create a new map aligner algo starting from existing master features
        val alnResult = mapAligner.computeMapAlignments(processedMaps, alnParams)

        // Create a temporary in-memory map set
        var tmpMapSet = new MapSet(
          id = mapSetId,
          name = mapSetName,
          creationTimestamp = new java.util.Date,
          childMaps = processedMaps.toArray,
          alnReferenceMapId = alnResult.alnRefMapId,
          mapAlnSets = alnResult.mapAlnSets
        )

        // Retrieve some mappings
        val metricsByRunId = detectorEntityCache.metricsByRunId
        val mzDbPeakelIdsByPeptideAndChargeByRunId = detectorEntityCache.mzDbPeakelIdsByPeptideAndChargeByRunId

        // Define some data structures
        // TODO: add these structures to detectorEntityCache
        val mftBuilderByPeptideAndCharge = new HashMap[(Peptide, Int), MasterFeatureBuilder]()
        val putativeFtsByRunId = new LongMap[ArrayBuffer[PutativeFeature]]()
        val putativeFtsByPeptideAndRunId = new HashMap[(Peptide, Long), ArrayBuffer[PutativeFeature]]()
        val peptideByPutativeFtId = new LongMap[Peptide]()
        val multiMatchedMzDbPeakelIdsByPutativeFtId = new LongMap[ArrayBuffer[Int]]()

        this.logger.info("Building the master map...")

        // Group found features by peptide and charge to build master features
        val featureTuplesByPepAndCharge = detectorEntityCache.featureTuples.groupBy { ft => (ft._2, ft._1.charge) }
        for (((peptide, charge), masterFeatureTuples) <- featureTuplesByPepAndCharge) {

          val masterFtChildren = new ArrayBuffer[Feature](masterFeatureTuples.length)
          val featureTuplesByLcMsRun = masterFeatureTuples.groupBy(_._3)

          // Iterate over each LC-MS run
          val runsWithMissingFt = new ArrayBuffer[LcMsRun]()
          for (lcMsRun <- lcMsRuns) {

            val lcMsRunId = lcMsRun.id

            // Check if a feature corresponding to this peptide has been found in this run
            if (featureTuplesByLcMsRun.contains(lcMsRun) == false) {
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
          val bestFt = masterFtChildren.maxBy(_.intensity)
          val bestFtProcMapId = bestFt.relations.processedMapId
          val bestFtLcMsRun = lcmsRunByProcMapId(bestFtProcMapId)

          // compute RT prediction Stats 
          for (feature <- masterFtChildren) {
            if (feature != bestFt) {
              val bestFtProcMapId = bestFt.relations.processedMapId
              val ftProcMapId = feature.relations.processedMapId
              val predictedRt = tmpMapSet.convertElutionTime(
                bestFt.elutionTime,
                bestFtProcMapId,
                ftProcMapId
              )

              if (predictedRt <= 0) {
                metricsByRunId(bestFtLcMsRun.id).incr("unpredictable peptide elution time")
              } else {
                val deltaRt = feature.elutionTime - predictedRt
                metricsByRunId(bestFtLcMsRun.id).storeValue("assigned peptides predicted retention time", (deltaRt))
              }
            }
          }

          val mftBuilder = new MasterFeatureBuilder(
            bestFeature = bestFt,
            children = masterFtChildren,
            peptideId = peptide.id // attach the peptide id to the master feature
          )
          mftBuilderByPeptideAndCharge += (peptide, charge) -> mftBuilder

          // Create a putative feature for each missing one
          for (lcMsRun <- runsWithMissingFt) {

            val lcMsRunId = lcMsRun.id
            val currentProcMapId = processedMapByRunId(lcMsRunId).id

            var predictedTime = tmpMapSet.convertElutionTime(
              bestFt.elutionTime,
              bestFtProcMapId,
              currentProcMapId
            )

            // Fix negative predicted times
            if (predictedTime <= 0) predictedTime = 1f

            val pf = new PutativeFeature(
              id = PutativeFeature.generateNewId,
              mz = bestFt.moz,
              charge = bestFt.charge,
              elutionTime = predictedTime,
              evidenceMsLevel = 2
            )
            pf.isPredicted = true
            pf.maxObservedIntensity = bestFt.apexIntensity

            val multiMatchedMzDbPeakelIds = ArrayBuffer.empty[Int]
            val conflictingPeptides = detectorEntityCache.getConflictedPeptides((peptide, charge)).orNull
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

        } // end (peptide, charge) loop

        // Delete created TMP files (they should be deleted on exit if program fails)
        //    for (tmpFile <- peakelFileByRun.values) {
        //      tmpFile.delete
        //    }

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
          val mft = mftBuilder.toMasterFeature()
          require(mft.children.length <= lcMsRuns.length, "master feature contains more child features than maps")

          def updateCorrectedElutionTime(ft: Feature) {
            ft.correctedElutionTime = Some(
              tmpMapSet.convertElutionTime(
                ft.elutionTime,
                ft.relations.processedMapId,
                alnRefMapId
              )
            )
          }

          for (ft <- mft.children) {
            updateCorrectedElutionTime(ft)
            if (ft.isCluster) ft.subFeatures.foreach(updateCorrectedElutionTime(_))
          }

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

        (tmpMapSet, x2RawMaps)
      } // End of Future

      mapSetDectionFuture.onFailure { case t: Throwable => throw t }

      logger.info("Streaming peakel insertion in the LCMSdb...")

      // Convert the peakel queue into a lambda stream that will be passed to the peakel storer
      val forEachPeakel = { onEachPeakel: (Peakel => Unit) =>

        while (isLastPublishedPeakel == false || !publishedPeakelQueue.isEmpty()) {
          val peakelOpt = publishedPeakelQueue.take()
          if (peakelOpt.isDefined) {
            onEachPeakel(peakelOpt.get)

            val otherPeakelOpts = new java.util.ArrayList[Option[Peakel]]
            publishedPeakelQueue.drainTo(otherPeakelOpts)

            val otherPeakelOptsIter = otherPeakelOpts.iterator()
            while (otherPeakelOptsIter.hasNext()) {
              onEachPeakel(otherPeakelOptsIter.next.get)
            }
          }
        }

        ()
      }

      // Insert the peakel stream
      val peakelIdByTmpIdByRawMapId = peakelWriter.asInstanceOf[PgPeakelWriter].insertPeakelStream(forEachPeakel, None)

      // Synchronize map set detection operations
      // We are now returning to the main thread
      val (tmpMapSet, x2RawMaps) = Await.result(mapSetDectionFuture, Duration.Inf)

      // Update feature peakel item IDs
      for (
        x2RawMap <- x2RawMaps;
        val rawMapId = x2RawMap.id;
        val peakelIdByTmpId = peakelIdByTmpIdByRawMapId(rawMapId);
        ft <- x2RawMap.features;
        peakelItem <- ft.relations.peakelItems
      ) {
        val peakelRef = peakelItem.peakelReference
        val tmpPeakelId = peakelRef.id
        val newPeakelId = peakelIdByTmpId(tmpPeakelId)
        peakelRef.asInstanceOf[PeakelIdentifier].id = newPeakelId
      }

      // Storing features of each detected raw map
      for ((x2RawMap, idx) <- x2RawMaps.zipWithIndex) {
        logger.info(s"Storing features of raw map #${idx + 1} (id=${x2RawMap.id})...")

        rawMapStorer.featureWriter.insertFeatures(x2RawMap.features, x2RawMap.id, linkToPeakels = true)

        // Log some raw map information
        val rawMapPeakelsCount = peakelIdByTmpIdByRawMapId(x2RawMap.id).size
        logger.info("Raw map peakels count = " + rawMapPeakelsCount)
        logger.info(detectorEntityCache.metricsByRunId(x2RawMap.runId).toString)
      }

      // Memorize processed maps temporary ID
      val procMapTmpIdByRawMapId = new LongMap[Long](detectorEntityCache.runsCount)
      for ((x2RawMap, processedMap) <- x2RawMaps.zip(tmpMapSet.childMaps)) {
        procMapTmpIdByRawMapId.put(x2RawMap.id, processedMap.id)
      }

      // --- Persist the corresponding map set ---
      val x2MapSet = CreateMapSet(lcmsDbCtx, mapSetName, tmpMapSet.childMaps)

      // Map processed map id by corresponding temp id
      val procMapIdByTmpId = new LongMap[Long](lcMsRuns.length)
      for (processedMap <- tmpMapSet.childMaps) {
        val rawMap = processedMap.getRawMaps().head.get
        val oldProcessedMapId = procMapTmpIdByRawMapId.get(rawMap.id).get
        procMapIdByTmpId.put(oldProcessedMapId, processedMap.id)
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
      // Shutdown the thread pools
      if (computationThreadPool.isShutdown() == false) computationThreadPool.shutdownNow()
      if (forkJoinPool.isShutdown() == false) forkJoinPool.shutdownNow()

      // Update terminatedThreadCount and reset the ThreadCount
      val terminatedThreadCount = HybridMapSetExtractor.terminatedThreadCount.addAndGet(HybridMapSetExtractor.MZDB_MAX_PARALLELISM)
      if (terminatedThreadCount == HybridMapSetExtractor.threadCount) {
        HybridMapSetExtractor.threadCount.set(0)
      }

    }

  }

  case class PeakelMatch(
    peakel: MzDbPeakel,
    peptide: Peptide,
    spectrumHeader: SpectrumHeader,
    charge: Int)

  private def _buildPeakelMatches(
    peakelFlow: Observable[Array[MzDbPeakel]],
    scanSequence: LcMsScanSequence,
    ms2SpectrumHeaderMatrix: Array[Array[SpectrumHeader]],
    pepMatchesMatrix: Array[ArrayBuffer[PeptideMatch]]): Future[ArrayBuffer[PeakelMatch]] = {

    val peakelMatches = new ArrayBuffer[PeakelMatch](scanSequence.ms2ScansCount)
    val peakelMatchesPromise = Promise[ArrayBuffer[PeakelMatch]]

    peakelFlow.doOnSubscribe(
      this.logger.info("Mapping peakels to PSMs...")
    ).subscribe({ detectedPeakels =>

        for (detectedPeakel <- detectedPeakels) {

          val peakelMz = detectedPeakel.getMz
          // TODO: define a specific m/z tolerance for this procedure or fix a low hardcoded value ???
          val ms2MatchingMzTolDa = MsUtils.ppmToDa(peakelMz, quantConfig.ftMappingParams.mozTol)
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

                // Double check we are in the right time range
                // TODO: is this really needed ?
                if (time >= firstTime && time <= lastTime) {

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
                          psm.charge
                        )
                      }
                      psmIdx += 1
                    }
                  }
                }

                ms2ShIdx += 1
              } // ends while (ms2ShIdx < ms2SpecHeadersCount)
            } // ends while (curCycle <= maxCycle)

            curCycle += 1
          } // ends while (curCycle <= maxCycle) 

        } // ends for detectedPeakels

      }, { e =>
        throw e
      }, { () =>
        peakelMatchesPromise.success(peakelMatches)
      })

    peakelMatchesPromise.future
  }

  private def _detectMapsFromPeakels(
    rawMaps: Seq[RawMap],
    entityCache: LcMsMapDetectorEntityCache,
    peakelPublisher: Subject[(Peakel, Long)],
    forkJoinPool: ForkJoinPool,
    rxCompScheduler: Scheduler): Array[ProcessedMap] = {

    val rawMapByRunId = rawMaps.mapByLong(_.runId)
    val lcMsRuns = entityCache.lcMsRuns
    val mapSetId = entityCache.mapSetId
    val mzDbFileByLcMsRunId = entityCache.mzDbFileByLcMsRunId
    val metricsByRunId = entityCache.metricsByRunId
    val peakelIdByMzDbPeakelIdByRunId = entityCache.peakelIdByMzDbPeakelIdByRunId
    val mzDbPeakelIdsByPeptideAndChargeByRunId = entityCache.mzDbPeakelIdsByPeptideAndChargeByRunId

    val groupedParLcMsRuns = lcMsRuns.grouped(HybridMapSetExtractor.MZDB_MAX_PARALLELISM).map(_.par).toList

    val parProcessedMaps = for (parLcMsRuns <- groupedParLcMsRuns; lcMsRun <- parLcMsRuns) yield {

      val mapNumber = lcMsRun.number
      val lcMsRunId = lcMsRun.id
      val rawMap = rawMapByRunId(lcMsRunId)
      val rawMapId = rawMap.id
      require(rawMapId > 0, "the raw map must be persisted")

      // Open mzDB file
      val mzDbFile = mzDbFileByLcMsRunId(lcMsRunId)

      // Create a buffer to store built features
      val rawMapFeatures = new ArrayBuffer[Feature]()

      // Retrieve the psmByScanNumber mapping for this run
      val pepMatchesBySpecNumber = peptideMatchByRunIdAndScanNumber.getOrElse(lcMsRunId, LongMap.empty[ArrayBuffer[PeptideMatch]])

      // Create a mapping avoiding the creation of duplicated peakels
      val peakelIdByMzDbPeakelId = peakelIdByMzDbPeakelIdByRunId(lcMsRunId)

      val mzDbPeakelIdsByPeptideAndCharge = mzDbPeakelIdsByPeptideAndChargeByRunId(lcMsRunId)

      val runMetrics = metricsByRunId(lcMsRunId)

      // Search for existing Peakel file
      val existingPeakelFiles = HybridMapSetExtractor.tempDir.listFiles.filter(_.getName.startsWith(s"${lcMsRun.getRawFileName}-"))
      val needPeakelDetection = (quantConfig.useLastPeakelDetection == false || existingPeakelFiles.isEmpty)

      val futurePeakelDetectionResult = if (needPeakelDetection) {

        // Remove TMP files if they exist
        existingPeakelFiles.foreach(_.delete())

        this.logger.info(s"Start peakel detection for run id=$lcMsRunId (map #$mapNumber) from " + mzDbFile.getAbsolutePath())

        // Create TMP file to store orphan peakels which will be deleted after JVM exit
        val peakelFile = File.createTempFile(s"${lcMsRun.getRawFileName}-", ".sqlite")
        //peakelFile.deleteOnExit()
        this.logger.debug(s"Creating tmp file for run id=$lcMsRunId at: " + peakelFile)

        // Create a mapping between the TMP file and the LC-MS run
        entityCache.addPeakelFile(lcMsRunId, peakelFile)

        val resultPromise = Promise[(Observable[Array[MzDbPeakel]], LongMap[Array[SpectrumHeader]])]

        Future {
          this.logger.info(s"Detecting peakels in raw MS survey for run id=$lcMsRunId...")

          val mzDb = new MzDbReader(mzDbFile, true)

          try {
            // Instantiate the feature detector
            val mzdbFtDetector = new MzDbFeatureDetector(
              mzDb,
              FeatureDetectorConfig(
                msLevel = 1,
                mzTolPPM = mozTolPPM,
                minNbOverlappingIPs = 5,
                peakelFinderConfig = SmartPeakelFinderConfig(maxIntensityRelThresh = 0.75f)
              )
            )

            // Launch the peakel detection
            val onDetectedPeakels = { observablePeakels: Observable[Array[MzDbPeakel]] =>
              resultPromise.success(observablePeakels, mzdbFtDetector.ms2SpectrumHeadersByCycle)
            }
            mzdbFtDetector.detectPeakelsAsync(mzDb.getLcMsRunSliceIterator(), onDetectedPeakels)

            // Used to simulate the old behavior
            /*val observablePeakels = Observable[Array[MzDbPeakel]] { subscriber =>
              val mzDb2 = new MzDbReader(mzDbFile, true)
              val mzdbFtDetector2 = new MzDbFeatureDetector(
                mzDb2,
                FeatureDetectorConfig(
                  msLevel = 1,
                  mzTolPPM = mozTolPPM,
                  minNbOverlappingIPs = 5
                )
              )
                
              subscriber.onNext(mzdbFtDetector2.detectPeakels(mzDb2.getLcMsRunSliceIterator()))
              subscriber.onCompleted()
              mzDb2.close()
            }
            
            resultPromise.success(observablePeakels,mzdbFtDetector.ms2SpectrumHeadersByCycle)*/

          } finally {
            mzDb.close()
          }

        } onFailure {
          case t: Throwable => {
            logger.info("Can't perform peakel detection", t)
            throw t
          }
        }

        val futureResult = resultPromise.future.map {
          case (observableRunSlicePeakels, ms2SpecHeadersByCycle) =>

            val rxIOScheduler = schedulers.IOScheduler()

            val observablePeakels = Observable[Array[MzDbPeakel]] { subscriber =>

              logger.info("Observing detected peakels to store them in the PeakelDB...")

              // Open TMP SQLite file
              lazy val peakelFileConnection = PeakelDbWriter.initPeakelStore(peakelFile)

              observableRunSlicePeakels.observeOn(rxIOScheduler).subscribe({ peakels =>

                logger.trace("Storing run slice peakels...")

                // Store peakels in SQLite file
                PeakelDbWriter.storePeakelsInPeakelDB(peakelFileConnection, peakels)

                subscriber.onNext(peakels)

              }, { e =>
                subscriber.onError(e)
              }, { () =>
                peakelFileConnection.dispose()
                subscriber.onCompleted()
              })
            } // ends Observable

            (peakelFile, observablePeakels, ms2SpecHeadersByCycle)
        }

        futureResult

      } else {

        logger.info(s"Will re-use existing peakelDB for run id=$lcMsRunId (map #$mapNumber): " + existingPeakelFiles(0))

        // Peakel file already exists => reuse it ! 
        // Create a mapping between the TMP file and the LC-MS run
        entityCache.addPeakelFile(lcMsRunId, existingPeakelFiles(0))

        val mzDb = new MzDbReader(mzDbFile, true)

        try {
          logger.info("Loading scan meta-data from mzDB file: " + mzDbFile)
          val ms2SpectrumHeadersByCycle = mzDb.getMs2SpectrumHeaders().groupByLong(_.getCycle.toInt)

          val peakelFile = existingPeakelFiles(0)
          val observablePeakels = _streamPeakels(peakelFile)

          Future.successful((existingPeakelFiles(0), observablePeakels, ms2SpectrumHeadersByCycle))

        } finally {
          mzDb.close()
        }

      }

      // Entering the same thread than the Future created above (detection or loading of peakels)
      val peakelRecordingFuture = futurePeakelDetectionResult.flatMap {
        case (peakelFile, peakelFlow, ms2SpecHeadersByCycle) =>

          //val rxCompScheduler = schedulers.ComputationScheduler() // TODO: remove me => we use now our own thread pool
          val publishedPeakelFlow = peakelFlow.observeOn(rxCompScheduler).onBackpressureBuffer(1000).publish

          val rTreePromise = Promise[RTree[java.lang.Integer, geometry.Point]]
          //val entriesBuffer = new ArrayBuffer[Entry[java.lang.Integer,geometry.Point]]
          val rTree_lock = new Object()
          var rTree = RTree
            .star()
            .maxChildren(4)
            .create[java.lang.Integer, geometry.Point]()
          //.add(entriesIterable)

          publishedPeakelFlow.doOnSubscribe(
            this.logger.info("Building peakel in-memory R*Tree to provide quick searches...")
          ).subscribe({ peakels =>

              val entriesBuffer = new ArrayBuffer[Entry[java.lang.Integer, geometry.Point]]
              for (peakel <- peakels) {
                val peakelId = new java.lang.Integer(peakel.id)
                val geom = geometry.Geometries.point(peakel.getMz, peakel.getElutionTime())
                entriesBuffer += Entries.entry(peakelId, geom)
              }

              val entriesIterable = collection.JavaConversions.asJavaIterable(entriesBuffer)

              rTree_lock.synchronized {
                rTree = rTree.add(entriesIterable)
              }

            }, { e => // on error
              throw e
            }, { () => // on completed

              /*val entriesIterable = collection.JavaConversions.asJavaIterable(entriesBuffer)
          
          val rTree = RTree
            .star()
            .maxChildren(4)
            .create[java.lang.Integer,geometry.Point]()
            .add(entriesIterable)*/

              rTreePromise.success(rTree)
            })

          val rTreeFuture = rTreePromise.future

          /*val emptyRTree = RTree
            .star()
            .maxChildren(4)
            .create[java.lang.Integer,geometry.Point]()
        val rTreeFuture = Future.successful(emptyRTree)*/

          // Optimize meta-data
          logger.debug(s"Optimize meta-data for run id=$lcMsRunId (map #$mapNumber)...")

          val scanSequence = lcMsRun.scanSequence.get

          // Put spectra headers into a matrix structure to optimize the lookup operations
          val lastCycle = scanSequence.scans.last.cycle
          val ms2SpectrumHeaderMatrix = new Array[Array[SpectrumHeader]](lastCycle + 1)
          for ((cycle, ms2SpectrumHeaders) <- ms2SpecHeadersByCycle) {
            ms2SpectrumHeaderMatrix(cycle.toInt) = ms2SpectrumHeaders
          }

          // Put PSMs into a matrix structure to optimize the lookup operations
          val lastSpecNumber = scanSequence.scans.last.initialId
          val pepMatchesMatrix = new Array[ArrayBuffer[PeptideMatch]](lastSpecNumber + 1)
          logger.debug(s"Last spectrum initial ID of run ${scanSequence.runId} is $lastSpecNumber")

          for ((specNum, pepMatches) <- pepMatchesBySpecNumber) {
            pepMatchesMatrix(specNum.toInt) = pepMatches
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
            (peakelFile, rTree, peakelMatches)
          }

          combinedFuture
      }

      peakelRecordingFuture.onFailure { case t: Throwable => throw t }

      // Synchronize all parallel computations that were previously performed
      // We are now returning to single thread execution
      val (peakelFile, rTree, peakelMatches) = Await.result(peakelRecordingFuture, Duration.Inf)

      this.logger.info("Total number of MS/MS matched peakels = " + peakelMatches.length)

      this.logger.info(s"Created R*Tree contains ${rTree.size} peakel indices")
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

          // Note: old way to compute this metric
          //runMetrics.setCounter("peptides sharing peakels", peakelIdByMzDbPeakelId.filter(_._2.featuresCount > 1).size)
          // This computation was not the number of peptides share a peakel 
          // but the number of peakels that are matched by multiple peptides (also called multi-matched peakels)
          // I keep the same metric naming but I think it should be changed
          if (distinctPeptideIds.length > 1) {
            runMetrics.incr("peptides sharing peakels")
          }

          for ((charge, sameChargePeakelMatches) <- peakelMatchesByCharge) {

            val mzDbFt = _createMzDbFeature(
              inMemoryPeakelDb,
              Some(rTree),
              identifiedPeakel,
              charge,
              false,
              sameChargePeakelMatches.map(_.spectrumHeader.getId).distinct.toArray
            )
            if (mzDbFt.getPeakelsCount == 1) runMetrics.incr("psm monoisotopic features")

            // Convert mzDb feature into LC-MS one
            val lcmsFt = this._mzDbFeatureToLcMsFeature(mzDbFt, None, rawMapId, lcMsRun.scanSequence.get, peakelIdByMzDbPeakelId)

            // Store feature peakels
            this._storeLcMsFeaturePeakels(lcmsFt, mzDbFt, rawMapId, peakelPublisher, mzDbPeakelIdByTmpPeakelId)

            // Add feature to the list of raw map features
            rawMapFeatures += lcmsFt

            // Update some mappings
            val peptides = sameChargePeakelMatches.map(_.peptide).distinct

            for (peptide <- peptides) {
              entityCache.addFeatureTuple((lcmsFt, peptide, lcMsRun))

              val peptideAndCharge = (peptide, charge)
              mzDbPeakelIdsByPeptideAndCharge.getOrElseUpdate(peptideAndCharge, ArrayBuffer[Int]()) += identifiedPeakel.id

              if (peptides.length > 1) {
                entityCache.addConflictedPeptides(peptideAndCharge, peptides)

                // FIXME: DBO => what is the purpose of this metric ?
                // Why is it incremented multiple times in the for loop ?
                // Why is it computed for different charge states ?
                // Isn't it redundant with the "peptides sharing peakels" metric ?
                // I think it should be updated by using the previously computed distinctPeptideIds
                runMetrics.incr("conflicting peakels (associated with more than one peptide)")
              }
            }

            //_testIsotopicPatternPrediction(mzDb, inMemoryPeakelDb, rTree, mzDbFt, runMetrics)

          }
        } // ends for peakelMatchesByPeakelId

      } catch {
        case e: Exception => {
          peakelPublisher.onError(e)
          throw e
        }
      } finally {
        inMemoryPeakelDb.dispose()
      }

      // Check that the number of built peakels equals the number of persisted ones
      assert(peakelIdByMzDbPeakelId.size == mzDbPeakelIdByTmpPeakelId.size)

      // Attach features to the raw map and convert it to a processed map
      val processedMap = rawMap.copy(features = rawMapFeatures.toArray).toProcessedMap(mapNumber, mapSetId)

      logger.info("Processed map peakels count = " + peakelIdByMzDbPeakelId.size)
      logger.info("Processed map features count = " + processedMap.features.length)

      // Set processed map id of the feature (
      for (procFt <- processedMap.features) {
        procFt.relations.processedMapId = processedMap.id
      }

      // Return the created processed map
      processedMap

    } // ends lcmsRun iteration loop

    parProcessedMaps.toArray
  }

  private def _storeLcMsFeaturePeakels(
    lcmsFt: Feature,
    mzDbFt: MzDbFeature,
    rawMapId: Long,
    peakelPublisher: Subject[(Peakel, Long)],
    mzDbPeakelIdByTmpPeakelId: LongMap[Int]) {
    for ((peakelItem, indexedMzDbPeakel) <- lcmsFt.relations.peakelItems.zip(mzDbFt.indexedPeakels)) {
      val lcmsPeakel = peakelItem.getPeakel().get

      // Do not persist the same peakel twice
      if (mzDbPeakelIdByTmpPeakelId.contains(lcmsPeakel.id) == false) {
        peakelPublisher.onNext((lcmsPeakel, rawMapId))
        mzDbPeakelIdByTmpPeakelId.put(lcmsPeakel.id, indexedMzDbPeakel._1.id)
      }

      // Detach peakel from feature (this should decrease memory footprint)
      peakelItem.peakelReference = PeakelIdentifier(peakelItem.peakelReference.id)
    }
  }

  private def _searchForUnidentifiedFeatures(
    processedMapByRunId: LongMap[ProcessedMap],
    peakelPublisher: Subject[(Peakel, Long)],
    mftBuilderByPeptideAndCharge: HashMap[(Peptide, Int), MasterFeatureBuilder],
    putativeFtsByLcMsRunId: LongMap[_ <: Seq[PutativeFeature]],
    putativeFtsByPeptideAndRunId: HashMap[(Peptide, Long), _ <: Seq[PutativeFeature]],
    peptideByPutativeFtId: LongMap[Peptide],
    multiMatchedMzDbPeakelIdsByPutativeFtId: LongMap[ArrayBuffer[Int]],
    entityCache: LcMsMapDetectorEntityCache): Seq[RawMap] = {

    val halfMzWindow = HybridMapSetExtractor.ISOTOPE_PATTERN_HALF_MZ_WINDOW

    // Retrieve some cached entities
    val runsCount = entityCache.runsCount
    val lcMsRuns = entityCache.lcMsRuns
    val metricsByRunId = entityCache.metricsByRunId
    val mzDbFileByLcMsRunId = entityCache.mzDbFileByLcMsRunId

    // Iterate lcMsRuns to create raw maps
    for (lcMsRun <- lcMsRuns) yield {

      val lcMsRunId = lcMsRun.id
      val runMetrics = metricsByRunId(lcMsRunId)
      val mzDbFile = mzDbFileByLcMsRunId(lcMsRunId)
      val mzDb = new MzDbReader(mzDbFile, true)

      // Retrieve processed an raw maps
      val processedMap = processedMapByRunId(lcMsRunId)
      val rawMap = processedMap.getRawMaps().head.get
      val putativeFtsOpt = putativeFtsByLcMsRunId.get(lcMsRunId)

      val x2RawMap = if (putativeFtsOpt.isEmpty) rawMap
      else {

        // Retrieve putative Features sorted by decreasing intensity
        val putativeFts = putativeFtsOpt.get.sortWith(_.maxObservedIntensity > _.maxObservedIntensity)
        val newLcmsFeatures = new ArrayBuffer[Feature]()

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
        val assignedMzDbPeakelIdSet = new HashSet[Int]() ++= peakelIdByMzDbPeakelId.map(_._1.toInt)

        // Create a mapping to track peakels already persisted in the LCMSdb
        val mzDbPeakelIdByTmpPeakelId = peakelIdByMzDbPeakelId.map { case (k, v) => (v, k.toInt) }

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
            val peakelIds = PeakelDbReader.findPeakelIdsInRange(
              rTree,
              mz - halfMzWindow,
              mz + halfMzWindow,
              putativeFt.elutionTime - ftMappingParams.timeTol,
              putativeFt.elutionTime + ftMappingParams.timeTol
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
          for (
            //peakelIds <- coelutingPeakelIds.sorted.grouped(999);
            //peakel <- this._loadPeakelsForIds(inMemoryPeakelDb, peakelIds)
            peakel <- coelutingPeakels
          ) {
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

            Tuple3(putativeFt, mftBuilder, _findUnidentifiedPeakel(
              mzDb,
              coelutingPeakels,
              putativeFt.mz,
              putativeFt.charge,
              minTime = putativeFt.elutionTime - ftMappingParams.timeTol,
              avgTime = putativeFt.elutionTime,
              maxTime = putativeFt.elutionTime + ftMappingParams.timeTol,
              expectedDuration = mftBuilder.bestFeature.duration,
              assignedMzDbPeakelIdSet = assignedMzDbPeakelIdSet,
              multimatchedMzDbPeakelIds = multiMatchedMzDbPeakelIdsByPutativeFtId(putativeFt.id),
              runMetrics
            //benchmarkMap
            ))
          }

          logger.info("Convert found unidentified peakels into features...")

          for (
            (putativeFt, mftBuilder, missingPeakelOpt) <- putativeFtAndMissingPeakelOptTuples.toArray;
            (missingPeakel, isReliable) <- missingPeakelOpt
          ) {
            val mzDbFt = _createMzDbFeature(
              inMemoryPeakelDb,
              Some(rTree),
              missingPeakel,
              putativeFt.charge,
              true,
              Array.empty[Long]
            )

            if (mzDbFt.getPeakelsCount == 1) runMetrics.incr("missing monoisotopic features")
            runMetrics.incr("missing feature found")

            // Update putative feature of conflicting peptides in this run to allow peakel re-assignment
            val charge = putativeFt.charge
            val peptide = peptideByPutativeFtId(putativeFt.id)
            val conflictingPeptides = entityCache.getConflictedPeptides((peptide, charge)).orNull
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
            this._storeLcMsFeaturePeakels(newLcmsFeature, mzDbFt, rawMap.id, peakelPublisher, mzDbPeakelIdByTmpPeakelId)

            if (isReliable) {
              for (peakel <- mzDbFt.getPeakels()) {
                assignedMzDbPeakelIdSet += peakel.id
              }
            }

            val newFtProps = newLcmsFeature.properties.get

            // Set predicted time property
            newFtProps.setPredictedElutionTime(Some(putativeFt.elutionTime))

            // Set isReliable property
            newFtProps.setIsReliable(Some(isReliable))

            // Deselect the feature if it not reliable
            // TODO: DBO => should we still do this ???
            if (!isReliable) {
              newLcmsFeature.selectionLevel = 0 // force manual deselection (for Profilizer compat)
            }

            // Set processed map id
            newLcmsFeature.relations.processedMapId = processedMap.id

            // Append newLcmsFt in the buffer to add to it the raw map
            newLcmsFeatures += newLcmsFeature

            // Retrieve master feature builder to append this new feature to its children buffer
            mftBuilder.children += newLcmsFeature

            // Compute some metrics
            val deltaRt = newLcmsFeature.elutionTime - putativeFt.elutionTime
            runMetrics.storeValue("missing predicted retention time", deltaRt)

          } // end for each missing peakel

          logger.info("Missing features search has finished !")

          // Create a new raw map by including the retrieved missing features
          val x2RawMap = rawMap.copy(
            features = rawMap.features ++ newLcmsFeatures
          //peakels = Some(peakelByMzDbPeakelId.values.toArray)
          )

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

  // TODO: move to MzDbReader when peakels are stored in the MzDbFile
  private def _streamPeakels(
    peakelFile: File): Observable[Array[MzDbPeakel]] = {

    Observable[Array[MzDbPeakel]] { subscriber =>

      this.logger.info(s"Loading peakels from existing peakelDB ${peakelFile}")

      // Open TMP SQLite file
      val sqliteConn = new SQLiteConnection(peakelFile)
      sqliteConn.openReadonly()

      try {

        val peakelsBuffer = PeakelDbReader.loadAllPeakels(sqliteConn)
        subscriber.onNext(peakelsBuffer.toArray)
        subscriber.onCompleted()

      } finally {
        // Release resources
        sqliteConn.dispose()
      }
    }

  }

  private def _createMzDbFeature(
    peakelFileConnection: SQLiteConnection,
    rTreeOpt: Option[RTree[java.lang.Integer, geometry.Point]],
    peakel: MzDbPeakel,
    charge: Int,
    isPredicted: Boolean,
    ms2SpectrumIds: Array[Long]): MzDbFeature = {

    val isotopes = this._findFeatureIsotopes(peakelFileConnection, rTreeOpt, peakel, charge)

    MzDbFeature(
      id = MzDbFeature.generateNewId,
      mz = peakel.getApexMz(),
      charge = charge,
      indexedPeakels = isotopes.zipWithIndex,
      isPredicted = isPredicted,
      ms2SpectrumIds = ms2SpectrumIds
    )
  }

  /*
  private def _testIsotopicPatternPrediction(
    reader: MzDbReader,
    sqliteConn: SQLiteConnection,
    rTree: RTree[java.lang.Integer,geometry.Point],
    ft: MzDbFeature,
    runMetrics: Metric
  ): Unit = {
    
    try {
    val mozTolInDa = MsUtils.ppmToDa(ft.getMz(), ftMappingParams.mozTol)    
   
    def updateMetrics(bestPattern: TheoreticalIsotopePattern, prefix: String) = {
      if (math.abs(bestPattern.monoMz - ft.getMz()) <= mozTolInDa) {
        if (bestPattern.charge == ft.charge) {
          runMetrics.incr(prefix+".correct")
        } else {
          runMetrics.incr(prefix+".wrong.charge")
        }
      } else {
        runMetrics.incr(prefix+".wrong.monoisotope")
      }
    }
    
    var bestPattern = MzDbPatternPredictor.getBestExplanation(reader, sqliteConn, ft.getFirstPeakel(), ft.charge, mozTolInDa)
    updateMetrics(bestPattern._2, "mzdb.prediction.firstIsotope")
    
    if (ft.getPeakelsCount() > 1) {
      bestPattern = MzDbPatternPredictor.getBestExplanation(reader, sqliteConn, ft.getPeakel(1), ft.charge, mozTolInDa)
      updateMetrics(bestPattern._2, "mzdb.prediction.secondIsotope")
    }
    if (ft.getPeakelsCount() > 2) {
       bestPattern = MzDbPatternPredictor.getBestExplanation(reader, sqliteConn, ft.getPeakel(2), ft.charge, mozTolInDa)
       updateMetrics(bestPattern._2, "mzdb.prediction.thirdIsotope")
    }
    
    val coelutingPeakels = {
      val halfTimeTol = ftMappingParams.timeTol / 2
      this._findPeakelsInRange(
        sqliteConn,
        rTree,
        ft.getMz - 5,
        ft.getMz + 5,
        ft.getElutionTime - ftMappingParams.timeTol,
        ft.getElutionTime + ftMappingParams.timeTol
      ).sortBy(_.getMz)
    }
    
    bestPattern = PeakelsPatternPredictor.getBestExplanation(mozTolPPM, coelutingPeakels, ft.getFirstPeakel(), ft.charge, mozTolInDa)
    updateMetrics(bestPattern._2, "peakels.predictor.firstIsotope")
    
    if (ft.getPeakelsCount() > 1) {
      bestPattern = PeakelsPatternPredictor.getBestExplanation(mozTolPPM, coelutingPeakels, ft.getPeakel(1), ft.charge, mozTolInDa)
      updateMetrics(bestPattern._2, "peakels.predictor.secondIsotope")
    }
    if (ft.getPeakelsCount() > 2) {
       bestPattern = PeakelsPatternPredictor.getBestExplanation(mozTolPPM, coelutingPeakels, ft.getPeakel(2), ft.charge, mozTolInDa)
       updateMetrics(bestPattern._2, "peakels.predictor.thirdIsotope")
    }
    } catch {
      case t: Throwable => logger.error("IP prediction fail", t)
    }
  }*/

  private def _findUnidentifiedPeakel(
    reader: MzDbReader,
    coelutingPeakels: Seq[MzDbPeakel],
    peakelMz: Double,
    charge: Int,
    minTime: Float,
    avgTime: Float,
    maxTime: Float,
    expectedDuration: Float,
    assignedMzDbPeakelIdSet: HashSet[Int],
    multimatchedMzDbPeakelIds: Seq[Int],
    metric: Metric //benchmarkMap: HashMap[String,Long]
    ): Option[(MzDbPeakel, Boolean)] = {

    val mozTolInDa = MsUtils.ppmToDa(peakelMz, ftMappingParams.mozTol)

    var start = System.nanoTime()
    /*val foundPeakels = _findPeakelsInRange(
      sqliteConn,
      rTree,
      peakelMz - mozTolInDa,
      peakelMz + mozTolInDa,
      minTime,
      maxTime
    )*/
    val foundPeakels = coelutingPeakels.filter { peakel =>
      math.abs(peakelMz - peakel.getApexMz) <= mozTolInDa
    }
    //benchmarkMap("foundPeakels") = benchmarkMap.getOrElseUpdate("foundPeakels",0L) + (System.nanoTime() - start)

    if (foundPeakels.isEmpty) {
      metric.incr("missing peakel: no peakel found in the peakelDB")
      return None
    }

    // Apply some filters to the found peakels
    val multimatchedPeakelIdSet = multimatchedMzDbPeakelIds.toSet
    val matchingPeakels = foundPeakels.filter { foundPeakel =>
      multimatchedPeakelIdSet.contains(foundPeakel.id) || !assignedMzDbPeakelIdSet.contains(foundPeakel.id)
    }

    /*start = System.nanoTime()
    val coelutingPeakels = if (matchingPeakels.isEmpty) { null } else {
      
      // TODO: compute minTime/maxTime using a combination of matchingPeakels durations ?
      
      // Look for co-eluting peakels
      val halfTimeTol = ftMappingParams.timeTol / 2
      this._findPeakelsInRange(
        sqliteConn,
        rTree,
        peakelMz - 5,
        peakelMz + 5,
        minTime,
        maxTime
      ).sortBy(_.getMz)
    }
    benchmarkMap("coelutingPeakels") = benchmarkMap.getOrElseUpdate("coelutingPeakels",0L) + (System.nanoTime() - start)
    */

    start = System.nanoTime()
    val filteredPeakels = PeakelsPatternPredictor.assessReliability(
      mozTolPPM,
      coelutingPeakels,
      matchingPeakels,
      charge,
      mozTolInDa
    )
    //benchmarkMap("assessReliability") = benchmarkMap.getOrElseUpdate("assessReliability",0L) + (System.nanoTime() - start)

    // Switch to mzdb based implementation by commenting previous line and uncommenting the next one
    //
    // val filteredPeakels = mzDbPatternPredictor.assessReliability(reader, sqliteConn, matchingPeakels, charge, mozTolInDa)

    // Fake filteredPeakels
    //val filteredPeakels = matchingPeakels.map( p => (p,true) )

    start = System.nanoTime()
    if (filteredPeakels.isEmpty) {
      metric.incr("missing peakel: no peakel matching charge or monoisotopic")
      None
    } else if (filteredPeakels.length == 1) {
      Some(filteredPeakels.head)
    } else {

      var reliablePeakels = filteredPeakels.filter(_._2)
      if (reliablePeakels.isEmpty) { reliablePeakels = filteredPeakels }
      val nearestPeakelInTime = reliablePeakels.minBy {
        case (peakel, isReliable) =>
          math.abs(avgTime - peakel.calcWeightedAverageTime())
      }

      val nearestFilteredPeakelInTime = filteredPeakels.minBy {
        case (peakel, isReliable) =>
          math.abs(avgTime - peakel.calcWeightedAverageTime())
      }

      if (nearestFilteredPeakelInTime != nearestPeakelInTime) { metric.incr("nearestPeakelInTime.not.reliable") }

      //benchmarkMap("nearestPeakelInTime") = benchmarkMap.getOrElseUpdate("nearestPeakelInTime",0L) + (System.nanoTime() - start)

      Some(nearestPeakelInTime)
    }
  }

  private def _findFeatureIsotopes(
    sqliteConn: SQLiteConnection,
    rTreeOpt: Option[RTree[java.lang.Integer, geometry.Point]],
    peakel: MzDbPeakel,
    charge: Int): Array[MzDbPeakel] = {

    val peakelMz = peakel.getMz
    val mozTolInDa = MsUtils.ppmToDa(peakelMz, ftMappingParams.mozTol)

    val peakelRt = peakel.getApexElutionTime
    val peakelDurationTol = 1 + math.min(peakel.getApexElutionTime - peakel.getFirstElutionTime, peakel.getLastElutionTime - peakel.getApexElutionTime)
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
        val foundPeakels = PeakelDbReader.findPeakelsInRange(
          sqliteConn,
          rTreeOpt,
          ipMoz - mozTolInDa,
          ipMoz + mozTolInDa,
          minRt,
          maxRt
        )

        if (foundPeakels.nonEmpty) {

          val isotopePeakel = PeakelCorrelationScorer.findCorrelatingPeakel(peakel, foundPeakels)

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