package fr.proline.core.service.lcms.io

import java.io.File
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.collection.mutable.HashSet
import scala.collection.mutable.LongMap
import scala.util.control.Breaks._
import com.almworks.sqlite4java.SQLiteConnection
import com.github.davidmoten.rtree._
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.math3.stat.descriptive.rank.Percentile
import org.apache.commons.math3.stat.descriptive.SummaryStatistics
import fr.profi.chemistry.model.MolecularConstants
import fr.profi.jdbc.easy._
import fr.profi.ms.algo.IsotopePatternInterpolator
import fr.profi.ms.algo.IsotopePatternEstimator
import fr.profi.mzdb._
import fr.profi.mzdb.algo.IsotopicPatternScorer
import fr.profi.mzdb.algo.feature.extraction.FeatureExtractorConfig
import fr.profi.mzdb.io.reader.provider.RunSliceDataProvider
import fr.profi.mzdb.model.{ Feature => MzDbFeature, Peak => MzDbPeak, Peakel => MzDbPeakel, PeakelBuilder, SpectrumHeader }
import fr.profi.mzdb.model.PeakelDataMatrix
import fr.profi.mzdb.model.PutativeFeature
import fr.profi.mzdb.model.SpectrumData
import fr.profi.mzdb.utils.ms.MsUtils
import fr.profi.util.collection._
import fr.profi.util.ms.massToMoz
import fr.profi.util.metrics.Metric
import fr.profi.util.serialization.ProfiMsgPack
import fr.proline.context.LcMsDbConnectionContext
import fr.proline.core.algo.lcms._
import fr.proline.core.algo.msq.config._
import fr.proline.core.dal.helper.LcmsDbHelper
import fr.proline.core.dal.{ DoJDBCWork, DoJDBCReturningWork }
import fr.proline.core.om.model.lcms.{ Feature => LcMsFeature, IsotopicPattern => LcMsIsotopicPattern }
import fr.proline.core.om.model.lcms._
import fr.proline.core.om.model.msi.{ Instrument, Peptide, PeptideMatch }
import fr.proline.core.om.provider.lcms.impl.SQLScanSequenceProvider
import fr.proline.core.om.storer.lcms.MasterMapStorer
import fr.proline.core.om.storer.lcms.ProcessedMapStorer
import fr.proline.core.om.storer.lcms.RawMapStorer
import fr.proline.core.om.storer.lcms.impl.SQLScanSequenceStorer
import fr.proline.core.service.lcms.ILcMsService
import fr.proline.core.service.lcms.AlignMapSet
import fr.proline.core.service.lcms.CreateMapSet
import fr.proline.core.service.lcms.CreateMasterMap
import fr.proline.core.algo.lcms.alignment.AlignmentResult
import fr.profi.ms.model.TheoreticalIsotopePattern

