package fr.proline.core.service.lcms.io

import java.io.File
import scala.collection.mutable.ArrayBuffer
import com.weiglewilczek.slf4s.Logging

import fr.profi.jdbc.easy._
import fr.profi.mzdb.MzDbFeatureExtractor
import fr.profi.mzdb.MzDbReader
import fr.profi.mzdb.io.reader.RunSliceDataProvider
import fr.profi.mzdb.model.{ Feature => MzDbFeature }
import fr.profi.mzdb.model.PutativeFeature
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.algo.lcms._
import fr.proline.core.dal.helper.LcmsDbHelper
import fr.proline.core.dal.{ DoJDBCWork, DoJDBCReturningWork }
import fr.proline.core.om.model.lcms.{ Feature => LcMsFeature, IsotopicPattern => LcMsIsotopicPattern }
import fr.proline.core.om.model.lcms._
import fr.proline.core.om.model.msi.Instrument
import fr.proline.core.om.provider.lcms.impl.SQLScanSequenceProvider
import fr.proline.core.om.storer.lcms.MasterMapStorer
import fr.proline.core.om.storer.lcms.ProcessedMapStorer
import fr.proline.core.om.storer.lcms.RunMapStorer
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
    version = "0.1.1",
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
    val processedMapByRunMapId = new collection.mutable.HashMap[Long,ProcessedMap]
    
    for( runMap <- runMaps ) {
      mapNumber += 1
      
      // Convert to processed map
      val processedMap = runMap.toProcessedMap( number = mapNumber, mapSetId = tmpMapSetId )
      
      // Set first map as default alignment reference
      if( mapCount == 1 ) {
        processedMap.isAlnReference = true
        alnRefMapId = processedMap.id
      }
      
      processedMaps(mapNumber-1) = processedMap
      processedMapByRunMapId += runMap.id -> processedMap
    }
    
    val mapSet = new MapSet(
      id = tmpMapSetId,
      name = "",
      creationTimestamp = new java.util.Date,
      childMaps = processedMaps.toArray,
      alnReferenceMapId = alnRefMapId
    )
    
    // TODO: clean maps and perform the feature clustering ???
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
    mapSet.setAlnReferenceMapId(alnResult.alnRefMapId)
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
    val x2ProcessedMaps = new ArrayBuffer[ProcessedMap]
    for( runMap <- runMaps ) {
      val runMapId = runMap.id
      val mzDbFile = mzDbFileByRunMapId(runMapId)
      val lcmsRun = lcmsRunByRunMapId(runMapId)
      val processedMap = processedMapByRunMapId(runMapId)
      val newLcmsFeatures = this._extractMissingFeatures(mzDbFile,lcmsRun,runMap,processedMap,mapSet)      
      
      // Create a new run map with the extracted missing features
      val x2RunMap = runMap.copy( features = runMap.features ++ newLcmsFeatures )
      x2RunMaps += x2RunMap
      
      // Create a processed map for this runMap
      x2ProcessedMaps += processedMap.copy( features = processedMap.features ++ newLcmsFeatures, runMapIdentifiers = Array(x2RunMap) )
    }
    
    // Instantiate a run map storer
    val runMapStorer = RunMapStorer( lcmsDbCtx ) 
    
    // --- Store the run maps ---
    for( x2RunMap <- x2RunMaps ) {
      runMapStorer.storeRunMap(x2RunMap, false)
    }
    
    // --- Create the corresponding map set ---
    val x2MapSet = CreateMapSet( lcmsDbCtx, mapSetName, x2ProcessedMaps )
    
    // Attach the computed master map to the newly created map set
    x2MapSet.masterMap = mapSet.masterMap
    x2MapSet.masterMap.mapSetId = x2MapSet.id
    x2MapSet.masterMap.runMapIdentifiers = x2RunMaps
    
    // --- Update and store the map alignment using processed maps with persisted ids ---
    AlignMapSet(lcmsDbCtx, x2MapSet,alnMethodName, alnParams)
    
    val x2AlnResult = mapAligner.computeMapAlignments( x2ProcessedMaps, alnParams )
    
    // --- Normalize the processed maps ---
    if (normalizationMethod != None && mapSet.childMaps.length > 1) {

      // Instantiate a Cmd for map set normalization
      logger.info("normalizing maps...")
      
      // Updates the normalized intensities
      MapSetNormalizer(normalizationMethod.get).normalizeFeaturesIntensity(mapSet)

      // Update master map feature intensity
      logger.info("updating master map feature data...")
      x2MapSet.masterMap = x2MapSet.masterMap.copy(features = MasterMapBuilder.rebuildMftsUsingBestChild(x2MapSet.masterMap.features))
    }
    
    // --- Store the processed maps features ---
    
    // Instantiate a processed map storer
    val processedMapStorer = ProcessedMapStorer( lcmsDbCtx )
    
    DoJDBCWork.withEzDBC( lcmsDbCtx, { ezDBC =>
      for( processedMap <- x2MapSet.childMaps ) {
        
        // Store the map
        processedMapStorer.storeProcessedMap( processedMap )
        
        // Remember the mapping between temporary map id and persisted map id
        //mapIdByTmpMapId += tmpMapId -> processedMap.id
        
        // Update map set alignment reference map
        if( processedMap.isAlnReference ) {
          logger.info("Set map set alignement reference map to id=" + processedMap.id)
          ezDBC.execute( "UPDATE map_set SET aln_reference_map_id = "+ processedMap.id +" WHERE id = " + mapSet.id )
        }
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
    
    println(x2MapSet.masterMap.features.length)
    
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
        minIntensity = 0., // TODO: compute this value ???
        maxIntensity = 0., // TODO: compute this value ???
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
    val lcmsFeatures = new ArrayBuffer[LcMsFeature](mzDbFts.length)
    for( mzDbFt <- mzDbFts ) {
      if( mzDbFt.area > 0 ) {
        lcmsFeatures += this._mzDbFeatureToLcMsFeature(mzDbFt,runMapId,lcmsRun.scanSequence.get)
      }
    }
    
    // Create a new Run Map
    new RunMap(
      id = runMapId,
      name = lcmsRun.rawFile.name,
      isProcessed = false,
      creationTimestamp = new java.util.Date,
      features = lcmsFeatures.toArray,
      runId = lcmsRun.id,
      peakPickingSoftware = pps
    )
  }
  
  private def _extractMissingFeatures(
    mzDbFile: File,
    lcmsRun: LcMsRun,
    runMap: RunMap,
    processedMap: ProcessedMap,
    mapSet: MapSet
  ): Seq[Feature] = {
    
    val runMapId = runMap.id
    val procMapId = processedMap.id
    val masterMap = mapSet.masterMap

    val mzDb = new MzDbReader( mzDbFile, true )
    var mzDbFts = Seq.empty[MzDbFeature]
    val mftsWithMissingChild = new ArrayBuffer[Feature]
    val missingFtIdByMftId = new collection.mutable.HashMap[Long,Int]()
    val pfs = new ArrayBuffer[PutativeFeature]()
    
    try {
      
      val mzdbFtX = new MzDbFeatureExtractor(mzDb,5,5)
      
      //val scanHeaders = mzDb.getScanHeaders()
      //val ms2ScanHeaders = scanHeaders.filter(_.getMsLevel() == 2 )
      
      this.logger.info("building putative features list using master features...")

      for( mft <- masterMap.features ) {
        
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
    
    val mzDbFtById = Map() ++ mzDbFts.map( ft => ft.id ->ft )   
    
    // Convert mzDB features into LC-MS DB features    
    val newLcmsFeatures = new ArrayBuffer[LcMsFeature](missingFtIdByMftId.size)
    for( mftWithMissingChild <- mftsWithMissingChild;
         mzDbFt <- mzDbFtById.get(missingFtIdByMftId( mftWithMissingChild.id ))
         if mzDbFt.area > 0
       ) {
      
      // Convert the extracted feature into a LC-MS feature
      val newLcmsFt = this._mzDbFeatureToLcMsFeature(mzDbFt,runMapId,lcmsRun.scanSequence.get)
      
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
  
  private def _mzDbFeatureToLcMsFeature( mzDbFt: MzDbFeature, runMapId: Long, scanSeq: LcMsScanSequence ): LcMsFeature = {
    
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
         runMapId = runMapId
       )
    )
  }
  
}