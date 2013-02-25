package fr.proline.core.service.uds

import scala.collection.mutable.HashMap
import scala.collection.mutable.ArrayBuffer
import scala.collection.JavaConversions.collectionAsScalaIterable
import com.weiglewilczek.slf4s.Logging
import fr.proline.api.service.IService
import fr.proline.context.{IExecutionContext, DatabaseConnectionContext, BasicExecutionContext}
import fr.proline.core.algo.msi.filtering._
import fr.proline.core.algo.msi.validation._
import fr.proline.core.dal.helper.{MsiDbHelper,UdsDbHelper}
import fr.proline.core.dal.{SQLConnectionContext, ContextFactory}
import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.om.provider.msi.impl.{SQLResultSetProvider, SQLResultSummaryProvider}
import fr.proline.core.orm.uds.{Dataset => UdsDataset}
import fr.proline.core.service.msi.{ResultSetValidator, ResultSetMerger, ResultSummaryMerger}
import fr.proline.repository.IDataStoreConnectorFactory
import fr.proline.core.dal.BuildExecutionContext
import fr.proline.core.om.model.msi.ResultSummary
import javax.persistence.EntityManager


class IdentificationTreeValidator(
  dsFactory: IDataStoreConnectorFactory,
  execJpaContext: IExecutionContext,
  identTreeId: Int,
  mergeResultSets: Boolean,
  tdAnalyzer: Option[ITargetDecoyAnalyzer] = None,
  pepMatchFilters: Option[Seq[IPeptideMatchFilter]] = None,
  pepMatchValidator: Option[IPeptideMatchValidator] = None,
  protSetFilters: Option[Seq[IProteinSetFilter]] = None,
  protSetValidator: Option[IProteinSetValidator] = None 
) extends IService with Logging {  
  require( execJpaContext.isJPA, "a JPA execution context is needed" )
  
  private val udsDbCtx = execJpaContext.getUDSDbConnectionContext()
  private val psDbCtx = execJpaContext.getPSDbConnectionContext()
  private val msiDbCtx = execJpaContext.getMSIDbConnectionContext()
  private val msiDbHelper = new MsiDbHelper( msiDbCtx )
  
  override def beforeInterruption = {
    // Release database connections
    this.logger.info("releasing database connections before service interruption...")
    execJpaContext.closeAll()
  }
  
  def runService(): Boolean = {
    
    // Retrieve UDSdb entity manager
    val udsEM = udsDbCtx.getEntityManager()
    
    // Retrieve identification datasets ids
    val udsIdentTreeDS = udsEM.find(classOf[UdsDataset], identTreeId)
    val projectId = udsIdentTreeDS.getProject.getId
    val identDatasets = udsIdentTreeDS.getIdentificationDataset() 
    
    // Retrieve target result sets ids
    val targetRsIds: Array[Int] = identDatasets.map(_.getResultSetId.toInt).toArray
    
    // Retrieve decoy RS ids if they exists
    val decoyRsIdsAsOpts = targetRsIds.map { msiDbHelper.getDecoyRsId( _ ) } filter { _ != None }
    
    // Check that if we have decoy data we have the same number of target and decoy result sets
    val( nbTargetRs, nbDecoyRs ) = ( targetRsIds.length, decoyRsIdsAsOpts.length )
    if( nbDecoyRs > 0 && nbDecoyRs != nbTargetRs ) {
      throw new Exception( "missing decoy result set for one of the provided result sets" )
    }
    
    // Load result sets
    val rsIds = targetRsIds ++ decoyRsIdsAsOpts.map { _.get }
    val( targetRsList, decoyRsList ) = this._loadResultSets(projectId,rsIds).partition { _.isDecoy == false }
    //val targetRsById = targetRsList.map( drs => drs.id -> drs ).toMap
    val decoyRsById = decoyRsList.map( drs => drs.id -> drs ).toMap
    
    // Link decoy RS to target RS and map RSM by RS id
    val rsmByRsId = new HashMap[Int,ResultSummary]
    for( targetRS <- targetRsList ) {
      targetRS.decoyResultSet = decoyRsById.get(targetRS.getDecoyResultSetId)
      val rsm = this._validateResultSet(targetRS)
      rsmByRsId += targetRS.id -> rsm
    }
    
    // Map result summaries by the corresponding dataset id
    val rsmByDsId = new HashMap[Int,ResultSummary]
    for( identDataset <- identDatasets ) {
      val rsId = identDataset.getResultSetId      
      rsmByDsId += identDataset.getId.toInt -> rsmByRsId(rsId)
    }
    
    // Begin new transaction  
    udsEM.getTransaction().begin()
    
    // Validate datasets recursively from leaves to the root
    this._validateDatasetTree( udsEM, identDatasets.toList, rsmByDsId )
    
    // Commit transaction
    udsEM.getTransaction().commit()
    udsEM.close()
    
    // Close execution context => caller need to close
    //execJpaContext.closeAll()
    
    true
  }
  
  /**
   * Loads result sets using SQL execution context.
   */
  private def _loadResultSets( projectId: Int, rsIds: Seq[Int] ): Array[ResultSet] = {
    
    val execSqlContext = BuildExecutionContext( dsFactory, projectId, false )
    val udsDbCtx = execSqlContext.getUDSDbConnectionContext.asInstanceOf[SQLConnectionContext]
    val udsDbHelper = new UdsDbHelper( udsDbCtx )
    val psDbCtx = execSqlContext.getPSDbConnectionContext.asInstanceOf[SQLConnectionContext]
    val msiDbCtx = execSqlContext.getMSIDbConnectionContext.asInstanceOf[SQLConnectionContext]
    
    // Instantiate a RS loader
    val rsProvider = new SQLResultSetProvider( msiDbCtx, psDbCtx, udsDbCtx )
    val resultSets = rsProvider.getResultSets( rsIds )
    
    execSqlContext.closeAll()
    
    resultSets
  }
  
  /**
   * Loads result summaries using SQL execution context.
   */
  /*private def _loadResultSummaries( projectId: Int, rsmIds: Seq[Int] ): Array[ResultSummary] = {
    
    val execSqlContext = BuildExecutionContext( dsFactory, projectId, false )
    val udsDbCtx = execSqlContext.getUDSDbConnectionContext.asInstanceOf[SQLConnectionContext]
    val udsDbHelper = new UdsDbHelper( udsDbCtx )
    val psDbCtx = execSqlContext.getPSDbConnectionContext.asInstanceOf[SQLConnectionContext]
    val msiDbCtx = execSqlContext.getMSIDbConnectionContext.asInstanceOf[SQLConnectionContext]
    
    // Instantiate a RSM loader
    val rsmProvider = new SQLResultSummaryProvider( msiDbCtx, psDbCtx, udsDbCtx )
    val resultSets = rsmProvider.getResultSummaries( rsmIds, true )
    
    execSqlContext.closeAll()
    
    resultSets
  }*/
  
  private def _validateResultSet( rs: ResultSet ): ResultSummary = {
    
    // Instantiate a result set validator
    val rsValidator = new ResultSetValidator(
      execContext = execJpaContext,
      targetRs = rs,
      tdAnalyzer = tdAnalyzer,
      pepMatchPreFilters = pepMatchFilters,
      pepMatchValidator = pepMatchValidator,
      protSetFilters = protSetFilters,
      protSetValidator = protSetValidator
    )
    
    rsValidator.validatedTargetRsm
  }
  
  /**
   * Validates dataset tree recursively from leaves to root
   */
  private def _validateDatasetTree(udsEM: EntityManager, udsDatasets: List[UdsDataset], rsmByRsId: HashMap[Int,ResultSummary]) {
    if( udsDatasets.length == 1 ) return
    
    // Group RSMs by parent DS id
    val rsmsByUdsParentDs = new HashMap[UdsDataset,ArrayBuffer[ResultSummary]]
    for( dataset <- udsDatasets ) {
      val udsParentDs = dataset.getParentDataset
      val rsm = rsmByRsId(dataset.getResultSetId)
      rsmsByUdsParentDs.getOrElseUpdate(udsParentDs, new ArrayBuffer[ResultSummary]() ) += rsm
    }

    // Define some vars
    val newUdsParentDatasets = new ArrayBuffer[UdsDataset]
    val newRsmByRsId = new HashMap[Int,ResultSummary]
    
    // Iterate over each group of result summaries to merge them
    for( (udsParentDs,rsms) <- rsmsByUdsParentDs ) {
      
      newUdsParentDatasets += udsParentDs
      
      // Validate dataset child result summaries
      val mergedRsm = this._mergeAndValidateDatasets(rsms)
      newRsmByRsId += mergedRsm.getResultSetId ->  mergedRsm
      
      // Replace existing result set/summary ids
      // Note: to preserve existing mappings it is needed to clone the tree in a previous step      
      udsParentDs.setResultSetId(mergedRsm.getResultSetId)
      udsParentDs.setResultSummaryId(mergedRsm.id)
      
      // TODO: store properties
      //val mergedDataType = if( mergeResultSets ) "result_set" else "result_summary"
      //udsParentDs.setSerializedProperties() // merged_data_type = mergedDataType
      
      udsEM.persist(udsParentDs)
    }
    
    this._validateDatasetTree(udsEM,newUdsParentDatasets.toList,newRsmByRsId)
  }
  
  private def _mergeAndValidateDatasets( rsms: Seq[ResultSummary] ): ResultSummary = {
    
    if( this.mergeResultSets ) {
      
      //val targetRsIds = rsmIds.map { rsIdByRsmId(_) }
      val targetRsList = rsms.map( _.resultSet.get )
      val decoyRsList = for( rs <- targetRsList; if rs.decoyResultSet.isDefined ) yield rs.decoyResultSet.get
      
      // Merge result set
      val targetRsMerger = new ResultSetMerger( execJpaContext, targetRsList )
      targetRsMerger.runService()
      val mergedTargetRs = targetRsMerger.mergedResultSet
      
      if( decoyRsList.length > 0 ) {
        val decoyRsMerger = new ResultSetMerger( execJpaContext, decoyRsList )
        decoyRsMerger.runService()
        mergedTargetRs.decoyResultSet = Some(decoyRsMerger.mergedResultSet)
      }
      
      // Instantiate a result set validator
      val rsValidator = new ResultSetValidator(
        execContext = execJpaContext,
        targetRs = mergedTargetRs,
        tdAnalyzer = tdAnalyzer,
        pepMatchPreFilters = pepMatchFilters,
        pepMatchValidator = pepMatchValidator,
        protSetFilters = protSetFilters,
        protSetValidator = protSetValidator,
        storeResultSummary = true
      )
      
      rsValidator.runService()
      
      rsValidator.validatedTargetRsm
      
    } else {      
      
      // Merge result summaries
      val rsmMerger = new ResultSummaryMerger( execCtx = execJpaContext, resultSummaries = rsms )
      rsmMerger.runService()
      
      // TODO: merge decoy rsms
      
      rsmMerger.mergedResultSummary
    }
    
  }
  
  
}