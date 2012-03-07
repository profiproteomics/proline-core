package fr.proline.core.om.provider.lcms.impl

import fr.proline.core.LcmsDb

class ProcessedMapLoader( val lcmsDb: LcmsDb,
                          val loadPeaks: Boolean = false ) extends IMapLoader {
  
  import scala.collection.mutable.ArrayBuffer
  import fr.proline.core.utils.sql._
  import fr.proline.core.om.model.lcms._
  
  /** Returns a list of features corresponding to a given list of processed map ids */
  def getMaps( processedMapIds: Seq[Int] ): Array[ProcessedMap] = {
    
    val runMapIdsByProcessedMapId = getRunMapIdsByProcessedMapId( processedMapIds )
    val features = this.getFeatures( processedMapIds )
    // Group features by map id
    val featuresByMapId = features.groupBy(_.mapId)
    
    /*for( mapId <- processedMapIds ) {
      if( !(runMapIdsByProcessedMapId contains mapId) ) println( mapId)
    runMapIdsByProcessedMapId(mapId)
  }*/
    
    val processedMaps = new Array[ProcessedMap](processedMapIds.length)
    
    var colNames: Seq[String] = null
    var lcmsMapIdx = 0
    
    // Load processed map features
    lcmsDbTx.selectAndProcess(
      "SELECT map.*, processed_map.number, processed_map.normalization_factor, processed_map.is_master, "+
      "processed_map.is_aln_reference, processed_map.is_locked, processed_map.map_set_id " +
      "FROM processed_map, map " + 
      "WHERE processed_map.id IN (" + processedMapIds.mkString(",")+") " +
      //"AND processed_map_run_map.processed_map_id = processed_map.id " +
      "AND map.id = processed_map.id " ) { r =>
        
      if( colNames == null ) { colNames = r.columnNames }
      
      // Build the map record
      val mapRecord = colNames.map( colName => ( colName -> r.nextObject.get ) ).toMap
      
      val mapId = mapRecord("id").asInstanceOf[Int]
      val mapFeatures = featuresByMapId( mapId )
      val featureScoring = featureScoringById.getOrElse(mapRecord("feature_scoring_id").asInstanceOf[Int],null)
    
      // Build the map
      processedMaps(lcmsMapIdx) = buildProcessedMap(mapRecord, mapFeatures,
                                                    runMapIdsByProcessedMapId.getOrElse(mapId,null), featureScoring )
      lcmsMapIdx += 1
      
      ()
    }
    
    
    processedMaps
    
  }
  
  def getRunMapIdsByProcessedMapId( processedMapIds: Seq[Int] ): Map[Int,Array[Int]] = {
    
    val runMapIdBufferByProcessedMapId = new java.util.HashMap[Int,ArrayBuffer[Int]]
    
    lcmsDbTx.selectAndProcess( 
      "SELECT processed_map_run_map.processed_map_id, processed_map_run_map.run_map_id "+
      "FROM processed_map_run_map WHERE processed_map_run_map.processed_map_id "+ // TODO : add _mapping suffix
      "IN (" + processedMapIds.mkString(",") + ")" ) { r =>
                   
      val( processedMapId, runMapId ) = (r.nextInt.get, r.nextInt.get) 
      if( !runMapIdBufferByProcessedMapId.containsKey(processedMapId) ) {
        runMapIdBufferByProcessedMapId.put(processedMapId, new ArrayBuffer[Int](1) )
      }
      runMapIdBufferByProcessedMapId.get(processedMapId) += runMapId
      
    }
    
    // Convert the HashMap into an immutable Map
    val mapBuilder = scala.collection.immutable.Map.newBuilder[Int,Array[Int]]
    for( processedMapId <- runMapIdBufferByProcessedMapId.keySet().toArray() ) { 
      mapBuilder += ( processedMapId.asInstanceOf[Int] -> runMapIdBufferByProcessedMapId.get(processedMapId).toArray[Int] )
    }    
    mapBuilder.result()
  }
  
  def getProcessedMapRunMapIds( processedMapId: Int ): Array[Int] = {    
    getRunMapIdsByProcessedMapId( Array(processedMapId) )(processedMapId)
  }
  
  def buildProcessedMap( mapRecord: Map[String,Any], features: Array[Feature], 
                         runMapIds: Array[Int], featureScoring: FeatureScoring ): ProcessedMap = {
    
    import java.util.Date
    new ProcessedMap(  id = mapRecord("id").asInstanceOf[Int],
                       name = mapRecord("name").asInstanceOf[String],
                       isProcessed = true,
                       creationTimestamp = new Date(), // TODO: parse date
                       features = features,
                       number = mapRecord("number").asInstanceOf[Int],
                       modificationTimestamp = new Date(), // TODO: parse date
                       isMaster = SQLStrToBool( mapRecord("is_master").toString() ),
                       isAlnReference = SQLStrToBool( mapRecord("is_aln_reference").toString() ),
                       mapSetId = mapRecord("map_set_id").asInstanceOf[Int],
                       runMapIds = runMapIds,
                       description = mapRecord("description").asInstanceOf[String],
                       featureScoring = featureScoring,
                       isLocked = false,//SQLStrToBool( mapRecord.getOrElse("is_locked","false").toString() ),
                       normalizationFactor = mapRecord("normalization_factor").asInstanceOf[Double].toFloat
                     )
  } 


  /** Returns a list of features corresponding to a given list of processed map ids */
  def getFeatures( processedMapIds: Seq[Int] ): Array[Feature] = {
 
    // Check that provided map ids correspond to run maps
    val nbMaps: Int = lcmsDbTx.selectInt( "SELECT count(*) FROM processed_map WHERE id IN (" + processedMapIds.mkString(",") + ")" )
    if( nbMaps < processedMapIds.length ) {
      throw new Exception("map ids must correspond to existing processed maps");
    }
    
    // Load run ids and run map ids
    var( runMapIds, runIds ) = ( new ArrayBuffer[Int](nbMaps), new ArrayBuffer[Int](nbMaps) )
    
    lcmsDbTx.selectAndProcess(
      "SELECT run_map.id, run_map.run_id FROM processed_map_run_map, run_map " +
      "WHERE processed_map_run_map.processed_map_id IN (" + processedMapIds.mkString(",") + ") " +
      "AND processed_map_run_map.run_map_id = run_map.id" ) { r => 
        runMapIds += r.nextInt.get
        runIds += r.nextInt.get                   
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
    
    var olpFeatureById: Map[Int,Feature] = null
    if( olpFtIdsByFtId.size > 0 ) {
      olpFeatureById = getOverlappingFeatureById( runMapIds, scanInitialIdById, ms2EventIdsByFtId )
    }
    
    // Retrieve mapping between cluster and sub-features
    val subFtIdsByClusterFtId = getSubFtIdsByClusterFtId( processedMapIds )
    /*if( !(subFtIdsByClusterFtId contains 311551) ) {
      error("test")
    }*/
    
    val ftBuffer = new ArrayBuffer[Feature]
    val subFtById = new java.util.HashMap[Int,Feature]
    
    // Load processed features
    eachProcessedFeatureRecord(processedMapIds, processedFtRecord => {
      
      val ftId = processedFtRecord("id").asInstanceOf[Int]
      //if( ftId == 311551 ) error("found")
        
      // Try to retrieve overlapping features
      var olpFeatures: Array[Feature] = null
      if( olpFtIdsByFtId contains ftId ) {
        olpFeatures = olpFtIdsByFtId(ftId) map { olpFtId => olpFeatureById(olpFtId) }
      }
      
      // TODO: load isotopic patterns if needed
      
      val feature = buildProcessedMapFeature( processedFtRecord,
                                              scanInitialIdById,
                                              ms2EventIdsByFtId,
                                              null,
                                              olpFeatures,
                                              null
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
    
  }
  
  def eachProcessedFeatureRecord( processedMapIds: Seq[Int], onEachFt: Map[String,Any] => Unit ): Unit = {
    
    var processedFtColNames: Seq[String] = null
    
    // TODO: handle conflicting column names (serialized_properties)
    
    // Load processed map features
    lcmsDbTx.selectAndProcess( "SELECT * FROM feature, processed_map_feature_item "+
                               "WHERE processed_map_feature_item.processed_map_id IN (" + processedMapIds.mkString(",")+") "+
                               "AND feature.id = processed_map_feature_item.feature_id " ) { r =>
      // "AND is_clusterized = " + BoolToSQLStr(isClusterized,boolStrAsInt)
      
      if( processedFtColNames == null ) { processedFtColNames = r.columnNames }
      
      // Build the feature record
      val processedFtRecord = processedFtColNames.map( colName => ( colName -> r.nextObject.get ) ).toMap
      onEachFt( processedFtRecord )
      
      ()
    }
    
  }
  
  /** Returns a map of sub features feature keyed by its id */
  def getSubFtIdsByClusterFtId( processedMapIds: Seq[Int] ): Map[Int,Array[Int]] = {
    
    val subFtIdBufferByClusterFtId = new java.util.HashMap[Int,ArrayBuffer[Int]]
    lcmsDbTx.selectAndProcess( "SELECT cluster_feature_id, sub_feature_id FROM feature_cluster_item "+
                               "WHERE processed_map_id IN (" + processedMapIds.mkString(",")+ ")" ) { r =>
        
      val( clusterFeatureId, subFeatureId ) = (r.nextInt.get, r.nextInt.get) 
      if( !subFtIdBufferByClusterFtId.containsKey(clusterFeatureId) ) {
        subFtIdBufferByClusterFtId.put(clusterFeatureId, new ArrayBuffer[Int](1) )
      }
      subFtIdBufferByClusterFtId.get(clusterFeatureId) += subFeatureId
      ()
    }
    
    // Convert the HashMap into an immutable Map
    val mapBuilder = scala.collection.immutable.Map.newBuilder[Int,Array[Int]]
    for( clusterFtId <- subFtIdBufferByClusterFtId.keySet().toArray() ) { 
      mapBuilder += ( clusterFtId.asInstanceOf[Int] -> subFtIdBufferByClusterFtId.get(clusterFtId).toArray[Int] )
    }
    mapBuilder.result()
    
  }
  
  /** Builds a feature object coming form a processed map */
  def buildProcessedMapFeature( processedMapFeatureRecord: Map[String,Any],
                                scanInitialIdById: Map[Int,Int],
                                ms2EventIdsByFtId: Map[Int,Array[Int]],
                                isotopicPatterns: Option[Array[IsotopicPattern]],
                                overlappingFeatures: Array[Feature],
                                subFeatures: Array[Feature],
                                children: Array[Feature] = null
                                ): Feature = {
    
    buildFeature( processedMapFeatureRecord,
                  scanInitialIdById,
                  ms2EventIdsByFtId,
                  isotopicPatterns,
                  overlappingFeatures,
                  processedMapFeatureRecord("processed_map_id").asInstanceOf[Int],
                  subFeatures,
                  children,
                  processedMapFeatureRecord.getOrElse("calibrated_moz",Double.NaN).asInstanceOf[Double],
                  processedMapFeatureRecord.getOrElse("normalized_intensity",Double.NaN).asInstanceOf[Double],
                  processedMapFeatureRecord.getOrElse("corrected_elution_time",Float.NaN).asInstanceOf[Double].toFloat,
                  SQLStrToBool(processedMapFeatureRecord("is_clusterized").toString()),
                  processedMapFeatureRecord.getOrElse("selection_level",2).asInstanceOf[Int]
                  )
    
  }
  
}