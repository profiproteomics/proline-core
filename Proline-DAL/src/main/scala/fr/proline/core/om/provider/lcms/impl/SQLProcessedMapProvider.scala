package fr.proline.core.om.provider.lcms.impl

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import fr.profi.jdbc.ResultSetRow
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.{ DoJDBCWork, DoJDBCReturningWork }
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.{ SelectQueryBuilder1, SelectQueryBuilder2 }
import fr.proline.core.dal.tables.lcms.{ LcmsDbFeatureTable, LcmsDbProcessedMapFeatureItemTable, LcmsDbFeatureClusterItemTable }
import fr.proline.core.dal.tables.lcms.{ LcmsDbMapTable, LcmsDbProcessedMapTable, LcmsDbProcessedMapRunMapMappingTable, LcmsDbRunMapTable }
import fr.proline.core.om.model.lcms._
import fr.proline.core.om.provider.lcms.IProcessedMapProvider
import fr.proline.util.primitives._
//import fr.proline.util.sql._
  
class SQLProcessedMapProvider(
  val lcmsDbCtx: DatabaseConnectionContext,
  val scans: Array[LcMsScan],
  val loadPeaks: Boolean = false
) extends AbstractSQLLcMsMapProvider with IProcessedMapProvider {
  
  val ProcFtCols = LcmsDbProcessedMapFeatureItemTable.columns
  val ProcMapCols = LcmsDbProcessedMapTable.columns
  
  /** Returns a list of LC-MS maps corresponding to a given list of processed map ids */
  def getLcMsMaps(mapIds: Seq[Long]): Seq[ILcMsMap] = this.getProcessedMaps(mapIds)
  
  /** Returns a list of features corresponding to a given list of processed map ids */
  def getProcessedMaps( processedMapIds: Seq[Long] ): Array[ProcessedMap] = {
    
    val runMapIdsByProcessedMapId = getRunMapIdsByProcessedMapId( processedMapIds )
    val features = this.getFeatures( processedMapIds )
    // Group features by map id
    val featuresByMapId = features.groupBy(_.relations.processedMapId)
    
    val processedMaps = new Array[ProcessedMap](processedMapIds.length)
    var lcmsMapIdx = 0
    
    DoJDBCWork.withEzDBC(lcmsDbCtx, { ezDBC =>
      
      val procMapQuery = new SelectQueryBuilder2(LcmsDbMapTable, LcmsDbProcessedMapTable).mkSelectQuery((t1, c1, t2, c2) =>
        List(t1.*, t2.*) ->
          "WHERE " ~ t2.ID ~ " IN(" ~ processedMapIds.mkString(",") ~ ") " ~
          "AND " ~ t1.ID ~ "=" ~ t2.ID
      )
    
      // Load processed map features
      ezDBC.selectAndProcess( procMapQuery ) { r =>
        
        val processedMapId = toLong(r.getAny(LcMsMapCols.ID))
        val mapFeatures = featuresByMapId( processedMapId )
        val featureScoring = featureScoringById.get(toLong(r.getAny(LcMsMapCols.FEATURE_SCORING_ID)))
        
        // Build the map
        processedMaps(lcmsMapIdx) = new ProcessedMap(
          id = processedMapId,
          name = r.getString(LcMsMapCols.NAME),
          isProcessed = true,
          creationTimestamp = r.getTimestamp(LcMsMapCols.CREATION_TIMESTAMP),
          features = features,
          number = r.getInt(ProcMapCols.NUMBER),
          modificationTimestamp = r.getTimestamp(LcMsMapCols.MODIFICATION_TIMESTAMP),
          isMaster = r.getBoolean(ProcMapCols.IS_MASTER),
          isAlnReference = r.getBoolean(ProcMapCols.IS_ALN_REFERENCE),
          mapSetId = toLong(r.getAny(ProcMapCols.MAP_SET_ID)),
          runMapIdentifiers = runMapIdsByProcessedMapId(processedMapId).map(Identifier(_)),
          description = r.getString(LcMsMapCols.DESCRIPTION),
          featureScoring = featureScoring,
          isLocked = r.getBooleanOrElse(ProcMapCols.IS_LOCKED,false),
          normalizationFactor = toFloat( r.getDouble(ProcMapCols.NORMALIZATION_FACTOR) )
        )
        
        lcmsMapIdx += 1
        
        ()
      }
      
    })
    
    
    processedMaps
    
  }
  
  def getRunMapIdsByProcessedMapId( processedMapIds: Seq[Long] ): Map[Long,Array[Long]] = {
    
    val runMapIdBufferByProcessedMapId = new HashMap[Long,ArrayBuffer[Long]]
    
    DoJDBCWork.withEzDBC(lcmsDbCtx, { ezDBC =>
      
      val mapMappingQuery = new SelectQueryBuilder1(LcmsDbProcessedMapRunMapMappingTable).mkSelectQuery( (t,c) =>
        List(t.*) -> "WHERE "~ t.PROCESSED_MAP_ID ~" IN("~ processedMapIds.mkString(",") ~") "
      )
      
      ezDBC.selectAndProcess( mapMappingQuery ) { r =>
        
        val( processedMapId, runMapId ) = (toLong(r.nextAny), toLong(r.nextAny))
        runMapIdBufferByProcessedMapId.getOrElseUpdate(processedMapId, new ArrayBuffer[Long](1) ) += runMapId
        
      }
      
    })
    
    // Convert the HashMap into an immutable Map
    val mapBuilder = scala.collection.immutable.Map.newBuilder[Long,Array[Long]]
    for( processedMapId <- runMapIdBufferByProcessedMapId.keys ) {
      mapBuilder += ( processedMapId -> runMapIdBufferByProcessedMapId(processedMapId).toArray[Long] )
    }
    mapBuilder.result()
  }
  
  def getProcessedMapRunMapIds( processedMapId: Long ): Array[Long] = {    
    getRunMapIdsByProcessedMapId( Array(processedMapId) )(processedMapId)
  }

  /** Returns a list of features corresponding to a given list of processed map ids */
  def getFeatures( processedMapIds: Seq[Long] ): Array[Feature] = {
 
    DoJDBCReturningWork.withEzDBC(lcmsDbCtx, { ezDBC =>
      
      // Check that provided map ids correspond to run maps
      val nbMaps: Int = ezDBC.selectInt( "SELECT count(id) FROM processed_map WHERE id IN (" + processedMapIds.mkString(",") + ")" )
      if( nbMaps < processedMapIds.length ) {
        throw new Exception("map ids must correspond to existing processed maps")
      }
      
      // --- Load run ids and run map ids
      var( runMapIds, runIds ) = ( new ArrayBuffer[Long](nbMaps), new ArrayBuffer[Long](nbMaps) )
      
      val sqlQuery = new SelectQueryBuilder2(LcmsDbRunMapTable, LcmsDbProcessedMapRunMapMappingTable).mkSelectQuery( (t1,c1,t2,c2) =>
        List(t1.ID,t1.RUN_ID) ->
          "WHERE " ~ t2.PROCESSED_MAP_ID ~ " IN(" ~ processedMapIds.mkString(",") ~ ") " ~
          "AND " ~ t1.ID ~ "=" ~ t2.RUN_MAP_ID
      )
      
      ezDBC.selectAndProcess( sqlQuery ) { r => 
        runMapIds += toLong(r.nextAny)
        runIds += toLong(r.nextAny)
        ()
      }
    
      // Load mapping between scan ids and scan initial ids
      val scanInitialIdById = lcmsDbHelper.getScanInitialIdById( runIds )
      
      // Retrieve mapping between features and MS2 scans
      val ms2EventIdsByFtId = lcmsDbHelper.getMs2EventIdsByFtId( runMapIds )
      
      // TODO: load isotopic patterns if needed
      //val ipsByFtId = if( loadPeaks ) getIsotopicPatternsByFtId( mapIds ) else null
      
      // Retrieve mapping between overlapping features
      val olpFtIdsByFtId = getOverlappingFtIdsByFtId( runMapIds )
      
      var olpFeatureById: Map[Long,Feature] = null
      if( olpFtIdsByFtId.size > 0 ) {
        olpFeatureById = getOverlappingFeatureById( runMapIds, scanInitialIdById, ms2EventIdsByFtId )
      }
      
      // Retrieve mapping between cluster and sub-features
      val subFtIdsByClusterFtId = getSubFtIdsByClusterFtId( processedMapIds )
      
      val ftBuffer = new ArrayBuffer[Feature]
      val subFtById = new java.util.HashMap[Long,Feature]
      
      // Load processed features
      this.eachProcessedFeatureRecord(processedMapIds, processedFtRecord => {
        
        val ftId = toLong(processedFtRecord.getAny(FtCols.ID))
        
        // Try to retrieve overlapping features
        var olpFeatures: Array[Feature] = null
        if( olpFtIdsByFtId contains ftId ) {
          olpFeatures = olpFtIdsByFtId(ftId) map { olpFtId => olpFeatureById(olpFtId) }
        }
        
        // TODO: load isotopic patterns if needed
        // TODO: parse serialized properties
        val feature = this.buildFeature(
          processedFtRecord,
          scanInitialIdById,
          ms2EventIdsByFtId,
          null,
          olpFeatures,
          null,
          null,
          processedFtRecord.getDoubleOption(ProcFtCols.CALIBRATED_MOZ),
          processedFtRecord.getDoubleOption(ProcFtCols.NORMALIZED_INTENSITY).map( _.toFloat ),
          processedFtRecord.getDoubleOption(ProcFtCols.CORRECTED_ELUTION_TIME).map( _.toFloat ),
          processedFtRecord.getBoolean(ProcFtCols.IS_CLUSTERIZED),
          processedFtRecord.getIntOrElse(ProcFtCols.SELECTION_LEVEL,2),
          toLong( processedFtRecord.getAny(ProcFtCols.PROCESSED_MAP_ID) )
        )
                                                
        //var subFeatures: Array[Feature] = null
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
    
    })
    
  }
  
  def eachProcessedFeatureRecord( processedMapIds: Seq[Long], onEachFt: ResultSetRow => Unit ): Unit = {
    
    DoJDBCWork.withEzDBC(lcmsDbCtx, { ezDBC =>
    
      val procFtQuery = new SelectQueryBuilder2(LcmsDbFeatureTable, LcmsDbProcessedMapFeatureItemTable).mkSelectQuery( (t1,c1,t2,c2) =>
        List(t1.*,t2.*) ->
          "WHERE " ~ t2.PROCESSED_MAP_ID ~ " IN(" ~ processedMapIds.mkString(",") ~ ") " ~
          "AND " ~ t1.ID ~ "=" ~ t2.FEATURE_ID
          // "AND is_clusterized = " + BoolToSQLStr(isClusterized,boolStrAsInt)
      )
      
      // Load processed map features
      ezDBC.selectAndProcess( procFtQuery ) { r =>        
        
        // Build the feature record
        onEachFt( r )
        
        ()
      }
    
    })
    
  }
  
  /** Returns a map of sub features feature keyed by its id */
  def getSubFtIdsByClusterFtId( processedMapIds: Seq[Long] ): Map[Long,Array[Long]] = {
    
    val subFtIdBufferByClusterFtId = new HashMap[Long,ArrayBuffer[Long]]
    
    DoJDBCWork.withEzDBC(lcmsDbCtx, { ezDBC =>
      
      val ftClusterRelationQuery = new SelectQueryBuilder1(LcmsDbFeatureClusterItemTable).mkSelectQuery( (t,c) =>
        List(t.CLUSTER_FEATURE_ID,t.SUB_FEATURE_ID) ->
        "WHERE "~ t.PROCESSED_MAP_ID ~" IN("~ processedMapIds.mkString(",") ~") "
      )
      
      ezDBC.selectAndProcess( ftClusterRelationQuery ) { r =>
        
        val( clusterFeatureId, subFeatureId ) = (toLong(r.nextAny), toLong(r.nextAny))
        subFtIdBufferByClusterFtId.getOrElseUpdate(clusterFeatureId, new ArrayBuffer[Long](1)) += subFeatureId
        
        ()
      }
    })
    
    // Convert the HashMap into an immutable Map
    val mapBuilder = scala.collection.immutable.Map.newBuilder[Long,Array[Long]]
    for( clusterFtId <- subFtIdBufferByClusterFtId.keys ) { 
      mapBuilder += ( clusterFtId -> subFtIdBufferByClusterFtId(clusterFtId).toArray[Long] )
    }
    mapBuilder.result()
    
  }
  
}