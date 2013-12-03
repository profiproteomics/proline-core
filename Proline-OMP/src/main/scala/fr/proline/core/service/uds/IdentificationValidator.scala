package fr.proline.core.service.uds

import scala.collection.JavaConversions.collectionAsScalaIterable
import com.weiglewilczek.slf4s.Logging
import fr.proline.api.service.IService
import fr.proline.context.{IExecutionContext, DatabaseConnectionContext, BasicExecutionContext}
import fr.proline.core.algo.msi.filtering._
import fr.proline.core.dal.helper.MsiDbHelper
import fr.proline.core.dal.ContextFactory
import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.om.provider.msi.impl.{SQLResultSetProvider, SQLResultSummaryProvider}
import fr.proline.core.orm.uds.{Dataset => UdsDataset}
import fr.proline.core.service.msi.{ResultSetValidator, ResultSetMerger, ResultSummaryMerger}
import fr.proline.repository.IDataStoreConnectorFactory
import fr.proline.core.algo.msi.validation.TargetDecoyModes
import fr.proline.core.dal.BuildExecutionContext
import fr.proline.core.algo.msi.validation.IPeptideMatchValidator

@deprecated("0.1.1","use IdentificationTreeValidator instead")
class IdentificationValidator( dbManager: IDataStoreConnectorFactory,
                               projectId: Long,
                               identificationId: Long,
                               rsmIds: Seq[Long],
                               mergeResultSets: Boolean,
                               pepMatchPreFilters: Option[Seq[IPeptideMatchFilter]] = None,
                               pepMatchValidator: Option[IPeptideMatchValidator] = None,
                               protSetFilters: Option[Seq[IProteinSetFilter]] = None
                               //targetDecoyMode: Option[TargetDecoyModes.Mode]
                               ) extends IService with Logging {
  
  
  private val execSqlContext = BuildExecutionContext( dbManager, projectId, false )
  private val udsDbCtx = execSqlContext.getUDSDbConnectionContext
  private val psDbCtx = execSqlContext.getPSDbConnectionContext
  private val msiDbCtx = execSqlContext.getMSIDbConnectionContext
  private val msiDbHelper = new MsiDbHelper( msiDbCtx )
  
  override def beforeInterruption = {
    // Release database connections
    this.logger.info("releasing database connections before service interruption...")
    execSqlContext.closeAll()
  }
  
  def runService(): Boolean = {
    
    /*val fractionCount = udsEzDBC.selectInt(
      "SELECT count(*) FROM identification_fraction WHERE identification_id ="+identificationId
    )*/
    
    // Retrieve existing identification summaries for this identification        
    val rsIdByRsmId = msiDbHelper.getResultSetIdByResultSummaryId( rsmIds )
    val rsmIdByRsId = for( (rsId, rsmId) <- rsIdByRsmId ) yield (rsmId, rsId)
    
    var identInstanceRsmId: Long = 0L
    //if( udsIdfFractions.size() > 1 ) {
    if( rsmIds.length > 1 ) {
      
      if( this.mergeResultSets ) {
        
        val targetRsIds = rsmIds.map { rsIdByRsmId(_) }        
        
        // Retrieve decoy RS ids if they exists
        val decoyRsIdsAsOpts = targetRsIds.map { msiDbHelper.getDecoyRsId( _ ) } filter { _ != None }
        
        // Check that if we have decoy data we have the same number of target and decoy result sets
        val( nbTargetRs, nbDecoyRs ) = ( targetRsIds.length, decoyRsIdsAsOpts.length )
        if( nbDecoyRs > 0 && nbDecoyRs != nbTargetRs ) {
          throw new Exception( "missing decoy dataset for one of the provided result sets" )
        }
        
        // Instantiate a RS loader
        val rsProvider = new SQLResultSetProvider( msiDbCtx, psDbCtx, udsDbCtx )
        
        // Load result sets
        val rsIds = targetRsIds ++ decoyRsIdsAsOpts.map { _.get }
        // TODO LMN Use a real SQL Db Contexts here ->
        val( targetRsList, decoyRsList ) = rsProvider.getResultSets( rsIds ).partition { _.isDecoy == false }
        
        // Merge result set
        val targetRsMerger = new ResultSetMerger( execSqlContext, None, Some(targetRsList) )
        targetRsMerger.runService()
        val mergedTargetRs = targetRsMerger.mergedResultSet
        
        var mergedDecoyRs = Option.empty[ResultSet]
        if( nbDecoyRs > 0 ) {
          val decoyRsMerger = new ResultSetMerger( execSqlContext, None, Some(decoyRsList) )
          decoyRsMerger.runService()
          mergedDecoyRs = Some(decoyRsMerger.mergedResultSet)
        }
        
        //Create ExecutionContext
        val exCtxt : IExecutionContext = new BasicExecutionContext(udsDbCtx ,ContextFactory.buildDbConnectionContext(dbManager.getPdiDbConnector, false), 
           this.psDbCtx, this.msiDbCtx,   null)
       
        
        // Instantiate a result set validator
        val rsValidator = new ResultSetValidator(
          execContext = execSqlContext,                              
          targetRs = mergedTargetRs,
          pepMatchPreFilters = pepMatchPreFilters,
          pepMatchValidator = pepMatchValidator,
          protSetFilters = protSetFilters
          // TODO: retrieve from the MSIdb
          //targetDecoyMode = targetDecoyMode
        )
        
                                                              
        rsValidator.runService()
        
        // Retrieve target RSM id
        identInstanceRsmId = rsValidator.validatedTargetRsm.id
        
      } else {
        
        // Iterate over result summary ids to load them
        val resultSummaries = new SQLResultSummaryProvider( msiDbCtx, psDbCtx,  udsDbCtx ).getResultSummaries( rsmIds, true )
        
        // Merge result summaries
        val rsmMerger = new ResultSummaryMerger( execCtx = execSqlContext, None, Some(resultSummaries) )
        rsmMerger.runService()
        
        identInstanceRsmId = rsmMerger.mergedResultSummary.id
      }
    
    } else {
      identInstanceRsmId = rsmIds(0)
      //identInstanceRsmId = rsmIdByRsId( udsIdfFractions.get(0).getResultSetId() )
    }
    
    // Close execution context
    execSqlContext.closeAll()
    
    // Create a new instance with fractions in UDS DB
    
    // Begin new transaction
     val udsEM = dbManager.getUdsDbConnector.getEntityManagerFactory.createEntityManager()
    
    val udsIdentAggregate = udsEM.find(classOf[UdsDataset], identificationId)
    val udsIdfDatasets = udsIdentAggregate.getChildren().toList.sortBy(_.getNumber())
  
    udsEM.getTransaction().begin()
    
    val mergedDataType = if( mergeResultSets ) "result_set" else "result_summary"
      
   // val udsIdentSummaries = udsIdent.getIdentificationSummaries().toList.sort { (a,b) => b.getNumber() < a.getNumber() }
   // val prevNbIdfSummaries: Int = if ( udsIdentSummaries.length == 0 ) 0
   //                               else udsIdentSummaries(0).getNumber()
    
    // Create a new identification instance in UDS database
    //val udsIdfSummary = new UdsIdfSummary()
    //udsIdfSummary.setNumber( prevNbIdfSummaries + 1 )
    //udsIdfSummary.setResultSummaryId( identInstanceRsmId )
    //udsIdfSummary.setIdentification( udsIdent )
    // TODO: store properties
    //udsIdfSummary.setSerializedProperties() // merged_data_type = mergedDataType
    
    //udsEM.persist( udsIdfSummary )
    
    for( udsIdfDataset <- udsIdfDatasets ) {
      
      // Create a new identification instance fraction in UDS database
      //val udsIdfFractionSummary = new UdsIdfFractionSummary()
      udsIdfDataset.setResultSummaryId( rsmIdByRsId(udsIdfDataset.getResultSetId) )      
      //udsIdfFractionSummary.setFraction( udsIdfFraction )
      //udsIdfFractionSummary.setIdentificationSummary( udsIdfSummary )
      
      udsEM.persist( udsIdfDataset )
      
    }
    
    // Commit transaction
    udsEM.getTransaction().commit()
    udsEM.close()
    
    this.beforeInterruption()
  
    true
  }
  
  
}