package fr.proline.core.om.provider.uds

import fr.proline.core.om.model.msi.Instrument

trait IInstrumentProvider {

  def getInstrumentsAsOptions( instrumentIds: Seq[Long] ): Array[Option[Instrument]]
  
  def getInstruments( instrumentIds: Seq[Long] ): Array[Instrument]
  
  def getInstrumentAsOption( instrumentId: Long ): Option[Instrument] = { getInstrumentsAsOptions( Seq(instrumentId) )(0) }

  def getInstrument( instrumentId: Long ): Instrument = { getInstruments( Seq(instrumentId) )(0) }
  
}