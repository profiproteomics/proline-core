package fr.proline.core.service.lcms.io

import java.io.File
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.collection.mutable.HashSet
import com.almworks.sqlite4java.SQLiteConnection
import com.typesafe.scalalogging.slf4j.Logging
import org.apache.commons.math.stat.descriptive.rank.Percentile
import org.apache.commons.math.stat.descriptive.SummaryStatistics
import fr.profi.chemistry.model.MolecularConstants
import fr.profi.jdbc.easy._
import fr.profi.ms.algo.IsotopePatternInterpolator
import fr.profi.mzdb._
import fr.profi.mzdb.algo.feature.extraction.FeatureExtractorConfig
import fr.profi.mzdb.io.reader.RunSliceDataProvider
import fr.profi.mzdb.model.{ Feature => MzDbFeature, Peak => MzDbPeak, Peakel => MzDbPeakel, PeakelBuilder, ScanHeader }
import fr.profi.mzdb.model.PutativeFeature
import fr.profi.mzdb.utils.ms.MsUtils
import fr.profi.util.ms.massToMoz
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.algo.lcms._
import fr.proline.core.dal.helper.LcmsDbHelper
import fr.proline.core.dal.{ DoJDBCWork, DoJDBCReturningWork }
import fr.proline.core.om.model.lcms.{ Feature => LcMsFeature, IsotopicPattern => LcMsIsotopicPattern }
import fr.proline.core.om.model.lcms._
import fr.proline.core.om.model.msi.{Instrument,Peptide,PeptideMatch}
import fr.proline.core.om.provider.lcms.impl.SQLScanSequenceProvider
import fr.proline.core.om.storer.lcms.MasterMapStorer
import fr.proline.core.om.storer.lcms.ProcessedMapStorer
import fr.proline.core.om.storer.lcms.RawMapStorer
import fr.proline.core.om.storer.lcms.impl.SQLScanSequenceStorer
import fr.proline.core.service.lcms.ILcMsService
import fr.proline.core.service.lcms.AlignMapSet
import fr.proline.core.service.lcms.CreateMapSet
import fr.proline.core.service.lcms.CreateMasterMap


/**
 * @author David Bouyssie
 *
 */
