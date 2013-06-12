package fr.proline.core.om.provider.msi

import fr.proline.core.om.model.msi.InstrumentConfig

trait IInstrumentConfigProvider {

  def getInstrumentConfigsAsOptions( instConfigIds: Seq[Long] ): Array[Option[InstrumentConfig]]
  
  def getInstrumentConfigs( instConfigIds: Seq[Long] ): Array[InstrumentConfig]
  
  def getInstrumentConfig( instConfigId: Long ): Option[InstrumentConfig] = { getInstrumentConfigsAsOptions( Seq(instConfigId) )(0) }
  
}