/**
 * @author David Bouyssie
 *
 */
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
  protected val mapAligner = LcmsMapAligner(methodName = alnMethodName)

  // FIXME: generate new id and store the pps
  protected val pps = new PeakPickingSoftware(
    id = 1,
    name = "Proline",
    version = "0.6.0",
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

    val mzDbFileByLcMsRunId = new LongMap[File](lcMsRuns.length)
    for (lcmsRun <- lcMsRuns) {

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
      this._detectMapSetFromPeakels(lcMsRuns, mzDbFileByLcMsRunId, tmpMapSetId)
      // Old way of map set creation (individual map extraction)
    } else {
      // TODO: move in a specific class implem (ExtractMapSet)
      this._extractMapSet(lcMsRuns, mzDbFileByLcMsRunId, tmpMapSetId)
    }

    // End of finalMapSet assignment bloc ....
    
    // Update processed map id of each feature
    // FIXME: DBO => why is it necessary now (I mean after the introduction of detectPeakels mode) ???
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
   
    // CBY: commented = could not find usage of this value .... ??
    //val finalAlnResult = mapAligner.computeMapAlignments(finalMapSet.childMaps, alnParams)

    // --- Normalize the processed maps ---
    if (normalizationMethod.isDefined && finalMapSet.childMaps.length > 1) {

      // Update the normalized intensities
      logger.info("normalizing maps...")
      MapSetNormalizer(normalizationMethod.get).normalizeFeaturesIntensity(finalMapSet)

      // Re-build master map features using best child
      logger.info("re-build master map features using best child...")
      finalMapSet.rebuildMasterFeaturesUsingBestChild()
    }

    // --- Store the processed maps features ---

    // Instantiate a processed map storer
    val processedMapStorer = ProcessedMapStorer(lcmsDbCtx)

    DoJDBCWork.withEzDBC(lcmsDbCtx) { ezDBC =>
      for (processedMap <- finalMapSet.childMaps) {
        // Store the map
        logger.info("storing the processed map...")
        processedMapStorer.storeProcessedMap(processedMap)
      }
    }

    // --- Store the master map ---
    logger.info("saving the master map...")
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
    lcMsRuns: Seq[LcMsRun],
    mzDbFileByLcMsRunId: LongMap[File],
    mapSetId: Long
  ): (MapSet, AlignmentResult) = {

    var mapIdx = 0;
    val mapsCount = lcMsRuns.length

    // --- Extract raw maps and convert them to processed maps ---
    val processedMaps = new Array[ProcessedMap](mapsCount)
    val lcmsRunByProcMapId = new LongMap[LcMsRun](mapsCount)
    val mzDbFileByProcMapId = new LongMap[File](mapsCount)
    val scanSeqByRunId = new LongMap[LcMsScanSequence](mapsCount)

    for (lcmsRun <- lcMsRuns) {
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
      lcMsRuns.map(_.scanSequence.get),
      masterFtFilter,
      ftMappingParams,
      clusteringParams
    )
    //mapSet.toTsvFile("D:/proline/data/test/quanti/debug/master_map_"+ (-mapSet.masterMap.id) +".tsv")

    // --- Re-build master map if peptide sequences have been provided ---
    for (pepMap <- peptideByRunIdAndScanNumber) {

      // Map peptides by scan id and scan sequence by run id
      val peptideByScanId = new LongMap[Peptide]()

      for (lcmsRun <- lcMsRuns) {
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
      //mapSet.toTsvFile("D:/proline/data/test/quanti/debug/master_map_with_peps_"+ (-mapSet.masterMap.id) +".tsv")
    }

    // Re-build child maps in order to be sure they contain master feature children (including clusters)
    tmpMapSet = tmpMapSet.rebuildChildMaps()

    // --- Extract LC-MS missing features in all raw files ---
    val x2RawMaps = new ArrayBuffer[RawMap](lcMsRuns.length)
    val x2ProcessedMaps = new ArrayBuffer[ProcessedMap](lcMsRuns.length)
    val procMapTmpIdByRawMapId =  new LongMap[Long](lcMsRuns.length)
    
    for (processedMap <- tmpMapSet.childMaps) {
      val rawMap = processedMap.getRawMaps().head.get
      val mzDbFile = mzDbFileByProcMapId(processedMap.id)
      val lcmsRun = lcmsRunByProcMapId(processedMap.id)

      // Extract missing features
      val peakelByMzDbPeakelId = new LongMap[Peakel]()
      val newLcmsFeatures = this._extractMissingFeatures(mzDbFile, lcmsRun, processedMap, tmpMapSet, peakelByMzDbPeakelId)

      // Create a new raw map by including the extracted missing features
      val x2RawMap = rawMap.copy(
        features = rawMap.features ++ newLcmsFeatures
      )

      // Append missing peakels
      x2RawMap.peakels = Some(rawMap.peakels.get ++ peakelByMzDbPeakelId.values)

      // Store the raw map
      logger.info("storing the raw map...")
      rawMapStorer.storeRawMap(x2RawMap, storePeakels = true)
      
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
    
    val procMapIdByTmpId = new LongMap[Long](lcMsRuns.length)
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
    for(alnSet <-alnResult.mapAlnSets){
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

  private def _detectMapSetFromPeakels(
    lcMsRuns: Seq[LcMsRun],
    mzDbFileByLcMsRunId: LongMap[File],
    mapSetId: Long
  ): (MapSet, AlignmentResult) = {

    val intensityComputationMethod = ClusterIntensityComputation.withName(
      clusteringParams.intensityComputation.toUpperCase()
    )
    val timeComputationMethod = ClusterTimeComputation.withName(
      clusteringParams.timeComputation.toUpperCase()
    )

    val runsCount = lcMsRuns.length
    val peakelFileByRun = new HashMap[LcMsRun, File]()
    val processedMapByRun = new HashMap[LcMsRun, ProcessedMap]()
    val lcmsRunByProcMapId = new LongMap[LcMsRun](runsCount)
    val processedMaps = new ArrayBuffer[ProcessedMap](runsCount)
    val featureTuples = new ArrayBuffer[(Feature, Peptide, LcMsRun)]()

    val tempDir = new File(System.getProperty("java.io.tmpdir"))
    val peakelByMzDbPeakelIdByRunId = new LongMap[LongMap[Peakel]](runsCount) // contains only assigned peakels  
    val mzDbPeakelIdsByPeptideAndChargeByRunId = new LongMap[HashMap[(Peptide, Int), ArrayBuffer[Int]]](runsCount)
    val conflictingPeptidesMap = new HashMap[(Peptide, Int), ArrayBuffer[Peptide]]()
    val metricsByRunId = new LongMap[Metric](runsCount)
    val rTreeByRunId = new LongMap[RTree[java.lang.Long,geometry.Point]](runsCount)
    
    var mapNumber = 0
    for (lcMsRun <- lcMsRuns) {
      mapNumber += 1

      val rawMapId = RawMap.generateNewId()
      // Open mzDB file
      val mzDbFile = mzDbFileByLcMsRunId(lcMsRun.id)
      val mzDb = new MzDbReader(mzDbFile, true)

      // Create a buffer to store built features
      val rawMapFeatures = new ArrayBuffer[Feature]()

      // Create a mapping avoiding the creation of duplicated peakels
      val peakelByMzDbPeakelId = new LongMap[Peakel]()
      peakelByMzDbPeakelIdByRunId(lcMsRun.id) = peakelByMzDbPeakelId
      
      val mzDbPeakelIdsByPeptideAndCharge = new HashMap[(Peptide, Int), ArrayBuffer[Int]]()
      mzDbPeakelIdsByPeptideAndChargeByRunId += (lcMsRun.id -> mzDbPeakelIdsByPeptideAndCharge)
      
      val runMetrics = new Metric("LCMSRun_"+lcMsRun.id)
      metricsByRunId(lcMsRun.id) = runMetrics
      
      var peakelFileConnection: SQLiteConnection = null

      try {

        // Search for existing Peakel file 
        val (detectedPeakels, ms1ScanHeaderById, ms2ScanHeadersByCycle) = {
          val existingPeakelFiles = tempDir.listFiles.filter(_.getName.startsWith(s"${lcMsRun.getRawFileName}-"))
          
          if( quantConfig.useLastPeakelDetection == false || existingPeakelFiles.isEmpty ) {
            
            // Remove TMP files if they exist
            existingPeakelFiles.foreach(_.delete())
            
            this.logger.info("start peakel detection from " + mzDbFile.getName())

            // Create TMP file to store orphan peakels which will be deleted after JVM exit
            val peakelFile = File.createTempFile(s"${lcMsRun.getRawFileName}-", ".sqlite")
            //peakelFile.deleteOnExit()
            this.logger.debug("creating tmp file at: " + peakelFile)

            // Create a mapping between the TMP file and the LC-MS run
            peakelFileByRun += lcMsRun -> peakelFile

            // Open TMP SQLite file
            peakelFileConnection = _initPeakelStore(peakelFile)

            this.logger.debug(s"detecting peakels in raw MS survey for run id=${lcMsRun.id}...")

            // Instantiate the feature detector
            val mzdbFtDetector = new MzDbFeatureDetector(
              mzDb,
              FeatureDetectorConfig(
                msLevel = 1,
                mzTolPPM = mozTolPPM,
                minNbOverlappingIPs = 5
              )
            )

            // Launch the peakel detection
            // TODO: create a queue instead of arraybuffer to store the result inside the detector algo ???
            val peakels = mzdbFtDetector.detectPeakels( mzDb.getLcMsRunSliceIterator() )

            // Store orphan peakels in SQLite file
            this._storePeakels(peakelFileConnection, peakels)

            (peakels, mzdbFtDetector.ms1SpectrumHeaderById, mzdbFtDetector.ms2SpectrumHeadersByCycle)
          } else {
            this.logger.info("Loading peakels from existing file " + existingPeakelFiles(0) + " for mzDB file " + mzDbFile.getName())
            
            // Peakel file already exists : reuse it ! 
            // Create a mapping between the TMP file and the LC-MS run
            peakelFileByRun += lcMsRun -> existingPeakelFiles(0)

            // Open TMP SQLite file
            peakelFileConnection = new SQLiteConnection(existingPeakelFiles(0))
            peakelFileConnection.openReadonly()
            //peakelFileConnection.open(false)
            val peakels = _loadPeakels(peakelFileConnection)

            (peakels, mzDb.getMs1SpectrumHeaders().toLongMapWith(sh => sh.getId -> sh), mzDb.getMs2SpectrumHeaders().groupByLong(_.getCycle.toInt))
          }
        }

        // Link peakels to peptides
        this.logger.debug("linking peakels to peptides...")
        val psmByScanNumber = peptideMatchByRunIdAndScanNumber.map(_(lcMsRun.id)).getOrElse(LongMap.empty[ArrayBuffer[PeptideMatch]])

        // Map detected peakels
        val peakelMatches = _buildPeakelMatches(
          detectedPeakels,
          lcMsRun.scanSequence.get,
          ms2ScanHeadersByCycle,
          psmByScanNumber
        )

        // Retrieve the list of peakels unmapped with peptides
        //val orphanPeakels = detectedPeakels.filter(pkl => psmTupleByPeakel.contains(pkl) == false)
        val peptides = psmByScanNumber.flatMap(_._2).map(_.peptide).toBuffer.distinct
        val assignedPeptideSet = peakelMatches.map(_.peptide).toSet
        val orphanPeptides = peptides.filter(peptide => !assignedPeptideSet.contains(peptide)).distinct

        runMetrics.setCounter("orphan peptides", orphanPeptides.size)
        runMetrics.setCounter("distinct peptides", peptides.size)
        
        this.logger.debug("building peakel in-memory R*Tree to provide quick searches...")
        
        val entriesView = detectedPeakels.view.map { peakel =>
          val peakelId = new java.lang.Long(peakel.id)
          val geom = geometry.Geometries.point(peakel.getMz,peakel.getElutionTime())
          Entries.entry(peakelId, geom)
        }
        val entriesIterable = collection.JavaConversions.asJavaIterable(entriesView)
        
        val rTree = RTree
          .star()
          .maxChildren(4)
          .create[java.lang.Long,geometry.Point]()
          .add(entriesIterable)
          
        this.logger.debug(s"created R*Tree contains ${rTree.size} peakel indices")
        
        rTreeByRunId += lcMsRun.id -> rTree
        
        // Iterate over peakels mapped with peptides to build features
        this.logger.debug("building features from peakels...")

        val peakelMatchesByPeakelId = peakelMatches.groupByLong(_.peakel.id)
        for ( (peakelId,peakelMatches) <- peakelMatchesByPeakelId) {
          val peakel = peakelMatches.head.peakel 
          val peakelMatchesByCharge = peakelMatches.groupBy(_.charge)

          for ((charge, sameChargePeakelMatches) <- peakelMatchesByCharge) {

            // TODO: possible optimization => perform the isotopes R*Tree search in-memory (using detectedPeakels)
            // Possible implementation to use: https://github.com/davidmoten/rtree
            val mzDbFt = _createMzDbFeature(
              peakelFileConnection,
              rTree,
              peakel,
              charge,
              false,
              sameChargePeakelMatches.map(_.spectrumHeader.getId).distinct.toArray
            )
            if (mzDbFt.getPeakelsCount == 1) runMetrics.incr("psm monoisotopic features")
            
            // Convert mzDb feature into LC-MS one
            val lcmsFt = this._mzDbFeatureToLcMsFeature(mzDbFt, rawMapId, lcMsRun.scanSequence.get, peakelByMzDbPeakelId)
            rawMapFeatures += lcmsFt

            val peptides = sameChargePeakelMatches.map(_.peptide).distinct
            
            for (peptide <- peptides) {
              featureTuples += Tuple3(lcmsFt, peptide, lcMsRun)
              mzDbPeakelIdsByPeptideAndCharge.getOrElseUpdate((peptide, charge), ArrayBuffer[Int]()) += peakel.id
              if (peptides.length > 1) {
                runMetrics.incr("conflicting peakels (associated with more than one peptide)")                
                conflictingPeptidesMap.getOrElseUpdate((peptide, charge), ArrayBuffer[Peptide]()) ++=  peptides
              }
            }
        
            //_testIsotopicPatternPrediction(mzDb, peakelFileConnection, rTree, mzDbFt, runMetrics)
            
          }
        } // ends for peakelMatchesByPeakelId

        runMetrics.setCounter("peptides sharing peakels", peakelByMzDbPeakelId.filter(_._2.featuresCount > 1).size)
      } finally {
        // Release opened connections
        mzDb.close()
        peakelFileConnection.dispose()
      }
      
      // Create a new raw Map and the corresponding processed map
      val processedMap = new RawMap(
        id = rawMapId,
        name = lcMsRun.rawFile.name,
        isProcessed = false,
        creationTimestamp = new java.util.Date,
        features = rawMapFeatures.toArray,
        peakels = Some(peakelByMzDbPeakelId.values.toArray),
        runId = lcMsRun.id,
        peakPickingSoftware = pps
      ).toProcessedMap(mapNumber, mapSetId)

      logger.info("processed map peakels count = "+peakelByMzDbPeakelId.size)
      logger.info("processed map features count = "+processedMap.features.size)
      
      // Set processed map id of the feature (
      for (procFt <- processedMap.features) {
        procFt.relations.processedMapId = processedMap.id
      }

      lcmsRunByProcMapId += processedMap.id -> lcMsRun
      processedMapByRun += lcMsRun -> processedMap
      processedMaps += processedMap
    
    } //end lcmsRun iteration loop

    this.logger.info("creating new map set...")

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

    // Define some data structure
    val mftBuilderByPeptideAndCharge = new HashMap[(Peptide, Int), MasterFeatureBuilder]()
    val putativeFtsByLcMsRun = new HashMap[LcMsRun, ArrayBuffer[PutativeFeature]]()
    val putativeFtsByPeptideAndRunId = new HashMap[(Peptide, Long), ArrayBuffer[PutativeFeature]]()
    val peptideByPutativeFtId = new LongMap[Peptide]()
    val multiMatchedPeakelIdsByPutativeFtId = new LongMap[ArrayBuffer[Int]]()
    
    this.logger.info("building the master map...")

    // Group found features by peptide and charge to build master features
    for (((peptide, charge), masterFeatureTuples) <- featureTuples.groupBy { ft => (ft._2, ft._1.charge) }) {

      val masterFtChildren = new ArrayBuffer[Feature](masterFeatureTuples.length)
      val featureTuplesByLcMsRun = masterFeatureTuples.groupBy(_._3)

      // Iterate over each LC-MS run
      val runsWithMissingFt = new ArrayBuffer[LcMsRun]()
      for (lcMsRun <- lcMsRuns) {

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
            metricsByRunId(lcMsRun.id).storeValue("cluster feature duration", clusterFeature.duration)
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

        val currentProcMapId = processedMapByRun(lcMsRun).id
        
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
        
        val multiMatchedPeakelIds = ArrayBuffer.empty[Int]
        val conflictingPeptides = conflictingPeptidesMap.getOrElse((peptide, charge), null)
        val mzDbPeakelIdsByPeptideAndCharge = mzDbPeakelIdsByPeptideAndChargeByRunId.getOrNull(lcMsRun.id)
        
        if (conflictingPeptides != null && mzDbPeakelIdsByPeptideAndCharge != null) {
          for (conflictingPeptide <- conflictingPeptides) {
          	val mzDbPeakelIds = mzDbPeakelIdsByPeptideAndCharge.getOrElse((conflictingPeptide, charge), null)
          	if (mzDbPeakelIds != null) {
          	  multiMatchedPeakelIds ++= mzDbPeakelIds
          	}
          }
        }
          
        putativeFtsByLcMsRun.getOrElseUpdate(lcMsRun, new ArrayBuffer[PutativeFeature]) += pf
        putativeFtsByPeptideAndRunId.getOrElseUpdate((peptide, lcMsRun.id), new ArrayBuffer[PutativeFeature]) += pf
        peptideByPutativeFtId(pf.id) = peptide
        multiMatchedPeakelIdsByPutativeFtId(pf.id) = multiMatchedPeakelIds
      }
      
    } // end (peptide, charge) loop

    val x2RawMaps = new ArrayBuffer[RawMap](processedMaps.length)
    val x2RawMapByRunId = new LongMap[RawMap](processedMaps.length)

    //
    //    Then search for missing features
    //
    val procMapTmpIdByRawMapId =  new LongMap[Long](lcMsRuns.length)
    val procMapIdByTmpId = new LongMap[Long](lcMsRuns.length)
    
    for (lcMsRun <- lcMsRuns) {

      val runMetrics = metricsByRunId(lcMsRun.id) 
      val mzDbFile = mzDbFileByLcMsRunId(lcMsRun.id)
      val mzDb = new MzDbReader(mzDbFile, true)
      
      // Retrieve processed an raw maps
      val processedMap = processedMapByRun(lcMsRun)
      val rawMap = processedMap.getRawMaps().head.get

      val x2RawMap = if (putativeFtsByLcMsRun.contains(lcMsRun) == false) rawMap
      else {

        val putativeFts = putativeFtsByLcMsRun(lcMsRun)
        val newLcmsFeatures = new ArrayBuffer[Feature]()

        this.logger.info(s"searching for ${putativeFts.length} missing features in run id=${lcMsRun.id}...")
        
        // TODO: possible optimization => perform the R*Tree search in-memory
        
        // Re-open peakel SQLite file
        val peakelFile = peakelFileByRun(lcMsRun) // TODO: map by run id ?
        val peakelFileConn = new SQLiteConnection(peakelFile)
        peakelFileConn.openReadonly()
        //peakelFileConn.open(false) // allowCreate = false
        
        // Retrieve corresponding R*Tree
        val rTree = rTreeByRunId(lcMsRun.id)

        // Retrieve the map avoiding the creation of duplicated peakels
        val peakelByMzDbPeakelId = peakelByMzDbPeakelIdByRunId(lcMsRun.id)
        val assignedPeakelIds = new HashSet[Long] () ++= peakelByMzDbPeakelId.keySet
        
        try {
          for (putativeFt <- putativeFts) {

            val charge = putativeFt.charge
            val peptide = peptideByPutativeFtId(putativeFt.id)
            val mftBuilder = mftBuilderByPeptideAndCharge((peptide, putativeFt.charge))
            val bestFt = mftBuilder.bestFeature
            
            val peakelOpt = _findPeakel(
              mzDb,
              peakelFileConn,
              rTree,
              putativeFt.mz,
              charge,
              minTime = putativeFt.elutionTime - ftMappingParams.timeTol,
              avgTime = putativeFt.elutionTime,
              maxTime = putativeFt.elutionTime + ftMappingParams.timeTol,
              expectedDuration = bestFt.duration,
              assignedPeakelById = assignedPeakelIds,
              multimatchedPeakelIds = multiMatchedPeakelIdsByPutativeFtId(putativeFt.id),
              runMetrics
            )

            if (peakelOpt.isDefined && peakelOpt.get._2) {

              val( peakel, isReliable) = peakelOpt.get
              val mzDbFt = _createMzDbFeature(
                peakelFileConn,
                rTree,
                peakel,
                charge,
                true,
                Array.empty[Long]
              )
               
              if (mzDbFt.getPeakelsCount == 1) runMetrics.incr("missing monoisotopic features")
              runMetrics.incr("missing feature found")

              // update putative feature of conflicting peptides in this run to allow peakel re-assignment
              val conflictingPeptides = conflictingPeptidesMap.getOrElse( (peptide, charge), null )
              if (conflictingPeptides != null) {
                for (conflictingPeptide <- conflictingPeptides) {
                  for (pft <- putativeFtsByPeptideAndRunId((peptide, lcMsRun.id))) {
                    runMetrics.incr("putative peakels list updated")
                    multiMatchedPeakelIdsByPutativeFtId(pft.id) += peakel.id
                  }
                }
              }

              val newLcmsFeature = this._mzDbFeatureToLcMsFeature(
                mzDbFt,
                rawMap.id,
                lcMsRun.scanSequence.get,
                peakelByMzDbPeakelId
              )

              if (isReliable) {
                for ( peakel <- mzDbFt.getPeakels() ) {
                  assignedPeakelIds += peakel.id
                }
              }
              
              val newFtProps = newLcmsFeature.properties.get
              
              // Set predicted time property
              newFtProps.setPredictedElutionTime(Some(putativeFt.elutionTime))
              
              // Set isReliable property
              newFtProps.setIsReliable(Some(isReliable))
              
              // Deselect the feature if it not reliable
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
              
            }
          }

          // Create a new raw map by including the retrieved missing features
          val x2RawMap = rawMap.copy(
            features = rawMap.features ++ newLcmsFeatures,
            peakels = Some(peakelByMzDbPeakelId.values.toArray)
          )

          logger.info("raw map peakels count = "+x2RawMap.peakels.get.size)
          logger.info(runMetrics.toString)

          // Return x2RawMap
          x2RawMap
          
        } finally {
          // Release resources
          peakelFileConn.dispose()
        }
      }

      // Store the raw map
      logger.info("storing the raw map...")
      rawMapStorer.storeRawMap(x2RawMap, storePeakels = true)
        
      procMapTmpIdByRawMapId.put(x2RawMap.id, processedMap.id)

      // Detach peakels from the raw map (this should decrease memory footprint)
      x2RawMap.peakels = None

      // Detach peakels from features (this should decrease memory footprint)
      for (ft <- x2RawMap.features; peakelItem <- ft.relations.peakelItems) {
        peakelItem.peakelReference = PeakelIdentifier(peakelItem.peakelReference.id)
      }

      // Append raw map to the array buffer
      x2RawMapByRunId += x2RawMap.runId -> x2RawMap
      x2RawMaps += x2RawMap
    }

    // Delete created TMP files (they should be deleted on exit if program fails)
    //    for (tmpFile <- peakelFileByRun.values) {
    //      tmpFile.delete
    //    }

    // --- Build a temporary master map ---
    val alnRefMap = tmpMapSet.getAlnReferenceMap.get
    val curTime = new java.util.Date()

    val masterFeatures = mftBuilderByPeptideAndCharge.values.map { mftBuilder =>
      val mft = mftBuilder.toMasterFeature()
      require(mft.children.length <= lcMsRuns.length, "master feature contains more child features than maps")
      
      // Deselect the master feature if one of its features is not reliable
      val nonReliableFtsCount = mft.children.count(_.properties.flatMap(_.getIsReliable()).getOrElse(true) == false)
      if (nonReliableFtsCount > 0) {
        mft.selectionLevel = 0
      }
      
      mft
    } toArray

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

    // --- Persist the corresponding map set ---
    val x2MapSet = CreateMapSet(lcmsDbCtx, mapSetName, tmpMapSet.childMaps)
    
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

    // update the map ids in the alnResult
    val finalMapAlnSets = new ArrayBuffer[MapAlignmentSet](alnResult.mapAlnSets.length)
    for (alnSet <- alnResult.mapAlnSets) {
      
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
      procMapIdByTmpId.get(alnResult.alnRefMapId).get,
      finalMapAlnSets.toArray
    )
      
    (x2MapSet, finalAlnResult)
  }
  
  case class PeakelMatch(
    peakel: MzDbPeakel,
    peptide: Peptide,
    spectrumHeader: SpectrumHeader,
    charge: Int
  )
  
  private def _buildPeakelMatches(
    detectedPeakels: Array[MzDbPeakel],
    scanSequence: LcMsScanSequence,
    ms2SpectrumHeadersByCycle: LongMap[Array[SpectrumHeader]],
    pepMatchesBySpectrumNumber: LongMap[ArrayBuffer[PeptideMatch]]
  ): ArrayBuffer[PeakelMatch] = {
    
    // Put spectra headers into a matrix structure to optimzie the lookup operations
    val lastCycle = scanSequence.scans.last.cycle
    val ms2SpectrumHeaderMatrix = new Array[Array[SpectrumHeader]](lastCycle + 1)
    for ( (cycle,ms2SpectrumHeaders) <- ms2SpectrumHeadersByCycle) {
      ms2SpectrumHeaderMatrix(cycle.toInt) = ms2SpectrumHeaders
    }
    
    // Put PSMs into a matrix structure to optimize the lookup operations
    val lastSpecNumber = scanSequence.scans.last.initialId
    val pepMatchesMatrix = new Array[ArrayBuffer[PeptideMatch]](lastSpecNumber + 1)
    for ( (specNum,pepMatches) <- pepMatchesBySpectrumNumber) {
      pepMatchesMatrix(specNum.toInt) = pepMatches
    }
    
    val peakelsCount = detectedPeakels.length
    val peakelMatches = new ArrayBuffer[PeakelMatch](lastSpecNumber * 2)
    
    var detectedPeakelIdx = 0
    while (detectedPeakelIdx < peakelsCount) {
      val detectedPeakel = detectedPeakels(detectedPeakelIdx)

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
                    peakelMatches += PeakelMatch(detectedPeakel,psm.peptide, ms2Sh, psm.charge)
                  }
                  psmIdx += 1
                }
              }
            }
            
            ms2ShIdx += 1
          } // while (ms2ShIdx < ms2SpecHeadersCount)
        } // while (curCycle <= maxCycle)
        
        curCycle += 1
      }
            
      detectedPeakelIdx += 1
    }
    
    peakelMatches
  }
  
  private def _createMzDbFeature(
    peakelFileConnection: SQLiteConnection,
    rTree: RTree[java.lang.Long,geometry.Point],
    peakel: MzDbPeakel,
    charge: Int,
    isPredicted: Boolean,
    ms2SpectrumIds: Array[Long]
  ): MzDbFeature = {

    val isotopes = this._findPeakelIsotopes(peakelFileConnection, rTree, peakel, charge)
    
    //println(s"found ${isotopes.length} isotopes")

    MzDbFeature(
      id = MzDbFeature.generateNewId,
      mz = peakel.getMz,
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
    // Note that this DDL will be later used directly in the mzDB
    val ddlQuery = """
    CREATE TABLE peakel (
      id INTEGER NOT NULL PRIMARY KEY,
      mz REAL NOT NULL,
      elution_time REAL NOT NULL,
      apex_intensity REAL NOT NULL,
      area REAL NOT NULL,
      duration REAL NOT NULL,
      left_hwhm_mean REAL,
      left_hwhm_cv REAL,
      right_hwhm_mean REAL,
      right_hwhm_cv REAL,
      is_overlapping TEXT NOT NULL,
      features_count INTEGER NOT NULL,
      peaks_count INTEGER NOT NULL,
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
    /*
      FOREIGN KEY (first_spectrum_id) REFERENCES spectrum (id),
      FOREIGN KEY (last_spectrum_id) REFERENCES spectrum (id),
      FOREIGN KEY (apex_spectrum_id) REFERENCES spectrum (id),
      FOREIGN KEY (map_id) REFERENCES map (id)
   */

    connection.exec(ddlQuery)

    connection
  }

  private def _storePeakels(sqliteConn: SQLiteConnection, peakels: Array[MzDbPeakel]) {

    // BEGIN TRANSACTION
    sqliteConn.exec("BEGIN TRANSACTION;");

    // Prepare the insertion in the peakel table
    val peakelStmt = sqliteConn.prepare(
      s"INSERT INTO peakel VALUES (${Array.fill(20)("?").mkString(",")})"
    )
    // Prepare the insertion in the peakel_rtree table
    val peakelIndexStmt = sqliteConn.prepare(
      s"INSERT INTO peakel_rtree VALUES (${Array.fill(9)("?").mkString(",")})"
    )

    try {
      for (peakel <- peakels) {

        val scanInitialIds = peakel.getSpectrumIds()
        val peakelMessage = peakel.toPeakelDataMatrix()
        val peakelMessageAsBytes = PeakelDataMatrix.pack(peakelMessage)

        val peakelMz = peakel.getMz
        val peakelTime = peakel.getApexElutionTime

        var fieldNumber = 1
        peakelStmt.bind(fieldNumber, peakel.id); fieldNumber += 1
        peakelStmt.bind(fieldNumber, peakelMz); fieldNumber += 1
        peakelStmt.bind(fieldNumber, peakelTime); fieldNumber += 1
        peakelStmt.bind(fieldNumber, peakel.getApexIntensity); fieldNumber += 1
        peakelStmt.bind(fieldNumber, peakel.area); fieldNumber += 1
        peakelStmt.bind(fieldNumber, peakel.calcDuration); fieldNumber += 1
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
    sqliteConn.exec("COMMIT TRANSACTION;");
  }
  
  // TODO: move to MzDbReader when peakels are stored in the PeakelDb file
  private def _findPeakelsInRange(
    sqliteConn: SQLiteConnection,
    rTree: RTree[java.lang.Long,geometry.Point],
    minMz: Double,
    maxMz: Double,
    minTime: Float,
    maxTime: Float
  ): Array[MzDbPeakel] = {
    
    // Retrieve peakel ids using the R*Tree
    val peakelIdIter = rTree.search(
      geometry.Geometries.rectangle(
        minMz, minTime, maxMz, maxTime
      )
    ).toBlocking().toIterable().iterator()
    
    val peakelIds = new ArrayBuffer[java.lang.Long]()
    while( peakelIdIter.hasNext() ) {
      peakelIds += peakelIdIter.next().value()
    }
    //println( peakelIds.toList )

    val peakelPkSqlQuery = "SELECT id, peaks, left_hwhm_mean, left_hwhm_cv, " +
    s"right_hwhm_mean, right_hwhm_cv FROM peakel WHERE id IN (${peakelIds.mkString(",")});"
    
    val peakelStmt = sqliteConn.prepare(peakelPkSqlQuery, false)
    //peakelStmt.bind(1, peakelIds.mkString(",") )
    
    val peakels = new ArrayBuffer[MzDbPeakel](peakelIds.length)

    try {

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
    
    assert( peakelIds.length == peakels.length, "invalid number of retrieved peakels from peakelDB file" )

    peakels.toArray
  }
  
  // TODO: remove me (an in-memory R*Tree is now used)
  private def _findPeakelsInRangeV1(
    sqliteConn: SQLiteConnection,
    rTree: RTree[java.lang.Long,geometry.Point],
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
  }

  // TODO: move to MzDbReader when peakels are stored in the MzDbFile
  private def _loadPeakels(
    sqliteConn: SQLiteConnection
  ): Array[MzDbPeakel] = {

    val peakelSqlQuery = "SELECT id, peaks, left_hwhm_mean, left_hwhm_cv, " +
      "right_hwhm_mean, right_hwhm_cv FROM peakel;"

    val peakelStmt = sqliteConn.prepare(peakelSqlQuery, false)
    val peakels = new ArrayBuffer[MzDbPeakel]()

    try {

      while (peakelStmt.step()) {
        val peakelId = peakelStmt.columnInt(0)
        val peakelMessageAsBytes = peakelStmt.columnBlob(1)

        //val peakelMessage = ProfiMsgPack.deserialize[fr.profi.mzdb.model.PeakelDataMatrix](peakelMessageAsBytes)
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
  }

  private def _testIsotopicPatternPrediction(
    reader: MzDbReader,
    sqliteConn: SQLiteConnection,
    rTree: RTree[java.lang.Long,geometry.Point],
    ft: MzDbFeature,
    runMetrics: Metric
    ) = {
    
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
    
    var bestPattern = mzDbPatternPredictor.getBestExplanation(reader, sqliteConn, ft.getFirstPeakel(), ft.charge, mozTolInDa)
    updateMetrics(bestPattern._2, "mzdb.prediction.firstIsotope")
    
    if (ft.getPeakelsCount() > 1) {
      bestPattern = mzDbPatternPredictor.getBestExplanation(reader, sqliteConn, ft.getPeakel(1), ft.charge, mozTolInDa)
      updateMetrics(bestPattern._2, "mzdb.prediction.secondIsotope")
    }
    if (ft.getPeakelsCount() > 2) {
       bestPattern = mzDbPatternPredictor.getBestExplanation(reader, sqliteConn, ft.getPeakel(2), ft.charge, mozTolInDa)
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
    

    bestPattern = PeakelsPatternPredictor.getBestExplanation(rTree, mozTolPPM, coelutingPeakels, ft.getFirstPeakel(), ft.charge, mozTolInDa)
    updateMetrics(bestPattern._2, "peakels.predictor.firstIsotope")
    
    if (ft.getPeakelsCount() > 1) {
      bestPattern = PeakelsPatternPredictor.getBestExplanation(rTree, mozTolPPM, coelutingPeakels, ft.getPeakel(1), ft.charge, mozTolInDa)
      updateMetrics(bestPattern._2, "peakels.predictor.secondIsotope")
    }
    if (ft.getPeakelsCount() > 2) {
       bestPattern = PeakelsPatternPredictor.getBestExplanation(rTree, mozTolPPM, coelutingPeakels, ft.getPeakel(2), ft.charge, mozTolInDa)
    	 updateMetrics(bestPattern._2, "peakels.predictor.thirdIsotope")
    }
    } catch {
      case t: Throwable => logger.error("IP prediction fail", t)
    }
  }
  
  private def _findPeakel(
    reader: MzDbReader,
    sqliteConn: SQLiteConnection,
    rTree: RTree[java.lang.Long,geometry.Point],
    peakelMz: Double,
    charge: Int,
    minTime: Float,
    avgTime: Float,
    maxTime: Float,
    expectedDuration: Float, 
    assignedPeakelById: scala.collection.Set[Long], 
    multimatchedPeakelIds: ArrayBuffer[Int], 
    metric: Metric
  ): Option[(MzDbPeakel,Boolean)] = {
    
    val mozTolInDa = MsUtils.ppmToDa(peakelMz, ftMappingParams.mozTol)

    val foundPeakels = _findPeakelsInRange(
      sqliteConn,
      rTree,
      peakelMz - mozTolInDa,
      peakelMz + mozTolInDa,
      minTime,
      maxTime
    )

    if (foundPeakels.isEmpty) {
      metric.incr("missing peakel: no peakel found in the peakelDB")
      return None
    }
    
    // Apply some filters to the found peakels
    val multimatchedPeakelIdMap = multimatchedPeakelIds.mapByLong(_.toLong)
    val matchingPeakels = foundPeakels.filter { foundPeakel => (multimatchedPeakelIdMap.contains(foundPeakel.id)|| ! assignedPeakelById.contains(foundPeakel.id))  }

    
    val coelutingPeakels = if (matchingPeakels.isEmpty) null
    else {
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
    
    val filteredPeakels = PeakelsPatternPredictor.assessReliability(rTree, mozTolPPM, coelutingPeakels, matchingPeakels, charge, mozTolInDa)

    // Switch to mzdb based implementation by commenting previous line and uncommenting the next one
    //
    // val filteredPeakels = mzDbPatternPredictor.assessReliability(reader, sqliteConn, matchingPeakels, charge, mozTolInDa)
    
    
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
      
      Some(nearestPeakelInTime)
    }
  }

  private def _findPeakelIsotopes(
    sqliteConn: SQLiteConnection,
    rTree: RTree[java.lang.Long,geometry.Point],
    peakel: MzDbPeakel,
    charge: Int
  ): Array[MzDbPeakel] = {

    val peakelMz = peakel.getMz
    val mozTolInDa = MsUtils.ppmToDa(peakelMz, ftMappingParams.mozTol)
    
    val peakelRt = peakel.getApexElutionTime
    val peakelQuarterDuration = peakel.calcDuration / 4
    val minRt = peakelRt - peakelQuarterDuration
    val maxRt = peakelRt + peakelQuarterDuration
    
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
          rTree,
          ipMoz - mozTolInDa,
          ipMoz + mozTolInDa,
          minRt,
          maxRt
        )
        
        if (foundPeakels.nonEmpty) {
          val isotopePeakel = foundPeakels.minBy(peakel => math.abs(ipMoz - peakel.getMz))
          val expectedIntensity = pattern.mzAbundancePairs(isotopeIdx)._2 * intensityScalingFactor
          
          // Gentle constraint on the observed intensity: no more than 4 times the expected intensity
          if (isotopePeakel.getApexIntensity() < 4 * expectedIntensity) {
            isotopes += isotopePeakel
          } else {
            // TODO: compute statistics of the observed ratios
            logger.trace(s"Isotope intensity is too high: is ${isotopePeakel.getApexIntensity()} but expected $expectedIntensity")
            break
          }
        } else {
          break
        }
      }
    }

    isotopes.toArray
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
    val peakelByMzDbPeakelId = new LongMap[Peakel]()
    val lcmsFeaturesWithoutClusters = mzDbFts.map(mzDbFt =>
      this._mzDbFeatureToLcMsFeature(mzDbFt, rawMapId, lcmsRun.scanSequence.get, peakelByMzDbPeakelId))

    //val peakels = lcmsFeaturesWithoutClusters.flatMap( _.relations.peakelItems.map(_.peakelReference.asInstanceOf[Peakel] ) )

    val rawMap = tmpRawMap.copy(
      features = lcmsFeaturesWithoutClusters,
      peakels = Some(peakelByMzDbPeakelId.values.toArray)
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

      this.logger.info("retrieving scan headers...")
      val scanHeaders = mzDb.getSpectrumHeaders()
      val ms2ScanHeaders = scanHeaders.filter(_.getMsLevel() == 2)
      val pfs = new ArrayBuffer[PutativeFeature](ms2ScanHeaders.length)

      this.logger.debug("building putative features list from MS2 scan events...")

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
    peakelByMzDbPeakelId: LongMap[Peakel]
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
      this.logger.info("building putative features list using master features...")

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
      this.logger.info("extracting " + missingFtIdByMftId.size + " missing Features from " + mzDbFile.getName)
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

      // FIXME: why do we extract features with 0 duration ???

      // Convert the extracted feature into a LC-MS feature
      val newLcmsFt = this._mzDbFeatureToLcMsFeature(mzDbFt, rawMapId, lcmsRun.scanSequence.get, peakelByMzDbPeakelId)

      // TODO: decide if we set or not this value (it may help for distinction with other features)
      newLcmsFt.correctedElutionTime = Some(mftWithMissingChild.elutionTime)

      // Update the processed map id of the new feature
      newLcmsFt.relations.processedMapId = processedMap.id

      // Add missing child feature to the master feature
      mftWithMissingChild.children ++= Array(newLcmsFt)

      // Add predicted time property
      val pf = pfById(mzDbFt.id)
      val predictedTime = pf.elutionTime
      newLcmsFt.properties.get.setPredictedElutionTime(Some(predictedTime))

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
    this.logger.info("re-building master map using peptide identities...")

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

  private def _mzDbFeatureToLcMsFeature(
    mzDbFt: MzDbFeature,
    rawMapId: Long,
    scanSeq: LcMsScanSequence,
    peakelByMzDbPeakelId: LongMap[Peakel]
  ): LcMsFeature = {

    val ftId = LcMsFeature.generateNewId

    // Retrieve some vars
    val lcmsScanIdByInitialId = scanSeq.scanIdByInitialId
    val scanInitialIds = mzDbFt.getSpectrumIds
    
    // WARNING: we assume here that these methods returns the intial ID but it may change in the future
    val apexScanInitialId = mzDbFt.getApexSpectrumId.toInt
    val (firstScanInitialId, lastScanInitialId) = (scanInitialIds.head.toInt, scanInitialIds.last.toInt)
    val firstLcMsScanId = lcmsScanIdByInitialId(firstScanInitialId)
    val lastLcMsScanId = lcmsScanIdByInitialId(lastScanInitialId)
    val apexLcMsScanId = lcmsScanIdByInitialId(apexScanInitialId)
    val ms2EventIds = mzDbFt.getMs2SpectrumIds.map(sid => lcmsScanIdByInitialId(sid.toInt))
    // END OF WARNING

    val mzDbFtBasePeakel = mzDbFt.getBasePeakel
    val indexedPeakels = mzDbFt.indexedPeakels
    
    var basePeakelIndex = 0
    val _theoBasePeakelIndex = 0 + {if (mzDbFt.mz*mzDbFt.charge > 2000) 1 else 0} + {if (mzDbFt.mz*mzDbFt.charge > 3500) 1 else 0 }
    
    for ( (peakel,idx) <- indexedPeakels ) {
      if (idx <= _theoBasePeakelIndex) {
        basePeakelIndex = idx
      }
    }

    val lcmsFtPeakelItems = indexedPeakels.map {
      case (mzDbPeakel, peakelIdx) =>

        // Retrieve cached LC-MS peakel if it exists
        val lcmsPeakel = if (peakelByMzDbPeakelId.contains(mzDbPeakel.id)) {
          val existingPeakel = peakelByMzDbPeakelId(mzDbPeakel.id)

          // Increase features count
          existingPeakel.featuresCount = existingPeakel.featuresCount + 1

          existingPeakel
        } // Else build new LC-MS peakel
        else {

          // Create the peakel data matrix
          val peakelDataMatrix = new PeakelDataMatrix(
            // Convert mzDB scan IDs into LCMSdb scan ids (same warning as above)
            spectrumIds = mzDbPeakel.spectrumIds.map( sid => lcmsScanIdByInitialId(sid.toInt) ),
            elutionTimes = mzDbPeakel.elutionTimes,
            mzValues = mzDbPeakel.mzValues,
            intensityValues = mzDbPeakel.intensityValues
          )

          val newPeakel = Peakel(
            id = Peakel.generateNewId,
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

          // Cache new LC-MS peakel
          peakelByMzDbPeakelId(mzDbPeakel.id) = newPeakel

          newPeakel
        }

        FeaturePeakelItem(
          featureReference = FeatureIdentifier(ftId),
          peakelReference = lcmsPeakel,
          isotopeIndex = peakelIdx,
          isBasePeakel = if( peakelIdx == basePeakelIndex ) true else false
        )
    }

    new LcMsFeature(
      id = ftId,
      moz = mzDbFt.mz,
      apexIntensity = mzDbFt.getPeakel(basePeakelIndex).getApexIntensity(),
      intensity = mzDbFt.getPeakel(basePeakelIndex).getApexIntensity(),
      charge = mzDbFt.charge,
      elutionTime = mzDbFt.getElutionTime,
      duration = mzDbFt.calcDuration(),
      qualityScore = Option(mzDbFt.qualityProperties).map(_.qualityScore).getOrElse(0f),
      ms1Count = mzDbFt.getMs1Count,
      ms2Count = mzDbFt.getMs2Count,
      isOverlapping = false,
      isotopicPatterns = None,
      selectionLevel = 2,
      properties = Some(FeatureProperties()),
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