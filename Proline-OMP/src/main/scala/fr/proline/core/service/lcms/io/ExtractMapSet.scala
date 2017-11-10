package fr.proline.core.service.lcms.io

import java.io.File
import java.util.Arrays
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.collection.mutable.HashSet
import scala.collection.mutable.LongMap
import scala.collection.parallel._
import scala.concurrent._
import scala.concurrent.duration.Duration
import scala.util.control.Breaks._

import com.almworks.sqlite4java.SQLiteConnection
import com.almworks.sqlite4java.SQLiteStatement
import com.github.davidmoten.rtree._
import com.typesafe.scalalogging.LazyLogging

import org.apache.commons.math3.stat.correlation.PearsonsCorrelation
import org.apache.commons.math3.stat.descriptive.rank.Percentile

import fr.profi.chemistry.model.MolecularConstants
import fr.profi.jdbc.easy._
import fr.profi.ms.algo.IsotopePatternEstimator
import fr.profi.ms.algo.IsotopePatternInterpolator
import fr.profi.ms.model.TheoreticalIsotopePattern
import fr.profi.mzdb._
import fr.profi.mzdb.algo.feature.extraction.FeatureExtractorConfig
import fr.profi.mzdb.io.reader.provider.RunSliceDataProvider
import fr.profi.mzdb.model.{ Feature => MzDbFeature, Peakel => MzDbPeakel, SpectrumHeader }
import fr.profi.mzdb.model.PeakelDataMatrix
import fr.profi.mzdb.model.PutativeFeature
import fr.profi.mzdb.util.ms.MsUtils
import fr.profi.util.collection._
import fr.profi.util.metrics.Metric

import fr.proline.context.LcMsDbConnectionContext
import fr.proline.core.algo.lcms._
import fr.proline.core.algo.lcms.alignment.AlignmentResult
import fr.proline.core.algo.msq.config._
import fr.proline.core.dal.DoJDBCWork
import fr.proline.core.dal.helper.LcmsDbHelper
import fr.proline.core.om.model.lcms._
import fr.proline.core.om.model.lcms.{ Feature => LcMsFeature }
import fr.proline.core.om.model.msi.{ Peptide, PeptideMatch }
import fr.proline.core.om.provider.lcms.impl.SQLScanSequenceProvider
import fr.proline.core.om.storer.lcms._
import fr.proline.core.om.storer.lcms.impl.PgPeakelWriter
import fr.proline.core.om.storer.lcms.impl.SQLPeakelWriter
import fr.proline.core.om.storer.lcms.impl.SQLScanSequenceStorer
import fr.proline.core.service.lcms.AlignMapSet
import fr.proline.core.service.lcms.CreateMapSet
import fr.proline.core.service.lcms.ILcMsService

import rx.lang.scala.Observable
import rx.lang.scala.JavaConversions.toJavaObservable
import rx.lang.scala.JavaConversions.toScalaObservable
import rx.lang.scala.Scheduler
import rx.lang.scala.schedulers
import rx.lang.scala.Subject
import rx.lang.scala.subjects.PublishSubject
import rx.exceptions.Exceptions

/**
 * @author David Bouyssie
 *
 */
object ExtractMapSet {
  
  val ISOTOPE_PATTERN_HALF_MZ_WINDOW = 5
  val RUN_SLICE_MAX_PARALLELISM = { // Defines how many run slices we want to process in parallel
    val nbProcessors = Runtime.getRuntime().availableProcessors()
    Some( math.max( 1, (nbProcessors / 2).toInt ) )
  }

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
      val threadName = s"ExtractMapSet-RTree-Thread-${ExtractMapSet.threadCount.incrementAndGet()}"
      new Thread(r, threadName)
    }
  }
}

