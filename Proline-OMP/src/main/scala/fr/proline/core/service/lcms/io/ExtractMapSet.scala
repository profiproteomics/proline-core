package fr.proline.core.service.lcms.io

import java.io.File
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import com.typesafe.scalalogging.slf4j.Logging

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
    
    // Check if a transaction is already initiated
    val wasInTransaction = lcmsDbCtx.isInTransaction()
    if( !wasInTransaction ) lcmsDbCtx.beginTransaction()
    
    // --- Extract run maps and convert them to processed maps ---
    val lcmsRunByRawMapId = new collection.mutable.HashMap[Long,LcMsRun]
    val mzDbFileByRawMapId = new collection.mutable.HashMap[Long,File]
    val mapCount = lcMsRuns.length
    val processedMaps = new Array[ProcessedMap](mapCount)
    //val processedMapByRawMapId = new collection.mutable.HashMap[Long,ProcessedMap]
    val tmpMapSetId = MapSet.generateNewId()    
    var mapIdx = 0
    var alnRefMapId: Long = 0L
    
    for( lcmsRun <- lcMsRuns ) {
      
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
      val rawMap = this._extractRawMapUsingMs2Events(lcmsRun,mzDbFile)
      //rawMap.toTsvFile("D:/proline/data/test/quanti/debug/run_map_"+ (-rawMap.id) +".tsv")
      
      // Update some mappings
      lcmsRunByRawMapId += rawMap.id -> lcmsRun
      mzDbFileByRawMapId += rawMap.id -> mzDbFile
    
      // Convert to processed map
      val processedMap = rawMap.toProcessedMap( number = mapIdx + 1, mapSetId = tmpMapSetId )
      
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
      childMaps = processedMaps.toArray,
      alnReferenceMapId = alnRefMapId
    )
    
    // --- Perform the LC-MS maps alignment ---
    // TODO: do we need to remove the clusters for the alignment ???
    val mapAligner = LcmsMapAligner( methodName = alnMethodName )
    val alnResult = mapAligner.computeMapAlignments( mapSet.childMaps, alnParams )
    
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
    
    // --- Extract LC-MS missing features in all raw files ---
    val x2RawMaps = new ArrayBuffer[RawMap]
    val x2ProcessedMaps = new ArrayBuffer[ProcessedMap]
    for( processedMap <- mapSet.childMaps ) {
      val rawMap = processedMap.getRawMaps().head.get
      val rawMapId = rawMap.id
      val mzDbFile = mzDbFileByRawMapId(rawMapId)
      val lcmsRun = lcmsRunByRawMapId(rawMapId)
      val newLcmsFeatures = this._extractMissingFeatures(mzDbFile,lcmsRun,processedMap,mapSet)      
      
      // Create a new run map with the extracted missing features
      val x2RawMap = rawMap.copy( features = rawMap.features ++ newLcmsFeatures )
      x2RawMaps += x2RawMap
      
      // Create a processed map for this rawMap
      x2ProcessedMaps += processedMap.copy( features = processedMap.features ++ newLcmsFeatures, rawMapIdentifiers = Array(x2RawMap) )
    }
    //mapSet.toTsvFile("D:/proline/data/test/quanti/debug/master_map_no_missing_"+ (-mapSet.masterMap.id) +".tsv")
    
    // Instantiate a run map storer
    val rawMapStorer = RawMapStorer( lcmsDbCtx )
    
    // --- Store the run maps ---
    for( x2RawMap <- x2RawMaps ) {
      rawMapStorer.storeRawMap(x2RawMap, false)
    }
    
    // --- Create the corresponding map set ---
    val x2MapSet = CreateMapSet( lcmsDbCtx, mapSetName, x2ProcessedMaps )
    
    // Attach the computed master map to the newly created map set
    x2MapSet.masterMap = mapSet.masterMap
    x2MapSet.masterMap.mapSetId = x2MapSet.id
    x2MapSet.masterMap.rawMapIdentifiers = x2RawMaps
    
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
  
  private def _extractRawMapUsingMs2Events( lcmsRun: LcMsRun, mzDbFile: File ): RawMap = {
    
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
    
    val rawMapId = RawMap.generateNewId()
    
    // Convert mzDB features into LC-MS DB features
    val lcmsFeatures = new ArrayBuffer[LcMsFeature](mzDbFts.length)
    for( mzDbFt <- mzDbFts ) {
      // Keep only features with defined area and at least two data points
      if( mzDbFt.area > 0 && mzDbFt.scanHeaders.length > 1 ) {
        val lcmsFt = this._mzDbFeatureToLcMsFeature(mzDbFt,rawMapId,lcmsRun.scanSequence.get)
        //if( lcmsFt.duration == 0 ) error( ""+scala.runtime.ScalaRunTime.stringOf(mzDbFt.scanHeaders) )
        lcmsFeatures += lcmsFt
      }
    }
    
    // Create a new Run Map
    new RawMap(
      id = rawMapId,
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
    processedMap: ProcessedMap,
    mapSet: MapSet
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
      
      val mzdbFtX = new MzDbFeatureExtractor(mzDb,5,5)
      
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
      val newLcmsFt = this._mzDbFeatureToLcMsFeature(mzDbFt,rawMapId,lcmsRun.scanSequence.get)
      
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
              ft.selectionLevel = 1
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
                  tmpFtCluster.selectionLevel = 1
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
  
  private def _mzDbFeatureToLcMsFeature( mzDbFt: MzDbFeature, rawMapId: Long, scanSeq: LcMsScanSequence ): LcMsFeature = {
    
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
       id = LcMsFeature.generateNewId,
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
         rawMapId = rawMapId
       )
    )
  }
  
}