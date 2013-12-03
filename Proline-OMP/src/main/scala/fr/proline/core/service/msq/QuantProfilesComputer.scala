package fr.proline.core.service.msq

import com.codahale.jerkson.Json
import fr.profi.jdbc.easy._
import fr.proline.api.service.IService
import fr.proline.context.IExecutionContext
import fr.proline.core.algo.msq.Profilizer
import fr.proline.core.dal.ContextFactory
import fr.proline.core.dal.DoJDBCWork
import fr.proline.core.dal.helper.UdsDbHelper
import fr.proline.core.om.model.msq.ExperimentalDesign
import fr.proline.core.om.provider.msq.impl.SQLQuantResultSummaryProvider
import fr.proline.core.orm.uds.MasterQuantitationChannel
import fr.proline.repository.IDataStoreConnectorFactory

class QuantProfilesComputer(
  executionContext: IExecutionContext,
  experimentalDesign: ExperimentalDesign,
  masterQuantChannelId: Long,
  peptideStatTestsAlpha: Float = 0.01f,
  proteinStatTestsAlpha: Float = 0.01f
) extends IService {
  
  private var _hasInitiatedExecContext: Boolean = false

  // Secondary constructor
  def this(
    dsFactory: IDataStoreConnectorFactory,
    projectId: Long,
    experimentalDesign: ExperimentalDesign,
    masterQuantChannelId: Long
  ) {
    this(
      ContextFactory.buildExecutionContext(dsFactory, projectId, true), // Force JPA context
      experimentalDesign,
      masterQuantChannelId
    )
    _hasInitiatedExecContext = true
  }
  
  def runService() = {
    
    // Get entity manager
    val udsDbCtx = executionContext.getUDSDbConnectionContext()
    val udsEM = executionContext.getUDSDbConnectionContext().getEntityManager()
    val udsDbHelper = new UdsDbHelper(executionContext.getUDSDbConnectionContext())
    
    // Retrieve the quantitation fraction
    val udsMasterQuantChannel = udsEM.find(classOf[MasterQuantitationChannel], masterQuantChannelId)    
    require( udsMasterQuantChannel != null, "undefined master quant channel with id=" + udsMasterQuantChannel )
    
    // FIXME: check the quantitation method first
    
    val quantRsmId = udsMasterQuantChannel.getQuantResultSummaryId()
    val qcIds = udsDbHelper.getQuantChannelIds(masterQuantChannelId)    
    
    // 1. Load the Quant RSM
    val quantRsmProvider = new SQLQuantResultSummaryProvider(
      executionContext.getMSIDbConnectionContext,
      executionContext.getPSDbConnectionContext,
      executionContext.getUDSDbConnectionContext
    )
    val quantRSM = quantRsmProvider.getQuantResultSummary(quantRsmId, qcIds, true).get
    
    // 2. Instantiate the profilizer
    val profilizer = new Profilizer(
      expDesign = experimentalDesign,
      groupSetupNumber = 1, // TODO: retrieve from params
      masterQCNumber = udsMasterQuantChannel.getNumber
    )
    
    // 3. Compute MasterQuantPeptides profiles
    profilizer.computeMasterQuantPeptideProfiles(quantRSM.masterQuantPeptides, peptideStatTestsAlpha)
    
    // 4. Compute MasterQuantProtSets profiles
    profilizer.computeMasterQuantProtSetProfiles(quantRSM.masterQuantProteinSets, proteinStatTestsAlpha)
    
    // 5. Update MasterQuantPeptides and MasterQuantProtSets properties
    val msiDbCtx = executionContext.getMSIDbConnectionContext()
    
    DoJDBCWork.tryTransactionWithEzDBC(executionContext.getMSIDbConnectionContext, { ezDBC =>
      
      ezDBC.executeInBatch("UPDATE master_quant_component SET serialized_properties = ? WHERE id = ?") { stmt =>
        
        // Update MasterQuantPeptides properties
        for( mqPep <- quantRSM.masterQuantPeptides ) {
          stmt.executeWith( Json.generate(mqPep.properties), mqPep.id )
        }
        
        // Update MasterQuantProtSets properties
        for( mqProtSet <- quantRSM.masterQuantProteinSets ) {
           stmt.executeWith( Json.generate(mqProtSet.properties), mqProtSet.getMasterQuantComponentId() )
        }
      }
      
    })
    
    // Close execution context if initiated locally
    if( this._hasInitiatedExecContext ) executionContext.closeAll()
    
    true
  }

}