class ExtractMapSet(
  val lcmsDbCtx: LcMsDbConnectionContext,
  val mapSetName: String,
  val lcMsRuns: Seq[LcMsRun],
  val quantConfig: ILcMsQuantConfig,
  val peptideByRunIdAndScanNumber: Option[LongMap[LongMap[Peptide]]] = None, // sequence data may or may not be provided
  val peptideMatchByRunIdAndScanNumber: Option[LongMap[LongMap[ArrayBuffer[PeptideMatch]]]] = None
) extends ILcMsService with LazyLogging {

  // Do some requirements
  require(quantConfig.extractionParams.mozTolUnit matches "(?i)PPM")
  
  require(
    lcMsRuns.map(_.number).filter(_ > 0) == lcMsRuns.length,
    "Invalid LC-MS run numbers: numbers should distinct and strictly positive"
  )
  protected val sortedLcMsRuns = lcMsRuns.sortBy(_.number)

  // Define some vars
  protected val mozTolPPM = quantConfig.extractionParams.mozTol.toFloat
  protected val clusteringParams = quantConfig.clusteringParams
  protected val alnMethodName = quantConfig.alnMethodName
  protected val alnParams = quantConfig.alnParams
  protected val masterFtFilter = quantConfig.ftFilter
  protected val ftMappingParams = quantConfig.ftMappingParams
  protected val normalizationMethod = quantConfig.normalizationMethod
  protected val avgIsotopeMassDiff = MolecularConstants.AVERAGE_PEPTIDE_ISOTOPE_MASS_DIFF

  protected val lcmsDbHelper = new LcmsDbHelper(lcmsDbCtx)
  protected val scanSeqProvider = new SQLScanSequenceProvider(lcmsDbCtx)

  // Instantiate a raw map storer and a map aligner
  protected val rawMapStorer = RawMapStorer(lcmsDbCtx)
  protected val peakelWriter = rawMapStorer.peakelWriter.get
  protected val mapAligner = LcmsMapAligner(methodName = alnMethodName)

  protected val pps = new PeakPickingSoftware(
    id = -1,
    name = "Proline",
    version = new fr.proline.core.service.Version().getVersion.split("-").head,
    algorithm = "ExtractMapSet"
  )

  var extractedMapSet: MapSet = null

  def runService(): Boolean = {

    val mapCount = lcMsRuns.length
    val nonNullLcMsRunCount = lcMsRuns.count(_ != null)
    this.logger.debug("LC-MS runs count = " + mapCount + " and non-null LC-MS runs count = " + nonNullLcMsRunCount)
    require(mapCount == nonNullLcMsRunCount, "the quantitation config contains null LC-MS runs")

    // Check if a transaction is already initiated
    val wasInTransaction = lcmsDbCtx.isInTransaction()
    if (!wasInTransaction) lcmsDbCtx.beginTransaction()

    val tmpMapSetId = MapSet.generateNewId()

    val mzDbFileByLcMsRunId = new LongMap[File](mapCount)
    for (lcmsRun <- sortedLcMsRuns) {

      val rawFile = lcmsRun.rawFile
      val mzDbFilePath = rawFile.getMzdbFilePath.get
      val mzDbFile = new File(mzDbFilePath)

      if (lcmsRun.scanSequence.isEmpty) {
        // Retrieve the corresponding LC-MS run
        // Or store it in the database if it doesn't exist
        lcmsRun.scanSequence = Some(this._fetchOrStoreScanSequence(lcmsRun, mzDbFile))
      }

      mzDbFileByLcMsRunId += lcmsRun.id -> mzDbFile
    }
    
    // New way of map set creation (concerted maps extraction)
    val (finalMapSet, alnResult) = if (quantConfig.detectPeakels) {
      // TODO: move in a specific class implem (DetectMapSet)
      this._detectMapSetFromPeakels(mzDbFileByLcMsRunId, tmpMapSetId)
      // Old way of map set creation (individual map extraction)
    } else {
      // TODO: move in a specific class implem (ExtractMapSet)
      this._extractMapSet(mzDbFileByLcMsRunId, tmpMapSetId)
    }

    // End of finalMapSet assignment block
    
    // Update peakel.feature_count column using an hand-made SQL query
    DoJDBCWork.withEzDBC(lcmsDbCtx) { ezDBC =>
      val rawMapIds = finalMapSet.childMaps.flatMap(_.getRawMapIds())
      val whereClause = rawMapIds.map(id => s"map_id=$id").mkString(" OR ")
      val subQuery = "SELECT count(feature_id) AS ft_count, peakel_id " +
      s"FROM feature_peakel_item WHERE ($whereClause) GROUP BY peakel_id"
      
      logger.debug("Calculating peakel.feature_count column using SQL query:\n"+subQuery)
      
      val updateQuery = "UPDATE peakel SET feature_count = ? WHERE id = ?"
      ezDBC.executePrepared(updateQuery) { stmt =>
        ezDBC.selectAndProcess(subQuery) { r =>
          val featureCount = r.nextInt
          // Only update peakels having a feature_count different than default value (1)
          if (featureCount > 1) {
            stmt.executeWith(featureCount,r.nextLong)
          }
        }
      }
      
      logger.debug("Column peakel.feature_count successfully updated!")
    }
    
    // Update processed map id of each feature
    // FIXME: DBO => why is it sill necessary now (I mean after the introduction of detectPeakels mode) ???
    for (x2ProcessedMap <- finalMapSet.childMaps; ft <- x2ProcessedMap.features) {
      ft.relations.processedMapId = x2ProcessedMap.id

      if (ft.isCluster) {
        for (subFt <- ft.subFeatures) {
          subFt.relations.processedMapId = x2ProcessedMap.id
        }
      }
    }

    // --- Update and store the map alignment using processed maps with persisted ids ---
    // do not re-compute the alignments because they could be different with the tmpAlignemnt 
    AlignMapSet(lcmsDbCtx, finalMapSet, alnResult)
    //AlignMapSet(lcmsDbCtx, finalMapSet, alnMethodName, alnParams)

    // --- Normalize the processed maps ---
    if (normalizationMethod.isDefined && finalMapSet.childMaps.length > 1) {

      // Update the normalized intensities
      logger.info("Normalizing maps...")
      MapSetNormalizer(normalizationMethod.get).normalizeFeaturesIntensity(finalMapSet)

      // Re-build master map features using best child
      logger.info("Re-build master map features using best child...")
      finalMapSet.rebuildMasterFeaturesUsingBestChild()
    }

    // --- Store the processed maps features ---

    // Instantiate a processed map storer
    val processedMapStorer = ProcessedMapStorer(lcmsDbCtx)

    DoJDBCWork.withEzDBC(lcmsDbCtx) { ezDBC =>
      for (processedMap <- finalMapSet.childMaps) {
        // Store the map
        logger.info("Storing the processed map...")
        processedMapStorer.storeProcessedMap(processedMap)
      }
    }

    // --- Store the master map ---
    logger.info("Saving the master map...")
    val masterMapStorer = MasterMapStorer(lcmsDbCtx)
    masterMapStorer.storeMasterMap(finalMapSet.masterMap)

    // Commit transaction if it was initiated locally
    if (!wasInTransaction) lcmsDbCtx.commitTransaction()

    logger.info(finalMapSet.masterMap.features.length + " master features have been created")

    this.extractedMapSet = finalMapSet

    // Check all features are persisted in childMaps
    for (x2ProcessedMap <- finalMapSet.childMaps; ft <- x2ProcessedMap.features) {
      require(ft.id > 0, "feature should be persisted")
      if (ft.isCluster) {
        for (subFt <- ft.subFeatures) {
          require(subFt.id > 0, "sub-feature should be persisted")
        }
      }
    }

    // Check all features are persisted in masterMap
    for (mft <- finalMapSet.masterMap.features) {
      require(mft.id > 0, "master feature should be persisted")
      for (ft <- mft.children) {
        require(ft.id > 0, "feature should be persisted")
      }
    }

    true
  }
  
  
  private def _extractMapSet(
    mzDbFileByLcMsRunId: LongMap[File],
    mapSetId: Long
  ): (MapSet, AlignmentResult) = {
    
    val sortedLcMsRuns = this.sortedLcMsRuns

    var mapIdx = 0
    val mapsCount = sortedLcMsRuns.length

    // --- Extract raw maps and convert them to processed maps ---
    val processedMaps = new Array[ProcessedMap](mapsCount)
    val lcmsRunByProcMapId = new LongMap[LcMsRun](mapsCount)
    val mzDbFileByProcMapId = new LongMap[File](mapsCount)
    val scanSeqByRunId = new LongMap[LcMsScanSequence](mapsCount)

    for (lcmsRun <- sortedLcMsRuns) {
      val scanSeq = lcmsRun.scanSequence.get
      scanSeqByRunId += lcmsRun.id -> scanSeq

      val mzDbFile = mzDbFileByLcMsRunId(lcmsRun.id)

      // Extract LC-MS map from the mzDB file
      val processedMap = this._extractProcessedMap(lcmsRun, mzDbFile, mapIdx + 1, mapSetId)

      // Update some mappings
      lcmsRunByProcMapId += processedMap.id -> lcmsRun
      mzDbFileByProcMapId += processedMap.id -> mzDbFile

      // Convert to processed map
      //val processedMap = rawMap.toProcessedMap( number = mapIdx + 1, mapSetId = tmpMapSetId )

      // Perform the feature clustering
      // TODO: use the clean maps service ???
      val scans = lcmsRun.scanSequence.get.scans
      val clusterizedMap = ClusterizeFeatures(processedMap, scans, clusteringParams)
      //clusterizedMap.toTsvFile("D:/proline/data/test/quanti/debug/clusterized_map_"+ (-clusterizedMap.id) +".tsv")

      // Set clusterized map id as the id of the provided map
      clusterizedMap.id = processedMap.id

      processedMaps(mapIdx) = clusterizedMap

      //processedMapByRawMapId += rawMap.id -> processedMap

      mapIdx += 1
    }

    // --- Perform the LC-MS maps alignment ---
    // TODO: do we need to remove the clusters for the alignment ???      
    val alnResult = mapAligner.computeMapAlignments(processedMaps.filter(_ != null), alnParams)

    // --- Create an in-memory map set ---
    var tmpMapSet = new MapSet(
      id = mapSetId,
      name = mapSetName,
      creationTimestamp = new java.util.Date,
      childMaps = processedMaps,
      alnReferenceMapId = alnResult.alnRefMapId,
      mapAlnSets = alnResult.mapAlnSets
    )

    // --- Build a temporary Master Map ---
    tmpMapSet.masterMap = BuildMasterMap(
      tmpMapSet,
      sortedLcMsRuns.map(_.scanSequence.get),
      masterFtFilter,
      ftMappingParams,
      clusteringParams
    )
    //tmpMapSet.toTsvFile("C:/Local/data/test/quanti/debug/master_map_"+ (-tmpMapSet.masterMap.id) +".tsv")

    // --- Re-build master map if peptide sequences have been provided ---
    for (pepMap <- peptideByRunIdAndScanNumber) {

      // Map peptides by scan id and scan sequence by run id
      val peptideByScanId = new LongMap[Peptide]()

      for (lcmsRun <- sortedLcMsRuns) {
        val scanSeq = lcmsRun.scanSequence.get

        for (lcmsScan <- scanSeq.scans) {
          for (peptide <- pepMap(lcmsRun.id).get(lcmsScan.initialId)) {
            peptideByScanId += lcmsScan.id -> peptide
          }
        }
      }

      // Instantiate a feature clusterer for each child map
      // TODO: provide this mapping to the master map builder ???
      val ftClustererByMapId = tmpMapSet.childMaps.toLongMapWith { childMap =>
        val scanSeq = scanSeqByRunId(childMap.runId.get)
        childMap.id -> new FeatureClusterer(childMap, scanSeq.scans, clusteringParams)
      }

      this._rebuildMasterMapUsingPeptides(tmpMapSet, peptideByScanId.result, ftClustererByMapId)
      //tmpMapSet.toTsvFile("D:/proline/data/test/quanti/debug/master_map_with_peps_"+ (-tmpMapSet.masterMap.id) +".tsv")
    }

    // Re-build child maps in order to be sure they contain master feature children (including clusters)
    tmpMapSet = tmpMapSet.rebuildChildMaps()

    // --- Extract LC-MS missing features in all raw files ---
    val x2RawMaps = new ArrayBuffer[RawMap](sortedLcMsRuns.length)
    val x2ProcessedMaps = new ArrayBuffer[ProcessedMap](sortedLcMsRuns.length)
    val procMapTmpIdByRawMapId =  new LongMap[Long](sortedLcMsRuns.length)
    
    for (processedMap <- tmpMapSet.childMaps) {
      val rawMap = processedMap.getRawMaps().head.get
      val mzDbFile = mzDbFileByProcMapId(processedMap.id)
      val lcmsRun = lcmsRunByProcMapId(processedMap.id)

      // Extract missing features
      val peakelIdByMzDbPeakelId = new LongMap[Long]()
      val newLcmsFeatures = this._extractMissingFeatures(mzDbFile, lcmsRun, processedMap, tmpMapSet, peakelIdByMzDbPeakelId)

      // Create a new raw map by including the extracted missing features
      val x2RawMap = rawMap.copy(
        features = rawMap.features ++ newLcmsFeatures
      )
      
      val ftPeakels = newLcmsFeatures.flatMap(_.relations.peakelItems.flatMap(_.getPeakel()))
      val rawMapPeakels = new ArrayBuffer[Peakel](rawMap.peakels.get.length + ftPeakels.length)
      rawMapPeakels ++= rawMap.peakels.get
      rawMapPeakels ++= ftPeakels.groupByLong(_.id).values.map(_.head)
      
      // Append missing peakels
      x2RawMap.peakels = Some(rawMapPeakels.toArray)

      // Store the raw map
      logger.info("Storing the raw map...")
      rawMapStorer.storeRawMap(x2RawMap, storeFeatures = true, storePeakels = true)
      
      procMapTmpIdByRawMapId.put( x2RawMap.id, processedMap.id )
      
      // Detach peakels from the raw map
      x2RawMap.peakels = None

      // Detach peakels from features
      for (ft <- x2RawMap.features; peakelItem <- ft.relations.peakelItems) {
        peakelItem.peakelReference = PeakelIdentifier(peakelItem.peakelReference.id)
      }

      // Append raw map to the array buffer
      x2RawMaps += x2RawMap

      // Create a processed map for this rawMap
      x2ProcessedMaps += processedMap.copy(
        features = processedMap.features ++ newLcmsFeatures,
        rawMapReferences = Array(x2RawMap)
      )
    }
    //mapSet.toTsvFile("D:/proline/data/test/quanti/debug/master_map_no_missing_"+ (-mapSet.masterMap.id) +".tsv")

    // --- Create the corresponding map set ---
    val x2MapSet = CreateMapSet(lcmsDbCtx, mapSetName, x2ProcessedMaps)
    
    val procMapIdByTmpId = new LongMap[Long](sortedLcMsRuns.length)
    for (processedMap <- x2ProcessedMaps) {
      val rawMap = processedMap.getRawMaps().head.get
      val oldProcessedMapId = procMapTmpIdByRawMapId.get(rawMap.id).get
      procMapIdByTmpId.put(oldProcessedMapId, processedMap.id )
    }

    // Attach the computed master map to the newly created map set
    x2MapSet.masterMap = tmpMapSet.masterMap
    x2MapSet.masterMap.mapSetId = x2MapSet.id
    x2MapSet.masterMap.rawMapReferences = x2RawMaps
    
    // update the map ids in the alnResult
    val finalMapAlnSets = new ArrayBuffer[MapAlignmentSet]
    for (alnSet <-alnResult.mapAlnSets) {
      val finalRefMapId = procMapIdByTmpId.get(alnSet.refMapId).get
      val finalTargetMapId = procMapIdByTmpId.get(alnSet.targetMapId).get
      val finalMapAlignments = new ArrayBuffer[MapAlignment]
      for (aln <- alnSet.mapAlignments){
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
      val finalMapAlnmSet = new MapAlignmentSet(
        finalRefMapId,
        finalTargetMapId,
        finalMapAlignments.toArray
      )
      finalMapAlnSets += finalMapAlnmSet
    }
    
    val finalAlnResult = new AlignmentResult(
      procMapIdByTmpId.get(alnResult.alnRefMapId).get,
      finalMapAlnSets.toArray
    )

    (x2MapSet, finalAlnResult)
  }

  private def _fetchOrStoreScanSequence(lcmsRun: LcMsRun, mzDbFile: File): LcMsScanSequence = {

    val mzDbFileDir = mzDbFile.getParent()
    val mzDbFileName = mzDbFile.getName()
    // FIXME: it should be retrieved from the mzDB file meta-data
    val rawFileIdentifier = mzDbFileName.split("\\.").head

    // Check if the scan sequence already exists
    //val scanSeqId = lcmsDbHelper.getScanSequenceIdForRawFileName(rawFileName)
    val scanSeqOpt = scanSeqProvider.getScanSequence(lcmsRun.id)

    if (scanSeqOpt.isDefined) scanSeqOpt.get
    else {

      val mzDb = new MzDbReader(mzDbFile, true)
      val mzDbScans = mzDb.getSpectrumHeaders()
      val scans = mzDbScans.map { mzDbScan =>

        val precMz = mzDbScan.getPrecursorMz
        val precCharge = mzDbScan.getPrecursorCharge

        new LcMsScan(
          id = LcMsScan.generateNewId(),
          initialId = mzDbScan.getInitialId,
          cycle = mzDbScan.getCycle,
          time = mzDbScan.getTime,
          msLevel = mzDbScan.getMsLevel,
          tic = mzDbScan.getTIC,
          basePeakMoz = mzDbScan.getBasePeakMz,
          basePeakIntensity = mzDbScan.getBasePeakIntensity,
          runId = lcmsRun.id,
          precursorMoz = if (precMz > 0) Some(precMz) else None,
          precursorCharge = if (precCharge > 0) Some(precCharge) else None
        )
      } sortBy(_.time) // FIXME: why scans are not sorted after call to mzDb.getScanHeaders ???

      val ms1ScansCount = scans.count(_.msLevel == 1)
      val ms2ScansCount = scans.count(_.msLevel == 2)

      val scanSeq = new LcMsScanSequence(
        runId = lcmsRun.id,
        rawFileIdentifier = rawFileIdentifier,
        minIntensity = 0.0, // TODO: compute this value ???
        maxIntensity = 0.0, // TODO: compute this value ???
        ms1ScansCount = ms1ScansCount,
        ms2ScansCount = ms2ScansCount,
        instrument = lcmsRun.rawFile.instrument,
        scans = scans
      )

      val scanSeqStorer = new SQLScanSequenceStorer(lcmsDbCtx)
      scanSeqStorer.storeScanSequence(scanSeq)

      // Close the mzDB file
      mzDb.close()

      scanSeq
    }

  }

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
    
  private def _detectMapSetFromPeakels(
    mzDbFileByLcMsRunId: LongMap[File],
    mapSetId: Long
  ): (MapSet, AlignmentResult) = {
    
    val sortedLcMsRuns = this.sortedLcMsRuns
    
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
      1 + ExtractMapSet.mzdbMaxParallelism * 2,
      Executors.defaultThreadFactory()
    )
    implicit val ioExecCtx = ExecutionContext.fromExecutor(ioThreadPool)
    
    // Customize a thread pool for R*Tree computation
    val computationThreadPool = Executors.newFixedThreadPool(
      ExtractMapSet.mzdbMaxParallelism,
      ExtractMapSet.rtreeThreadFactory
    )
    val rxCompScheduler = rx.lang.scala.JavaConversions.javaSchedulerToScalaScheduler(
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
      val peakelPublisher = PublishSubject[(Peakel,Long)]().toSerialized
      
      logger.info(s"Inserting ${detectorEntityCache.runsCount} raw maps before peakel detection...")
      val rawMaps = sortedLcMsRuns.map { lcMsRun =>
        
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
          for (lcMsRun <- sortedLcMsRuns) {
            
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
          
          val mftBuilder = new MasterFeatureBuilder(
            bestFeature = bestFt,
            children = masterFtChildren,
            peptideId = peptide.id // attach the peptide id to the master feature
          )
          mftBuilderByPeptideAndCharge += (peptide, charge) -> mftBuilder
          
        } // ends (peptide, charge) loop
        
        this.logger.info("Aligning maps and creating new map set...")
        
        // Align maps
        val alnResult = alnParams.getFeatureMappingMethod match {
          case FeatureMappingMethod.FEATURE_COORDINATES => {
            mapAligner.computeMapAlignments(processedMaps, alnParams)
          }
          case FeatureMappingMethod.PEPTIDE_IDENTITY => {
            
            // Map mftBuilder by the feature id
            val mftBuilderByFtId = new LongMap[MasterFeatureBuilder](processedMaps.foldLeft(0)(_ + _.features.length))
            for (
              mftBuilder <- mftBuilderByPeptideAndCharge.values;
              childFt <- mftBuilder.children
            ) mftBuilderByFtId.put(childFt.id, mftBuilder)
    
            // Create a new map aligner algo starting from existing master features
            mapAligner.computeMapAlignmentsUsingCustomFtMapper(processedMaps, alnParams) { (map1Features, map2Features) =>
              
              val ftMapping = new LongMap[ArrayBuffer[Feature]](Math.max(map1Features.length,map2Features.length))
              
              val map2FtByMftBuilder = Map() ++ (for (
                map2Ft <- map2Features;
                val mftBuilderOpt = mftBuilderByFtId.get(map2Ft.id);
                if mftBuilderOpt.isDefined
              ) yield (mftBuilderOpt.get, map2Ft))
              
             for (
                map1Ft <- map1Features;
                val mftBuilderOpt = mftBuilderByFtId.get(map1Ft.id);
                if mftBuilderOpt.isDefined;
                val map2FtOpt = map2FtByMftBuilder.get(mftBuilderOpt.get);
                if map2FtOpt.isDefined
              ) {
                ftMapping.getOrElseUpdate(map1Ft.id, new ArrayBuffer[Feature]) += map2FtOpt.get
              }
              
              ftMapping
            }
            
          }
          case _ => throw new Exception("Unsupported feature mapping method")
        }
        
        // Create a temporary in-memory map set
        var tmpMapSet = new MapSet(
          id = mapSetId,
          name = mapSetName,
          creationTimestamp = new java.util.Date,
          childMaps = processedMaps.toArray,
          alnReferenceMapId = alnResult.alnRefMapId,
          mapAlnSets = alnResult.mapAlnSets
        )
        
        this.logger.info("Predicting missing features coordinates...")

        // Iterate create master features to predict missing ones
        for (((peptide, charge), mftBuilder) <- mftBuilderByPeptideAndCharge) {
          
          val masterFtChildren = mftBuilder.children
          val bestFt = mftBuilder.bestFeature
          val bestFtProcMapId = bestFt.relations.processedMapId
          val bestFtLcMsRun = lcmsRunByProcMapId(bestFtProcMapId)
    
          // Compute RT prediction Stats 
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
          
          val childFtByProcMapId = masterFtChildren.mapByLong { ft => ft.relations.processedMapId }
          val runsWithMissingFt = sortedLcMsRuns.filterNot { lcMsRun =>
            childFtByProcMapId.contains(processedMapByRunId(lcMsRun.id).id)
          }
    
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
          val mft = mftBuilder.toMasterFeature()
          require(mft.children.length <= sortedLcMsRuns.length, "master feature contains more child features than maps")
          
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
        
        while (isLastPublishedPeakel == false || !publishedPeakelQueue.isEmpty()) {
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
        val rawMapId = x2RawMap.id;
        val peakelIdByTmpId = peakelIdByTmpIdByRawMapId(rawMapId);
        ft <- x2RawMap.features;
        peakelItem <- ft.relations.peakelItems
      ) {
         //println("rawMapId",rawMapId);
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
      for ((x2RawMap,idx) <- x2RawMaps.zipWithIndex) {
        logger.info(s"Storing features of raw map #${idx+1} (id=${x2RawMap.id}, name=${x2RawMap.name})...")
        
        rawMapStorer.featureWriter.insertFeatures(x2RawMap.features, x2RawMap.id, linkToPeakels = true)
        
        // Log some raw map information
        val rawMapPeakelsCount = peakelIdByTmpIdByRawMapId(x2RawMap.id).size
        logger.info("Raw map peakels count = "+rawMapPeakelsCount)
        logger.info(detectorEntityCache.metricsByRunId(x2RawMap.runId).toString)
      }
      
      // Memorize processed maps temporary ID
      val procMapTmpIdByRawMapId = tmpMapSet.childMaps.toLongMapWith { processedMap =>
        (processedMap.getRawMapIds.head, processedMap.id)
      }

      // --- Persist the corresponding map set ---
      val x2MapSet = CreateMapSet(lcmsDbCtx, mapSetName, tmpMapSet.childMaps)
      
      // Map processed map id by corresponding temp id
      val procMapIdByTmpId = new LongMap[Long](sortedLcMsRuns.length)
      for (processedMap <- tmpMapSet.childMaps) {
        val rawMap = processedMap.getRawMaps().head.get
        val oldProcessedMapId = procMapTmpIdByRawMapId.get(rawMap.id).get
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
      val terminatedThreadCount = ExtractMapSet.terminatedThreadCount.addAndGet(ExtractMapSet.mzdbMaxParallelism)
      if (terminatedThreadCount == ExtractMapSet.threadCount) {
        ExtractMapSet.threadCount.set(0)
      }
      
      this.logger.debug("ExtractMapSet thread pools have been closed!")
    }
    
  }
  
  case class PeakelMatch(
    peakel: MzDbPeakel,
    peptide: Peptide,
    spectrumHeader: SpectrumHeader,
    charge: Int
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
      peakelMatchesPromise.failure(e)
    }, { () =>
      peakelMatchesPromise.success(peakelMatches)
    })
    
    peakelMatchesPromise.future
  }
  
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
    
    val groupedParLcMsRuns = lcMsRuns.sortBy(_.number).grouped(ExtractMapSet.mzdbMaxParallelism).map(_.par).toList
    
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
      val pepMatchesBySpecNumber = peptideMatchByRunIdAndScanNumber.flatMap(_.get(lcMsRunId)).getOrElse(LongMap.empty[ArrayBuffer[PeptideMatch]])

      // Create a mapping avoiding the creation of duplicated peakels
      val peakelIdByMzDbPeakelId = peakelIdByMzDbPeakelIdByRunId(lcMsRunId)
      
      val mzDbPeakelIdsByPeptideAndCharge = mzDbPeakelIdsByPeptideAndChargeByRunId(lcMsRunId)
      
      val runMetrics = metricsByRunId(lcMsRunId)
      
      // Search for existing Peakel file
      val existingPeakelFiles = ExtractMapSet.tempDir.listFiles.filter(_.getName.startsWith(s"${lcMsRun.getRawFileName}-"))
      val needPeakelDetection = (quantConfig.useLastPeakelDetection == false || existingPeakelFiles.isEmpty)
      
      val futurePeakelDetectionResult = if (needPeakelDetection) {
        
        // Remove TMP files if they exist
        existingPeakelFiles.foreach { peakelFile =>
          if (peakelFile.delete() == false) {
            this.logger.warn("Can't delete peakelDB file located at: "+  peakelFile)
          }
        }
        
        this.logger.info(s"Start peakel detection for run id=$lcMsRunId (map #$mapNumber) from " + mzDbFile.getAbsolutePath())

        // Create TMP file to store orphan peakels which will be deleted after JVM exit
        val peakelFile = File.createTempFile(s"${lcMsRun.getRawFileName}-", ".sqlite",ExtractMapSet.tempDir)
        //peakelFile.deleteOnExit()
        this.logger.debug(s"Creating tmp file for run id=$lcMsRunId at: " + peakelFile)

        // Create a mapping between the TMP file and the LC-MS run
        entityCache.addPeakelFile(lcMsRunId, peakelFile)

        val resultPromise = Promise[(Observable[(Int,Array[MzDbPeakel])],Array[SpectrumHeader],LongMap[Array[SpectrumHeader]])]
        
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
            val onDetectedPeakels = { observablePeakels: Observable[(Int,Array[MzDbPeakel])] =>
              resultPromise.success(observablePeakels,mzDb.getMs1SpectrumHeaders,mzdbFtDetector.ms2SpectrumHeadersByCycle)
              ()
            }
            val nParRunSlices = ExtractMapSet.RUN_SLICE_MAX_PARALLELISM
            mzdbFtDetector.detectPeakelsAsync( mzDb.getLcMsRunSliceIterator(), onDetectedPeakels, nParRunSlices )
            
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
            resultPromise.failure(t)
          }
        }

        val futureResult = resultPromise.future.map { case (observableRunSlicePeakels, ms1SpecHeaders, ms2SpecHeadersByCycle) =>

          // TODO: create/manage this scheduler differently ?
          val rxIOScheduler = schedulers.IOScheduler()
          
          val observablePeakels = Observable[Array[MzDbPeakel]] { subscriber =>
            
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
              lazy val peakelFileConnection = _initPeakelStore(peakelFile)
              
              observableRunSlicePeakels.observeOn(rxIOScheduler).subscribe({ case (rsId,peakels) =>
                
                logger.trace(s"Storing ${peakels.length} peakels for run slice #$rsId and run #${lcMsRun.id}...")
                
                // Store peakels in SQLite file
                this._storePeakelsInPeakelDB(peakelFileConnection, peakels, ms1SpecHeaders)
                
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
          val observablePeakels = _streamPeakels(lastPeakelFile)
          
          Future.successful( (lastPeakelFile, observablePeakels, ms2SpectrumHeadersByCycle) )
          
        } finally {
          mzDb.close()
        }
        
      }
      
      // Entering the same thread than the Future created above (detection or loading of peakels)
      val peakelRecordingFuture = futurePeakelDetectionResult.flatMap { case (peakelFile, peakelFlow, ms2SpecHeadersByCycle) =>
        
        //val rxCompScheduler = schedulers.ComputationScheduler() // TODO: remove me => we use now our own thread pool
        val publishedPeakelFlow = peakelFlow.observeOn(rxCompScheduler).onBackpressureBuffer(1000).publish
        
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
        ).subscribe( { peakels =>
          
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
                s"The spectrum number (${specNum.toInt}) is too high for the conisdered mzDB file (lastSpecNumber is $lastSpecNumber). " + 
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
      
      this.logger.info("Total number of MS/MS matched peakels = "+ peakelMatches.length)
      
      this.logger.info(s"Created R*Tree contains ${rTree.size} peakel indices")
      entityCache.addRTree(lcMsRunId, rTree)

      // Retrieve the list of peakels unmapped with peptides
      // TODO: DBO => should we remove this commented line ?
      //val orphanPeakels = detectedPeakels.filter(pkl => psmTupleByPeakel.contains(pkl) == false)
      
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
        inMemoryPeakelDb = this._loadPeakelDbInMemory(peakelFileConnection)
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
            this._storeLcMsFeaturePeakels(lcmsFt,mzDbFt,rawMapId,peakelPublisher,mzDbPeakelIdByTmpPeakelId)
            
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
    lcmsFt: Feature,
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
    
    val halfMzWindow = ExtractMapSet.ISOTOPE_PATTERN_HALF_MZ_WINDOW
    
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
        val assignedMzDbPeakelIdSet = new HashSet[Int] () ++= peakelIdByMzDbPeakelId.map(_._1.toInt)
        
        // Create a mapping to track peakels already persisted in the LCMSdb
        val mzDbPeakelIdByTmpPeakelId = peakelIdByMzDbPeakelId.map { case (k,v) => (v,k.toInt) }
        
        try {
          //val benchmarkMap = new HashMap[String,Long]()
          logger.info(s"Putting the peakelDB in memory...")
          inMemoryPeakelDb = this._loadPeakelDbInMemory(peakelFileConn)
          peakelFileConn.dispose()
          logger.info(s"The peakelDB has been put in memory !")
          
          // Perform a first pass search of missing peakels
          logger.info(s"Searching for coeluting peakels of ${putativeFts.length} missing features...")
          val missingFtIdAndCoelutingPeakelIds = putativeFts.par.map { putativeFt =>
            val mz = putativeFt.mz
            val peakelIds = _findPeakelIdsInRange(
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
          val coelutingPeakels = this._loadManyPeakels(
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
            (putativeFt,mftBuilder,missingPeakelOpt) <- putativeFtAndMissingPeakelOptTuples.toArray;
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
  
  private def _initPeakelStore(fileLocation: File): SQLiteConnection = {

    // Open SQLite conenction
    val connection = new SQLiteConnection(fileLocation)
    connection.open(true) // allowCreate = true

    // SQLite optimization
    connection.exec("PRAGMA synchronous=OFF;")
    connection.exec("PRAGMA journal_mode=OFF;")
    connection.exec("PRAGMA temp_store=2;")
    connection.exec("PRAGMA cache_size=100000;")

    // Create the DDL
    // Note that this DDL will be later used in the peakelDB format
    val ddlQuery = """
    CREATE TABLE peakel (
      id INTEGER NOT NULL PRIMARY KEY,
      mz REAL NOT NULL,
      elution_time REAL NOT NULL,
      duration REAL NOT NULL,
      gap_count INTEGER NOT NULL,
      apex_intensity REAL NOT NULL,
      area REAL NOT NULL,
      amplitude REAL NOT NULL,
      intensity_cv REAL NOT NULL,
      left_hwhm_mean REAL,
      left_hwhm_cv REAL,
      right_hwhm_mean REAL,
      right_hwhm_cv REAL,
      is_overlapping TEXT NOT NULL,
      feature_count INTEGER NOT NULL,
      peak_count INTEGER NOT NULL,
      peaks BLOB NOT NULL,
      param_tree TEXT,
      first_spectrum_id INTEGER NOT NULL,
      last_spectrum_id INTEGER NOT NULL,
      apex_spectrum_id INTEGER NOT NULL,
      ms_level INTEGER NOT NULL,
      map_id INTEGER
    );

    CREATE VIRTUAL TABLE peakel_rtree USING rtree(
      id INTEGER NOT NULL PRIMARY KEY,
      min_ms_level INTEGER NOT NULL,
      max_ms_level INTEGER NOT NULL, 
      min_parent_mz REAL NOT NULL,
      max_parent_mz REAL NOT NULL,
      min_mz REAL NOT NULL,
      max_mz REAL NOT NULL,
      min_time REAL NOT NULL,
      max_time REAL NOT NULL
    );
"""
    connection.exec(ddlQuery)

    connection
  }

  private def _storePeakelsInPeakelDB(
    sqliteConn: SQLiteConnection,
    peakels: Array[MzDbPeakel],
    ms1SpecHeaders: Array[SpectrumHeader]
  ) {
    
    // Retrieve some mappings relative to the mzDB spectra ids
    val initialIdByMzDbSpecId = ms1SpecHeaders.toLongMapWith { sh =>
      sh.getId -> sh.getInitialId
    }
    val cycleByMzDbSpecId = ms1SpecHeaders.toLongMapWith { sh =>
      sh.getId -> sh.getCycle
    }

    // BEGIN TRANSACTION
    sqliteConn.exec("BEGIN TRANSACTION;")

    // Prepare the insertion in the peakel table
    val peakelStmt = sqliteConn.prepare(
      s"INSERT INTO peakel VALUES (${Array.fill(23)("?").mkString(",")})"
    )
    // Prepare the insertion in the peakel_rtree table
    val peakelIndexStmt = sqliteConn.prepare(
      s"INSERT INTO peakel_rtree VALUES (${Array.fill(9)("?").mkString(",")})"
    )
    
    try {
      for (peakel <- peakels) {

        val scanInitialIds = peakel.getSpectrumIds().map(initialIdByMzDbSpecId(_))
        val peakelMessage = peakel.toPeakelDataMatrix()
        val peakelMessageAsBytes = PeakelDataMatrix.pack(peakelMessage)

        val peakelMz = peakel.getMz
        val peakelTime = peakel.getApexElutionTime

        var fieldNumber = 1
        peakelStmt.bind(fieldNumber, peakel.id); fieldNumber += 1
        peakelStmt.bind(fieldNumber, peakelMz); fieldNumber += 1
        peakelStmt.bind(fieldNumber, peakelTime); fieldNumber += 1
        peakelStmt.bind(fieldNumber, peakel.calcDuration()); fieldNumber += 1
        peakelStmt.bind(fieldNumber, peakel.calcGapCount(cycleByMzDbSpecId)); fieldNumber += 1
        peakelStmt.bind(fieldNumber, peakel.getApexIntensity); fieldNumber += 1
        peakelStmt.bind(fieldNumber, peakel.area); fieldNumber += 1
        peakelStmt.bind(fieldNumber, peakel.calcAmplitude()); fieldNumber += 1
        peakelStmt.bind(fieldNumber, peakel.calcIntensityCv()); fieldNumber += 1
        //peakelStmt.bind(fieldNumber, peakel.calcKurtosis()); fieldNumber += 1
        peakelStmt.bind(fieldNumber, peakel.leftHwhmMean); fieldNumber += 1
        peakelStmt.bind(fieldNumber, peakel.leftHwhmCv); fieldNumber += 1
        peakelStmt.bind(fieldNumber, peakel.rightHwhmMean); fieldNumber += 1
        peakelStmt.bind(fieldNumber, peakel.rightHwhmCv); fieldNumber += 1
        peakelStmt.bind(fieldNumber, 0); fieldNumber += 1 // is_overlapping (0|1 boolean encoding)
        peakelStmt.bind(fieldNumber, 0); fieldNumber += 1 // features_count
        peakelStmt.bind(fieldNumber, peakel.spectrumIds.length); fieldNumber += 1
        peakelStmt.bind(fieldNumber, peakelMessageAsBytes); fieldNumber += 1
        peakelStmt.bindNull(fieldNumber); fieldNumber += 1 // param_tree
        peakelStmt.bind(fieldNumber, scanInitialIds.head); fieldNumber += 1
        peakelStmt.bind(fieldNumber, scanInitialIds.last); fieldNumber += 1
        peakelStmt.bind(fieldNumber, peakel.getApexSpectrumId); fieldNumber += 1
        peakelStmt.bind(fieldNumber, 1); fieldNumber += 1 // ms_level
        peakelStmt.bindNull(fieldNumber); // map_id

        peakelStmt.step()
        peakelStmt.reset()

        fieldNumber = 1
        peakelIndexStmt.bind(fieldNumber, peakel.id); fieldNumber += 1
        peakelIndexStmt.bind(fieldNumber, 1); fieldNumber += 1 // min_ms_level
        peakelIndexStmt.bind(fieldNumber, 1); fieldNumber += 1 // max_ms_level
        peakelIndexStmt.bind(fieldNumber, 0d); fieldNumber += 1 // min_parent_mz
        peakelIndexStmt.bind(fieldNumber, 0d); fieldNumber += 1 // max_parent_mz
        peakelIndexStmt.bind(fieldNumber, peakelMz); fieldNumber += 1 // min_mz
        peakelIndexStmt.bind(fieldNumber, peakelMz); fieldNumber += 1 // max_mz
        peakelIndexStmt.bind(fieldNumber, peakelTime); fieldNumber += 1 // min_time
        peakelIndexStmt.bind(fieldNumber, peakelTime); // max_time

        peakelIndexStmt.step()
        peakelIndexStmt.reset()
      }
    } finally {
      // Release statements
      peakelStmt.dispose()
      peakelIndexStmt.dispose()
    }

    // COMMIT TRANSACTION
    sqliteConn.exec("COMMIT TRANSACTION;")
  }

    // TODO: remove me (an in-memory R*Tree is now used)
  /*private def _findPeakelsInRangeV1(
    sqliteConn: SQLiteConnection,
    minMz: Double,
    maxMz: Double,
    minTime: Float,
    maxTime: Float
  ): Array[MzDbPeakel] = {
    
    val peakelRtreeSqlQuery = "SELECT id, peaks, left_hwhm_mean, left_hwhm_cv, " +
    "right_hwhm_mean, right_hwhm_cv FROM peakel WHERE id IN " +
    "(SELECT id FROM peakel_rtree WHERE min_mz >= ? AND max_mz <= ? AND min_time >= ? AND max_time <= ? );"

    val peakelStmt = sqliteConn.prepare(peakelRtreeSqlQuery, false)
    val peakels = new ArrayBuffer[MzDbPeakel]()

    try {
      peakelStmt.bind(1, minMz)
      peakelStmt.bind(2, maxMz)
      peakelStmt.bind(3, minTime)
      peakelStmt.bind(4, maxTime)
      //println(s"minMz=$minMz maxMz=$maxMz minTime=$minTime maxTime=$maxTime")

      while (peakelStmt.step()) {
        val peakelId = peakelStmt.columnInt(0)
        val peakelMessageAsBytes = peakelStmt.columnBlob(1)
        //println(peakelId)
        
        //val peakelMessage = ProfiMsgPack.deserialize[PeakelDataMatrix](peakelMessageAsBytes)
        val peakelMessage = PeakelDataMatrix.unpack(peakelMessageAsBytes)
        val (intensitySum, area) = peakelMessage.integratePeakel()

        peakels += new MzDbPeakel(
          peakelId,
          peakelMessage,
          intensitySum,
          area,
          leftHwhmMean = peakelStmt.columnDouble(2).toFloat,
          leftHwhmCv = peakelStmt.columnDouble(3).toFloat,
          rightHwhmMean = peakelStmt.columnDouble(4).toFloat,
          rightHwhmCv = peakelStmt.columnDouble(5).toFloat
        )
      }

    } finally {
      // Release resources
      peakelStmt.dispose()
    }

    peakels.toArray
  }*/
  
  // Important: SQlite R*Tree floating values are 32bits floats, thus we need to expand the search
  // Advice from SQLite developers (https://www.sqlite.org/rtree.html#roundoff_error):
  // Applications should expand their contained-within query boxes slightly (by 0.000012%)
  // by rounding down the lower coordinates and rounding up the top coordinates, in each dimension.
  private val SQLITE_RTREE_UB_CORR = 1.0 + 0.00000012
  private val SQLITE_RTREE_LB_CORR = 1.0 - 0.00000012

  private def _findPeakelIdsInRangeFromPeakelDB(
    sqliteConn: SQLiteConnection,
    minMz: Double,
    maxMz: Double,
    minTime: Float,
    maxTime: Float
  ): Seq[Int] = {
    
    // Retrieve peakel ids using the peakelDB R*Tree
    val peakelIdRtreeSqlQuery = "SELECT id, min_mz, max_mz FROM peakel_rtree WHERE min_mz >= ? AND max_mz <= ? AND min_time >= ? AND max_time <= ?"

    val peakelIdStmt = sqliteConn.prepare(peakelIdRtreeSqlQuery, false)
    val peakelIds = new ArrayBuffer[Int]()
    
    try {
      peakelIdStmt
        .bind(1, minMz * SQLITE_RTREE_LB_CORR)
        .bind(2, maxMz * SQLITE_RTREE_UB_CORR)
        .bind(3, minTime)
        .bind(4, maxTime)
      //println(s"minMz=$minMz maxMz=$maxMz minTime=$minTime maxTime=$maxTime")

      while (peakelIdStmt.step()) {
        val mz1 = peakelIdStmt.columnDouble(1)
        val mz2 = peakelIdStmt.columnDouble(2)
        // Filter the peakel again to compensate for query padding
        if (mz1 >= minMz || mz2 <= maxMz) {
          peakelIds += peakelIdStmt.columnInt(0)
        }
      }

    } finally {
      // Release resources
      peakelIdStmt.dispose()
    }

    peakelIds
  }

  private def _findPeakelIdsInRange(
    rTree: RTree[java.lang.Integer,geometry.Point],
    minMz: Double,
    maxMz: Double,
    minTime: Float,
    maxTime: Float
  ): Seq[Int] = {
    
    // Retrieve peakel ids using the in-memory R*Tree
    val peakelIdIter = rTree.search(
      geometry.Geometries.rectangle(
        minMz, minTime, maxMz, maxTime
      )
    ).toBlocking().toIterable().iterator()
    
    val peakelIds = new ArrayBuffer[Int]()
    while( peakelIdIter.hasNext() ) {
      peakelIds += peakelIdIter.next().value()
    }
    
    peakelIds
  }
  
  private def _findPeakelsInRange(
    sqliteConn: SQLiteConnection,
    rTreeOpt: Option[RTree[java.lang.Integer,geometry.Point]],
    minMz: Double,
    maxMz: Double,
    minTime: Float,
    maxTime: Float
  ): Array[MzDbPeakel] = {
    
    // Retrieve peakel ids using the in-memory R*Tree
    val peakelIds = if (rTreeOpt.isDefined) {
      _findPeakelIdsInRange(rTreeOpt.get, minMz, maxMz, minTime, maxTime)
    }
    // Retrieve peakel ids using the peakelDB R*Tree
    else {
      _findPeakelIdsInRangeFromPeakelDB(sqliteConn, minMz, maxMz, minTime, maxTime)
    }
    
    //assert(peakelIdsMem.length == peakelIds.length, "something wrong in Rtrees")
    
    val peakels = _loadPeakelsForIds(sqliteConn, peakelIds).toArray
    
    peakels
  }

  private def _loadPeakelsForIds(sqliteConn: SQLiteConnection, peakelIds: Seq[Int]): ArrayBuffer[MzDbPeakel] = {

    val peakelPkSqlQuery = "SELECT id, peaks, left_hwhm_mean, left_hwhm_cv, " +
    s"right_hwhm_mean, right_hwhm_cv FROM peakel WHERE id IN (${peakelIds.mkString(",")});"

    val peakels = _loadPeakelsForQuery(sqliteConn, peakelPkSqlQuery, peakelIds.length)

    assert(peakelIds.length == peakels.length, "invalid number of retrieved peakels from peakelDB file")

    peakels
  }
  
  private def _loadAllPeakels(sqliteConn: SQLiteConnection, sizeHint: Int = 50000): ArrayBuffer[MzDbPeakel] = {
    this._loadManyPeakels(sqliteConn, sizeHint)
  }
  
  private def _loadManyPeakels(
    sqliteConn: SQLiteConnection,
    sizeHint: Int = 50000,
    idPredicate: Option[Int => Boolean] = None
  ): ArrayBuffer[MzDbPeakel] = {

    val dbFile = sqliteConn.getDatabaseFile
    // Read file if not in-memory database
    if (dbFile != null) this._readWholeFile(sqliteConn.getDatabaseFile)
    
    val peakelSqlQuery = "SELECT id, peaks, left_hwhm_mean, left_hwhm_cv, right_hwhm_mean, right_hwhm_cv FROM peakel;"
    
    _loadPeakelsForQuery(sqliteConn, peakelSqlQuery,sizeHint,idPredicate) 
  }
  
  private def _readWholeFile(file: File) {

    val fis = new java.io.FileInputStream(file)
    val b = new Array[Byte](1024 * 1024)

    while (fis.available() != 0) {
      fis.read(b)
    }

    fis.close()
  }
  
  private def _loadPeakelsForQuery(
    sqliteConn: SQLiteConnection,
    sqlQuery: String,
    sizeHint: Int = 100,
    idPredicate: Option[Int => Boolean] = None
  ): ArrayBuffer[MzDbPeakel] = {
    
    val peakelStmt = sqliteConn.prepare(sqlQuery, false)
    
    val peakels = new ArrayBuffer[MzDbPeakel](sizeHint)

    try {
      
      if (idPredicate.isDefined) {
        while (peakelStmt.step()) {
          if ( idPredicate.get.apply(peakelStmt.columnInt(0)) ) {
            peakels += this._buildMzDbPeakel(peakelStmt)
          }
        }
      }
      else {
        while (peakelStmt.step()) {
          peakels += this._buildMzDbPeakel(peakelStmt)
        }  
      }
      
    } finally {
      // Release resources
      peakelStmt.dispose()
    }
    
    peakels
  }
  
  private def _loadPeakelDbInMemory(peakelDb: SQLiteConnection): SQLiteConnection = {
    val backup = peakelDb.initializeBackup(null)
    val memPeakelDb = backup.getDestinationConnection()
    while (!backup.isFinished()) {
      backup.backupStep(-1)
    }
    
    backup.dispose(false) // false means "do not dispose the in-memory database"
    
    memPeakelDb
  }
  
  // TODO: move to MzDbReader when peakels are stored in the MzDbFile
  private def _streamPeakels(
    peakelFile: File
  ): Observable[Array[MzDbPeakel]] = {

    Observable[Array[MzDbPeakel]] { subscriber =>
      
      this.logger.info(s"Loading peakels from existing peakelDB ${peakelFile}")
      
      // Open TMP SQLite file
      val sqliteConn = new SQLiteConnection(peakelFile)
      sqliteConn.openReadonly()
      
      try {
        
        val peakelsBuffer = this._loadAllPeakels(sqliteConn)
        
        subscriber.onNext(peakelsBuffer.toArray)
        subscriber.onCompleted()
  
      } finally {
        // Release resources
        sqliteConn.dispose()
      }
    }

  }
  
  private def _buildMzDbPeakel(peakelStmt: SQLiteStatement): MzDbPeakel = {
    val peakelId = peakelStmt.columnInt(0)
    val peakelMessageAsBytes = peakelStmt.columnBlob(1)
    
    //val peakelMessage = ProfiMsgPack.deserialize[PeakelDataMatrix](peakelMessageAsBytes)
    val peakelMessage = PeakelDataMatrix.unpack(peakelMessageAsBytes)
    val (intensitySum, area) = peakelMessage.integratePeakel()

    new MzDbPeakel(
      peakelId,
      peakelMessage,
      intensitySum,
      area,
      leftHwhmMean = peakelStmt.columnDouble(2).toFloat,
      leftHwhmCv = peakelStmt.columnDouble(3).toFloat,
      rightHwhmMean = peakelStmt.columnDouble(4).toFloat,
      rightHwhmCv = peakelStmt.columnDouble(5).toFloat
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
    metric: Metric
    //benchmarkMap: HashMap[String,Long]
  ): Option[(MzDbPeakel,Boolean)] = {
    
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
      multimatchedPeakelIdSet.contains(foundPeakel.id) || ! assignedMzDbPeakelIdSet.contains(foundPeakel.id)
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
    } else if(filteredPeakels.length == 1) {
      Some(filteredPeakels.head)
    }
    else {
      
      var reliablePeakels = filteredPeakels.filter(_._2)
      if (reliablePeakels.isEmpty) { reliablePeakels = filteredPeakels }
      val nearestPeakelInTime = reliablePeakels.minBy { case (peakel,isReliable) => 
        math.abs(avgTime - peakel.calcWeightedAverageTime())
      }

      val nearestFilteredPeakelInTime = filteredPeakels.minBy { case (peakel,isReliable) => 
        math.abs(avgTime - peakel.calcWeightedAverageTime())
      }

      if (nearestFilteredPeakelInTime != nearestPeakelInTime) { metric.incr("nearestPeakelInTime.not.reliable") }
      
      //benchmarkMap("nearestPeakelInTime") = benchmarkMap.getOrElseUpdate("nearestPeakelInTime",0L) + (System.nanoTime() - start)
    
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
        val foundPeakels = _findPeakelsInRange(
          sqliteConn,
          rTreeOpt,
          ipMoz - mozTolInDa,
          ipMoz + mozTolInDa,
          minRt,
          maxRt
        )

        if (foundPeakels.nonEmpty) {

          val isotopePeakel = _findCorrelatingPeakel(peakel, foundPeakels)

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
  
  private def _findCorrelatingPeakel(ref: MzDbPeakel, peakels: Array[MzDbPeakel]): Option[MzDbPeakel] = {
    
    val correlations = peakels.map { peakel => 
      _computeCorrelation(ref,peakel) -> peakel
    }
    val (correlation, bestPeakel) = correlations.maxBy(_._1)
    
    // TODO: DBO => I don't like the result of the pearson correlation, 
    // I think we should not apply a filter based on this metric
    if (correlation > 0.6) Some(bestPeakel)
    else None
  }

  private def _computeCorrelation(p1: MzDbPeakel, p2: MzDbPeakel): Double = {
    var p1Offset = 0
    var p2Offset = 0

    // not clean : some RT values can be missing in elutionTime array when intensity = 0
    if (p1.getFirstElutionTime() < p2.getFirstElutionTime()) {
      // search p2.firstElutionTime index in p1
      val nearestIdx = Arrays.binarySearch(p1.getElutionTimes(), p2.getFirstElutionTime())
      p2Offset = if (nearestIdx < 0) ~nearestIdx else nearestIdx
    } else {
      // search p1.firstElutionTime in p2
      val nearestIdx = Arrays.binarySearch(p2.getElutionTimes(), p1.getFirstElutionTime())
      p1Offset = if (nearestIdx < 0) ~nearestIdx else nearestIdx
    }

    val p1Values = p1.getIntensityValues()
    val p2Values = p2.getIntensityValues()
    val length = Math.max(p1Values.length + p1Offset, p2Values.length + p2Offset)

    val y1 = new Array[Double](length)
    val y2 = new Array[Double](length)
    var k = 0
    while (k < length) {
      if (k >= p1Offset && k < p1Values.length) {
        y1(k) = p1Values(k - p1Offset)
      }
      if (k >= p2Offset && k < p2Values.length) {
        y2(k) = p2Values(k - p2Offset)
      }
      k += 1
    }

    val pearson = new PearsonsCorrelation()
    math.abs(pearson.correlation(y1, y2))
  }
  
  private def _extractProcessedMap(lcmsRun: LcMsRun, mzDbFile: File, mapNumber: Int, mapSetId: Long): ProcessedMap = {

    // TODO: add max charge in config
    val maxCharge = 4
    val mzDbFts = if (quantConfig.detectFeatures) this._detectFeatures(mzDbFile).filter(ft => ft.charge > 1 && ft.charge <= maxCharge)
    else this._extractFeaturesUsingMs2Events(mzDbFile, lcmsRun)

    val rmsds = mzDbFts.par.map { mzdbFt =>
      val theoAbundances = IsotopePatternInterpolator.getTheoreticalPattern(mzdbFt.mz, mzdbFt.charge).abundances
      val peakelApexIntensities = mzdbFt.getPeakels.map(_.getApexIntensity)
      IsotopePatternInterpolator.calcAbundancesRmsd(theoAbundances, peakelApexIntensities)
    }.toArray

    val percentileComputer = new Percentile()
    val ipDevQ1 = percentileComputer.evaluate(rmsds, 25)
    val ipDevQ3 = percentileComputer.evaluate(rmsds, 75)
    val ipDevIQR = ipDevQ3 - ipDevQ1
    val ipDevUB = ipDevQ3 + 1.5 * ipDevIQR

    val rawMapId = RawMap.generateNewId()

    // Create a new raw Map
    val tmpRawMap = new RawMap(
      id = rawMapId,
      name = lcmsRun.rawFile.name,
      isProcessed = false,
      creationTimestamp = new java.util.Date,
      features = Array(),
      runId = lcmsRun.id,
      peakPickingSoftware = pps,
      properties = Some(
        LcMsMapProperties(
          ipDeviationUpperBound = Some(ipDevUB.toFloat)
        )
      )
    )

    // Convert features
    val peakelIdByMzDbPeakelId = new LongMap[Long]()
    val lcmsFeaturesWithoutClusters = mzDbFts.map { mzDbFt =>
      this._mzDbFeatureToLcMsFeature(mzDbFt, None, rawMapId, lcmsRun.scanSequence.get, peakelIdByMzDbPeakelId)
    }
    
    val ftPeakels = lcmsFeaturesWithoutClusters.flatMap( _.relations.peakelItems.map(_.peakelReference.asInstanceOf[Peakel] ) )

    val rawMap = tmpRawMap.copy(
      features = lcmsFeaturesWithoutClusters,
      peakels = Some(ftPeakels.groupByLong(_.id).values.map(_.head).toArray)
    )

    rawMap.toProcessedMap(mapNumber, mapSetId)
  }

  private def _extractFeaturesUsingMs2Events(mzDbFile: File, lcmsRun: LcMsRun): Array[MzDbFeature] = {

    logger.info("Start extracting features from MS2 events from " + mzDbFile.getName)

    val restrictToIdentifiedPeptides = quantConfig.startFromValidatedPeptides
    val peptideByScanNumber = peptideByRunIdAndScanNumber.map(_(lcmsRun.id)).getOrElse(LongMap.empty[Peptide])
    val mzDb = new MzDbReader(mzDbFile, true)

    val mzDbFts = try {

      val ftXtractConfig = FeatureExtractorConfig(
        mzTolPPM = this.mozTolPPM
      )

      val mzdbFtX = new MzDbFeatureExtractor(mzDb, 5, 5, ftXtractConfig)

      this.logger.info("Retrieving scan headers...")
      val scanHeaders = mzDb.getSpectrumHeaders()
      val ms2ScanHeaders = scanHeaders.filter(_.getMsLevel() == 2)
      val pfs = new ArrayBuffer[PutativeFeature](ms2ScanHeaders.length)

      this.logger.debug("Building putative features list from MS2 scan events...")

      for (scanH <- ms2ScanHeaders) {

        if (!restrictToIdentifiedPeptides || peptideByScanNumber.contains(scanH.getInitialId())) {
          pfs += new PutativeFeature(
            id = PutativeFeature.generateNewId,
            mz = scanH.getPrecursorMz,
            charge = scanH.getPrecursorCharge,
            spectrumId = scanH.getId,
            evidenceMsLevel = 2
          )
        }
      }

      // Instantiates a Run Slice Data provider
      val rsdProv = new RunSliceDataProvider(mzDb.getLcMsRunSliceIterator())

      // Extract features
      mzdbFtX.extractFeatures(rsdProv, pfs, mozTolPPM)

    } finally {
      mzDb.close()
    }

    logger.info("Feature extraction done for file " + mzDbFile.getName)
    mzDbFts.toArray
  }

  private def _detectFeatures(mzDbFile: File): Array[MzDbFeature] = {

    val mzDb = new MzDbReader(mzDbFile, true)

    val mzDbFts = try {

      this.logger.info("Start detecting features in raw MS survey from " + mzDbFile.getName)

      val mzdbFtDetector = new MzDbFeatureDetector(
        mzDb,
        FeatureDetectorConfig(
          msLevel = 1,
          mzTolPPM = mozTolPPM,
          minNbOverlappingIPs = 5
        )
      )

      // Extract features
      mzdbFtDetector.detectFeatures()

    } finally {
      mzDb.close()
    }

    mzDbFts
  }

  private def _extractMissingFeatures(
    mzDbFile: File,
    lcmsRun: LcMsRun,
    processedMap: ProcessedMap,
    mapSet: MapSet,
    peakelIdByMzDbPeakelId: LongMap[Long]
  ): Seq[Feature] = {

    val procMapId = processedMap.id
    val rawMapId = processedMap.getRawMapIds().head
    val masterMap = mapSet.masterMap
    val nbMaps = mapSet.childMaps.length

    val mzDb = new MzDbReader(mzDbFile, true)
    var mzDbFts = Seq.empty[MzDbFeature]
    val mftsWithMissingChild = new ArrayBuffer[Feature]
    val missingFtIdByMftId = new LongMap[Int]()
    val pfs = new ArrayBuffer[PutativeFeature]()

    try {

      val ftXtractConfig = FeatureExtractorConfig(
        mzTolPPM = this.mozTolPPM,
        maxIPDeviation = processedMap.properties.flatMap(_.ipDeviationUpperBound)
      )

      val mzdbFtX = new MzDbFeatureExtractor(mzDb, 5, 5, ftXtractConfig)

      //val scanHeaders = mzDb.getScanHeaders()
      //val ms2ScanHeaders = scanHeaders.filter(_.getMsLevel() == 2 )
      
      this.logger.info("Start extracting missing Features from " + mzDbFile.getName)
      this.logger.info("Building putative features list using master features...")

      for (mft <- masterMap.features) {
        require(mft.children.length <= nbMaps, "master feature contains more child features than maps")

        // Check for master features having a missing child for this processed map
        val childFtOpt = mft.children.find(_.relations.processedMapId == processedMap.id)
        if (childFtOpt.isEmpty) {
          mftsWithMissingChild += mft

          val bestChildProcMapId = mft.relations.bestChildProcessedMapId
          val bestChild = mft.children.find(_.relations.processedMapId == bestChildProcMapId).get

          //val childMapAlnSet = revRefMapAlnSetByMapId(bestChildMapId)
          //val predictedTime = childMapAlnSet.calcReferenceElutionTime(mft.elutionTime, mft.mass)
          var predictedTime = mapSet.convertElutionTime(bestChild.elutionTime, bestChildProcMapId, procMapId)
          //println( "ftTime="+ mft.elutionTime +" and predicted time (in "+mzDbMapId+")="+predictedTime)

          // Fix negative predicted times
          if (predictedTime <= 0) predictedTime = 1f

          // Note: we can have multiple missing features for a given MFT
          // However we assume there a single missing feature for a given map
          val missingFtId = PutativeFeature.generateNewId
          missingFtIdByMftId += (mft.id -> missingFtId)

          val pf = new PutativeFeature(
            id = missingFtId,
            mz = mft.moz,
            charge = mft.charge,
            elutionTime = predictedTime,
            evidenceMsLevel = 2
          )

          pf.isPredicted = true
          
          // TODO: check the usage of these values
          pf.durations = mft.children.map(_.duration)
          pf.areas = mft.children.map(_.intensity)
          pf.mozs = mft.children.map(_.moz)

          pfs += pf

        }
      }

      // Instantiates a Run Slice Data provider
      val rsdProv = new RunSliceDataProvider(mzDb.getLcMsRunSliceIterator())
      this.logger.info("Extracting " + missingFtIdByMftId.size + " missing features from " + mzDbFile.getName)
      // Extract features
      // TODO: add minNbCycles param
      mzDbFts = mzdbFtX.extractFeatures(rsdProv, pfs, mozTolPPM)

    } finally {
      mzDb.close()
    }

    val pfById = pfs.mapByLong(_.id)
    val mzDbFtById = mzDbFts.mapByLong(_.id)

    // Convert mzDB features into LC-MS DB features
    val newLcmsFeatures = new ArrayBuffer[LcMsFeature](missingFtIdByMftId.size)
    for (
      mftWithMissingChild <- mftsWithMissingChild;
      mzDbFt <- mzDbFtById.get(missingFtIdByMftId(mftWithMissingChild.id)) if mzDbFt.area > 0 && mzDbFt.getMs1Count >= 5
    ) {
      
      // Retrieve putative feature
      val pf = pfById(mzDbFt.id)

      // FIXME: why do we extract features with 0 duration ???

      // Convert the extracted feature into a LC-MS feature
      val newLcmsFt = this._mzDbFeatureToLcMsFeature(mzDbFt, Some(pf), rawMapId, lcmsRun.scanSequence.get, peakelIdByMzDbPeakelId)

      // TODO: decide if we set or not this value (it may help for distinction with other features)
      newLcmsFt.correctedElutionTime = Some(mftWithMissingChild.elutionTime)

      // Update the processed map id of the new feature
      newLcmsFt.relations.processedMapId = processedMap.id

      // Add missing child feature to the master feature
      mftWithMissingChild.children ++= Array(newLcmsFt)

      // Add new LC-MS feature
      newLcmsFeatures += newLcmsFt
    }

    newLcmsFeatures
  }

  // TODO: apply this before and after filling missing values ???
  // Note: in this method we will break master features if they are matching multiple peptides
  // However we do not group existing master features
  private def _rebuildMasterMapUsingPeptides(
    mapSet: MapSet,
    peptideByScanId: LongMap[Peptide],
    clustererByMapId: LongMap[FeatureClusterer]
  ): Unit = {
    this.logger.info("Re-building master map using peptide identities...")

    val alnRefMapId = mapSet.getAlnReferenceMapId
    val masterFeatures = mapSet.masterMap.features
    val newMasterFeatures = new ArrayBuffer[Feature](masterFeatures.length)

    // Iterate over all map set master features
    for (mft <- masterFeatures) {

      // --- Find peptides matching child sub features ---
      val featuresByPepId = new LongMap[ArrayBuffer[Feature]]
      val pepIdsByFeature = new HashMap[Feature, ArrayBuffer[Long]]
      val unidentifiedFtSet = new collection.mutable.HashSet[Feature]

      // Decompose clusters if they exist and map them by peptide identification
      mft.eachChildSubFeature { subFt =>
        val ftRelations = subFt.relations
        val peptideIds = ftRelations.ms2EventIds.map(peptideByScanId.get(_)).withFilter(_.isDefined).map(_.get.id).distinct

        if (peptideIds.isEmpty) unidentifiedFtSet += subFt
        else {
          for (pepId <- peptideIds) {
            featuresByPepId.getOrElseUpdate(pepId, new ArrayBuffer[Feature]) += subFt
            pepIdsByFeature.getOrElseUpdate(subFt, new ArrayBuffer[Long]) += pepId
          }
        }
      }

      // --- Solve identification conflicts ---

      // If no identified feature in this master
      if (featuresByPepId.isEmpty) {
        // We keep the existing master feature as is
        newMasterFeatures += mft
      } // If all child features match the same peptide
      else if (featuresByPepId.size == 1) {
        // We tag this master feature with the peptide ID
        mft.relations.peptideId = featuresByPepId.head._1
        // And we keep the existing master feature
        newMasterFeatures += mft
      } else {
        // Else we create a master feature for each matching peptide
        for ((pepId, features) <- featuresByPepId) {

          val newMftFeatures = features ++ unidentifiedFtSet
          val ftsByMapId = newMftFeatures.groupBy(_.relations.processedMapId)
          val clusterizedFeatures = new ArrayBuffer[Feature]

          // Check if these features are assigned to a single peptide
          newMftFeatures.foreach { ft =>
            if (pepIdsByFeature.get(ft).map(_.length).getOrElse(0) > 1) {
              // Flag this feature as a conflicting one
              ft.selectionLevel = 0
            }
          }

          // Iterate over features grouped by maps
          for ((mapId, fts) <- ftsByMapId) {
            // If we have a single feature for this map => we keep it as is
            if (fts.length == 1) clusterizedFeatures += fts.head
            // Else we clusterize the multiple detected features
            else {

              // Partition identified and unidentified features
              val (identifiedFts, unidentifiedFts) = fts.partition(unidentifiedFtSet.contains(_) == false)

              // If we don't have at least one identified feature
              val ftCluster = if (identifiedFts.isEmpty) {
                // Clusterize unidentified features
                clustererByMapId(mapId).buildFeatureCluster(unidentifiedFts)
              } else {
                // Else clusterize identified features
                val tmpFtCluster = clustererByMapId(mapId).buildFeatureCluster(identifiedFts)

                if (unidentifiedFts.isEmpty == false) {
                  // Append unidentified features to the cluster
                  tmpFtCluster.subFeatures ++= unidentifiedFts
                  // Flag this cluster as a conflicting feature
                  tmpFtCluster.selectionLevel = 0
                }

                tmpFtCluster
              }

              // Append cluster to the master features list
              clusterizedFeatures += ftCluster
            }
          }

          val refFtOpt = clusterizedFeatures.find(_.relations.processedMapId == alnRefMapId)
          val refFt = refFtOpt.getOrElse(clusterizedFeatures.head)
          val newMft = refFt.toMasterFeature(children = clusterizedFeatures.toArray)

          // We tag this new master feature with the peptide ID
          newMft.relations.peptideId = pepId

          newMasterFeatures += newMft
        }
      }
    }

    // TODO: find where duplicated mft are introduced
    /*val mftByPep = newMasterFeatures.groupBy(_.relations.peptideId)
    for( (pid,mftbs) <- mftByPep) {
      println("pid="+pid)
      println("mftbs="+mftbs.length)
    }
    error("")*/

    mapSet.masterMap = mapSet.masterMap.copy(features = newMasterFeatures.toArray)

    ()
  }
  
  private def _mzDbPeakelToLcMsPeakel(
    mzDbPeakel: MzDbPeakel,
    lcMsPeakelIdOpt: Option[Long],
    rawMapId: Long,
    scanSeq: LcMsScanSequence
  ): Peakel = {
    
    // Retrieve some vars
    val lcmsScanIdByInitialId = scanSeq.scanIdByInitialId
    
    // Create the peakel data matrix
    val peakelDataMatrix = new PeakelDataMatrix(
      // Convert mzDB scan IDs into LCMSdb scan ids (same warning as above)
      spectrumIds = mzDbPeakel.spectrumIds.map( sid => lcmsScanIdByInitialId(sid.toInt) ),
      elutionTimes = mzDbPeakel.elutionTimes,
      mzValues = mzDbPeakel.mzValues,
      intensityValues = mzDbPeakel.intensityValues
    )

    val lcmsPeakel = Peakel(
      id = lcMsPeakelIdOpt.getOrElse(Peakel.generateNewId),
      moz = mzDbPeakel.getMz,
      elutionTime = mzDbPeakel.getElutionTime(),
      //apexIntensity = mzDbPeakel.getApexIntensity(), now lazilly computed
      area = mzDbPeakel.area,
      duration = mzDbPeakel.calcDuration,
      //fwhm = Some( mzDbPeakel.fwhm ),
      isOverlapping = false, // FIXME: determine this value
      featuresCount = 1,
      dataMatrix = peakelDataMatrix,
      // FIXME: scanId and scanInitialId may be different in future mzDB configurations
      //firstScanId = lcmsScanIdByInitialId(peakelScanInitialIds.head),
      //lastScanId = lcmsScanIdByInitialId(peakelScanInitialIds.last),
      //apexScanId = lcmsScanIdByInitialId(mzDbPeakel.getApexScanInitialId),
      rawMapId = rawMapId
    )

    lcmsPeakel
  }
  
  private def _mzDbFeatureToLcMsFeature(
    mzDbFt: MzDbFeature,
    putativeFeature: Option[PutativeFeature],
    rawMapId: Long,
    scanSeq: LcMsScanSequence,
    peakelIdByMzDbPeakelId: LongMap[Long]
  ): LcMsFeature = {
    
    val ftId = LcMsFeature.generateNewId

    // Retrieve some vars
    val lcmsScanIdByInitialId = scanSeq.scanIdByInitialId
    val scanInitialIds = mzDbFt.getSpectrumIds
    
    // WARNING: we assume here that these methods returns the initial ID but it may change in the future
    val apexScanInitialId = mzDbFt.getApexSpectrumId.toInt
    val (firstScanInitialId, lastScanInitialId) = (scanInitialIds.head.toInt, scanInitialIds.last.toInt)
    val firstLcMsScanId = lcmsScanIdByInitialId(firstScanInitialId)
    val lastLcMsScanId = lcmsScanIdByInitialId(lastScanInitialId)
    val apexLcMsScanId = lcmsScanIdByInitialId(apexScanInitialId)
    val ms2EventIds = mzDbFt.getMs2SpectrumIds.map(sid => lcmsScanIdByInitialId(sid.toInt))
    // END OF WARNING

    val approxMass = mzDbFt.mz * mzDbFt.charge
    // FIXME: DBO => this is not very accurate (what about big peptides ?)
    val theoBasePeakelIndex = if (approxMass < 2000) 0
    else if (approxMass < 3500) 1
    else 2
    
    val indexedPeakels = mzDbFt.indexedPeakels
    
    var basePeakelIndex = 0
    for ( (peakel,isotopeIdx) <- indexedPeakels ) {
      if (isotopeIdx <= theoBasePeakelIndex) {
        basePeakelIndex = isotopeIdx
      }
    }
    val basePeakel = indexedPeakels(basePeakelIndex)._1

    val lcmsFtPeakelItems = for ( (mzDbPeakel, peakelIdx) <- indexedPeakels) yield {
      
      // Retrieve perisited LC-MS peakel id if it exists
      val lcmsPeakelIdOpt = peakelIdByMzDbPeakelId.get(mzDbPeakel.id)
      
      val lcmsPeakel = this._mzDbPeakelToLcMsPeakel(mzDbPeakel,lcmsPeakelIdOpt,rawMapId,scanSeq)
      
      // Cache new LC-MS peakel ID
      peakelIdByMzDbPeakelId(mzDbPeakel.id) = lcmsPeakel.id
      
      FeaturePeakelItem(
        featureReference = FeatureIdentifier(ftId),
        peakelReference = lcmsPeakel,
        isotopeIndex = peakelIdx,
        isBasePeakel = (peakelIdx == basePeakelIndex)
      )
    }
    
    val ftProps = FeatureProperties()
    if (mzDbFt.isPredicted) {
      require(
        putativeFeature.isDefined,
        "the putativeFeature must be defined if the feature has been predicted"
      )
      
      // Add predicted time property
      val predictedTime = putativeFeature.get.elutionTime
      ftProps.setPredictedElutionTime(Some(predictedTime))
    }
    
    new LcMsFeature(
      id = ftId,
      moz = mzDbFt.mz,
      apexIntensity = basePeakel.getApexIntensity(),
      intensity = basePeakel.getApexIntensity(),
      charge = mzDbFt.charge,
      elutionTime = mzDbFt.getElutionTime,
      duration = mzDbFt.calcDuration(),
      qualityScore = Option(mzDbFt.qualityProperties).map(_.qualityScore).getOrElse(0f),
      ms1Count = mzDbFt.getMs1Count,
      ms2Count = mzDbFt.getMs2Count,
      isOverlapping = false,
      isotopicPatterns = None,
      selectionLevel = 2,
      properties = Some(ftProps),
      relations = new FeatureRelations(
        ms2EventIds = ms2EventIds,
        peakelItems = lcmsFtPeakelItems,
        peakelsCount = lcmsFtPeakelItems.length,
        firstScanInitialId = firstScanInitialId,
        lastScanInitialId = lastScanInitialId,
        apexScanInitialId = apexScanInitialId,
        firstScanId = firstLcMsScanId,
        lastScanId = lastLcMsScanId,
        apexScanId = apexLcMsScanId,
        rawMapId = rawMapId
      )
    )
  }

}

