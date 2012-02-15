package fr.proline.core.service.lcms

import fr.proline.core.om.helper.SqlUtils._
import fr.proline.core.LcmsDb
import fr.proline.core.service.IService
import fr.proline.core.algo.lcms.FeatureClusterer
import fr.proline.core.algo.lcms.ClusteringParams
import fr.proline.core.om.lcms._
import fr.proline.core.om.provider.sql.lcms.RunLoader
import fr.proline.core.om.storer.ProcessedMapStorer

object ClusterizeMapFeatures {

  def apply( lcmsDb: LcmsDb, lcmsMap: ProcessedMap, params: ClusteringParams ): Array[Feature] = {
    
    val mapCleaner = new ClusterizeMapFeatures( lcmsDb, lcmsMap, params )
    mapCleaner.runService()
    mapCleaner.getFeaturesWithClusters
    
  }
  
}

class ClusterizeMapFeatures( lcmsDb: LcmsDb, lcmsMap: ProcessedMap, params: ClusteringParams ) extends IService {
  
  val boolStrAsInt = lcmsDb.boolStrAsInt
  val maxNbIters = lcmsDb.maxVariableNumber
  var featuresWithClusters: Array[Feature] = null
  
  def getFeaturesWithClusters = featuresWithClusters
  
  def runService(): Boolean = {
    
    // Check if a transaction is already initiated
    val wasInTransaction = lcmsDb.isInTransaction()
    
    // Retrieve database transaction
    val lcmsDbTx = lcmsDb.getOrCreateTransaction()
    
    if( ! lcmsMap.isProcessed ) throw new Exception( "the map must be a processed map" )
    
    val runMapIds = lcmsMap.runMapIds
    if( runMapIds.length > 1 ) throw new Exception( "the processed map must correspond to a unique run map" )
    
    val processedMapId = lcmsMap.id
    val runMapId = runMapIds(0)
    
    // Retrieve run id corresponding to run map id
    val runId = lcmsDbTx.selectInt( "SELECT run_id FROM run_map WHERE id = " + runMapId )
    
    println("clusterizing features...")
    
    // Retrieve corresponding scans
    val runLoader = new RunLoader( lcmsDb )
    val scans = runLoader.getScans( Array(runId) )
    
    // Remove existing cluster from the processed map
    val lcmsMapWithoutClusters = lcmsMap.copyWithoutClusters()
    
    // Perform the feature clustering
    val ftClusterer = new FeatureClusterer()
    val lcmsMapWithClusters = ftClusterer.clusterizeFeatures( lcmsMapWithoutClusters, scans, params )
    
    // Retrieve feature cluster ids if they exist
    val existingFtClusterIds = lcmsDbTx.select("SELECT cluster_feature_id FROM feature_cluster_item " +
                                               "WHERE processed_map_id="+ processedMapId +" " +
                                               "GROUP BY cluster_feature_id") { _.nextInt.get }
    
    if( existingFtClusterIds.length > 0 ) {
      
      // Delete existing clusters from this processed map
      println( "delete existing feature clusters..." )
      existingFtClusterIds.grouped(maxNbIters).foreach( tmpFtIds => {
        lcmsDbTx.execute("DELETE FROM feature WHERE id IN (" + tmpFtIds.mkString(",") +")")
        lcmsDbTx.execute("DELETE FROM processed_map_feature_item WHERE feature_id IN (" + tmpFtIds.mkString(",") +")")
      })
      
      // Delete existing feature_cluster_items for this map
      lcmsDbTx.execute( "DELETE FROM feature_cluster_item WHERE processed_map_id = " + processedMapId )
      
      // Set all sub-features of the map as not clusterized
      lcmsDbTx.execute( "UPDATE processed_map_feature_item SET is_clusterized = " + BoolToSQLStr(false,boolStrAsInt) +
                        " WHERE processed_map_id = " + processedMapId ) //+
                        //" AND is_clusterized = " + BoolToSQLStr(true,boolStrAsInt) )
    
    } else println( "no feature cluster detected..." )
    
    // Store the feature clusters
    val processedMapStorer = ProcessedMapStorer( lcmsDb )
    processedMapStorer.storeFeatureClusters( lcmsMapWithClusters.features )
    
    /*
    // TODO: Update map modification time
    Pairs::Lcms::RDBO::Map::Manager.updateMaps(
      set = { modification_timestamp = time },
      where = ( id = processedMapId ),
      db = lcmsRdb
      )*/
    
    // Commit transaction if it was initiated locally
    if( !wasInTransaction ) lcmsDb.commitTransaction()
    
    // Update service results
    featuresWithClusters = lcmsMapWithClusters.features
    
    // Exit the service with a success status
    true
    
  }

  
}
