package fr.proline.core.om.provider.msi

import fr.proline.core.om.model.msi.Instrument

trait IInstrumentProvider {

  def getInstrumentsAsOptions( instrumentIds: Seq[Int] ): Array[Option[Instrument]]
  
  def getInstruments( instrumentIds: Seq[Int] ): Array[Instrument]
  
  def getInstrumentAsOption( instrumentId: Int ): Option[Instrument] = { getInstrumentsAsOptions( Seq(instrumentId) )(0) }

  def getInstrument( instrumentId: Int ): Instrument = { getInstruments( Seq(instrumentId) )(0) }
  
}