class ExtractMapSet(
  val lcmsDbCtx: DatabaseConnectionContext,
  val quantConfig: ILcMsQuantConfig,
  val peptideByRunIdAndScanNumber: Option[Map[Long,HashMap[Int,Peptide]]] = None, // sequence data may or may not be provided
  val psmByRunIdAndScanNumber: Option[Map[Long, HashMap[Int, PeptideMatch]]] = None
) extends ILcMsService with Logging {
  
  // Do some requirements
  require( quantConfig.extractionParams.mozTolUnit matches "(?i)PPM" )
  
  // Define some vars
  protected val mapSetName = quantConfig.mapSetName
  protected val lcMsRuns = quantConfig.lcMsRuns
  protected val mozTolPPM = quantConfig.extractionParams.mozTol.toFloat
  protected val clusteringParams = quantConfig.clusteringParams
  protected val alnMethodName = quantConfig.alnMethodName
  protected val alnParams = quantConfig.alnParams
  protected val masterFtFilter = quantConfig.ftFilter
  protected val ftMappingParams = quantConfig.ftMappingParams
  protected val normalizationMethod = quantConfig.normalizationMethod
  protected val avgIsotopeMassDiff = MolecularConstants.AVERAGE_PEPTIDE_ISOTOPE_MASS_DIFF
  
  protected val lcmsDbHelper = new LcmsDbHelper( lcmsDbCtx )
  protected val scanSeqProvider = new SQLScanSequenceProvider( lcmsDbCtx )
  
  // Instantiate a raw map storer and a map aligner
  protected val rawMapStorer = RawMapStorer( lcmsDbCtx )
  protected val mapAligner = LcmsMapAligner( methodName = alnMethodName )
  
  // FIXME: generate new id and store the pps
  protected val pps = new PeakPickingSoftware(
    id = 1,
    name = "Proline",
    version = "0.1.1",
    algorithm = "ExtractMapSet"
  )
  
  var extractedMapSet: MapSet = null
  
  def runService(): Boolean = {
    
    val mapCount = lcMsRuns.length
    val nonNullLcMsRunCount = lcMsRuns.count(_ != null)
    this.logger.debug("LC-MS runs count = " + mapCount + " and non-null LC-MS runs count = " + nonNullLcMsRunCount )
    require( mapCount == nonNullLcMsRunCount, "the quantitation config contains null LC-MS runs")
    
    // Check if a transaction is already initiated
    val wasInTransaction = lcmsDbCtx.isInTransaction()
    if( !wasInTransaction ) lcmsDbCtx.beginTransaction()
    
    // --- Extract raw maps and convert them to processed maps ---
    val lcmsRunByProcMapId = new collection.mutable.HashMap[Long,LcMsRun]
    val mzDbFileByProcMapId = new collection.mutable.HashMap[Long,File]
    val processedMaps = new Array[ProcessedMap](mapCount)

    val tmpMapSetId = MapSet.generateNewId()
    var mapIdx = 0
    
    val mzDbFileByLcMsRunId = new HashMap[Long,File]()
    for( lcmsRun <- lcMsRuns ) {
      
      val rawFile = lcmsRun.rawFile
      val rawPropOpt = rawFile.properties
      val mzDbFilePath = rawPropOpt.get.getMzdbFilePath
      val mzDbFile = new File(mzDbFilePath)
      
      if( lcmsRun.scanSequence.isEmpty ) {
        // Retrieve the corresponding LC-MS run
        // Or store it in the database if it doesn't exist
        lcmsRun.scanSequence = Some( this._fetchOrStoreScanSequence(lcmsRun,mzDbFile) )
      }
      
      mzDbFileByLcMsRunId += lcmsRun.id -> mzDbFile
    }
    
    // New way of map set creation (concerted maps extraction)
    val finalMapSet = if( quantConfig.detectPeakels ) {
      // TODO: move in a specific class implem (DetectMapSet)
      this._detectMapSetFromPeakels(lcMsRuns, mzDbFileByLcMsRunId, tmpMapSetId)
    // Old way of map set creation (individual map extraction)
    } else {
      // TODO: move in a specific class implem (ExtractMapSet)
      for( lcmsRun <- lcMsRuns ) {
    
        val mzDbFile = mzDbFileByLcMsRunId(lcmsRun.id)
        
        // Extract LC-MS map from the mzDB file
        val processedMap = this._extractProcessedMap(lcmsRun,mzDbFile, mapIdx + 1, tmpMapSetId)
        
        // Update some mappings
        lcmsRunByProcMapId += processedMap.id -> lcmsRun
        mzDbFileByProcMapId += processedMap.id -> mzDbFile
      
        // Convert to processed map
        //val processedMap = rawMap.toProcessedMap( number = mapIdx + 1, mapSetId = tmpMapSetId )
        
        // Perform the feature clustering
        // TODO: use the clean maps service ???
        val scans = lcmsRun.scanSequence.get.scans
        val clusterizedMap = ClusterizeFeatures( processedMap, scans, clusteringParams )
        //clusterizedMap.toTsvFile("D:/proline/data/test/quanti/debug/clusterized_map_"+ (-clusterizedMap.id) +".tsv")
        
        // Set clusterized map id as the id of the provided map
        clusterizedMap.id = processedMap.id
        
        processedMaps(mapIdx) = clusterizedMap
        
        //processedMapByRawMapId += rawMap.id -> processedMap
        
        mapIdx += 1
      }
      
      // --- Perform the LC-MS maps alignment ---
      // TODO: do we need to remove the clusters for the alignment ???      
      val alnResult = mapAligner.computeMapAlignments( processedMaps.filter(_ != null), alnParams )
      
      // --- Create an in-memory map set ---
      var tmpMapSet = new MapSet(
        id = tmpMapSetId,
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
      for( pepMap <- peptideByRunIdAndScanNumber ) {
        
        // Map peptides by scan id and scan sequence by run id
        val peptideByScanId = Map.newBuilder[Long,Peptide]
        val scanSeqByRunId = new HashMap[Long,LcMsScanSequence]
        
        for( lcmsRun <- lcMsRuns ) {
          val scanSeq = lcmsRun.scanSequence.get
          scanSeqByRunId += lcmsRun.id -> scanSeq
          
          for( lcmsScan <- scanSeq.scans ) {
            for( peptide <- pepMap(lcmsRun.id).get(lcmsScan.initialId) ) {
              peptideByScanId += lcmsScan.id -> peptide
            }
          }
        }
        
        // Instantiate a feature clusterer for each child map
        // TODO: provide this mapping to the master map builder ???
        val ftClustererByMapId = Map() ++ tmpMapSet.childMaps.map { childMap =>
          val scanSeq = scanSeqByRunId(childMap.runId.get)
          childMap.id -> new FeatureClusterer(childMap,scanSeq.scans,clusteringParams)
        }
        
        this._rebuildMasterMapUsingPeptides(tmpMapSet,peptideByScanId.result,ftClustererByMapId)
        //mapSet.toTsvFile("D:/proline/data/test/quanti/debug/master_map_with_peps_"+ (-mapSet.masterMap.id) +".tsv")
      }
      
      // Re-build child maps in order to be sure they contain master feature children (including clusters)
      tmpMapSet = tmpMapSet.rebuildChildMaps()
      
      // --- Extract LC-MS missing features in all raw files ---
      val x2RawMaps = new ArrayBuffer[RawMap](lcMsRuns.length)
      val x2ProcessedMaps = new ArrayBuffer[ProcessedMap](lcMsRuns.length)
      for( processedMap <- tmpMapSet.childMaps ) {
        val rawMap = processedMap.getRawMaps().head.get
        val mzDbFile = mzDbFileByProcMapId(processedMap.id)
        val lcmsRun = lcmsRunByProcMapId(processedMap.id)
        
        // Extract missing features
        val peakelByMzDbPeakelId = new HashMap[Int,Peakel]()
        val newLcmsFeatures = this._extractMissingFeatures(mzDbFile,lcmsRun,processedMap,tmpMapSet,peakelByMzDbPeakelId)      
        
        // Create a new raw map by including the extracted missing features
        val x2RawMap = rawMap.copy(
          features = rawMap.features ++ newLcmsFeatures
        )
        
        // Append missing peakels
        x2RawMap.peakels = Some( rawMap.peakels.get ++ peakelByMzDbPeakelId.values )
        
        // Store the raw map
        logger.info("storing the raw map...")
        rawMapStorer.storeRawMap(x2RawMap, storePeakels = true)
        
        // Detach peakels from the raw map
        x2RawMap.peakels = None
        
        // Detach peakels from features
        for( ft <- x2RawMap.features; peakelItem <- ft.relations.peakelItems ) {
          peakelItem.peakelReference = PeakelIdentifier( peakelItem.peakelReference.id )
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
      val x2MapSet = CreateMapSet( lcmsDbCtx, mapSetName, x2ProcessedMaps )
      
      // Attach the computed master map to the newly created map set
      x2MapSet.masterMap = tmpMapSet.masterMap
      x2MapSet.masterMap.mapSetId = x2MapSet.id
      x2MapSet.masterMap.rawMapReferences = x2RawMaps
      
      x2MapSet
    }
    
    // Update processed map id of each feature
    // FIXME: DBO => why is it necessary now (I mean after the introduction of detectPeakels mode) ???
    for( x2ProcessedMap <- finalMapSet.childMaps; ft <- x2ProcessedMap.features ) {      
      ft.relations.processedMapId = x2ProcessedMap.id
      
      if( ft.isCluster ) {
        for( subFt <- ft.subFeatures ) {
          subFt.relations.processedMapId = x2ProcessedMap.id
        }
      }
    }
    
    // --- Update and store the map alignment using processed maps with persisted ids ---
    AlignMapSet(lcmsDbCtx, finalMapSet, alnMethodName, alnParams)
    
    val finalAlnResult = mapAligner.computeMapAlignments( finalMapSet.childMaps, alnParams )
    
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
    val processedMapStorer = ProcessedMapStorer( lcmsDbCtx )
    
    DoJDBCWork.withEzDBC( lcmsDbCtx, { ezDBC =>
      for( processedMap <- finalMapSet.childMaps ) {
        
        // Store the map
        logger.info("storing the processed map...")
        processedMapStorer.storeProcessedMap( processedMap )
        
        // Update map set alignment reference map
        // Note this now done by the processed map storer
        /*if( processedMap.isAlnReference ) {
          logger.info("Set map set alignement reference map to id=" + processedMap.id)
          ezDBC.execute( "UPDATE map_set SET aln_reference_map_id = "+ processedMap.id +" WHERE id = " + mapSet.id )
        }*/
        
      }
    })
    
    // --- Store the master map ---
    logger.info("saving the master map...")
    val masterMapStorer = MasterMapStorer(lcmsDbCtx)
    masterMapStorer.storeMasterMap(finalMapSet.masterMap)
    
    // --- Create and store the master map, the associated processed maps and their alignments ---
    /*CreateMasterMap(
      lcmsDbCtx = lcmsDbCtx,
      mapSet = x2MapSet,
      alnMethodName = alnMethodName,
      alnParams = alnParams,
      masterFtFilter = masterFtFilter,
      ftMappingParams = ftMappingParams,
      normalizationMethod = normalizationMethod
    )*/
    
    // Commit transaction if it was initiated locally
    if( !wasInTransaction ) lcmsDbCtx.commitTransaction()
    
    logger.info(finalMapSet.masterMap.features.length +" master features have been created")
    
    this.extractedMapSet = finalMapSet
    
    // Check all features are persisted in childMaps
    for( x2ProcessedMap <- finalMapSet.childMaps; ft <- x2ProcessedMap.features ) {      
      require( ft.id > 0, "feature should be persisted" )      
      if( ft.isCluster ) {
        for( subFt <- ft.subFeatures ) {
          require( subFt.id > 0, "sub-feature should be persisted" )
        }
      }
    }
    
    // Check all features are persisted in masterMap
    for( mft <- finalMapSet.masterMap.features ) {      
      require( mft.id > 0, "master feature should be persisted" )      
      for( ft <- mft.children ) {
        require( ft.id > 0, "feature should be persisted" )
      }
    }
    
    true
  }
  
  private def _fetchOrStoreScanSequence( lcmsRun: LcMsRun, mzDbFile: File ): LcMsScanSequence = {
    
    val mzDbFileDir = mzDbFile.getParent()
    val mzDbFileName = mzDbFile.getName()
    // FIXME: it should be retrieved from the mzDB file meta-data
    val rawFileName = mzDbFileName.split("\\.").head
    
    // Check if the scan sequence already exists
    //val scanSeqId = lcmsDbHelper.getScanSequenceIdForRawFileName(rawFileName)
    val scanSeqOpt = scanSeqProvider.getScanSequence(lcmsRun.id)
    
    if( scanSeqOpt.isDefined ) scanSeqOpt.get
    else {
    	
      val mzDb = new MzDbReader( mzDbFile, true )
      //val newScanSeqId = LcMsScanSequence.generateNewId()
      
      /*val rawFile = new RawFile(
        id = runId,
        name = rawFileName,
        extension = "", //FIXME: retrieve the extension
        directory = mzDbFileDir,
        creationTimestamp = new java.util.Date(), // FIXME: retrieve the creation date
        instrument = Some(instrument)
      )*/
      
      val mzDbScans = mzDb.getScanHeaders()
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
          precursorMoz = if( precMz > 0 ) Some(precMz) else None,
          precursorCharge = if( precCharge > 0 ) Some(precCharge) else None
        )
      }
      
      val ms1ScansCount = scans.count(_.msLevel == 1)
      val ms2ScansCount = scans.count(_.msLevel == 2)
      
      val scanSeq = new LcMsScanSequence(
        runId = lcmsRun.id,
        rawFileName = rawFileName,
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
  
  // Naive and slow search of peakel in m/z and time dimension (too many iterations)
  // TODO: use MVRTreeMap (H2 builtin) or PRTree (http://www.khelekore.org/prtree/) implementations
  /*private def _getPeakelsInRange( peakels: Array[MzDbPeakel], minMz: Double, maxMz: Double, minTime: Float, maxTime: Float ): Array[MzDbPeakel] = {
      
    val matchingPeakels = new ArrayBuffer[MzDbPeakel]()
    for( peakel <- peakels ) {
      val peakelMz = peakel.getMz
      val peakelTime = peakel.getElutionTime
      if( peakelMz >= minMz && peakelMz <= maxMz && peakelTime >= minTime && peakelTime <= maxTime ) {
        matchingPeakels += peakel
      }
    }

    matchingPeakels.toArray
  }*/
  
  private def _detectMapSetFromPeakels(
    lcMsRuns: Seq[LcMsRun],
    mzDbFileByLcMsRunId: HashMap[Long,File],
    mapSetId: Long
  ): MapSet = {
    
    val intensityComputationMethod = ClusterIntensityComputation.withName(
      clusteringParams.intensityComputation.toUpperCase()
    )
    val timeComputationMethod = ClusterTimeComputation.withName(
      clusteringParams.timeComputation.toUpperCase()
    )
    
    val peakelFileByRun = new HashMap[LcMsRun,File]()
    val processedMapByRun = new HashMap[LcMsRun,ProcessedMap]()
    val processedMaps = new ArrayBuffer[ProcessedMap](lcMsRuns.length)
    val featureTuples = new ArrayBuffer[(Feature,Peptide,LcMsRun)]()
    
    var mapNumber = 0
    for( lcMsRun <- lcMsRuns ) {
      mapNumber += 1
      
      val rawMapId = RawMap.generateNewId()
      
      // Open mzDB file
      val mzDbFile = mzDbFileByLcMsRunId(lcMsRun.id)
      this.logger.info("start extraction peakels from "+mzDbFile.getName());
      val mzDb = new MzDbReader( mzDbFile, true )
      
      // Create TMP file to store orphan peakels which will be deleted after JVM exit
      val peakelFile = File.createTempFile("proline-tmp-peakels-", ".sqlite")
      peakelFile.deleteOnExit()
      this.logger.debug("creating tmp file at: " + peakelFile)
      
      // Create a mapping betwwen the TMP file and the LC-MS run
      peakelFileByRun += lcMsRun -> peakelFile
      
      // Open TMP SQLite file
      val peakelFileConnection = _initPeakelStore( peakelFile )
      
      // Create a buffer to store built features
      val rawMapFeatures = new ArrayBuffer[Feature]()
      
      // Create a mapping avoiding the creation of duplicated peakels
      val peakelByMzDbPeakelId = new HashMap[Int,Peakel]()
      
      try {
        
        this.logger.debug("detecting peakels in raw MS survey...")
  
        // Instantiate teh feature detector
        val mzdbFtDetector = new MzDbFeatureDetector(
          mzDb,
          FeatureDetectorConfig(
            msLevel = 1,
            mzTolPPM = mozTolPPM,
            minNbOverlappingIPs = 5
          )
        )
        
        // Retrieve some mappings
        val ms1ScanHeaderById = mzdbFtDetector.ms1ScanHeaderById
        val ms2ScanHeadersByCycle = mzdbFtDetector.ms2ScanHeadersByCycle
        
        // Launch the peakel detection
        // TODO: create a queue instead of arraybuffer to store the result inside the detector algo ???
        val detectedPeakels = mzdbFtDetector.detectPeakels()
        
        // Link peakels to peptides
        this.logger.debug("linking peakels to peptides...")
        val peptideByScanNumber = peptideByRunIdAndScanNumber.map( _(lcMsRun.id) ).getOrElse( HashMap.empty[Int,Peptide] )
        val psmByScanNumber = psmByRunIdAndScanNumber.map( _(lcMsRun.id) ).getOrElse( HashMap.empty[Int,PeptideMatch] )
        
        val peptideMs2ShPairsByPeakel = new HashMap[MzDbPeakel,ArrayBuffer[(Peptide,ScanHeader, Int)]]()
        
        for( detectedPeakel <- detectedPeakels ) {
          
          val peakelMz = detectedPeakel.getMz
          val mzTolDa = MsUtils.ppmToDa( peakelMz, mozTolPPM )
          
          // Find identified MS2 scans concurrent with the detected peakel
          for(
            scanId <- detectedPeakel.scanIds;
            // Retrieve the ScanHeader
            sh = ms1ScanHeaderById(scanId);
            // Retrieve corresponding MS2 scans for this cycle
            if ms2ScanHeadersByCycle.contains(sh.getCycle);
            ms2Sh <- ms2ScanHeadersByCycle(sh.getCycle);
            // Filter on m/z difference between the peakel and the precursor
            if (
              psmByScanNumber.contains(ms2Sh.getInitialId()) &&
              math.abs(psmByScanNumber(ms2Sh.getInitialId()).getExperimentalMoz - peakelMz) <= 2.0 * mzTolDa
            );
            // Keep only identified MS2 scans
            peptide <- peptideByScanNumber.get(ms2Sh.getInitialId)
          ) {
            peptideMs2ShPairsByPeakel.getOrElseUpdate( detectedPeakel, new ArrayBuffer[(Peptide,ScanHeader, Int)] ) += Tuple3(peptide, ms2Sh, psmByScanNumber(ms2Sh.getInitialId()).charge)
          }
        }
      
        // Retrieve the list of peakels unmapped with peptides
        val orphanPeakels = detectedPeakels.filter( pkl => peptideMs2ShPairsByPeakel.contains(pkl) == false )        
        
        // Store orphan peakels in SQLite file
        this.logger.debug("storing peakels in temporary file...")
        this._storePeakels(peakelFileConnection, orphanPeakels)
        
        // Iterate over peakels mapped with peptides to build features
        this.logger.debug("building features from peakels...")
        val timeTol = ftMappingParams.timeTol
        
        for(
          (peakel,peptideMs2ShTuple) <- peptideMs2ShPairsByPeakel
        ) {   
          
          val peptideMs2ShPairsGroupedByCharge = peptideMs2ShTuple.groupBy( _._3 )
          
          for( (charge,sameChargePeptideMs2ShTuple) <- peptideMs2ShPairsGroupedByCharge ) {
                
            val peakelMz = peakel.getMz
            val elutionTimes = peakel.getElutionTimes
            
            val foundPeakel = this._findPeakelIsotope(
              peakelFileConnection,
              peakelMz,
              charge,
              isotopeIdx = 1,
              minTime = elutionTimes.head - timeTol,
              avgTime = peakel.calcWeightedAverageTime(),
              maxTime = elutionTimes.last + timeTol
            )
            
            val featurePeakels = if( foundPeakel.isEmpty ) {   
              Array(peakel)
            }
            else {
              //logger.trace(s"found second isotope for peakel with m/z=$peakelMz, charge=$charge and time=${peakel.getElutionTime}")
              Array(peakel, foundPeakel.get)
            }
            
            // TODO: skip feature creation if we have only one isotope ???
            val mzDbFt = MzDbFeature(
              id = MzDbFeature.generateNewId,
              mz = peakelMz,
              charge = charge,
              indexedPeakels = featurePeakels.zipWithIndex,
              isPredicted = false,
              ms2ScanIds = sameChargePeptideMs2ShTuple.map(_._2.getId).toArray
            )
            
            // Convert mzDb feature into LC-MS one
            val lcmsFt = this._mzDbFeatureToLcMsFeature(mzDbFt,rawMapId,lcMsRun.scanSequence.get,peakelByMzDbPeakelId)
            rawMapFeatures += lcmsFt
            
            val peptides = peptideMs2ShTuple.map(_._1).distinct
            for( peptide <- peptides ) {
              featureTuples += Tuple3(lcmsFt,peptide,lcMsRun)
            }
          }
        }
        
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
        peakels = Some( peakelByMzDbPeakelId.values.toArray ),
        runId = lcMsRun.id,
        peakPickingSoftware = pps
      ).toProcessedMap(mapNumber, mapSetId)
      
      // Set processed map id of the feature (
      for ( procFt <- processedMap.features ) {
        procFt.relations.processedMapId = processedMap.id
      }
      
      processedMapByRun += lcMsRun -> processedMap
      processedMaps += processedMap
    }
    
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
    val mftBuilderByPeptideAndCharge = new HashMap[(Peptide,Int),MasterFeatureBuilder]()
    val putativeFtsByLcMsRun = new HashMap[LcMsRun,ArrayBuffer[PutativeFeature]]()
    val peptideByPutativeFt = new HashMap[PutativeFeature,Peptide]()
    
    this.logger.info("building the master map...")
    
    // Group found features by peptide and charge to build master features
    for( ( (peptide,charge), masterFeatureTuples) <- featureTuples.groupBy { ft => (ft._2,ft._1.charge) } ) {
      
      val masterFtChildren = new ArrayBuffer[Feature](masterFeatureTuples.length)
      val featureTuplesByLcMsRun =  masterFeatureTuples.groupBy(_._3)
      
      // Iterate over each LC-MS run
      val runsWithMissingFt = new ArrayBuffer[LcMsRun]()
      for( lcMsRun <- lcMsRuns ) {
        
        // Check if a feature corresponding to this peptide has been found in this run
        if( featureTuplesByLcMsRun.contains(lcMsRun) == false ) {
          runsWithMissingFt += lcMsRun
        } else {
          val runFeatureTuples = featureTuplesByLcMsRun(lcMsRun)
          val runFeatures = runFeatureTuples.map(_._1)
          // If a single feature has been mapped to a given peptide ion in a single run
          if( runFeatures.length == 1 ) masterFtChildren += runFeatures.head
          // Else if multiple features have been mapped to the same peptide ion in a single run
          else {
            // TODO: maybe we should cluster features only if they are close in time dimension ???
            masterFtChildren += ClusterizeFeatures.buildFeatureCluster(
              runFeatures,
              rawMapId = runFeatures.head.relations.rawMapId,
              procMapId = runFeatures.head.relations.processedMapId,
              intensityComputationMethod,
              timeComputationMethod,
              lcMsRun.scanSequence.get.scanById
            )
          }
        }
      }
      
      // Create TMP master feature builders
      val bestFt = masterFtChildren.maxBy( _.intensity )
      val mftBuilder = new MasterFeatureBuilder(
        bestFeature = bestFt,
        children = masterFtChildren,
        peptideId = peptide.id // attach the peptide id to the master feature
      )
      mftBuilderByPeptideAndCharge += (peptide,charge) -> mftBuilder
      
      // Create a putative feature for each missing one
      for( lcMsRun <- runsWithMissingFt ) {
        
        val procMap = processedMapByRun(lcMsRun)
        
        var predictedTime = tmpMapSet.convertElutionTime(
          bestFt.elutionTime,
          bestFt.relations.processedMapId,
          procMap.id
        )

        // Fix negative predicted times
        if( predictedTime <= 0 ) predictedTime = 1f
        
        val pf = new PutativeFeature(
          id = PutativeFeature.generateNewId,
          mz = bestFt.moz,
          charge = bestFt.charge,
          elutionTime = predictedTime,
          evidenceMsLevel = 2
        )
        pf.isPredicted = true
        
        putativeFtsByLcMsRun.getOrElseUpdate(lcMsRun, new ArrayBuffer[PutativeFeature]) += pf
        peptideByPutativeFt(pf) = peptide
      }
    }
    
    val timeTol = ftMappingParams.timeTol

    val x2RawMaps = new ArrayBuffer[RawMap](processedMaps.length)
    val x2RawMapByRunId = new HashMap[Long,RawMap]()
    
    for( lcMsRun <- lcMsRuns ) {
      
      // Retrieve processed an raw maps
      val processedMap = processedMapByRun(lcMsRun)
      val rawMap = processedMap.getRawMaps().head.get
      
      // --- Search for missing features in peakel file if any ---
      val x2RawMap = if( putativeFtsByLcMsRun.contains(lcMsRun) == false ) rawMap
      else {
        
        val putativeFts = putativeFtsByLcMsRun(lcMsRun)
        val newLcmsFeatures = new ArrayBuffer[Feature]()
        
        this.logger.info("searching for missing features to fill the raw maps...")
        
        // Re-open peakel SQLite file
        val peakelFile = peakelFileByRun(lcMsRun)
        val peakelFileConn = new SQLiteConnection(peakelFile)
        peakelFileConn.open(false) // allowCreate = false
        
        // Create a mapping avoiding the creation of duplicated peakels
        val peakelByMzDbPeakelId = new HashMap[Int,Peakel]()
        
        try {
          for( putativeFt <- putativeFts ) {
            
            val charge = putativeFt.charge
            
            val putativePeakels = for( isotopeIdx <- 0 to 1 ) yield
              this._findPeakelIsotope(
                peakelFileConn,
                putativeFt.mz,
                charge,
                isotopeIdx = isotopeIdx,
                minTime = putativeFt.elutionTime - timeTol,
                avgTime = putativeFt.elutionTime,
                maxTime = putativeFt.elutionTime + timeTol
              )
            
            if( putativePeakels.head.isDefined ) {
              val featurePeakels = putativePeakels.withFilter(_.isDefined).map(_.get).toArray
              
              // TODO: skip feature creation if we have only one isotope ???
              val mzDbFt = MzDbFeature(
                id = MzDbFeature.generateNewId,
                mz = featurePeakels.head.getMz,
                charge = charge,
                indexedPeakels = featurePeakels.zipWithIndex,
                isPredicted = true
              )
              
              val newLcmsFeature = this._mzDbFeatureToLcMsFeature(
                mzDbFt,
                rawMap.id,
                lcMsRun.scanSequence.get,
                peakelByMzDbPeakelId
              )
              
              // Set predicted time property
              newLcmsFeature.properties.get.setPredictedElutionTime( Some(putativeFt.elutionTime) )
              
              // Set processed map id
              newLcmsFeature.relations.processedMapId = processedMap.id
              
              // Append newLcmsFt in the buffer to add to it the raw map
              newLcmsFeatures += newLcmsFeature
              
              // Retrieve master feature builder to append this new feature to its children buffer
              val peptide = peptideByPutativeFt(putativeFt)
              val mftBuilder = mftBuilderByPeptideAndCharge( (peptide,putativeFt.charge) )         
              mftBuilder.children += newLcmsFeature
            }
          }
          
          // Create a new raw map by including the retrieved missing features
          val x2RawMap = rawMap.copy(
            features = rawMap.features ++ newLcmsFeatures
          )
          
          // Append missing peakels
          x2RawMap.peakels = Some( rawMap.peakels.get ++ peakelByMzDbPeakelId.values )
          
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
      
      // Detach peakels from the raw map (this should decrease memory footprint)
      x2RawMap.peakels = None
      
      // Detach peakels from features (this should decrease memory footprint)
      for( ft <- x2RawMap.features; peakelItem <- ft.relations.peakelItems ) {
        peakelItem.peakelReference = PeakelIdentifier( peakelItem.peakelReference.id )
      }
      
      // Append raw map to the array buffer
      x2RawMapByRunId += x2RawMap.runId -> x2RawMap
      x2RawMaps += x2RawMap
    }

    // Delete created TMP files (they should be deleted on exit if program fails)
    for( tmpFile <- peakelFileByRun.values ) {
      tmpFile.delete
    }
    
    // --- Build a temporary master map ---
    val alnRefMap = tmpMapSet.getAlnReferenceMap.get
    val curTime = new java.util.Date()
    
    val masterFeatures = mftBuilderByPeptideAndCharge.values.map { mftBuilder =>
      val mft = mftBuilder.toMasterFeature()
      require( mft.children.length <= lcMsRuns.length, "master feature contains more child features than maps" )
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
    for( processedMap <- tmpMapSet.childMaps ) {
      val runId = processedMap.runId.get
      processedMap.rawMapReferences = Array(x2RawMapByRunId(runId))
    }
    
    // --- Persist the corresponding map set ---
    val x2MapSet = CreateMapSet( lcmsDbCtx, mapSetName, tmpMapSet.childMaps )
    
    // Attach the computed master map to the newly created map set
    val tmpMasterMap = tmpMapSet.masterMap
    tmpMasterMap.mapSetId = x2MapSet.id
    tmpMasterMap.rawMapReferences = x2RawMaps
    x2MapSet.masterMap = tmpMasterMap
    
    x2MapSet
  }
  
  private def _initPeakelStore( fileLocation: File ): SQLiteConnection = {
    
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
  
  private def _storePeakels( sqliteConn: SQLiteConnection, peakels: Array[MzDbPeakel] ) {
    
    // BEGIN TRANSACTION
    sqliteConn.exec("BEGIN TRANSACTION;");
    
    // Prepare the insertion in the peakel table
    val peakelStmt = sqliteConn.prepare(
      s"INSERT INTO peakel VALUES (${ Array.fill(20)("?").mkString(",") })"
    )
    // Prepare the insertion in the peakel_rtree table
    val peakelIndexStmt = sqliteConn.prepare(
      s"INSERT INTO peakel_rtree VALUES (${ Array.fill(9)("?").mkString(",") })"
    )
    
    try {
      for( peakel <- peakels ) {
        
        val scanIds = peakel.getScanIds()
        val peakelMessage = peakel.toPeakelDataMessage()
        val peakelMessageAsBytes = org.msgpack.ScalaMessagePack.write(peakelMessage)
        
        val peakelMz = peakel.getMz
        val peakelTime = peakel.getApexElutionTime
        
        var fieldNumber = 1
        peakelStmt.bind(fieldNumber, peakel.id); fieldNumber += 1
        peakelStmt.bind(fieldNumber, peakelMz ); fieldNumber += 1
        peakelStmt.bind(fieldNumber, peakelTime ); fieldNumber += 1
        peakelStmt.bind(fieldNumber, peakel.getApexIntensity ); fieldNumber += 1
        peakelStmt.bind(fieldNumber, peakel.area ); fieldNumber += 1
        peakelStmt.bind(fieldNumber, peakel.calcDuration ); fieldNumber += 1
        peakelStmt.bind(fieldNumber, peakel.leftHwhmMean ); fieldNumber += 1
        peakelStmt.bind(fieldNumber, peakel.leftHwhmCv ); fieldNumber += 1
        peakelStmt.bind(fieldNumber, peakel.rightHwhmMean ); fieldNumber += 1
        peakelStmt.bind(fieldNumber, peakel.rightHwhmCv ); fieldNumber += 1
        peakelStmt.bind(fieldNumber, 0 ); fieldNumber += 1 // is_overlapping (0|1 boolean encoding)
        peakelStmt.bind(fieldNumber, 0 ); fieldNumber += 1 // features_count
        peakelStmt.bind(fieldNumber, peakel.scanIds.length ); fieldNumber += 1
        peakelStmt.bind(fieldNumber, peakelMessageAsBytes ); fieldNumber += 1
        peakelStmt.bindNull(fieldNumber); fieldNumber += 1 // param_tree
        peakelStmt.bind(fieldNumber, scanIds.head ); fieldNumber += 1
        peakelStmt.bind(fieldNumber, scanIds.last ); fieldNumber += 1
        peakelStmt.bind(fieldNumber, peakel.getApexScanId ); fieldNumber += 1
        peakelStmt.bind(fieldNumber, 1 ); fieldNumber += 1 // ms_level
        peakelStmt.bindNull(fieldNumber); // map_id
        
        peakelStmt.step()
        peakelStmt.reset()
        
        fieldNumber = 1
        peakelIndexStmt.bind(fieldNumber, peakel.id); fieldNumber += 1
        peakelIndexStmt.bind(fieldNumber, 1); fieldNumber += 1 // min_ms_level
        peakelIndexStmt.bind(fieldNumber, 1); fieldNumber += 1 // max_ms_level
        peakelIndexStmt.bind(fieldNumber, 0d ); fieldNumber += 1 // min_parent_mz
        peakelIndexStmt.bind(fieldNumber, 0d ); fieldNumber += 1 // max_parent_mz
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
  
  // TODO: move to MzDbReader when peakels are stored in the MzDbFile
  private def _findPeakelsInRange(
    sqliteConn: SQLiteConnection,
    minMz: Double,
    maxMz: Double,
    minTime: Float,
    maxTime: Float
  ): Array[MzDbPeakel] = {
    
    
    val peakelSqlQuery = "SELECT id, peaks, left_hwhm_mean, left_hwhm_cv, "+
    "right_hwhm_mean, right_hwhm_cv FROM peakel WHERE id IN " +
    "(SELECT id FROM peakel_rtree WHERE min_mz >= ? AND max_mz <= ? AND min_time >= ? AND max_time <= ? );"
    
    /*val peakelSqlQuery = "SELECT id, peaks FROM peakel " +
    "WHERE id IN (SELECT id FROM peakel_rtree " +
    "WHERE min_mz >= 0 AND max_mz <= 2000 AND min_time >= 0 AND max_time <= 2000 );"*/
    
    val peakelStmt = sqliteConn.prepare(peakelSqlQuery, false)
    val peakels = new ArrayBuffer[MzDbPeakel]()
    
    try {
      peakelStmt.bind(1, minMz)
      peakelStmt.bind(2, maxMz)
      peakelStmt.bind(3, minTime)
      peakelStmt.bind(4, maxTime)
      //println(s"minMz=$minMz maxMz=$maxMz minTime=$minTime maxTime=$maxTime")
      
      while (peakelStmt.step() ) {
        val peakelId = peakelStmt.columnInt(0)
        val peakelMessageAsBytes = peakelStmt.columnBlob(1)
        //println(peakelId)
        
        val peakelMessage = org.msgpack.ScalaMessagePack.read[fr.profi.mzdb.model.PeakelDataMessage](peakelMessageAsBytes)
        val( intensitySum, area, fwhm ) = peakelMessage.integratePeakel()
        
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
  
  private def _findPeakelIsotope(
    sqliteConn: SQLiteConnection,
    peakelMz: Double,
    charge: Int,
    isotopeIdx: Int,
    minTime: Float,
    avgTime: Float,
    maxTime: Float
  ): Option[MzDbPeakel] = {
    
    val avgIsotopeMzDiff = isotopeIdx * avgIsotopeMassDiff / charge
    val searchedMz = peakelMz + avgIsotopeMzDiff
    val mozTolInDa = MsUtils.ppmToDa(searchedMz, mozTolPPM)
    
    // Search for peakel corresponding to second isotope
    val foundPeakels = _findPeakelsInRange(
      sqliteConn,
      searchedMz - mozTolInDa,
      searchedMz + mozTolInDa,
      minTime,
      maxTime
    )
    
    if( foundPeakels.isEmpty ) None
    else {
      // TODO: minimize m/z diff instead of time diff ??? or ask for a given isotope theo ratio
//      if (foundPeakels.length > 1) this.logger.debug(s"Found ${foundPeakels.length} putative isotopes for peakel $peakelMz")
      val nearestPeakelInTime = foundPeakels.minBy( peakel => math.abs( avgTime - peakel.calcWeightedAverageTime() ) )

      Some(nearestPeakelInTime)
    }
  }
  
  private def _extractProcessedMap( lcmsRun: LcMsRun, mzDbFile: File, mapNumber: Int, mapSetId: Long ): ProcessedMap = {
    
    // TODO: add max charge in config
    val maxCharge = 4
    val mzDbFts = if( quantConfig.detectFeatures ) this._detectFeatures(mzDbFile).filter(ft => ft.charge > 1 && ft.charge <= maxCharge)
    else this._extractFeaturesUsingMs2Events( mzDbFile, lcmsRun )
    
    val rmsds = mzDbFts.par.map{ mzdbFt =>
      val theoAbundances = IsotopePatternInterpolator.getTheoreticalPattern(mzdbFt.mz, mzdbFt.charge).abundances
      val peakelApexIntensities = mzdbFt.getPeakels.map(_.getApexIntensity)
      this._calcRmsd(theoAbundances, peakelApexIntensities)
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
    
    /*
    // Map features by each of their monoisotopic peakel peaks
    val mzDbFtsByPeak = new HashMap[MzDbPeak, ArrayBuffer[MzDbFeature]]
    val ftByPeak = for(
      mzDbFt <- mzDbFts;
      peak <- mzDbFt.peakels.head.definedPeaks
    ) {
      mzDbFtsByPeak.getOrElseUpdate(peak, new ArrayBuffer[MzDbFeature] ) += mzDbFt
    }
    
    def findFeaturesWithSharedPeaks( mzDbFt: MzDbFeature, mzDbFtSet: HashSet[MzDbFeature] ) {
      
      // Add the current feature the set of already clusterized features
      mzDbFtSet += mzDbFt
      
      val foundMzDbFtSet = new HashSet[MzDbFeature]
      for( peak <- mzDbFt.peakels.head.definedPeaks ) {
        foundMzDbFtSet ++= mzDbFtsByPeak(peak)
      }
      
      for( foundMzDbFt <- foundMzDbFtSet ) {
        if( mzDbFtSet.contains(foundMzDbFt) == false ) {          
          findFeaturesWithSharedPeaks( foundMzDbFt, mzDbFtSet )
        }
      }
    }
    
    // Clusterize features
    val mzDbFtClusters = new ArrayBuffer[Array[MzDbFeature]](mzDbFts.length)
    val clusterizedFts = new HashSet[MzDbFeature]()
    for( mzDbFt <- mzDbFts ) {
      if( clusterizedFts.contains(mzDbFt) == false ) {
        val mzDbFtsCluster = new HashSet[MzDbFeature]()
        
        findFeaturesWithSharedPeaks(mzDbFt, mzDbFtsCluster)
        
        clusterizedFts ++= mzDbFtsCluster
        mzDbFtClusters += mzDbFtsCluster.toArray        
      }
    }
    
    // Instantiate a feature clusterer
    val ftClusterer = new FeatureClusterer(
      lcmsMap = tmpRawMap,
      scans = lcmsRun.scanSequence.get.scans,
      params = clusteringParams
    )
    
    // Convert mzDB features into LC-MS DB features
    val lcmsFeaturesWithoutClusters = new ArrayBuffer[LcMsFeature](mzDbFts.length)
    val lcmsFeaturesWithClusters = new ArrayBuffer[LcMsFeature](mzDbFts.length)    
    
    for( mzDbFtCluster <- mzDbFtClusters ) {
      
      val lcmsFtCluster = new ArrayBuffer[LcMsFeature](mzDbFtCluster.length)
      for( mzDbFt <- mzDbFtCluster ) {
        // Keep only features with defined area and at least two data points
        if( mzDbFt.area > 0 && mzDbFt.scanHeaders.length > 1 ) {
          val lcmsFt = this._mzDbFeatureToLcMsFeature(mzDbFt,rawMapId,lcmsRun.scanSequence.get)
          
          lcmsFtCluster += lcmsFt
          lcmsFeaturesWithoutClusters += lcmsFt
        }
      }
      
      if( lcmsFtCluster.length == 1 ) lcmsFeaturesWithClusters += lcmsFtCluster.head
      else {
        lcmsFeaturesWithClusters += ftClusterer.buildFeatureCluster(lcmsFtCluster)
      }
    }*/
    
    // Convert features
    val peakelByMzDbPeakelId = new HashMap[Int,Peakel]()
    val lcmsFeaturesWithoutClusters = mzDbFts.map( mzDbFt => 
      this._mzDbFeatureToLcMsFeature(mzDbFt,rawMapId,lcmsRun.scanSequence.get,peakelByMzDbPeakelId )
    )
    
    //val peakels = lcmsFeaturesWithoutClusters.flatMap( _.relations.peakelItems.map(_.peakelReference.asInstanceOf[Peakel] ) )
    
    val rawMap = tmpRawMap.copy(
      features = lcmsFeaturesWithoutClusters,
      peakels = Some(peakelByMzDbPeakelId.values.toArray)
    )
    
    rawMap.toProcessedMap(mapNumber, mapSetId)
  }
  
  private def _extractFeaturesUsingMs2Events( mzDbFile: File, lcmsRun: LcMsRun ): Array[MzDbFeature] = {
    
    logger.info("Start extracting features from MS2 events from " + mzDbFile.getName)
    
    val restrictToIdentifiedPeptides = quantConfig.startFromValidatedPeptides
    val peptideByScanNumber = peptideByRunIdAndScanNumber.map( _(lcmsRun.id) ).getOrElse( HashMap.empty[Int,Peptide] )
    val mzDb = new MzDbReader( mzDbFile, true )
    
    val mzDbFts = try {
      
      val ftXtractConfig = FeatureExtractorConfig(
         mzTolPPM = this.mozTolPPM
      )
      
      val mzdbFtX = new MzDbFeatureExtractor(mzDb,5,5,ftXtractConfig)
      
      this.logger.info("retrieving scan headers...")
      val scanHeaders = mzDb.getScanHeaders()
      val ms2ScanHeaders = scanHeaders.filter(_.getMsLevel() == 2 )
      val pfs = new ArrayBuffer[PutativeFeature](ms2ScanHeaders.length)
      
      this.logger.debug("building putative features list from MS2 scan events...")

      for( scanH <- ms2ScanHeaders ) {
        
        if( !restrictToIdentifiedPeptides || peptideByScanNumber.contains(scanH.getInitialId()) ) {
          pfs += new PutativeFeature(
            id = PutativeFeature.generateNewId,
            mz = scanH.getPrecursorMz,
            charge =scanH.getPrecursorCharge,
            scanId = scanH.getId,
            evidenceMsLevel = 2
          )
        }
      }
      
      // Instantiates a Run Slice Data provider
      val rsdProv = new RunSliceDataProvider( mzDb.getRunSliceIterator(1) )
      
      // Extract features
      mzdbFtX.extractFeatures(rsdProv, pfs, mozTolPPM)

    } finally {
      mzDb.close()
    }
    
    logger.info("Feature extraction done for file " + mzDbFile.getName)
    mzDbFts.toArray
  }
  
  
  
  private def _detectFeatures( mzDbFile: File ): Array[MzDbFeature] = {
    
    val mzDb = new MzDbReader( mzDbFile, true )
    
    val mzDbFts = try {
      
      this.logger.info("Start detecting features in raw MS survey from "+mzDbFile.getName)

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
    peakelByMzDbPeakelId: HashMap[Int,Peakel]
  ): Seq[Feature] = {
    
    val procMapId = processedMap.id
    val rawMapId = processedMap.getRawMapIds().head
    val masterMap = mapSet.masterMap
    val nbMaps = mapSet.childMaps.length

    val mzDb = new MzDbReader( mzDbFile, true )
    var mzDbFts = Seq.empty[MzDbFeature]
    val mftsWithMissingChild = new ArrayBuffer[Feature]
    val missingFtIdByMftId = new collection.mutable.HashMap[Long,Int]()
    val pfs = new ArrayBuffer[PutativeFeature]()
    
    try {
      
      val ftXtractConfig = FeatureExtractorConfig(
         mzTolPPM = this.mozTolPPM,
         maxIPDeviation = processedMap.properties.flatMap( _.ipDeviationUpperBound )
      )
      
      val mzdbFtX = new MzDbFeatureExtractor(mzDb,5,5,ftXtractConfig)
      
      //val scanHeaders = mzDb.getScanHeaders()
      //val ms2ScanHeaders = scanHeaders.filter(_.getMsLevel() == 2 )
      
      this.logger.info("Start extracting missing Features from "+mzDbFile.getName)
      this.logger.info("building putative features list using master features...")

      for( mft <- masterMap.features ) {
        require( mft.children.length <= nbMaps, "master feature contains more child features than maps")
        
        // Check for master features having a missing child for this processed map
        val childFtOpt = mft.children.find( _.relations.processedMapId == processedMap.id )
        if( childFtOpt.isEmpty ) {
          mftsWithMissingChild += mft
          
          val bestChildProcMapId = mft.relations.bestChildProcessedMapId
          val bestChild = mft.children.find( _.relations.processedMapId == bestChildProcMapId ).get
          
          //val childMapAlnSet = revRefMapAlnSetByMapId(bestChildMapId)
          //val predictedTime = childMapAlnSet.calcReferenceElutionTime(mft.elutionTime, mft.mass)
          var predictedTime = mapSet.convertElutionTime(bestChild.elutionTime, bestChildProcMapId, procMapId)
          //println( "ftTime="+ mft.elutionTime +" and predicted time (in "+mzDbMapId+")="+predictedTime)
          
          // Fix negative predicted times
          if( predictedTime <= 0 ) predictedTime = 1f
          
          // Note: we can have multiple missing features for a given MFT
          // However we assume there a single missing feature for a given map
          val missingFtId = PutativeFeature.generateNewId
          missingFtIdByMftId += ( mft.id -> missingFtId )
          
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
      val rsdProv = new RunSliceDataProvider( mzDb.getRunSliceIterator(1) )
      this.logger.info("extracting "+missingFtIdByMftId.size+" missing Features from "+mzDbFile.getName)
      // Extract features
      // TODO: add minNbCycles param
      mzDbFts = mzdbFtX.extractFeatures(rsdProv, pfs, mozTolPPM)

    }
    finally {
      mzDb.close()
    }
    
    val pfById = Map() ++ pfs.map( pf => pf.id -> pf )
    val mzDbFtById = Map() ++ mzDbFts.map( ft => ft.id -> ft )
    
    // Convert mzDB features into LC-MS DB features
    val newLcmsFeatures = new ArrayBuffer[LcMsFeature](missingFtIdByMftId.size)
    for( mftWithMissingChild <- mftsWithMissingChild;
         mzDbFt <- mzDbFtById.get(missingFtIdByMftId( mftWithMissingChild.id ))
         if mzDbFt.area > 0 && mzDbFt.getMs1Count >= 5
       ) {
      
      // FIXME: why do we extract features with 0 duration ???
      
      // Convert the extracted feature into a LC-MS feature
      val newLcmsFt = this._mzDbFeatureToLcMsFeature(mzDbFt,rawMapId,lcmsRun.scanSequence.get, peakelByMzDbPeakelId )
      
      // TODO: decide if we set or not this value (it may help for distinction with other features)
      newLcmsFt.correctedElutionTime = Some(mftWithMissingChild.elutionTime)
      
      // Update the processed map id of the new feature
      newLcmsFt.relations.processedMapId = processedMap.id
      
      // Add missing child feature to the master feature
      mftWithMissingChild.children ++= Array(newLcmsFt)
      
      // Add predicted time property
      val pf = pfById(mzDbFt.id)
      val predictedTime = pf.elutionTime
      newLcmsFt.properties.get.setPredictedElutionTime( Some(predictedTime) )
      
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
    peptideByScanId: Map[Long,Peptide],
    clustererByMapId: Map[Long,FeatureClusterer]
  ): Unit = {
    this.logger.info("re-building master map using peptide identities...")
    
    val alnRefMapId = mapSet.getAlnReferenceMapId
    val masterFeatures = mapSet.masterMap.features
    val newMasterFeatures = new ArrayBuffer[Feature](masterFeatures.length)
    
    // Iterate over all map set master features
    for( mft <- masterFeatures ) {
      
      // --- Find peptides matching child sub features ---
      val featuresByPepId = new HashMap[Long,ArrayBuffer[Feature]]
      val pepIdsByFeature = new HashMap[Feature,ArrayBuffer[Long]]
      val unidentifiedFtSet = new collection.mutable.HashSet[Feature]
      
      // Decompose clusters if they exist and map them by peptide identification
      mft.eachChildSubFeature { subFt =>
        val ftRelations = subFt.relations
        val peptideIds = ftRelations.ms2EventIds.map( peptideByScanId.get(_) ).withFilter(_.isDefined).map(_.get.id).distinct
        
        if( peptideIds.isEmpty ) unidentifiedFtSet += subFt
        else {
          for( pepId <- peptideIds ) {
            featuresByPepId.getOrElseUpdate(pepId, new ArrayBuffer[Feature]) += subFt
            pepIdsByFeature.getOrElseUpdate(subFt, new ArrayBuffer[Long]) += pepId
          }
        }
      }
      
      // --- Solve identification conflicts ---
      
      // If no identified feature in this master
      if( featuresByPepId.isEmpty ) {
        // We keep the existing master feature as is
        newMasterFeatures += mft
      }
      // If all child features match the same peptide
      else if( featuresByPepId.size == 1 ) {
        // We tag this master feature with the peptide ID
        mft.relations.peptideId = featuresByPepId.head._1 
        // And we keep the existing master feature
        newMasterFeatures += mft
      } else {
        // Else we create a master feature for each matching peptide
        for( (pepId, features) <- featuresByPepId ) {
          
          val newMftFeatures = features ++ unidentifiedFtSet
          val ftsByMapId = newMftFeatures.groupBy( _.relations.processedMapId )
          val clusterizedFeatures = new ArrayBuffer[Feature]
          
          // Check if these features are assigned to a single peptide
          newMftFeatures.foreach { ft =>
            if( pepIdsByFeature.get(ft).map( _.length ).getOrElse(0) > 1 ) {
              // Flag this feature as a conflicting one
              ft.selectionLevel = 0
            }
          }
          
          // Iterate over features grouped by maps
          for( (mapId,fts) <- ftsByMapId ) {
            // If we have a single feature for this map => we keep it as is
            if( fts.length == 1 ) clusterizedFeatures += fts.head
            // Else we clusterize the multiple detected features
            else {
              
              // Partition identified and unidentified features
              val( identifiedFts, unidentifiedFts ) = fts.partition( unidentifiedFtSet.contains(_) == false )
              
              // If we don't have at least one identified feature
              val ftCluster = if( identifiedFts.isEmpty ) {
                // Clusterize unidentified features
                clustererByMapId(mapId).buildFeatureCluster(unidentifiedFts)
              } else {
                // Else clusterize identified features
                val tmpFtCluster = clustererByMapId(mapId).buildFeatureCluster(identifiedFts)
                
                if( unidentifiedFts.isEmpty == false ) {
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
          
          val refFtOpt = clusterizedFeatures.find( _.relations.processedMapId == alnRefMapId )
          val refFt = refFtOpt.getOrElse(clusterizedFeatures.head)
          val newMft = refFt.toMasterFeature( children = clusterizedFeatures.toArray )
          
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

    mapSet.masterMap = mapSet.masterMap.copy( features = newMasterFeatures.toArray )
    
    ()
  }
  
  private def _mzDbFeatureToLcMsFeature(
    mzDbFt: MzDbFeature,
    rawMapId: Long,
    scanSeq: LcMsScanSequence,
    peakelByMzDbPeakelId: HashMap[Int,Peakel]
  ): LcMsFeature = {
    
    val ftId = LcMsFeature.generateNewId
    
    // Retrieve some vars
    val lcmsScanIdByInitialId = scanSeq.scanIdByInitialId
    val scanInitialIds = mzDbFt.getScanIds
    val( firstScanInitialId, lastScanInitialId ) = (scanInitialIds.head,scanInitialIds.last)
    val apexScanInitialId = mzDbFt.getApexScanId
    val firstLcMsScanId = lcmsScanIdByInitialId(firstScanInitialId)
    val lastLcMsScanId = lcmsScanIdByInitialId(lastScanInitialId)
    val apexLcMsScanId = lcmsScanIdByInitialId(apexScanInitialId)
    val ms2EventIds = mzDbFt.getMs2ScanIds.map( lcmsScanIdByInitialId(_) )
    
    val indexedPeakels = mzDbFt.indexedPeakels
    // TODO: parameterize the computation of this value ???
    val intensitySum2Peakels = indexedPeakels.take(2).foldLeft(0f)( (s,p) => s + p._1.getApexIntensity )
    
    val lcmsFtPeakelItems = indexedPeakels.map { case (mzDbPeakel,peakelIdx) =>
      
      // Retrieve cached LC-MS peakel if it exists
      val lcmsPeakel = if( peakelByMzDbPeakelId.contains(mzDbPeakel.id) ) {
        val existingPeakel = peakelByMzDbPeakelId(mzDbPeakel.id)
        
        // Increase features count
        existingPeakel.featuresCount = existingPeakel.featuresCount + 1
        
        existingPeakel
      }
      // Else build new LC-MS peakel
      else {
        
        val peakelCursor = mzDbPeakel.getNewCursor()
        val lcMsPeaks = new Array[LcMsPeak](mzDbPeakel.scanIds.length)
        while( peakelCursor.next() ) {
          lcMsPeaks(peakelCursor.peakIndex) = LcMsPeak(
            peakelCursor.getMz(),
            peakelCursor.getElutionTime(),
            peakelCursor.getIntensity()
          )
        }
        
        val peakelScanInitialIds = mzDbPeakel.getScanIds
        
        val newPeakel = Peakel(
          id = Peakel.generateNewId,
          moz = mzDbPeakel.getMz,
          elutionTime = mzDbPeakel.getElutionTime(),
          apexIntensity = mzDbPeakel.getApexIntensity(),
          area = mzDbPeakel.area,
          duration = mzDbPeakel.calcDuration,
          //fwhm = Some( mzDbPeakel.fwhm ),
          isOverlapping = false, // FIXME: determine this value
          featuresCount = 1,
          peaks = lcMsPeaks,
          // FIXME: scanId and scanInitialId may be different in future mzDB configurations
          firstScanId = lcmsScanIdByInitialId(peakelScanInitialIds.head),
          lastScanId = lcmsScanIdByInitialId(peakelScanInitialIds.last),
          apexScanId = lcmsScanIdByInitialId(mzDbPeakel.getApexScanId),
          rawMapId = rawMapId
        )
        
        // Cache new LC-MS peakel
        peakelByMzDbPeakelId(mzDbPeakel.id) = newPeakel
        
        newPeakel
      }
      
      FeaturePeakelItem(
        featureReference = FeatureIdentifier(ftId),
        peakelReference = lcmsPeakel,
        isotopeIndex = peakelIdx
      )
    }
    
    val ftProps = FeatureProperties(
      peakelsCount = Some(lcmsFtPeakelItems.length)
    )
    
    new LcMsFeature(
       id = ftId,
       moz = mzDbFt.mz,
       apexIntensity = mzDbFt.getBasePeakel().getApexIntensity(),
       intensity = mzDbFt.getBasePeakel().getApexIntensity(), //intensitySum2Peakels, //mzDbFt.area,
       charge = mzDbFt.charge,
       elutionTime = mzDbFt.getElutionTime,
       duration = mzDbFt.calcDuration(),
       qualityScore = Option(mzDbFt.qualityProperties).map(_.qualityScore).getOrElse(0f),
       ms1Count = mzDbFt.getMs1Count,
       ms2Count = mzDbFt.getMs2Count,
       isOverlapping = false,
       isotopicPatterns = None,
       //overlappingFeatures = overlappingFeatures,
       /*children = children,
       subFeatures = subFeatures,
       calibratedMoz = calibratedMoz,
       normalizedIntensity = normalizedIntensity,
       correctedElutionTime = correctedElutionTime,
       isClusterized = isClusterized,*/
       selectionLevel = 2,
       properties = Some(ftProps),
       relations = new FeatureRelations(
         ms2EventIds = ms2EventIds,
         peakelItems = lcmsFtPeakelItems,
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
  
  private def _calcRmsd(theoInt: Array[Float], obsInt: Array[Float]): Double = {
    val maxObsInt = obsInt.max
    val scaledInt = obsInt.map(_ * 100 / maxObsInt)
    val s = theoInt.zip(scaledInt).foldLeft(0d)( (s, ab) => s + math.pow((ab._1 - ab._2), 2) )
    math.sqrt(s)
  }
  
}