package fr.proline.core.om.provider.lcms.impl

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap

import fr.profi.jdbc.ResultSetRow
import fr.proline.context.LcMsDbConnectionContext
import fr.proline.core.dal.{ DoJDBCWork, DoJDBCReturningWork }
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.{ SelectQueryBuilder1, SelectQueryBuilder2 }
import fr.proline.core.dal.tables.lcms.{ LcmsDbFeatureTable, LcmsDbProcessedMapFeatureItemTable, LcmsDbFeatureClusterItemTable }
import fr.proline.core.dal.tables.lcms.{ LcmsDbMapTable, LcmsDbProcessedMapTable, LcmsDbProcessedMapRawMapMappingTable, LcmsDbRawMapTable }
import fr.proline.core.om.model.lcms._
import fr.proline.core.om.provider.lcms.IProcessedMapProvider
import fr.profi.util.primitives._
  
class SQLProcessedMapProvider(
  val lcmsDbCtx: LcMsDbConnectionContext
) extends AbstractSQLLcMsMapProvider with IProcessedMapProvider {
  
  val ProcFtCols = LcmsDbProcessedMapFeatureItemTable.columns
  val ProcMapCols = LcmsDbProcessedMapTable.columns
  
  /** Returns a list of LC-MS maps corresponding to a given list of processed map ids */
  def getLcMsMaps(mapIds: Seq[Long], loadPeakels: Boolean): Seq[ILcMsMap] = this.getProcessedMaps(mapIds)
  
  /** Returns a list of features corresponding to a given list of processed map ids */
  def getProcessedMaps( processedMapIds: Seq[Long], loadPeakels: Boolean = false ): Array[ProcessedMap] = {
    if( processedMapIds.isEmpty ) return Array()
    
    // Retrieve raw map ids mapped by processes map id
    val rawMapIdsByProcessedMapId = getRawMapIdsByProcessedMapId( processedMapIds )
    //val rawMapIds = processedMapIds.flatMap( rawMapIdsByProcessedMapId(_) ).distinct
    
    // Load features and group them by map id
    val featuresByProcMapId = this.getFeatures(processedMapIds,loadPeakels).groupBy(_.relations.processedMapId)
    
    // If peakels were loaded group them by processed map id
    /*val peakelsByRawMapId = if( loadPeakels == false ) Map.empty[Long,Array[Peakel]]
    else peakelProvider.getPeakels(rawMapIds).groupBy(_.rawMapId)*/
    val peakelsByProcMapId = if( loadPeakels == false ) Map.empty[Long,Array[Peakel]]
    else {
      featuresByProcMapId.view.map { case(procMapId,features) =>
        procMapId -> features.flatMap( _.relations.peakelItems.map(_.getPeakel.get) )
      } toMap
    }
    
    val processedMaps = new Array[ProcessedMap](processedMapIds.length)
    var lcmsMapIdx = 0
    
    DoJDBCWork.withEzDBC(lcmsDbCtx) { ezDBC =>
      
      val procMapQuery = new SelectQueryBuilder2(LcmsDbMapTable, LcmsDbProcessedMapTable).mkSelectQuery((t1, c1, t2, c2) =>
        List(t1.*, t2.*) ->
          "WHERE " ~ t2.ID ~ " IN(" ~ processedMapIds.mkString(",") ~ ") " ~
          "AND " ~ t1.ID ~ "=" ~ t2.ID
      )
    
      // Load processed map features
      ezDBC.selectAndProcess( procMapQuery ) { r =>
        
        val processedMapId = r.getLong(ProcMapCols.ID.aliasedString)
        val featureScoring = featureScoringById.get(r.getLong(LcMsMapCols.FEATURE_SCORING_ID))
        val peakelsOpt = if( loadPeakels == false ) Option.empty[Array[Peakel]]
        else Some( peakelsByProcMapId(processedMapId) )
        //else Some( rawMapIds.flatMap( rawMapId => peakelsByProcMapId.getOrElse(rawMapId,Array.empty[Peakel]) ) )
        
        // Build the map
        processedMaps(lcmsMapIdx) = new ProcessedMap(
          id = processedMapId,
          name = r.getString(LcMsMapCols.NAME),
          isProcessed = true,
          creationTimestamp = r.getTimestamp(LcMsMapCols.CREATION_TIMESTAMP),
          features = featuresByProcMapId( processedMapId ),
          peakels = peakelsOpt,
          number = r.getInt(ProcMapCols.NUMBER),
          modificationTimestamp = r.getTimestamp(LcMsMapCols.MODIFICATION_TIMESTAMP),
          isMaster = r.getBoolean(ProcMapCols.IS_MASTER),
          isAlnReference = r.getBoolean(ProcMapCols.IS_ALN_REFERENCE),
          mapSetId = r.getLong(ProcMapCols.MAP_SET_ID),
          rawMapReferences = rawMapIdsByProcessedMapId(processedMapId).map(RawMapIdentifier(_)),
          description = r.getString(LcMsMapCols.DESCRIPTION),
          featureScoring = featureScoring,
          isLocked = r.getBooleanOrElse(ProcMapCols.IS_LOCKED,false),
          normalizationFactor = toFloat( r.getDouble(ProcMapCols.NORMALIZATION_FACTOR) )
        )
        
        lcmsMapIdx += 1
        
        ()
      }
    }
    
    processedMaps
    
  }
  
  def getRawMapIdsByProcessedMapId( processedMapIds: Seq[Long] ): Map[Long,Array[Long]] = {
    if( processedMapIds.isEmpty ) return Map()
    
    val rawMapIdBufferByProcessedMapId = new HashMap[Long,ArrayBuffer[Long]]
    
    DoJDBCWork.withEzDBC(lcmsDbCtx) { ezDBC =>
      
      val mapMappingQuery = new SelectQueryBuilder1(LcmsDbProcessedMapRawMapMappingTable).mkSelectQuery( (t,c) =>
        List(t.*) -> "WHERE "~ t.PROCESSED_MAP_ID ~" IN("~ processedMapIds.mkString(",") ~") "
      )
      
      ezDBC.selectAndProcess( mapMappingQuery ) { r =>
        
        val( processedMapId, rawMapId ) = (toLong(r.nextAny), toLong(r.nextAny))
        rawMapIdBufferByProcessedMapId.getOrElseUpdate(processedMapId, new ArrayBuffer[Long](1) ) += rawMapId
        
      }
    }
    
    // Convert the HashMap into an immutable Map
    val mapBuilder = scala.collection.immutable.Map.newBuilder[Long,Array[Long]]
    for( processedMapId <- rawMapIdBufferByProcessedMapId.keys ) {
      mapBuilder += ( processedMapId -> rawMapIdBufferByProcessedMapId(processedMapId).toArray[Long] )
    }
    mapBuilder.result()
  }
  
  def getProcessedMapRunMapIds( processedMapId: Long ): Array[Long] = {
    getRawMapIdsByProcessedMapId( Array(processedMapId) )(processedMapId)
  }

  /** Returns a list of features corresponding to a given list of processed map ids */
  // TODO: rename to getProcessedMapsFeatures
  // TODO: move to Feature provider
  def getFeatures( processedMapIds: Seq[Long], loadPeakels: Boolean = false ): Array[Feature] = {
    if( processedMapIds.isEmpty ) return Array()
 
    DoJDBCReturningWork.withEzDBC(lcmsDbCtx) { ezDBC =>
      
      // Check that provided map ids correspond to run maps
      val nbMaps: Int = ezDBC.selectInt( "SELECT count(id) FROM processed_map WHERE id IN (" + processedMapIds.mkString(",") + ")" )
      if( nbMaps < processedMapIds.length ) {
        throw new Exception("map ids must correspond to existing processed maps")
      }
      
      // --- Load run ids and run map ids
      var( rawMapIds, runIds ) = ( new ArrayBuffer[Long](nbMaps), new ArrayBuffer[Long](nbMaps) )
      
      val sqlQuery = new SelectQueryBuilder2(LcmsDbRawMapTable, LcmsDbProcessedMapRawMapMappingTable).mkSelectQuery( (t1,c1,t2,c2) =>
        List(t1.ID,t1.SCAN_SEQUENCE_ID) ->
          "WHERE " ~ t2.PROCESSED_MAP_ID ~ " IN(" ~ processedMapIds.mkString(",") ~ ") " ~
          "AND " ~ t1.ID ~ "=" ~ t2.RAW_MAP_ID
      )
      
      ezDBC.selectAndProcess( sqlQuery ) { r => 
        rawMapIds += toLong(r.nextAny)
        runIds += toLong(r.nextAny)
        ()
      }
      
      // Remove duplicated ids
      rawMapIds = rawMapIds.distinct
      runIds = runIds.distinct
    
      // Load mapping between scan ids and scan initial ids
      val scanInitialIdById = lcmsDbHelper.getScanInitialIdById( runIds )
      
      // Retrieve mapping between features and MS2 scans
      val ms2EventIdsByFtId = lcmsDbHelper.getMs2EventIdsByFtId( rawMapIds )
      
      // Retrieve mapping between overlapping features
      val olpFtIdsByFtId = getOverlappingFtIdsByFtId( rawMapIds )
      val olpFeatureById = if (olpFtIdsByFtId.isEmpty) Map.empty[Long, Feature]
      else getOverlappingFeatureById(rawMapIds, scanInitialIdById, ms2EventIdsByFtId)
      
      // Load peakel items
      val peakelItems = peakelProvider.getRawMapPeakelItems(rawMapIds, loadPeakels = loadPeakels)
      val peakelItemsByFtId = peakelItems.groupBy(_.featureReference.id)
      
      // Retrieve mapping between cluster and sub-features
      val subFtIdsByClusterFtId = getSubFtIdsByClusterFtId( processedMapIds )
      
      val ftBuffer = new ArrayBuffer[Feature]
      val subFtById = new java.util.HashMap[Long,Feature]
      
      // Load processed features
      this.eachProcessedFeatureRecord(processedMapIds, processedFtRecord => {
        
        val ftId = toLong(processedFtRecord.getAny(FtCols.ID))
        
        // Try to retrieve overlapping features
        val olpFeatures = if (olpFtIdsByFtId.contains(ftId) == false ) null
        else olpFtIdsByFtId(ftId) map { olpFtId => olpFeatureById(olpFtId) }
        
        // Retrieve peakel items
        val peakelItems = peakelItemsByFtId.getOrElse(ftId,Array()).sortBy(_.isotopeIndex)
        
        val feature = this.buildFeature(
          processedFtRecord,
          scanInitialIdById,
          ms2EventIdsByFtId,
          peakelItems,
          olpFeatures,
          null, // subFeatures
          null, // children
          processedFtRecord.getDoubleOption(ProcFtCols.CALIBRATED_MOZ),
          processedFtRecord.getDoubleOption(ProcFtCols.NORMALIZED_INTENSITY).map( _.toFloat ),
          processedFtRecord.getDoubleOption(ProcFtCols.CORRECTED_ELUTION_TIME).map( _.toFloat ),
          processedFtRecord.getBoolean(ProcFtCols.IS_CLUSTERIZED),
          processedFtRecord.getIntOrElse(ProcFtCols.SELECTION_LEVEL,2),
          toLong( processedFtRecord.getAny(ProcFtCols.PROCESSED_MAP_ID) )
        )
        
        if( feature.isClusterized ) { subFtById.put( ftId, feature ) }
        else { ftBuffer += feature }
        
      })
      
      // Link sub-features to loaded features
      val ftArray = new Array[Feature]( ftBuffer.length )
      var ftIndex = 0
      for( ft <- ftBuffer ) {
        
        if( subFtIdsByClusterFtId contains ft.id ) {
          ft.subFeatures = subFtIdsByClusterFtId(ft.id) map { subFtId =>
            val subFt = subFtById.get(subFtId)
            if( subFt == null ) throw new Exception( "can't find a sub-feature with id=" + subFtId )
            subFt
          }
        }
        
        ftArray(ftIndex) = ft
        ftIndex += 1
      }
      
      ftArray
    
    }
    
  }
  
  def eachProcessedFeatureRecord( processedMapIds: Seq[Long], onEachFt: ResultSetRow => Unit ): Unit = {
    if( processedMapIds.isEmpty ) return ()
    
    DoJDBCWork.withEzDBC(lcmsDbCtx) { ezDBC =>
    
      val procFtQuery = new SelectQueryBuilder2(LcmsDbFeatureTable, LcmsDbProcessedMapFeatureItemTable).mkSelectQuery( (t1,c1,t2,c2) =>
        List(t1.*,t2.*) ->
          "WHERE " ~ t2.PROCESSED_MAP_ID ~ " IN (" ~ processedMapIds.mkString(",") ~ ") " ~
          "AND " ~ t1.ID ~ "=" ~ t2.FEATURE_ID
          // "AND is_clusterized = " + BoolToSQLStr(isClusterized,boolStrAsInt)
      )
      
      // Load processed map features
      ezDBC.selectAndProcess( procFtQuery ) { r =>
        
        // Build the feature record
        onEachFt( r )
        
        ()
      }
    
    }
    
  }
  
  /** Returns a map of sub features feature keyed by its id */
  def getSubFtIdsByClusterFtId( processedMapIds: Seq[Long] ): Map[Long,Array[Long]] = {
    if( processedMapIds.isEmpty ) return Map()
    
    val subFtIdBufferByClusterFtId = new HashMap[Long,ArrayBuffer[Long]]
    
    DoJDBCWork.withEzDBC(lcmsDbCtx) { ezDBC =>
      
      val ftClusterRelationQuery = new SelectQueryBuilder1(LcmsDbFeatureClusterItemTable).mkSelectQuery( (t,c) =>
        List(t.CLUSTER_FEATURE_ID,t.SUB_FEATURE_ID) ->
        "WHERE "~ t.PROCESSED_MAP_ID ~" IN ("~ processedMapIds.mkString(",") ~") "
      )
      
      ezDBC.selectAndProcess( ftClusterRelationQuery ) { r =>
        
        val( clusterFeatureId, subFeatureId ) = (toLong(r.nextAny), toLong(r.nextAny))
        subFtIdBufferByClusterFtId.getOrElseUpdate(clusterFeatureId, new ArrayBuffer[Long](1)) += subFeatureId
        
        ()
      }
    }
    
    // Convert the HashMap into an immutable Map
    val mapBuilder = scala.collection.immutable.Map.newBuilder[Long,Array[Long]]
    for( clusterFtId <- subFtIdBufferByClusterFtId.keys ) { 
      mapBuilder += ( clusterFtId -> subFtIdBufferByClusterFtId(clusterFtId).toArray[Long] )
    }
    mapBuilder.result()
    
  }
  
}