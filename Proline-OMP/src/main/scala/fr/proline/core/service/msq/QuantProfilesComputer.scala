package fr.proline.core.service.msq

import com.typesafe.scalalogging.slf4j.Logging
import fr.profi.jdbc.easy._
import fr.profi.util.serialization.ProfiJson
import fr.proline.api.service.IService
import fr.proline.context.IExecutionContext
import fr.proline.core.algo.msq.Profilizer
import fr.proline.core.algo.msq.ProfilizerConfig
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
  config: ProfilizerConfig
) extends IService with Logging {
  
  require( executionContext.isJPA,"invalid type of executionContext, JPA type is required")
  
  private var _hasInitiatedExecContext: Boolean = false

  // Secondary constructor
  def this(
    dsFactory: IDataStoreConnectorFactory,
    projectId: Long,
    experimentalDesign: ExperimentalDesign,
    masterQuantChannelId: Long,
    config: ProfilizerConfig
  ) {
    this(
      ContextFactory.buildExecutionContext(dsFactory, projectId, true), // Force JPA context
      experimentalDesign,
      masterQuantChannelId,
      config
    )
    _hasInitiatedExecContext = true
  }
  
  def runService() = {

    this.logger.info("Running service QuantProfilesComputer.")
    
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
    profilizer.computeMasterQuantPeptideProfiles(quantRSM.masterQuantPeptides, config)
    
    // 4. Compute MasterQuantProtSets profiles
    profilizer.computeMasterQuantProtSetProfiles(quantRSM.masterQuantProteinSets, config)
    
    // 5. Update MasterQuantPeptides and MasterQuantProtSets properties
    val msiDbCtx = executionContext.getMSIDbConnectionContext()
    
    DoJDBCWork.tryTransactionWithEzDBC(msiDbCtx, { ezDBC =>
      
      // TODO: create an UPDATE query builder
      val mqComponentUpdateStmt = ezDBC.prepareStatementWrapper(
        "UPDATE master_quant_component SET selection_level = ?, serialized_properties = ? WHERE id = ?"
      )
      
      val objTreeUpdateStmt = ezDBC.prepareStatementWrapper(
        "UPDATE object_tree SET clob_data = ? "+
        "WHERE master_quant_component.object_tree_id = object_tree.id "+
        "AND master_quant_component.id = ?"
      )
      
      /*val mqProtSetObjTreeUpdateStmt = ezDBC.prepareStatementWrapper(
        "UPDATE master_quant_component SET selection_level = ?, serialized_properties = ? WHERE id = ?"
      )*/

      this.logger.info("Updating MasterQuantPeptides...")
      
      // Iterate over MasterQuantPeptides 
      for( mqPep <- quantRSM.masterQuantPeptides ) {
        
        // Update MasterQuantPeptides selection level and properties
        mqComponentUpdateStmt.executeWith(
          mqPep.selectionLevel,
          ProfiJson.serialize(mqPep.properties),
          mqPep.id
        )
        
        // Retrieve quant peptides sorted by quant channel
        val quantPeptideMap = mqPep.quantPeptideMap
        val quantPeptides = qcIds.map { quantPeptideMap.getOrElse(_, null) }
        
        // Update MasterQuantPeptides object tree
        objTreeUpdateStmt.executeWith(
          ProfiJson.serialize(quantPeptides),
          mqPep.id
        )
      }
      
      this.logger.info("Updating MasterQuantProtSets...")
      
      // Iterate over MasterQuantProtSets
      for( mqProtSet <- quantRSM.masterQuantProteinSets ) {
        
        // Update MasterQuantProtSets selection level and properties
        mqComponentUpdateStmt.executeWith(
          mqProtSet.selectionLevel,
          ProfiJson.serialize(mqProtSet.properties),
          mqProtSet.getMasterQuantComponentId()
        )
        
        // Retrieve quant protein sets sorted by quant channel
        val quantProtSetMap = mqProtSet.quantProteinSetMap
        val quantProtSets = qcIds.map { quantProtSetMap.getOrElse(_, null) }
         
        // Update MasterQuantProtSets object tree
        objTreeUpdateStmt.executeWith(
          ProfiJson.serialize(quantProtSets),
          mqProtSet.id
        )
      }
      
    })
    
    // Close execution context if initiated locally
    if( this._hasInitiatedExecContext ) executionContext.closeAll()

    this.logger.info("Exiting QuantProfilesComputer service.")
    
    true
  }

}