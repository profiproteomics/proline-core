package fr.proline.core.service.lcms

import fr.profi.jdbc.easy._

import fr.profi.api.service.IService
import fr.proline.context.LcMsDbConnectionContext
import fr.proline.core.algo.lcms.ClusterizeFeatures
import fr.proline.core.algo.lcms.ClusteringParams
import fr.proline.core.dal.{ DoJDBCWork, DoJDBCReturningWork }
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.{ SelectQueryBuilder1 }
import fr.proline.core.dal.tables.lcms.LcmsDbFeatureClusterItemTable
import fr.proline.core.dal.tables.lcms.LcmsDbRawMapTable
import fr.proline.core.om.model.lcms._
import fr.proline.core.om.provider.lcms.impl.SQLScanSequenceProvider
import fr.proline.core.om.storer.lcms.ProcessedMapStorer
import fr.proline.repository.IDatabaseConnector

object ClusterizeMapFeatures {

  def apply(lcmsDbCtx: LcMsDbConnectionContext, lcmsMap: ProcessedMap, params: ClusteringParams): Array[Feature] = {

    val mapCleaner = new ClusterizeMapFeatures(lcmsDbCtx, lcmsMap, params)
    mapCleaner.runService()
    mapCleaner.getFeaturesWithClusters

  }

}

class ClusterizeMapFeatures(val lcmsDbCtx: LcMsDbConnectionContext, lcmsMap: ProcessedMap, params: ClusteringParams) extends ILcMsService {
  
  //val ezDBC = lcmsQueryHelper.ezDBC
  //val inExprLimit = ezDBC.getInExpressionCountLimit
  var featuresWithClusters: Array[Feature] = null

  def getFeaturesWithClusters = featuresWithClusters

  def runService(): Boolean = {

    // Make some requirements
    require(lcmsMap.isProcessed, "the map must be a processed map")

    val rawMapIds = lcmsMap.getRawMapIds
    require(rawMapIds.length == 1, "the processed map must correspond to a unique run map")

    // Check if a transaction is already initiated
    val wasInTransaction = lcmsDbCtx.isInTransaction()
    if (!wasInTransaction) lcmsDbCtx.beginTransaction()

    // Define some vars
    val processedMapId = lcmsMap.id
    val rawMapId = rawMapIds(0)
    
    DoJDBCWork.withEzDBC( lcmsDbCtx) { ezDBC =>
      
      val inExprLimit = ezDBC.getInExpressionCountLimit
      
      // Retrieve run id corresponding to run map id
      val rawMapScanSeqIdQuery = new SelectQueryBuilder1(LcmsDbRawMapTable).mkSelectQuery( (t,c) =>
        List(t.SCAN_SEQUENCE_ID) -> "WHERE "~ t.ID ~" = "~ rawMapId
      )
      val scanSeqId = ezDBC.selectInt(rawMapScanSeqIdQuery)
      
      // Retrieve corresponding scans
      val scanSeqProvider = new SQLScanSequenceProvider(lcmsDbCtx)
      val scans = scanSeqProvider.getScans(Array(scanSeqId))

      logger.info("clusterizing features...")
      
      // Remove existing cluster from the processed map
      val lcmsMapWithoutClusters = lcmsMap.copyWithoutClusters()
  
      // Perform the feature clustering
      val lcmsMapWithClusters = ClusterizeFeatures(lcmsMapWithoutClusters, scans, params)
  
      // Retrieve feature cluster ids if they exist
      val existingFtClusterIdsQuery = new SelectQueryBuilder1(LcmsDbFeatureClusterItemTable).mkSelectQuery( (t,c) =>
        List(t.CLUSTER_FEATURE_ID) ->
        " WHERE "~ t.PROCESSED_MAP_ID ~" = "~ processedMapId ~
        " GROUP BY "~ t.CLUSTER_FEATURE_ID
      )
      
      val existingFtClusterIds = ezDBC.selectInts(existingFtClusterIdsQuery)
      
      if (existingFtClusterIds.length > 0) {
  
        // Delete existing clusters from this processed map
        logger.info("delete existing feature clusters...")
        existingFtClusterIds.grouped(inExprLimit).foreach(tmpFtIds => {
          ezDBC.execute("DELETE FROM feature WHERE id IN (" + tmpFtIds.mkString(",") + ")")
          ezDBC.execute("DELETE FROM processed_map_feature_item WHERE feature_id IN (" + tmpFtIds.mkString(",") + ")")
        })
  
        // Delete existing feature_cluster_items for this map
        ezDBC.execute("DELETE FROM feature_cluster_item WHERE processed_map_id = " + processedMapId)
  
        // Set all sub-features of the map as not clusterized
        ezDBC.execute("UPDATE processed_map_feature_item SET is_clusterized = ? WHERE processed_map_id = ?",
          false, processedMapId) //+
        //" AND is_clusterized = " + BoolToSQLStr(true,boolStrAsInt) )
  
      } else logger.info("no feature cluster detected...")
  
      // Store the feature clusters
      val processedMapStorer = ProcessedMapStorer(lcmsDbCtx)
      processedMapStorer.storeFeatureClusters(lcmsMapWithClusters.features)
  
      /*
      // TODO: Update map modification time
      Pairs::Lcms::RDBO::Map::Manager.updateMaps(
        set = { modification_timestamp = time },
        where = ( id = processedMapId ),
        db = lcmsRdb
        )*/
      
      // Update service results
      featuresWithClusters = lcmsMapWithClusters.features
    
    }//end DoJDBCWork
    
    // Commit transaction if it was initiated locally
    if (!wasInTransaction) lcmsDbCtx.commitTransaction()

    // Exit the service with a success status
    true

  }

}
