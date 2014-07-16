package fr.proline.core.service.uds

import scala.collection.JavaConversions._
import scala.collection.mutable.ArrayBuffer
import com.typesafe.scalalogging.slf4j.Logging
import fr.profi.util.serialization.ProfiJson._
import fr.proline.context.IExecutionContext
import fr.proline.core.algo.lcms.IMsQuantConfig
import fr.proline.core.algo.lcms.LabelFreeQuantConfig
import fr.proline.core.dal.ContextFactory
import fr.proline.core.dal.context._
import fr.proline.core.om.model.msq.ExperimentalDesign
import fr.proline.core.om.provider.lcms.impl.SQLRunProvider
import fr.proline.core.orm.uds.{ Dataset => UdsDataset, MasterQuantitationChannel => UdsMasterQuantChannel }
import fr.proline.core.service.msq.QuantifyMasterQuantChannel
import fr.proline.repository.IDataStoreConnectorFactory
import fr.proline.api.service.IService

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
    
    // Isolate future actions in an SQL transaction
    udsDbCtx.tryInTransaction {
      
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
        
        val mqcQuantifier = new QuantifyMasterQuantChannel(
          executionContext,
          experimentalDesign,
          udsMasterQuantChannel.getId,
          masterConfig
        )
        
        mqcQuantifier.run()
      }
      
    } // end of tryInTransaction

    true
  }

}
