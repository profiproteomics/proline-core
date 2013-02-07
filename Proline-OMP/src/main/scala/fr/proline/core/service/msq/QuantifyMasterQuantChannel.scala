package fr.proline.core.service.msq

import fr.proline.api.service.IService
import fr.proline.core.orm.uds.MasterQuantitationChannel
import fr.proline.repository.IDataStoreConnectorFactory

class QuantifyMasterQuantChannel( dbManager: IDataStoreConnectorFactory, masterQuantChannelID: Int ) extends IService {
  
  def runService() = {
    
    // Create entity manager
    val udsEM = dbManager.getUdsDbConnector.getEntityManagerFactory.createEntityManager()
    
    // Retrieve the quantitation fraction
    val udsMasterQuantChannel = udsEM.find(classOf[MasterQuantitationChannel], masterQuantChannelID)    
    require( udsMasterQuantChannel != null,
             "undefined quantitation fraction with id=" + udsMasterQuantChannel )
    
    MasterQuantChannelQuantifier( dbManager, udsEM, udsMasterQuantChannel ).quantify()
    
    // Close entity manager
    udsEM.close()
    
    true
  }

}


object AbundanceUnit extends Enumeration {
  val FEATURE = Value("feature")
  val REPORTER_ION = Value("reporter_ion")  
  val SPECTRAL_COUNTS = Value("spectral_counts")
}

object QuantMethodType extends Enumeration {
  val ATOM_LABELING = Value("atom_labeling")
  val ISOBARIC_LABELING = Value("isobaric_labeling")
  val LABEL_FREE = Value("label_free")
  val RESIDUE_LABELING = Value("residue_labeling")
  val SPECTRAL_COUNTING = Value("spectral_counting")
}

object MasterQuantChannelQuantifier {
  
  import javax.persistence.EntityManager
  import fr.proline.core.service.msq.impl._
  
  def apply( dbManager: IDataStoreConnectorFactory,
             udsEm: EntityManager,
             udsMasterQuantChannel: MasterQuantitationChannel ): IQuantifier = {
    
    val udsQuantMethod = udsMasterQuantChannel.getDataset.getMethod
    val quantMethodType = udsQuantMethod.getType
    val abundanceUnit = udsQuantMethod.getAbundanceUnit
    
    var masterQuantChannelQuantifier: IQuantifier = null
    
    // TODO: create some enumerations
    if( abundanceUnit == AbundanceUnit.REPORTER_ION ) {      
    
    /*require Pairs::Msq::Module::Quantifier::ReporterIons
    fractionQuantifier = new Pairs::Msq::Module::Quantifier::ReporterIons(
                                  rdb_quantitation_fraction = rdbQuantFraction
                                  )*/
    
    } 
    else if( quantMethodType == QuantMethodType.LABEL_FREE ) {
      if( abundanceUnit == AbundanceUnit.FEATURE ) {
        masterQuantChannelQuantifier = new Ms1DrivenLabelFreeFeatureQuantifier(
                                   dbManager = dbManager,
                                   udsEm = udsEm,
                                   udsMasterQuantChannel = udsMasterQuantChannel
                                 )
      }
      else if( abundanceUnit == AbundanceUnit.SPECTRAL_COUNTS ) {
        masterQuantChannelQuantifier = new SpectralCountQuantifier(
                                   dbManager = dbManager,
                                   udsEm = udsEm,
                                   udsMasterQuantChannel = udsMasterQuantChannel
                                 )
      }
    }
    
    assert( masterQuantChannelQuantifier != null, "The needed quantifier is not yet implemented" )
    
    masterQuantChannelQuantifier

  }

}