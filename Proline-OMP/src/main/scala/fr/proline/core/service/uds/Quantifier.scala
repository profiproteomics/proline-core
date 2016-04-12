package fr.proline.core.service.uds

import javax.persistence.EntityManager
import scala.collection.JavaConversions.asScalaBuffer
import scala.collection.JavaConversions.collectionAsScalaIterable
import com.typesafe.scalalogging.LazyLogging
import fr.profi.util.serialization.ProfiJson.deserialize
import fr.profi.util.serialization.ProfiJson.serialize
import fr.proline.api.service.IService
import fr.proline.context.IExecutionContext
import fr.proline.core.algo.msq.config._
import fr.proline.core.dal.BuildLazyExecutionContext
import fr.proline.core.dal.context.execCtxToTxExecCtx
import fr.proline.core.om.provider.lcms.impl.SQLRunProvider
import fr.proline.core.om.model.msq._
import fr.proline.core.om.model.msq.QuantMethodType._
import fr.proline.core.om.provider.ProviderDecoratedExecutionContext
import fr.proline.core.om.provider.lcms.IRunProvider
import fr.proline.core.om.provider.lcms.impl.SQLRunProvider
import fr.proline.core.om.provider.lcms.impl.SQLScanSequenceProvider
import fr.proline.core.orm.uds.{Dataset => UdsDataset}
import fr.proline.core.orm.uds.{MasterQuantitationChannel => UdsMasterQuantChannel}
import fr.proline.core.orm.uds.{QuantitationMethod => UdsQuantMethod}
import fr.proline.core.orm.uds.ObjectTree
import fr.proline.core.orm.uds.ObjectTreeSchema
import fr.proline.core.orm.uds.ObjectTreeSchema.{ SchemaName => UdsSchemaName }
import fr.proline.core.orm.uds.repository.ObjectTreeSchemaRepository
import fr.proline.core.service.msq.quantify.BuildMasterQuantChannelQuantifier
import fr.proline.repository.IDataStoreConnectorFactory

