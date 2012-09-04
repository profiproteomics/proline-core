package fr.proline.core.service.uds

import com.weiglewilczek.slf4s.Logging
import collection.JavaConversions.collectionAsScalaIterable
import fr.proline.core.dal.{DatabaseManagement,MsiDb,PsDb,UdsDb}
import fr.proline.core.service.IService
import fr.proline.core.service.msi.ResultSummaryMerger
import fr.proline.core.algo.msi.validation.{TargetDecoyModes,ValidationParams}
import fr.proline.core.dal.helper.MsiDbHelper
import fr.proline.core.orm.uds.{ Identification => UdsIdentification,
                                 IdentificationSummary => UdsIdfSummary,
                                 IdentificationFractionSummary => UdsIdfFractionSummary }

import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.om.provider.msi.impl.{SQLResultSetProvider,SQLResultSummaryProvider}
import fr.proline.core.service.msi.{ResultSetValidator, ResultSetMerger}

class IdentificationValidator( dbManager: DatabaseManagement,
                               identificationId: Int,
                               rsmIds: Seq[Int],
                               mergeResultSets: Boolean,
                               pepMatchValParams: Option[ValidationParams] = None,
                               protSetValParams: Option[ValidationParams] = None
                               ) extends IService with Logging {
  
  private val udsEM = dbManager.udsEMF.createEntityManager() 
  private val udsIdent = udsEM.find(classOf[UdsIdentification], identificationId)
  private val projectId = udsIdent.getProject().getId()
  
  private val psDb = new PsDb(PsDb.buildConfigFromDatabaseConnector(dbManager.psDBConnector) ) 
  
  private val msiDbConnector = dbManager.getMSIDatabaseConnector(projectId, false)
  private val msiDb = new MsiDb( MsiDb.buildConfigFromDatabaseConnector(msiDbConnector) )
  private val msiDbHelper = new MsiDbHelper( msiDb )
  
  private def closeDbConnections() = {
    // Close connections before launching another service
    this.msiDb.closeConnection()
    this.psDb.closeConnection()
  }
  override def beforeInterruption = {
    // Release database connections
    this.logger.info("releasing database connections before service interruption...")
    this.closeDbConnections()
    this.dbManager.udsEMF.close()
  }
  
  def runService(): Boolean = {
    
    val udsIdfFractions = udsIdent.getFractions()    
    
    // Retrieve existing identification summaries for this identification        
    val rsIdByRsmId = msiDbHelper.getResultSetIdByResultSummaryId( rsmIds )
    val rsmIdByRsId = for( (rsId, rsmId) <- rsIdByRsmId ) yield (rsmId, rsId)
    
    var identInstanceRsmId = 0
    if( udsIdfFractions.size() > 1 ) {
      
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
        val rsProvider = new SQLResultSetProvider( msiDb, psDb )
        
        // Load result sets
        val rsIds = targetRsIds ++ decoyRsIdsAsOpts.map { _.get }
        val( targetRsList, decoyRsList ) = rsProvider.getResultSets( rsIds ).partition { _.isDecoy == false }
        
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
        val resultSummaries = new SQLResultSummaryProvider( msiDb, psDb ).getResultSummaries( rsmIds, true )
        
        // Close connections before launching another service
        this.msiDb.closeConnection()
        this.psDb.closeConnection()
        
        // Merge result summaries
        val rsmMerger = new ResultSummaryMerger( dbManager, projectId, resultSummaries )
        rsmMerger.runService()
        
        identInstanceRsmId = rsmMerger.mergedResultSummary.id
      }
    
    } else {
      identInstanceRsmId = rsmIdByRsId( udsIdfFractions.get(0).getResultSetId() )
    }
    
    // Create a new instance with fractions in UDS DB
    
    // Begin new transaction
    this.udsEM.getTransaction().begin()
    
    val mergedDataType = if( mergeResultSets ) "result_set" else "result_summary"
    val udsIdentSummaries = udsIdent.getIdentificationSummaries().toList.sort { (a,b) => b.getNumber() < a.getNumber() }
    val prevNbIdfSummaries: Int = if ( udsIdentSummaries.length == 0 ) 0
                                  else udsIdentSummaries(0).getNumber()
    
    // Create a new identification instance in UDS database
    val udsIdfSummary = new UdsIdfSummary()
    udsIdfSummary.setNumber( prevNbIdfSummaries + 1 )
    udsIdfSummary.setResultSummaryId( identInstanceRsmId )
    udsIdfSummary.setIdentification( udsIdent )
    // TODO: store properties
    //udsIdfSummary.setSerializedProperties() // merged_data_type = mergedDataType
    
    this.udsEM.persist( udsIdfSummary )
    
    for( udsIdfFraction <- udsIdfFractions ) {
      
      // Create a new identification instance fraction in UDS database
      val udsIdfFractionSummary = new UdsIdfFractionSummary()
      udsIdfFractionSummary.setResultSummaryId( rsmIdByRsId(udsIdfFraction.getResultSetId) )      
      udsIdfFractionSummary.setFraction( udsIdfFraction )
      udsIdfFractionSummary.setIdentificationSummary( udsIdfSummary )
      
      this.udsEM.persist( udsIdfFractionSummary )
      
    }
    
    // Commit transaction
    this.udsEM.getTransaction().commit()
    
    this.beforeInterruption()
  
    true
  }
  
  
}