package fr.proline.core.service.lcms

import fr.profi.jdbc.easy._
import fr.proline.api.service.IService
import fr.proline.core.algo.lcms.FeatureClusterer
import fr.proline.core.algo.lcms.ClusteringParams
import fr.proline.core.dal.SQLQueryHelper
import fr.proline.core.om.model.lcms._
import fr.proline.core.om.provider.lcms.impl.RunLoader
import fr.proline.core.om.storer.lcms.ProcessedMapStorer
import fr.proline.repository.IDatabaseConnector

object ClusterizeMapFeatures {

  def apply(lcmsDbConnector: IDatabaseConnector, lcmsMap: ProcessedMap, params: ClusteringParams): Array[Feature] = {

    val mapCleaner = new ClusterizeMapFeatures(lcmsDbConnector, lcmsMap, params)
    mapCleaner.runService()
    mapCleaner.getFeaturesWithClusters

  }

}

class ClusterizeMapFeatures(lcmsDbConnector: IDatabaseConnector, lcmsMap: ProcessedMap, params: ClusteringParams) extends IService {

  val lcmsQueryHelper = new SQLQueryHelper(lcmsDbConnector)
  val ezDBC = lcmsQueryHelper.ezDBC
  val inExprLimit = ezDBC.getInExpressionCountLimit
  var featuresWithClusters: Array[Feature] = null

  def getFeaturesWithClusters = featuresWithClusters

  def runService(): Boolean = {

    // Make some requirements
    require(lcmsMap.isProcessed, "the map must be a processed map")

    val runMapIds = lcmsMap.runMapIds
    require(runMapIds.length == 1, "the processed map must correspond to a unique run map")

    // Check if a transaction is already initiated
    val wasInTransaction = ezDBC.isInTransaction()
    if (!wasInTransaction) ezDBC.beginTransaction()

    // Define some vars
    val processedMapId = lcmsMap.id
    val runMapId = runMapIds(0)

    // Retrieve run id corresponding to run map id
    val runId = ezDBC.selectInt("SELECT run_id FROM run_map WHERE id = " + runMapId)

    logger.info("clusterizing features...")

    // Retrieve corresponding scans
    val runLoader = new RunLoader(ezDBC)
    val scans = runLoader.getScans(Array(runId))

    // Remove existing cluster from the processed map
    val lcmsMapWithoutClusters = lcmsMap.copyWithoutClusters()

    // Perform the feature clustering
    val lcmsMapWithClusters = FeatureClusterer.clusterizeFeatures(lcmsMapWithoutClusters, scans, params)

    // Retrieve feature cluster ids if they exist
    val existingFtClusterIds = ezDBC.select("SELECT cluster_feature_id FROM feature_cluster_item " +
      "WHERE processed_map_id=" + processedMapId + " " +
      "GROUP BY cluster_feature_id") { _.nextInt }

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
    val processedMapStorer = ProcessedMapStorer(lcmsQueryHelper)
    processedMapStorer.storeFeatureClusters(lcmsMapWithClusters.features)

    /*
    // TODO: Update map modification time
    Pairs::Lcms::RDBO::Map::Manager.updateMaps(
      set = { modification_timestamp = time },
      where = ( id = processedMapId ),
      db = lcmsRdb
      )*/

    // Commit transaction if it was initiated locally
    if (!wasInTransaction) ezDBC.commitTransaction()

    // Update service results
    featuresWithClusters = lcmsMapWithClusters.features

    // Exit the service with a success status
    true

  }

}

/*
import org.gearman.GearmanFunction
  
private[service] class NSClusterizeMapFeatures extends GearmanFunction {
  
  import org.gearman.GearmanJob
  import org.gearman.GearmanJobResult
  import net.liftweb.json._
  implicit val formats = DefaultFormats // Brings in default date formats etc.
  
  import fr.proline.core.om.provider.sql.lcms.ProcessedMapLoader
  
  // Define parameters required to run the job
  case class JobParams( project_id: Int,
                        processed_map_id: Int,
                        clustering_params: ClusteringParamsFromJSON
                       )
  case class ClusteringParamsFromJSON( moz_tol: Double,
                                       moz_tol_unit: String, time_tol: Float,
                                       intensity_computation: String,
                                       time_computation: String
                                     )
  
  override def work(job: GearmanJob): GearmanJobResult = {
    
    var jobResult: GearmanJobResult = null
    try {
      
      // Parse job parameters
      val jobParams = parse( new String(job.getJobData) ).extract[JobParams]
      
      // Open database connection
      val lcmsDb = LcmsDb( projectId = jobParams.project_id )
      
      // Load the processed map
      val mapLoader = new ProcessedMapLoader( lcmsDb )
      val procMap = mapLoader.getMaps( Array(jobParams.processed_map_id) )(0)
      
      // Convert JSON parameters into Java parameters
      val jsonNode = jobParams.clustering_params
      val clustParams = ClusteringParams( mozTol = jsonNode.moz_tol,
                                          mozTolUnit = jsonNode.moz_tol_unit,
                                          timeTol = jsonNode.time_tol,
                                          timeComputation = jsonNode.time_computation,
                                          intensityComputation = jsonNode.intensity_computation
                                         )
 
      // Run the job
      val ftsWithClusters = ClusterizeMapFeatures( lcmsDb, procMap, clustParams )
      val nbClusters = ftsWithClusters.count( _.isCluster )
      
      // Set data to send back to client
      jobResult = GearmanJobResult.workSuccessful( nbClusters.toString().getBytes() )
      
    } catch {
      case e: Exception => {
        System.err.println( "clustering job failed: " + e.getStackTraceString )
        jobResult = GearmanJobResult.workFailed()
      }
    }
    
    jobResult
  }

}*/