class Quantifier(
  executionContext: IExecutionContext,
  name: String,
  description: String,
  projectId: Long,
  methodId: Long,
  experimentalDesign: ExperimentalDesign,
  quantConfigAsMap: java.util.Map[String,Object]
) extends IService with LazyLogging {
  
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
      BuildLazyExecutionContext(dsFactory, projectId, true), // Force JPA context
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
    val txResult = executionContext.tryInTransactions(udsTx = true, msiTx = true, txWork = {

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
      val udsQuantitation = udsEM.find(classOf[UdsDataset], quantiCreator.getUdsQuantitation.getId)

      // Retrieve master quant channels (they should be sorted by their number)
      val udsMasterQuantChannels = udsQuantitation.getMasterQuantitationChannels.toList
      val udsQuantMethod = udsMasterQuantChannels.head.getDataset.getMethod
      val methodType = QuantMethodType.withName(udsQuantMethod.getType)
      val abundanceUnit = AbundanceUnit.withName(udsQuantMethod.getAbundanceUnit)

      if(abundanceUnit == AbundanceUnit.FEATURE_INTENSITY) {
        // Fake missing fields
        quantConfigAsMap.put("map_set_name", null)
        quantConfigAsMap.put("lc_ms_runs", null)
      }

      // Parse the quant configuration
      val quantConfigAsStr = serialize(quantConfigAsMap)
      val quantConfigSchemaName = this.getQuantConfigSchemaName(methodType, abundanceUnit)
      
       // Store QUANT CONFIG in ObjectTree
      val qtConfigObjectTree = storeQuantConfig(quantConfigAsStr, quantConfigSchemaName, udsEM)

      // Link QUANT CONFIG to quantitation DS
      udsQuantitation.putObject(quantConfigSchemaName.toString(), qtConfigObjectTree.getId())
      udsEM.merge(udsQuantitation)
      
      // Perform the quantitation
      // TODO: implement missing cases
      methodType match {
        case ATOM_LABELING => {}
        case ISOBARIC_TAG => {
          quantifyIsobaricTaggingMasterQC(udsMasterQuantChannels, udsQuantMethod, deserialize[IsobaricTaggingQuantConfig](quantConfigAsStr))
        }
        case LABEL_FREE => {
          abundanceUnit match {
            case AbundanceUnit.FEATURE_INTENSITY => {
              quantifyLabelFreeMasterQC(udsMasterQuantChannels, deserialize[LabelFreeQuantConfig](quantConfigAsStr))
            }
            case AbundanceUnit.SPECTRAL_COUNTS => {}
          }
        }
        case RESIDUE_LABELING => {}
      }
 

    }) // end of tryInTransactions
    
    txResult
  }

  protected def quantifyIsobaricTaggingMasterQC(
    udsMasterQuantChannels: List[UdsMasterQuantChannel],
    udsQuantMethod: UdsQuantMethod,
    isobaricQuantConfig: IsobaricTaggingQuantConfig
  ) {
    
    val isobaricTags = udsQuantMethod.getLabels.toList.map { udsQuantLabel =>
      
      IsobaricTag(
        id = udsQuantLabel.getId,
        name = udsQuantLabel.getName,
        properties =  deserialize[IsobaricTagProperties](udsQuantLabel.getSerializedProperties)
      )
    }
    
    // TODO: sort by number when the column has been added
    val quantMethod = IsobaricTaggingQuantMethod( isobaricTags.sortBy(_.reporterMz) )

    // Quantify each master quant channel
    for (udsMasterQuantChannel <- udsMasterQuantChannels) {
      
      // Finalize label-free quant config if it is provided
      /*val masterQcConfig = if( isobaricQuantConfig.labelFreeQuantConfig.isEmpty ) isobaricQuantConfig
      else {
        val lfQcConfig = this.finalizeLabelFreeQuantConfig(udsMasterQuantChannel, isobaricQuantConfig.labelFreeQuantConfig.get, lcMsRunProvider)
        isobaricQuantConfig.copy( labelFreeQuantConfig = Some(lfQcConfig) )
      }*/
      
      BuildMasterQuantChannelQuantifier(
        executionContext,
        udsMasterQuantChannel,
        experimentalDesign,
        quantMethod,
        isobaricQuantConfig
      ).quantify()

    }
  }
  
  protected def quantifyLabelFreeMasterQC(
    udsMasterQuantChannels: List[UdsMasterQuantChannel],
    lfQuantConfig: LabelFreeQuantConfig
  ) {
    
    // Quantify each master quant channel
    for (udsMasterQuantChannel <- udsMasterQuantChannels) {
      
      //val masterQcConfig = this.finalizeLabelFreeQuantConfig(udsMasterQuantChannel, lfQuantConfig, lcMsRunProvider)
      
      BuildMasterQuantChannelQuantifier(
        executionContext,
        udsMasterQuantChannel,
        experimentalDesign,
        LabelFreeQuantMethod,
        lfQuantConfig
      ).quantify()

    }
  }
  
  /*protected def finalizeLabelFreeQuantConfig(
    udsMasterQuantChannel: UdsMasterQuantChannel,
    lfQuantConfig: LabelFreeQuantConfig,
    lcMsRunProvider: IRunProvider
  ): LabelFreeQuantConfig = {
    
   // Retrieve master quant channels sorted by their number
    val sortedQuantChannels = udsMasterQuantChannel.getQuantitationChannels()

    // Retrieve run ids
    val runIds = sortedQuantChannels.map(_.getRun.getId).distinct

    // Load the LC-MS runs
    val runs = lcMsRunProvider.getRuns(runIds, loadScanSequence = false)

    // Clone the config and inject the missing parameters
    lfQuantConfig.copy(
      mapSetName = udsMasterQuantChannel.getName(),
      lcMsRuns = runs
    )
  }*/

  protected def getQuantConfigSchemaName(methodType: QuantMethodType.Value, abundanceUnit: AbundanceUnit.Value): UdsSchemaName = {
    methodType match {
      case ATOM_LABELING => UdsSchemaName.ATOM_LABELING_QUANT_CONFIG
      // TODO: rename into ISOBARIC_TAGGING
      case ISOBARIC_TAG => UdsSchemaName.ISOBARIC_TAGGING_QUANT_CONFIG
      case LABEL_FREE => {
        abundanceUnit match {
          case AbundanceUnit.FEATURE_INTENSITY => UdsSchemaName.LABEL_FREE_QUANT_CONFIG
          case AbundanceUnit.SPECTRAL_COUNTS => UdsSchemaName.SPECTRAL_COUNTING_QUANT_CONFIG
        }
      }
      case RESIDUE_LABELING => UdsSchemaName.RESIDUE_LABELING_QUANT_CONFIG
    }
  }

  protected def storeQuantConfig(quantConfigAsStr: String, schemaName: UdsSchemaName, udsEM: EntityManager): ObjectTree = {

    logger.info("Storing quantitation configuration with schema named: " + schemaName.toString())
    
    // Store the object tree
    val quantDSObjectTree = new ObjectTree()
    quantDSObjectTree.setSchema(ObjectTreeSchemaRepository.loadOrCreateObjectTreeSchema(udsEM, schemaName.toString()))
    quantDSObjectTree.setClobData(quantConfigAsStr)

    udsEM.persist(quantDSObjectTree)

    quantDSObjectTree
  }


}
