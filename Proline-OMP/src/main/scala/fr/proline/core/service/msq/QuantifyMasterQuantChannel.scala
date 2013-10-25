package fr.proline.core.service.msq

import fr.proline.api.service.IService
import fr.proline.context.IExecutionContext
import fr.proline.core.dal.ContextFactory
import fr.proline.core.om.model.msq.ExperimentalDesign
import fr.proline.core.orm.uds.MasterQuantitationChannel
import fr.proline.core.service.msq.quantify._
import fr.proline.repository.IDataStoreConnectorFactory
import fr.proline.core.algo.msq.SpectralCountConfig

class QuantifyMasterQuantChannel(
  executionContext: IExecutionContext,
  experimentalDesign: ExperimentalDesign,
  masterQuantChannelId: Long,
  quantConfig: AnyRef
) extends IService {
  
  private var _hasInitiatedExecContext: Boolean = false

  // Secondary constructor
  def this(
    dsFactory: IDataStoreConnectorFactory,
    projectId: Long,
    experimentalDesign: ExperimentalDesign,
    masterQuantChannelId: Long,
    quantConfig: AnyRef
  ) {
    this(
      ContextFactory.buildExecutionContext(dsFactory, projectId, true), // Force JPA context
      experimentalDesign,
      masterQuantChannelId,
      quantConfig
    )
    _hasInitiatedExecContext = true
  }
  
  def runService() = {
    
    // Get entity manager
    val udsEM = executionContext.getUDSDbConnectionContext().getEntityManager()
    
    // Retrieve the quantitation fraction
    val udsMasterQuantChannel = udsEM.find(classOf[MasterQuantitationChannel], masterQuantChannelId)    
    require( udsMasterQuantChannel != null,
             "undefined master quant channel with id=" + udsMasterQuantChannel )
    
    MasterQuantChannelQuantifier( executionContext, experimentalDesign, udsMasterQuantChannel, quantConfig ).quantify()
    
    // Close execution context if initiated locally
    if( this._hasInitiatedExecContext )
      executionContext.closeAll()
    
    true
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
  import fr.proline.core.algo.lcms.LabelFreeQuantConfig
  
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
    
    // TODO: create some enumerations
    if( abundanceUnit == AbundanceUnit.REPORTER_ION_INTENSITY.toString() ) {
    
    /*require Pairs::Msq::Module::Quantifier::ReporterIons
    fractionQuantifier = new Pairs::Msq::Module::Quantifier::ReporterIons(
                                  rdb_quantitation_fraction = rdbQuantFraction
                                  )*/
    
    } 
    else if( quantMethodType == QuantMethodType.LABEL_FREE.toString() ) {
      if( abundanceUnit == AbundanceUnit.FEATURE_INTENSITY.toString() ) {
        masterQuantChannelQuantifier = new Ms2DrivenLabelFreeFeatureQuantifier(
          executionContext = executionContext,
          experimentalDesign = experimentalDesign,
          udsMasterQuantChannel = udsMasterQuantChannel,
          quantConfig.asInstanceOf[LabelFreeQuantConfig]
        )
      }
      else if( abundanceUnit == AbundanceUnit.SPECTRAL_COUNTS.toString() ) {
        masterQuantChannelQuantifier = new SpectralCountQuantifier(
          executionContext = executionContext,
//          experimentalDesign = experimentalDesign,
          udsMasterQuantChannel = udsMasterQuantChannel,
          scConfig = quantConfig.asInstanceOf[SpectralCountConfig]
        )
      }
    }
    
    assert( masterQuantChannelQuantifier != null, "The needed quantifier is not yet implemented" )
    
    masterQuantChannelQuantifier

  }

}