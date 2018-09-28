package fr.proline.core.om.model.msi

import scala.beans.BeanProperty
import fr.profi.util.misc.InMemoryIdGen

object Activation extends Enumeration {
  type Type = Value
  val CID = Value("CID")
  val ECD = Value("ECD")
  val ETD = Value("ETD")
  val HCD = Value("HCD")
  val PSD = Value("PSD")
}

object Instrument extends InMemoryIdGen
case class Instrument(
   // Required fields
   val id: Long,
   val name: String,
   // Immutable optional fields
   val source: String = null,
   
   var properties: Option[InstrumentProperties] = None
) 

case class InstrumentProperties()


object InstrumentConfig extends InMemoryIdGen {
  
  def makeName( instrumentName: String, activationType: String, 
                ms1Analyzer: String, msnAnalyzer: String
              ): String = {
    "%s (A1=%s F=%s A2=%s)".format( instrumentName, ms1Analyzer, activationType, msnAnalyzer )
  }
}

case class InstrumentConfig(
  // Required fields
  val id: Long,
  val name: String,
  var instrument: Instrument,
  val ms1Analyzer: String,
  val msnAnalyzer: String,
  var activationType: String,
  var properties: Option[InstrumentConfigProperties] = None
) {
  
  // Secondary constructors were the name is automatically built
  def this( id: Long, instrument: Instrument, ms1Analyzer: String, msnAnalyzer: String,
            activationType: String) {
    this( id,InstrumentConfig.makeName(instrument.name,activationType,ms1Analyzer,msnAnalyzer),
          instrument,ms1Analyzer,msnAnalyzer,activationType)
  }

}

case class InstrumentConfigProperties ( 
  @BeanProperty val isHidden: Boolean
)
