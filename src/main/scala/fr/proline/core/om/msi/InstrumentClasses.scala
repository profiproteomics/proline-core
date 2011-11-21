package fr.proline.core.om.msi

package InstrumentClasses {

  class Instrument(
                   // Required fields
                   val name: String,
                   
                   // Immutable optional fields
                   val source: String = null
                   ) {
      
  }
  
  class InstrumentConfig(   
                   // Required fields
                   val id: Int,
                   val name: String,
                   val instrument: Instrument,
                   val ms1_analyzer: String,
                   val msn_analyzer: String,
                   val activation_type: String
                   ) {
      
  }
  
}