package fr.proline.core.service.msq

import scala.Array.canBuildFrom

import com.typesafe.scalalogging.slf4j.Logging

import fr.profi.jdbc.easy.int2Formattable
import fr.profi.jdbc.easy.long2Formattable
import fr.profi.jdbc.easy.string2Formattable
import fr.profi.jdbc.easy.stringOption2Formattable
import fr.profi.util.serialization.ProfiJson
import fr.proline.api.service.IService
import fr.proline.context.IExecutionContext
import fr.proline.core.algo.msq.Profilizer
import fr.proline.core.algo.msq.ProfilizerConfig
import fr.proline.core.dal.ContextFactory
import fr.proline.core.dal.context.execCtxToTxExecCtx
import fr.proline.core.dal.DoJDBCWork
import fr.proline.core.dal.helper.UdsDbHelper
import fr.proline.core.om.model.msq.ExperimentalDesign
import fr.proline.core.om.provider.msq.impl.SQLExperimentalDesignProvider
import fr.proline.core.om.provider.msq.impl.SQLQuantResultSummaryProvider
import fr.proline.core.orm.uds.MasterQuantitationChannel
import fr.proline.core.orm.uds.ObjectTree
import fr.proline.core.orm.uds.ObjectTreeSchema
import fr.proline.core.orm.uds.ObjectTreeSchema.SchemaName
import fr.proline.core.orm.uds.repository.ObjectTreeSchemaRepository
import fr.proline.repository.IDataStoreConnectorFactory
import javax.persistence.EntityManager


// Factory for Proline-Cortex
object QuantProfilesComputer {
  
  def apply(
    executionContext: IExecutionContext,
    masterQuantChannelId: Long,
    config: ProfilizerConfig
  ): QuantProfilesComputer = {
    
    val udsDbCtx = executionContext.getUDSDbConnectionContext
    
    val udsDbHelper = new UdsDbHelper( udsDbCtx )
    val quantiId = udsDbHelper.getQuantitationId( masterQuantChannelId ).get
    
    val expDesignProvider = new SQLExperimentalDesignProvider(executionContext.getUDSDbConnectionContext)
    val expDesign = expDesignProvider.getExperimentalDesign(quantiId).get
    
    new QuantProfilesComputer(
      executionContext = executionContext,
      experimentalDesign = expDesign,
      masterQuantChannelId = masterQuantChannelId,
      config = config
    )
  }
  
}

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
    val udsEM = udsDbCtx.getEntityManager()
    val udsDbHelper = new UdsDbHelper(udsDbCtx)
    
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
        "UPDATE object_tree SET clob_data = ? " +
        "WHERE object_tree.id IN (SELECT object_tree_id FROM master_quant_component WHERE master_quant_component.id = ?)"
      )

      this.logger.info("Updating MasterQuantPeptides...")
      
      // Iterate over MasterQuantPeptides 
      for( mqPep <- quantRSM.masterQuantPeptides ) {
        
        // Update MasterQuantPeptides selection level and properties
        mqComponentUpdateStmt.executeWith(
          mqPep.selectionLevel,
          mqPep.properties.map( props => ProfiJson.serialize(props) ),
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
          mqProtSet.properties.map( props => ProfiJson.serialize(props) ),
          mqProtSet.getMasterQuantComponentId()
        )
        
        // Retrieve quant protein sets sorted by quant channel
        val quantProtSetMap = mqProtSet.quantProteinSetMap
        val quantProtSets = qcIds.map { quantProtSetMap.getOrElse(_, null) }
         
        // Update MasterQuantProtSets object tree
        objTreeUpdateStmt.executeWith(
          ProfiJson.serialize(quantProtSets),
          mqProtSet.getMasterQuantComponentId()
        )
      }
      objTreeUpdateStmt.close()
      mqComponentUpdateStmt.close()
      
    })
        
      udsDbCtx.beginTransaction()
      //Save PostProcessingQuantConfig in DataSet ObjectTree         
	  val postQuantProcessinqObjectTree =  buildDataSetObjectTree(config, udsEM)
	  udsEM.persist(postQuantProcessinqObjectTree)
		
	  // Store ObjectTree component        
	  val udsQuantitation = udsMasterQuantChannel.getDataset()
	  udsQuantitation.putObject(SchemaName.POST_QUANT_PROCESSING_CONFIG.toString(), postQuantProcessinqObjectTree.getId())
	  udsEM.merge(udsQuantitation)
              
	  udsDbCtx.commitTransaction()
    
    // Close execution context if initiated locally
    if( this._hasInitiatedExecContext ) executionContext.closeAll()

    this.logger.info("Exiting QuantProfilesComputer service.")
    
    true
  }

    protected def buildDataSetObjectTree(postQuantProcessingConfig : ProfilizerConfig, udsEM: EntityManager): ObjectTree = {
    // Store the object tree
    val quantDSObjectTree = new ObjectTree()
    quantDSObjectTree.setSchema(ObjectTreeSchemaRepository.loadOrCreateObjectTreeSchema(udsEM,ObjectTreeSchema.SchemaName.POST_QUANT_PROCESSING_CONFIG.toString()))
    quantDSObjectTree.setClobData(ProfiJson.serialize(postQuantProcessingConfig))

    quantDSObjectTree
  }
    
}