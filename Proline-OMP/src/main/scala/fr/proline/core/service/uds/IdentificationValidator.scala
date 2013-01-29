package fr.proline.core.service.uds

import com.weiglewilczek.slf4s.Logging
import collection.JavaConversions.collectionAsScalaIterable
import fr.proline.api.service.IService
import fr.proline.core.algo.msi.validation.{TargetDecoyModes,ValidationParams}
import fr.proline.core.dal.ProlineEzDBC
import fr.proline.core.dal.helper.MsiDbHelper
import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.om.provider.msi.impl.{SQLResultSetProvider,SQLResultSummaryProvider}
import fr.proline.core.orm.uds.{ Dataset => UdsIdentificationDataset }
import fr.proline.core.orm.util.DatabaseManager
import fr.proline.core.service.msi.{ResultSetValidator, ResultSetMerger,ResultSummaryMerger}
import fr.proline.repository.DatabaseContext
import fr.proline.core.dal.SQLContext

class IdentificationValidator( dbManager: DatabaseManager,
                               identificationId: Int,
                               rsmIds: Seq[Int],
                               mergeResultSets: Boolean,
                               pepMatchValParams: Option[ValidationParams] = None,
                               protSetValParams: Option[ValidationParams] = None
                               ) extends IService with Logging {
  
  private val udsDbConnector = dbManager.getUdsDbConnector
  
  private val udsDbCtx = new DatabaseContext( udsDbConnector.getDataSource.getConnection, udsDbConnector.getDriverType )
  private val udsEzDBC = ProlineEzDBC( udsDbCtx )
  private val udsSqlCtx = new SQLContext( udsDbCtx, udsEzDBC )
  private val projectId = udsEzDBC.selectInt("SELECT project_id FROM identification WHERE id ="+identificationId)
  
  private val psDbConnector = dbManager.getPsDbConnector
  private val psDbCtx = new DatabaseContext( psDbConnector.getDataSource.getConnection, psDbConnector.getDriverType )
  private val psEzDBC = ProlineEzDBC( psDbCtx )
  
  private val msiDbConnector = dbManager.getMsiDbConnector(projectId)
  private val msiDbCtx = new DatabaseContext( msiDbConnector.getDataSource.getConnection, msiDbConnector.getDriverType )
  private val msiEzDBC = ProlineEzDBC( msiDbCtx )
  private val msiDbHelper = new MsiDbHelper( msiEzDBC )
  
  private def closeDbConnections() = {
    // Close connections before launching another service
    udsDbCtx.close()
    psDbCtx.close()
    msiDbCtx.close()
    //this.psDb.closeConnection()
  }
  
  override def beforeInterruption = {
    // Release database connections
    this.logger.info("releasing database connections before service interruption...")
    this.closeDbConnections()
    //this.dbManager.udsEMF.close()
    //udsEM.close()
  }
  
  def runService(): Boolean = {
    
    /*val fractionCount = udsEzDBC.selectInt(
      "SELECT count(*) FROM identification_fraction WHERE identification_id ="+identificationId
    )*/
    
    // Retrieve existing identification summaries for this identification        
    val rsIdByRsmId = msiDbHelper.getResultSetIdByResultSummaryId( rsmIds )
    val rsmIdByRsId = for( (rsId, rsmId) <- rsIdByRsmId ) yield (rsmId, rsId)
    
    var identInstanceRsmId = 0
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
        val rsProvider = new SQLResultSetProvider( msiDbCtx, msiEzDBC, psDbCtx, psEzDBC, udsSqlCtx )
        
        // Load result sets
        val rsIds = targetRsIds ++ decoyRsIdsAsOpts.map { _.get }
        // TODO LMN Use a real SQL Db Contexts here ->
        val( targetRsList, decoyRsList ) = rsProvider.getResultSets( rsIds ).partition { _.isDecoy == false }
        
        // Close connections before launching another service
        this.closeDbConnections()
        
        // Merge result set
        val targetRsMerger = new ResultSetMerger( dbManager, projectId, targetRsList )
        targetRsMerger.runService()        
        val mergedTargetRs = targetRsMerger.mergedResultSet
        
        var mergedDecoyRs = Option.empty[ResultSet]
        if( nbDecoyRs > 0 ) {
          val decoyRsMerger = new ResultSetMerger( dbManager, projectId, decoyRsList )
          decoyRsMerger.runService()
          mergedDecoyRs = Some(decoyRsMerger.mergedResultSet)
        }
        
        // Instantiate a result set validator
        val rsValidator = new ResultSetValidator(
                                dbManager = dbManager,
                                projectId = projectId,
                                targetRs = mergedTargetRs,
                                decoyRsOpt = mergedDecoyRs,
                                pepMatchValParams = pepMatchValParams,
                                protSetValParams = protSetValParams,
                                // TODO: retrieve from the MSIdb
                                targetDecoyMode = Some(TargetDecoyModes.withName( "separated" ))
                                )
        rsValidator.runService()
        
        // Retrieve target RSM id
        identInstanceRsmId = rsValidator.validatedTargetRsm.id
        
      } else {
        
        // Iterate over result summary ids to load them
        val resultSummaries = new SQLResultSummaryProvider( msiDbCtx, msiEzDBC, psDbCtx, psEzDBC, udsSqlCtx ).getResultSummaries( rsmIds, true )
        
        // Close connections before launching another service
        this.closeDbConnections()
        
        // Merge result summaries
        val rsmMerger = new ResultSummaryMerger( dbManager, projectId, resultSummaries )
        rsmMerger.runService()
        
        identInstanceRsmId = rsmMerger.mergedResultSummary.id
      }
    
    } else {
      identInstanceRsmId = rsmIds(0)
      this.closeDbConnections()
      //identInstanceRsmId = rsmIdByRsId( udsIdfFractions.get(0).getResultSetId() )
    }
    
    // Create a new instance with fractions in UDS DB
    
    // Begin new transaction
    val udsEM = udsDbConnector.getEntityManagerFactory.createEntityManager()
    val udsIdent = udsEM.find(classOf[UdsIdentificationDataset], identificationId)    
    val udsIdfDatasets = udsIdent.getIdentificationDataset().toList.sortBy(_.getNumber())
  
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