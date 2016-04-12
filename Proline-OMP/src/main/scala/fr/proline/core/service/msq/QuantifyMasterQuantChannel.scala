package fr.proline.core.service.msq

/*
import fr.proline.api.service.IService
import fr.proline.context.IExecutionContext
import fr.proline.core.algo.msq.config._
import fr.proline.core.dal.BuildLazyExecutionContext
import fr.proline.core.om.model.msq._
import fr.proline.core.orm.uds.MasterQuantitationChannel
import fr.proline.core.service.msq.quantify._
import fr.proline.repository.IDataStoreConnectorFactory

class QuantifyMasterQuantChannel(
  executionContext: IExecutionContext,
  experimentalDesign: ExperimentalDesign,
  masterQuantChannelId: Long,
  quantConfig: AnyRef
) extends IService {
  
  private var _hasInitiatedExecContext: Boolean = false
  private var _usedMasterQuantChannelQuantifier : AbstractMasterQuantChannelQuantifier = null
  
  // Secondary constructor
  def this(
    dsFactory: IDataStoreConnectorFactory,
    projectId: Long,
    experimentalDesign: ExperimentalDesign,
    masterQuantChannelId: Long,
    quantConfig: IQuantConfig
  ) {
    this(
      BuildLazyExecutionContext(dsFactory, projectId, true), // Force JPA context
      experimentalDesign,
      masterQuantChannelId,
      quantConfig
    )
    _hasInitiatedExecContext = true
  }
  
  
  def runService() = {
    
    // Get entity manager
    val udsEM = executionContext.getUDSDbConnectionContext().getEntityManager()
    
    // Retrieve the master quant channel
    val udsMasterQuantChannel = udsEM.find(classOf[MasterQuantitationChannel], masterQuantChannelId)
    require( udsMasterQuantChannel != null, "undefined master quant channel with id=" + masterQuantChannelId )
    
      
   _usedMasterQuantChannelQuantifier = MasterQuantChannelQuantifier(
     executionContext,
     experimentalDesign,
     udsMasterQuantChannel,
     quantConfig
   )
   _usedMasterQuantChannelQuantifier.quantify()
    
    // Close execution context if initiated locally
    if( this._hasInitiatedExecContext )
      executionContext.closeAll()
    
    true
  }
  
  def getResultAsJSONString(): String = {
    _usedMasterQuantChannelQuantifier.getResultAsJSON()
  } 

}

object AbundanceUnit extends Enumeration {
  val FEATURE_INTENSITY = Value("feature_intensity")
  val REPORTER_ION_INTENSITY = Value("reporter_ion_intensity")
  val SPECTRAL_COUNTS = Value("spectral_counts")
}

object QuantMethodType extends Enumeration {
  val ATOM_LABELING = Value("atom_labeling")
  val ISOBARIC_LABELING = Value("isobaric_labeling")
  val LABEL_FREE = Value("label_free")
  val RESIDUE_LABELING = Value("residue_labeling")
}

object MasterQuantChannelQuantifier {
  
  import javax.persistence.EntityManager
  import fr.proline.core.algo.msq.config.LabelFreeQuantConfig
  
  def apply(    
    executionContext: IExecutionContext,
    experimentalDesign: ExperimentalDesign,
    udsMasterQuantChannel: MasterQuantitationChannel,
    quantConfig: AnyRef
  ): AbstractMasterQuantChannelQuantifier = {
    
        
    val udsQuantMethod = udsMasterQuantChannel.getDataset.getMethod
    val quantMethodType = udsQuantMethod.getType
    val abundanceUnit = udsQuantMethod.getAbundanceUnit
     
    var masterQuantChannelQuantifier: AbstractMasterQuantChannelQuantifier = null
 
    if( quantMethodType == QuantMethodType.LABEL_FREE.toString() && ( abundanceUnit == AbundanceUnit.SPECTRAL_COUNTS.toString())) {
       masterQuantChannelQuantifier = new WeightedSpectralCountQuantifier(
          executionContext = executionContext,
          udsMasterQuantChannel = udsMasterQuantChannel,
          quantConfig = quantConfig.asInstanceOf[SpectralCountConfig]
        )
    }
    
    assert( masterQuantChannelQuantifier != null, "QuantifyMasterQuantChannel should be used for WeightedSpectralCountQuantifier Only !" )
    
    masterQuantChannelQuantifier

  }

}*/