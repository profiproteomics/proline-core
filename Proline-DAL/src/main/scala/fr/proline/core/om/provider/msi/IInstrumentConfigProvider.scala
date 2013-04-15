package fr.proline.core.om.provider.msi

import fr.proline.core.om.model.msi.InstrumentConfig

trait IInstrumentConfigProvider {

  def getInstrumentConfigsAsOptions( instConfigIds: Seq[Int] ): Array[Option[InstrumentConfig]]
  
  def getInstrumentConfigs( instConfigIds: Seq[Int] ): Array[InstrumentConfig]
  
  def getInstrumentConfigAsOption( instConfigId: Int ): Option[InstrumentConfig] = { getInstrumentConfigsAsOptions( Seq(instConfigId) )(0) }

  def getInstrumentConfig( instConfigId: Int ): InstrumentConfig = { getInstrumentConfigs( Seq(instConfigId) )(0) }
  
}