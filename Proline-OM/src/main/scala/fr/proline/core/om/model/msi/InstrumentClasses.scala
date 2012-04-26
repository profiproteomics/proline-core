package fr.proline.core.om.model.msi

import fr.proline.core.utils.misc.InMemoryIdGen

object Instrument extends InMemoryIdGen
case class Instrument(
                   // Required fields
                   val id: Int,
                   val name: String,
                   
                   // Immutable optional fields
                   val source: String = null
                   ) {
      
}

object InstrumentConfig extends InMemoryIdGen
case class InstrumentConfig(   
                   // Required fields
                   val id: Int,
                   val name: String,
                   val instrument: Instrument,
                   val ms1Analyzer: String,
                   val msnAnalyzer: String,
                   val activationType: String
                   ) {
      
}
  
