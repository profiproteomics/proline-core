package fr.proline.core.service.lcms.io

import java.io.File
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.collection.mutable.HashSet
import com.typesafe.scalalogging.slf4j.Logging
import org.apache.commons.math.stat.descriptive.rank.Percentile
import fr.profi.jdbc.easy._
import fr.profi.ms.algo.IsotopePatternInterpolator
import fr.profi.mzdb._
import fr.profi.mzdb.algo.feature.extraction.FeatureExtractorConfig
import fr.profi.mzdb.io.reader.RunSliceDataProvider
import fr.profi.mzdb.model.{ Feature => MzDbFeature, Peak => MzDbPeak, Peakel => MzDbPeakel }
import fr.profi.mzdb.model.PutativeFeature
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.algo.lcms._
import fr.proline.core.dal.helper.LcmsDbHelper
import fr.proline.core.dal.{ DoJDBCWork, DoJDBCReturningWork }
import fr.proline.core.om.model.lcms.{ Feature => LcMsFeature, IsotopicPattern => LcMsIsotopicPattern }
import fr.proline.core.om.model.lcms._
import fr.proline.core.om.model.msi.{Instrument,Peptide}
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
  val peptideByRunIdAndScanNumber: Option[Map[Long,HashMap[Int,Peptide]]] = None // sequence data may or may not be provided
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
  
  protected val lcmsDbHelper = new LcmsDbHelper( lcmsDbCtx )
  protected val scanSeqProvider = new SQLScanSequenceProvider( lcmsDbCtx )
  
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
    
    // --- Extract run maps and convert them to processed maps ---
    val lcmsRunByProcMapId = new collection.mutable.HashMap[Long,LcMsRun]
    val mzDbFileByProcMapId = new collection.mutable.HashMap[Long,File]
    val processedMaps = new Array[ProcessedMap](mapCount)

    val tmpMapSetId = MapSet.generateNewId()
    var mapIdx = 0
    var alnRefMapId: Long = 0L
    
    for( lcmsRun <- lcMsRuns ) {
  
      val rawFile = lcmsRun.rawFile
      val rawPropOpt = rawFile.properties
      val mzDbFilePath = rawPropOpt.get.getMzdbFilePath
      val mzDbFile = new File(mzDbFilePath)
      
      //val mzDb = new MzDbReader( mzDbFile, true )
      //val scanH = mzDb.getScanHeaderForTime(1755.0521f, 1)
      //println( "scan time ="+ scanH.getTime )
      
      if( lcmsRun.scanSequence.isEmpty ) {
        // Retrieve the corresponding LC-MS run
        // Or store it in the database if it doesn't exist
        lcmsRun.scanSequence = Some( this._fetchOrStoreScanSequence(lcmsRun,mzDbFile) )
      }
      
      // Extract LC-MS map from the mzDB file
      val processedMap = this._extractProcessedMap(lcmsRun,mzDbFile, mapIdx + 1, tmpMapSetId)
      
      // Update some mappings
      lcmsRunByProcMapId += processedMap.id -> lcmsRun
      mzDbFileByProcMapId += processedMap.id -> mzDbFile
    
      // Convert to processed map
      //val processedMap = rawMap.toProcessedMap( number = mapIdx + 1, mapSetId = tmpMapSetId )
      
      // Set first map as default alignment reference
      if( mapCount == 1 ) {
        processedMap.isAlnReference = true
        alnRefMapId = processedMap.id
      }
      
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
    
    // --- Create an in-memory map set ---
    var mapSet = new MapSet(
      id = tmpMapSetId,
      name = "",
      creationTimestamp = new java.util.Date,
      childMaps = processedMaps,//.toArray,
      alnReferenceMapId = alnRefMapId
    )
    
    // --- Perform the LC-MS maps alignment ---
    // TODO: do we need to remove the clusters for the alignment ???
    val mapAligner = LcmsMapAligner( methodName = alnMethodName )
    val alnResult = mapAligner.computeMapAlignments( mapSet.childMaps.filter(_ != null), alnParams )
    
    /*for( as <- alnResult.mapAlnSets; a <- as.mapAlignments  ) {
      println("map aln from "+as.refMapId+" to " +as.targetMapId)
      for( t <- a.timeList)
        println(t)
    }*/
    
    // Update MapSet attributes
    mapSet.setAlnReferenceMapId(alnResult.alnRefMapId)
    mapSet.mapAlnSets = alnResult.mapAlnSets
    
    // --- Build a temporary Master Map ---
    mapSet.masterMap = BuildMasterMap(
      mapSet,
      lcMsRuns.map(_.scanSequence.get),
      masterFtFilter,
      ftMappingParams,
      clusteringParams
    )
    //mapSet.toTsvFile("D:/proline/data/test/quanti/debug/master_map_"+ (-mapSet.masterMap.id) +".tsv")
    
    // --- Retrieve the map alignements ---
    /*val refMapAlnSetByMapId = mapSet.getRefMapAlnSetByMapId.get
    val reversedRefMapAlnSetByMapId = Map() ++ refMapAlnSetByMapId.values.map { alnSet =>
      val revAlnSet = alnSet.getReversedAlnSet
      revAlnSet.refMapId -> revAlnSet
    }*/
    
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
      val ftClustererByMapId = Map() ++ mapSet.childMaps.map { childMap =>
        val scanSeq = scanSeqByRunId(childMap.runId.get)
        childMap.id -> new FeatureClusterer(childMap,scanSeq.scans,clusteringParams)
      }
      
      this._rebuildMasterMapUsingPeptides(mapSet,peptideByScanId.result,ftClustererByMapId)
      //mapSet.toTsvFile("D:/proline/data/test/quanti/debug/master_map_with_peps_"+ (-mapSet.masterMap.id) +".tsv")
    }
    
    // Re-build child maps in order to be sure they contain master feature children (including clusters)
    mapSet = mapSet.rebuildChildMaps()
    
    // Instantiate a raw map storer
    val rawMapStorer = RawMapStorer( lcmsDbCtx )
    
    // --- Extract LC-MS missing features in all raw files ---
    val x2RawMaps = new ArrayBuffer[RawMap]
    val x2ProcessedMaps = new ArrayBuffer[ProcessedMap]
    for( processedMap <- mapSet.childMaps ) {
      val rawMap = processedMap.getRawMaps().head.get
      val mzDbFile = mzDbFileByProcMapId(processedMap.id)
      val lcmsRun = lcmsRunByProcMapId(processedMap.id)
      
      // Extract missign features
      val peakelByMzDbPeakel = new HashMap[MzDbPeakel,Peakel]()
      val newLcmsFeatures = this._extractMissingFeatures(mzDbFile,lcmsRun,processedMap,mapSet,peakelByMzDbPeakel)      
      
      // Create a new run map with the extracted missing features
      val x2RawMap = rawMap.copy(
        features = rawMap.features ++ newLcmsFeatures
      )
      
      // Append missing peakels
      x2RawMap.peakels = Some( rawMap.peakels.get ++ peakelByMzDbPeakel.values )
      
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
    x2MapSet.masterMap = mapSet.masterMap
    x2MapSet.masterMap.mapSetId = x2MapSet.id
    x2MapSet.masterMap.rawMapReferences = x2RawMaps
    
    // --- Update and store the map alignment using processed maps with persisted ids ---
    AlignMapSet(lcmsDbCtx, x2MapSet,alnMethodName, alnParams)
    
    val x2AlnResult = mapAligner.computeMapAlignments( x2ProcessedMaps, alnParams )
    
    // --- Normalize the processed maps ---
    if (normalizationMethod.isDefined && mapSet.childMaps.length > 1) {

      // Instantiate a service for map set normalization
      logger.info("normalizing maps...")
      
      // Updates the normalized intensities
      MapSetNormalizer(normalizationMethod.get).normalizeFeaturesIntensity(mapSet)

      // Re-build master map features using best child
      logger.info("re-build master map features using best child...")
      x2MapSet.rebuildMasterFeaturesUsingBestChild()
    }
    
    // --- Store the processed maps features ---
    
    // Instantiate a processed map storer
    val processedMapStorer = ProcessedMapStorer( lcmsDbCtx )
    
    DoJDBCWork.withEzDBC( lcmsDbCtx, { ezDBC =>
      for( processedMap <- x2MapSet.childMaps ) {
        
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
    masterMapStorer.storeMasterMap(x2MapSet.masterMap)
    
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
    
    logger.info(x2MapSet.masterMap.features.length +" master features have been extracted")
    
    this.extractedMapSet = x2MapSet
    
    true
  }
  
  private def _fetchOrStoreScanSequence( lcmsRun: LcMsRun, mzDbFile: File ): LcMsScanSequence = {
    
    val mzDbFileDir = mzDbFile.getParent()
    val mzDbFileName = mzDbFile.getName()
    // FIXME: it should be retrieved from the mzDB file meta-data
    val rawFileName = mzDbFileName.take(mzDbFileName.lastIndexOf("."))
    
    // Check if a LC-MS run already exists
    val scanSeqId = lcmsDbHelper.getScanSequenceIdForRawFileName(rawFileName)
    
    if( scanSeqId.isDefined ) scanSeqProvider.getScanSequence(scanSeqId.get)
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
  
  private def _extractProcessedMap( lcmsRun: LcMsRun, mzDbFile: File, mapNumber: Int, mapSetId: Long ): ProcessedMap = {
    
    val mzDbFts = if( quantConfig.detectFeatures ) this._detectFeatures(mzDbFile)
    else this._extractFeaturesUsingMs2Events( mzDbFile, lcmsRun )
    
    val rmsds = mzDbFts.par.map{f => 
      val theoAbundances = IsotopePatternInterpolator.getTheoreticalPattern(f.mz, f.charge).abundances
      val peakelApexIntensities = f.peakels.map(_.getApex().getIntensity)
      this._calcRmsd(theoAbundances, peakelApexIntensities)
    }.toArray
    
    val percentileComputer = new Percentile()
    val ipDevQ1 = percentileComputer.evaluate(rmsds, 25)
    val ipDevQ3 = percentileComputer.evaluate(rmsds, 75)
    val ipDevIQR = ipDevQ3 - ipDevQ1
    val ipDevUB = ipDevQ3 + 1.5 * ipDevIQR
    
    val rawMapId = RawMap.generateNewId()
    
    // Create a new Run Map
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
    val peakelByMzDbPeakel = new HashMap[MzDbPeakel,Peakel]()
    val lcmsFeaturesWithoutClusters = mzDbFts.map( mzDbFt => 
      this._mzDbFeatureToLcMsFeature(mzDbFt,rawMapId,lcmsRun.scanSequence.get,peakelByMzDbPeakel)
    )
    
    //val peakels = lcmsFeaturesWithoutClusters.flatMap( _.relations.peakelItems.map(_.peakelReference.asInstanceOf[Peakel] ) )
    
    val rawMap = tmpRawMap.copy(
      features = lcmsFeaturesWithoutClusters,
      peakels = Some(peakelByMzDbPeakel.values.toArray)
    )
    
    rawMap.toProcessedMap(mapNumber, mapSetId)
  }
  
  private def _extractFeaturesUsingMs2Events( mzDbFile: File, lcmsRun: LcMsRun ): Array[MzDbFeature] = {
    
    val restrictToIdentifiedPeptides = quantConfig.startFromValidatedPeptides
    val peptideByScanNumber = peptideByRunIdAndScanNumber.map( _(lcmsRun.id) ).getOrElse( HashMap.empty[Int,Peptide] )
    val mzDb = new MzDbReader( mzDbFile, true )
    
    val mzDbFts = try {
      
      val mzdbFtX = new MzDbFeatureExtractor(mzDb,5,5)
      
      this.logger.info("retrieve scan headers...")
      val scanHeaders = mzDb.getScanHeaders()
      val ms2ScanHeaders = scanHeaders.filter(_.getMsLevel() == 2 )
      val pfs = new ArrayBuffer[PutativeFeature](ms2ScanHeaders.length)
      
      this.logger.info("building putative features list from MS2 scan events...")

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
    
    mzDbFts.toArray
  }
  
  private def _detectFeatures( mzDbFile: File ): Array[MzDbFeature] = {
    
    val mzDb = new MzDbReader( mzDbFile, true )
    
    val mzDbFts = try {
      
      this.logger.info("detect features in raw MS survey...")

      val mzdbFtDetector = new MzDbFeatureDetector(mzDb, FeatureDetectorConfig(minNbOverlappingIPs=5))
      
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
    peakelByMzDbPeakel: HashMap[MzDbPeakel,Peakel]
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
          
          // Warning: we can have multiple missing features for a given MFT
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
      
      // Extract features
      // TODO: add minNbCycles param
      mzDbFts = mzdbFtX.extractFeatures(rsdProv, pfs, mozTolPPM)

    }
    finally {
      mzDb.close()
    }
    
    val mzDbFtById = Map() ++ mzDbFts.map( ft => ft.id ->ft )
    
    // Convert mzDB features into LC-MS DB features    
    val newLcmsFeatures = new ArrayBuffer[LcMsFeature](missingFtIdByMftId.size)
    for( mftWithMissingChild <- mftsWithMissingChild;
         mzDbFt <- mzDbFtById.get(missingFtIdByMftId( mftWithMissingChild.id ))
         if mzDbFt.area > 0 && mzDbFt.scanHeaders.length >= 5
       ) {
      
      // FIXME: why do we extract features with 0 duration ???
      
      // Convert the extracted feature into a LC-MS feature
      val newLcmsFt = this._mzDbFeatureToLcMsFeature(mzDbFt,rawMapId,lcmsRun.scanSequence.get,peakelByMzDbPeakel)
      
      // TODO: decide if we set or not this value (it may help for distinction with other features)
      newLcmsFt.correctedElutionTime = Some(mftWithMissingChild.elutionTime)
      
      // Update the processed map id of the new feature
      newLcmsFt.relations.processedMapId = processedMap.id
      
      // Add missing child feature to the master feature
      mftWithMissingChild.children ++= Array(newLcmsFt)
      
      // Add new LC-MS feature to the Run Map
      newLcmsFeatures += newLcmsFt
    }
    
    newLcmsFeatures
  }
  
  // TODO: apply this before and after filling missing values ???
  // Note: in this method we will break master features if they are matching multiple peptides
  // Howver we do not group existing master features
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
    peakelByMzDbPeakel: HashMap[MzDbPeakel,Peakel]
  ): LcMsFeature = {
    
    val ftId = LcMsFeature.generateNewId
    
    // Retrieve some vars
    val lcmsScanIdByInitialId = scanSeq.scanIdByInitialId
    val scanHeaders = mzDbFt.scanHeaders
    val( ftFirstScanH, ftLastScanH ) = (scanHeaders.head,scanHeaders.last)
    val firstScanInitialId = ftFirstScanH.getInitialId
    val lastScanInitialId = ftLastScanH.getInitialId
    val apexScanInitialId = mzDbFt.apexScanHeader.getInitialId
    val firstLcMsScanId = lcmsScanIdByInitialId(firstScanInitialId)
    val lastLcMsScanId = lcmsScanIdByInitialId(lastScanInitialId)
    val apexLcMsScanId = lcmsScanIdByInitialId(apexScanInitialId)
    val ms2EventIds = mzDbFt.getMs2ScanIds.map( lcmsScanIdByInitialId(_) )
    
    val peakels = mzDbFt.getPeakels()
    // TODO: parameterize the computation of this value ???
    val intensitySum2Peakels = peakels.take(2).foldLeft(0f)( (s,p) => s + p.getApex.getIntensity )
    
    val lcmsFtPeakelItems = peakels.map { mzDbPeakel =>
      
      // Retrieve cached LC-MS peakel if it exists
      val lcmsPeakel = if( peakelByMzDbPeakel.contains(mzDbPeakel) ) {
        val existingPeakel = peakelByMzDbPeakel(mzDbPeakel)
        
        // Increase features count
        existingPeakel.featuresCount = existingPeakel.featuresCount + 1
        
        existingPeakel
      }
      // Else build new LC-MS peakel
      else {
        
        val lcMsPeaks = mzDbPeakel.definedPeaks.map { p => LcMsPeak(p.getMz(),p.getLcContext().getElutionTime(),p.getIntensity()) }
        val( peakelFirstLcContext, peakelLastLcContext ) = mzDbPeakel.getLcContextRange()
        val peakelApexLcContext = mzDbPeakel.getApexScanContext()
        
        val newPeakel = Peakel(
          id = Peakel.generateNewId(),
          moz = mzDbPeakel.mz,
          elutionTime = mzDbPeakel.getElutionTime(),
          apexIntensity = mzDbPeakel.getApex().getIntensity(),
          area = mzDbPeakel.area,
          duration = mzDbPeakel.duration,
          fwhm = Some( mzDbPeakel.fwhm ),
          isOverlapping = false, // FIXME: determine this value
          featuresCount = 1,
          peaks = lcMsPeaks,
          // FIXME: scanId and scanInitialId may be different in future mzDB configurations
          firstScanId = lcmsScanIdByInitialId(peakelFirstLcContext.getScanId),
          lastScanId = lcmsScanIdByInitialId(peakelLastLcContext.getScanId),
          apexScanId = lcmsScanIdByInitialId(peakelApexLcContext.getScanId),
          rawMapId = rawMapId
        )
        
        // Cache new LC-MS peakel
        peakelByMzDbPeakel(mzDbPeakel) = newPeakel
        
        newPeakel
      }
      
      FeaturePeakelItem(
        featureReference = FeatureIdentifier(ftId),
        peakelReference = lcmsPeakel,
        isotopeIndex = mzDbPeakel.index
      )
    }
    
    val ftProps = FeatureProperties(
      isPredicted = Some(mzDbFt.isPredicted),
      peakelsCount = Some(peakels.length)
    )
    
    new LcMsFeature(
       id = ftId,
       moz = mzDbFt.mz,
       apexIntensity = mzDbFt.getBasePeakel().getApex().getIntensity(),
       intensity = intensitySum2Peakels, //mzDbFt.area,
       charge = mzDbFt.charge,
       elutionTime = mzDbFt.elutionTime,
       duration = ftLastScanH.getElutionTime - ftFirstScanH.getElutionTime,
       qualityScore = mzDbFt.qualityScore,
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