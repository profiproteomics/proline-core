package fr.proline.core.service.lcms.io

import java.io.File
import scala.collection.mutable.ArrayBuffer
import com.weiglewilczek.slf4s.Logging
import fr.profi.mzdb.MzDbFeatureExtractor
import fr.profi.mzdb.MzDbReader
import fr.profi.mzdb.io.reader.RunSliceDataProvider
import fr.profi.mzdb.model.{ Feature => MzDbFeature }
import fr.profi.mzdb.model.PutativeFeature
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.algo.lcms._
import fr.proline.core.dal.helper.LcmsDbHelper
import fr.proline.core.om.model.lcms.{ Feature => LcMsFeature, IsotopicPattern => LcMsIsotopicPattern }
import fr.proline.core.om.model.lcms._
import fr.proline.core.om.model.msi.Instrument
import fr.proline.core.om.provider.lcms.impl.SQLScanSequenceProvider
import fr.proline.core.om.storer.lcms.impl.SQLScanSequenceStorer
import fr.proline.core.om.storer.lcms.RunMapStorer
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
  val quantConfig: ILcMsQuantConfig  
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
    version = "0.0.9",
    algorithm = "ExtractMapSet"
  )
  
  var extractedMapSet: MapSet = null
  
  def runService(): Boolean = {
    
    // Check if a transaction is already initiated
    val wasInTransaction = lcmsDbCtx.isInTransaction()
    if( !wasInTransaction ) lcmsDbCtx.beginTransaction()
    
    // --- Extract run maps ---
    val lcmsRunByRunMapId = new collection.mutable.HashMap[Long,LcMsRun]
    val mzDbFileByRunMapId = new collection.mutable.HashMap[Long,File]
    val runMaps = new ArrayBuffer[RunMap]
    for( val lcmsRun <- lcMsRuns ) {
      
      val mzDbFilePath = lcmsRun.rawFile.properties.get.getMzdbFilePath
      val mzDbFile = new File(mzDbFilePath)
      require( mzDbFile.exists, "mzdb file can't be found at: "+ mzDbFilePath)
      
      //val mzDb = new MzDbReader( mzDbFile, true )
      //val scanH = mzDb.getScanHeaderForTime(1755.0521f, 1)
      //println( "scan time ="+ scanH.getTime )
      
      if( lcmsRun.scanSequence.isEmpty ) {
        // Retrieve the corresponding LC-MS run
        // Or store it in the database if it doesn't exist
        lcmsRun.scanSequence = Some( this._fetchOrStoreScanSequence(lcmsRun,mzDbFile) )
      }
      
      // Extract LC-MS map from the mzDB file
      val runMap = this._extractRunMapUsingMs2Events(lcmsRun,mzDbFile)
      runMaps += runMap
      
      lcmsRunByRunMapId += runMap.id -> lcmsRun
      mzDbFileByRunMapId += runMap.id -> mzDbFile
    
    }
    //sys.exit()
    
    // --- Create an in-memory map set ---
    val tmpMapSetId = MapSet.generateNewId()
    val mapCount = runMaps.length
    var mapNumber = 0
    var alnRefMapId: Long = 0L
    val processedMaps = new Array[ProcessedMap](runMaps.length)
    
    for( runMap <- runMaps ) {
      mapNumber += 1
      
      // Convert to processed map
      var processedMap = runMap.toProcessedMap( id = runMap.id, number = mapNumber, mapSetId = tmpMapSetId )
      
      // Set first map as default alignment reference
      if( mapCount == 1 ) {
        processedMap.isAlnReference = true
        alnRefMapId = processedMap.id
      }
      
      processedMaps(mapNumber-1) = processedMap
    }
    
    val mapSet = new MapSet(
      id = tmpMapSetId,
      name = "",
      creationTimestamp = new java.util.Date,
      childMaps = processedMaps.toArray,
      alnReferenceMapId = alnRefMapId
    )
    
    // TODO: perform the feature clustering ???
    val childMapsWithoutClusters = mapSet.childMaps
    
    // --- Perform the LC-MS maps alignment ---
    val mapAligner = LcmsMapAligner( methodName = alnMethodName )
    val alnResult = mapAligner.computeMapAlignments( childMapsWithoutClusters, alnParams )
    
    /*for( as <- alnResult.mapAlnSets; a <- as.mapAlignments  ) {
      println("map aln from "+as.refMapId+" to " +as.targetMapId)
      for( t <- a.timeList)
        println(t)
    }*/
    
    // Update MapSet attributes
    mapSet.alnReferenceMapId = alnResult.alnRefMapId
    mapSet.mapAlnSets = alnResult.mapAlnSets
    
    // --- Build a temporary Master Map ---
    mapSet.masterMap = MasterMapBuilder.buildMasterMap(mapSet, masterFtFilter, ftMappingParams)
    
    // --- Retrieve the map alignements ---
    /*val refMapAlnSetByMapId = mapSet.getRefMapAlnSetByMapId.get
    val reversedRefMapAlnSetByMapId = Map() ++ refMapAlnSetByMapId.values.map { alnSet =>
      val revAlnSet = alnSet.getReversedAlnSet
      revAlnSet.refMapId -> revAlnSet
    }*/
    
    // --- Extract LC-MS missing features in all raw files ---
    val x2RunMaps = new ArrayBuffer[RunMap]
    for( (mzDbMapId,mzDbFile) <- mzDbFileByRunMapId ) {      
      val lcmsRun = lcmsRunByRunMapId(mzDbMapId)
      x2RunMaps += this._extractRunMapUsingMasterMap(mzDbFile,mzDbMapId,lcmsRun,mapSet)
    }
    
    // Instantiate a run map storer
    val runMapStorer = RunMapStorer( lcmsDbCtx ) 
    
    // --- Store the run maps ---
    for( x2RunMap <- x2RunMaps ) {
      runMapStorer.storeRunMap(x2RunMap, false)
    }
    
    // --- Create the corresponding map set ---
    val x2MapSet = CreateMapSet( lcmsDbCtx, mapSetName, x2RunMaps, clusteringParams )
    
    // --- Align the map set and store the alignment ---
    AlignMapSet(
      lcmsDbCtx = lcmsDbCtx,
      mapSet = x2MapSet, 
      alnMethodName = alnMethodName,
      alnParams = alnParams
    )
    
    // --- Create and store the master map and the associated processed maps ---
    CreateMasterMap(
      lcmsDbCtx = lcmsDbCtx,
      mapSet = x2MapSet, 
      masterFtFilter = masterFtFilter,
      ftMappingParams = ftMappingParams,
      normalizationMethod = normalizationMethod
    )
    
    // Commit transaction if it was initiated locally
    if( !wasInTransaction ) lcmsDbCtx.commitTransaction()
    
    this.extractedMapSet = x2MapSet
    
    true
  }
  
  private def _fetchOrStoreScanSequence( lcmsRun: LcMsRun, mzDbFile: File ): LcMsScanSequence = {
    
    val mzDbFileDir = mzDbFile.getParent()
    val mzDbFileName = mzDbFile.getName()
    // FIXME: it should be retrieved from the mzDB file meta-data
    val rawFileName = mzDbFileName.take(1 + mzDbFileName.lastIndexOf("."))
    
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
        id = lcmsRun.id,
        rawFileName = rawFileName,
        minIntensity = 0., // TODO: compute this value ???
        maxIntensity = 0., // TODO: compute this value ???
        ms1ScansCount = ms1ScansCount,
        ms2ScansCount = ms2ScansCount,
        instrumentId = lcmsRun.rawFile.instrument.map(_.id),
        scans = scans
      )
      
      val scanSeqStorer = new SQLScanSequenceStorer(lcmsDbCtx)    
      scanSeqStorer.storeScanSequence(scanSeq)
      
      // Close the mzDB file
      mzDb.close()
      
      scanSeq
    }
    
  }
  
  private def _extractRunMapUsingMs2Events( lcmsRun: LcMsRun, mzDbFile: File ): RunMap = {
    
    val mzDb = new MzDbReader( mzDbFile, true )
    var mzDbFts: Seq[MzDbFeature] = null
    
    try {
      
      val mzdbFtX = new MzDbFeatureExtractor(mzDb,5,5)
      
      this.logger.info("retrieve scan headers...")
      val scanHeaders = mzDb.getScanHeaders()
      val ms2ScanHeaders = scanHeaders.filter(_.getMsLevel() == 2 )
      val pfs = new ArrayBuffer[PutativeFeature](ms2ScanHeaders.length)
      
      this.logger.info("building putative features list from MS2 scan events...")

      for( scanH <- ms2ScanHeaders ) {
        pfs += new PutativeFeature(
          id = PutativeFeature.generateNewId,
          mz = scanH.getPrecursorMz,
          charge =scanH.getPrecursorCharge,
          scanId = scanH.getId,
          evidenceMsLevel = 2
        )
      }
      
      // Instantiates a Run Slice Data provider
      val rsdProv = new RunSliceDataProvider( mzDb.getRunSliceIterator(1) )
      
      // Extract features
      mzDbFts = mzdbFtX.extractFeatures(rsdProv, pfs, mozTolPPM)      

    }
    finally {
      mzDb.close()
    }
    
    val runMapId = RunMap.generateNewId()
    
    // Convert mzDB features into LC-MS DB features
    val lcmsFeatures = new Array[LcMsFeature](mzDbFts.length)
    for( ftIdx <- 0 until mzDbFts.length ) {
      lcmsFeatures(ftIdx) = this._mzDbFeatureToLcMsFeature(mzDbFts(ftIdx),runMapId,lcmsRun.scanSequence.get)
    }
    
    // Create a new Run Map
    new RunMap(
      id = runMapId,
      name = lcmsRun.rawFile.name,
      isProcessed = false,
      creationTimestamp = new java.util.Date,
      features = lcmsFeatures,
      runId = lcmsRun.id,
      peakPickingSoftware = pps
    )
  }
  
  private def _extractRunMapUsingMasterMap(
    mzDbFile: File,
    mzDbMapId: Long,
    lcmsRun: LcMsRun,
    mapSet: MapSet
  ): RunMap = {
    
    val masterMap = mapSet.masterMap

    val mzDb = new MzDbReader( mzDbFile, true )
    var mzDbFts: Seq[MzDbFeature] = null
    val lcmsFeatures = new ArrayBuffer[LcMsFeature](masterMap.features.length)
    
    try {
      
      val mzdbFtX = new MzDbFeatureExtractor(mzDb,5,5)
      
      //val scanHeaders = mzDb.getScanHeaders()
      //val ms2ScanHeaders = scanHeaders.filter(_.getMsLevel() == 2 )
      val pfs = new ArrayBuffer[PutativeFeature]()
      
      this.logger.info("building putative features list using master features...")

      for( mft <- masterMap.features ) {
        
        val childFtOpt = mft.children.find( _.relations.mapId == mzDbMapId )
        if( childFtOpt.isDefined ) {
          // Very important because otherwise it could corrupt the corrected elution time
          lcmsFeatures += childFtOpt.get.toRunMapFeature
          
          /*
          pfs += new PutativeFeature(
            id = PutativeFeature.generateNewId,
            mz = childFt.moz,
            charge = childFt.charge,
            scanId = childFt.relations.apexScanId,
            evidenceMsLevel = 2
          )*/
        }
        else {
          val bestChildMapId = mft.relations.bestChildMapId
          val bestChild = mft.children.find( _.relations.mapId == bestChildMapId ).get
          //val childMapAlnSet = revRefMapAlnSetByMapId(bestChildMapId)
          //val predictedTime = childMapAlnSet.calcReferenceElutionTime(mft.elutionTime, mft.mass)
          
          
          var predictedTime = mapSet.convertElutionTime(mft.elutionTime, bestChildMapId, mzDbMapId)
          //println( "ftTime="+ mft.elutionTime +" and predicted time (in "+mzDbMapId+")="+predictedTime)
          
          // Fix negative predicted times
          if( predictedTime <= 0 ) predictedTime = 1f
          
          val pf = new PutativeFeature(
            id = PutativeFeature.generateNewId,
            mz = mft.moz,
            charge = mft.charge,
            elutionTime = predictedTime,
            evidenceMsLevel = 2
          )            
          pf.isPredicted = true
          
          pfs += pf

        }
      }
      
      // Instantiates a Run Slice Data provider
      val rsdProv = new RunSliceDataProvider( mzDb.getRunSliceIterator(1) )
      
      // Extract features
      mzDbFts = mzdbFtX.extractFeatures(rsdProv, pfs, mozTolPPM)

    }
    finally {
      mzDb.close()
    }
    
    val runMapId = RunMap.generateNewId()
    
    // Convert mzDB features into LC-MS DB features    
    for( mzDbFt <- mzDbFts ) {
      lcmsFeatures += this._mzDbFeatureToLcMsFeature(mzDbFt,runMapId,lcmsRun.scanSequence.get)
    }
    
    // Create a new Run Map
    new RunMap(
      id = runMapId,
      name = lcmsRun.rawFile.name,
      isProcessed = false,
      creationTimestamp = new java.util.Date,
      features = lcmsFeatures.toArray.distinct, // Some FTs may be in multiple master FTs
      runId = lcmsRun.id,
      peakPickingSoftware = pps
    )
  }
  
  private def _mzDbFeatureToLcMsFeature( mzDbFt: MzDbFeature, lcmsMapId: Long, scanSeq: LcMsScanSequence ): LcMsFeature = {
    
    // Convert isotopic patterns
    /*val ips = mzDbFt.getIsotopicPatterns.map { mzDbIp =>
      new LcMsIsotopicPattern(
        moz = mzDbIp.mz,
        intensity = mzDbIp.intensity,
        charge = mzDbIp.charge,
        scanInitialId = mzDbIp.scanInitialId
      )
    } toArray*/
    
    // Retrieve some vars
    val lcmsScanIdByInitialId = scanSeq.scanIdByInitialId
    val scanHeaders = mzDbFt.scanHeaders
    val( firstScanH, lastScanH ) = (scanHeaders.head,scanHeaders.last)
    val firstScanInitialId = firstScanH.getInitialId
    val lastScanInitialId = lastScanH.getInitialId
    val apexScanInitialId = mzDbFt.apexScanHeader.getInitialId
    val firstLcMsScanId = lcmsScanIdByInitialId(firstScanInitialId)
    val lastLcMsScanId = lcmsScanIdByInitialId(lastScanInitialId)
    val apexLcMsScanId = lcmsScanIdByInitialId(apexScanInitialId)
    val ms2EventIds = mzDbFt.getMs2ScanIds.map( lcmsScanIdByInitialId(_) )
    
    
    new LcMsFeature(
       id = mzDbFt.id,
       moz = mzDbFt.mz,
       intensity = mzDbFt.area,
       charge = mzDbFt.charge,
       elutionTime = mzDbFt.elutionTime,
       duration = lastScanH.getElutionTime - firstScanH.getElutionTime,
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
       //properties = null, // TODO: set properties
       relations = new FeatureRelations(
         firstScanInitialId = firstScanInitialId,
         lastScanInitialId = lastScanInitialId,
         apexScanInitialId = apexScanInitialId,
         ms2EventIds = ms2EventIds,
         firstScanId = firstLcMsScanId,
         lastScanId = lastLcMsScanId,
         apexScanId = apexLcMsScanId,
         mapId = lcmsMapId
       )
    )
  }
  
}