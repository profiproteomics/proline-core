package fr.proline.core.om.provider.msi

import fr.proline.core.om.model.msi.InstrumentConfig

trait IInstrumentConfigProvider {

  def getInstrumentConfigsAsOptions( instConfigIds: collection.Seq[Long] ): Array[Option[InstrumentConfig]]
  
  def getInstrumentConfigs( instConfigIds: collection.Seq[Long] ): Array[InstrumentConfig]
  
  def getInstrumentConfig( instConfigId: Long ): Option[InstrumentConfig] = { getInstrumentConfigsAsOptions( Seq(instConfigId) )(0) }
  
}