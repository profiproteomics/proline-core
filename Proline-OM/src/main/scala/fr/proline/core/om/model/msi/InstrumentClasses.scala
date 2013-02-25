package fr.proline.core.om.model.msi

import scala.reflect.BeanProperty
import com.codahale.jerkson.JsonSnakeCase
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include
import fr.proline.util.misc.InMemoryIdGen

@deprecated("use ORM ActivationType enumeration instead","0.0.7")
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
                   val id: Int,
                   val name: String,
                   
                   // Immutable optional fields
                   val source: String = null,
                   
                   var properties: Option[InstrumentProperties] = None
                   ) {
      
}

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class InstrumentProperties


object InstrumentConfig extends InMemoryIdGen {
  
  def makeName( instrumentName: String, activationType: String, 
                ms1Analyzer: String, msnAnalyzer: String
              ): String = {
    "%s (A1=%s F=%s A2=%s)".format( instrumentName, ms1Analyzer, activationType, msnAnalyzer )
  }
}

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class InstrumentConfig(
                   // Required fields
                   val id: Int,
                   val name: String,
                   var instrument: Instrument,
                   val ms1Analyzer: String,
                   val msnAnalyzer: String,
                   var activationType: String,
                   var fragmentationRules: Option[Array[FragmentationRule]] = None,
                   
                   var properties: Option[InstrumentConfigProperties] = None
                   ) {
  
  // Secondary constructor were the name is automatically built
  def this( id: Int, instrument: Instrument, ms1Analyzer: String, msnAnalyzer: String,
            activationType: String, fragmentationRules: Option[Array[FragmentationRule]] = None ) {
    this( id,InstrumentConfig.makeName(instrument.name,activationType,ms1Analyzer,msnAnalyzer),
          instrument,ms1Analyzer,msnAnalyzer,activationType,fragmentationRules)
  }
      
}

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class InstrumentConfigProperties ( 
  @BeanProperty val isHidden: Boolean
)
