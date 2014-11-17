package fr.proline.core.service.uds

import scala.collection.JavaConversions.asScalaBuffer

import com.typesafe.scalalogging.slf4j.Logging

import fr.profi.util.serialization.ProfiJson.deserialize
import fr.profi.util.serialization.ProfiJson.serialize
import fr.proline.api.service.IService
import fr.proline.context.IExecutionContext
import fr.proline.core.algo.lcms.LabelFreeQuantConfig
import fr.proline.core.dal.ContextFactory
import fr.proline.core.dal.context.execCtxToTxExecCtx
import fr.proline.core.om.model.msq.ExperimentalDesign
import fr.proline.core.om.provider.lcms.impl.SQLRunProvider
import fr.proline.core.orm.uds.{Dataset => UdsDataset } 
import fr.proline.core.orm.uds.ObjectTree
import fr.proline.core.orm.uds.ObjectTreeSchema
import fr.proline.core.orm.uds.ObjectTreeSchema.SchemaName
import fr.proline.core.orm.uds.repository.ObjectTreeSchemaRepository
import fr.proline.core.service.msq.QuantifyMasterQuantChannel
import fr.proline.repository.IDataStoreConnectorFactory
import javax.persistence.EntityManager


class Quantifier(
  executionContext: IExecutionContext,
  name: String,
  description: String,
  projectId: Long,
  methodId: Long,
  experimentalDesign: ExperimentalDesign,
  quantConfigAsMap: java.util.Map[String,Object]
) extends IService with Logging {
  
  private var _hasInitiatedExecContext: Boolean = false
  private var _quantiId: Long = 0L
  def getQuantitationId() = _quantiId
  
  // Secondary constructor
  def this(
    dsFactory: IDataStoreConnectorFactory,
    name: String,
    description: String,
    projectId: Long,
    methodId: Long,
    experimentalDesign: ExperimentalDesign,
    quantConfigAsMap: java.util.Map[String,Object]
  ) {
    this(
      ContextFactory.buildExecutionContext(dsFactory, projectId, true), // Force JPA context
      name,
      description,
      projectId,
      methodId,
      experimentalDesign,
      quantConfigAsMap
    )
    _hasInitiatedExecContext = true
  }

  def runService() = {
    
    // Isolate future actions in an SQL transaction
    val txResult = executionContext.tryInTransactions( udsTx = true, msiTx = true, txWork = {

      // Store quantitation in the UDSdb
      val quantiCreator = new CreateQuantitation(
        executionContext = executionContext,
        name = name,
        description = description,
        projectId = projectId,
        methodId = methodId,
        experimentalDesign = experimentalDesign
      )
      quantiCreator.runService()
      
      this._quantiId = quantiCreator.getUdsQuantitation.getId
      
      // Retrieve entity manager
      val udsDbCtx = executionContext.getUDSDbConnectionContext()
      val udsEM = udsDbCtx.getEntityManager()
      val udsQuantitation = udsEM.find( classOf[UdsDataset],quantiCreator.getUdsQuantitation.getId)
      
      // Retrieve master quant channels (they should be sorted by their number)
      val udsMasterQuantChannels = udsQuantitation.getMasterQuantitationChannels.toList
      
      // Fake missing fields
      quantConfigAsMap.put("map_set_name", null)
      quantConfigAsMap.put("lc_ms_runs", null)
      
      // Parse the quant configuration
      // TODO: parse other kinds of configuration (spectal count)
      val quantConfig = deserialize[LabelFreeQuantConfig](serialize(quantConfigAsMap))
      
      // Create a LC-MS run provider
      val lcmsRunProvider = new SQLRunProvider(udsDbCtx,None)

      // Quantify each master quant channel
      for( udsMasterQuantChannel <- udsMasterQuantChannels ) {
        
        // Retrieve master quant channels sorted by their number
        val sortedQuantChannels = udsMasterQuantChannel.getQuantitationChannels()
        
        // Retrieve run ids
        val runIds = sortedQuantChannels.map( _.getRun.getId )
        
        // Load the LC-MS runs
        val runs = lcmsRunProvider.getRuns(runIds)
        
        // Clone the config and inject the missing parameters
        val masterConfig = quantConfig.copy(
          mapSetName = udsMasterQuantChannel.getName(),
          lcMsRuns = runs
        )
        
        //Save LabelFreeQuantConfig in DataSet ObjectTree 
        val qtConfigObjectTree =  buildDataSetObjectTree( masterConfig, udsEM)
        udsEM.persist(qtConfigObjectTree)
        
        // Store ObjectTree component        
        udsQuantitation.putObject(SchemaName.LABEL_FREE_QUANT_CONFIG.toString(), qtConfigObjectTree.getId())
        udsEM.merge(udsQuantitation)
        
        val mqcQuantifier = new QuantifyMasterQuantChannel(
          executionContext,
          experimentalDesign,
          udsMasterQuantChannel.getId,
          masterConfig
        )
        
        mqcQuantifier.run()
      }
      
    }) // end of tryInTransactions

    txResult
  }
  
   protected def buildDataSetObjectTree(quantConfig : LabelFreeQuantConfig, udsEM: EntityManager): ObjectTree = {
    // Store the object tree
    val quantDSObjectTree = new ObjectTree()
    quantDSObjectTree.setSchema(ObjectTreeSchemaRepository.loadOrCreateObjectTreeSchema(udsEM,ObjectTreeSchema.SchemaName.LABEL_FREE_QUANT_CONFIG.toString()))
    quantDSObjectTree.setClobData(serialize(quantConfig))

    quantDSObjectTree
  }


}
