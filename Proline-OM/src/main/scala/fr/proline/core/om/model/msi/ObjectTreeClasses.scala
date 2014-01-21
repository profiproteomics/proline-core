package fr.proline.core.om.model.msi

import scala.beans.BeanProperty
//import com.fasterxml.jackson.annotation.JsonInclude
//import com.fasterxml.jackson.annotation.JsonInclude.Include
//import org.msgpack.annotation.Message

/** 
* @param label 
* @param aaPosition 
* @param type 
* @param charge 
* @param moz 
* @param calculatedMoz 
* @param intensity 
* @param neutralLossMass 
* @param theoreticalFragmentId 
**/
//@JsonInclude( Include.NON_NULL )
//@Message
case class FragmentMatch (  
  var label: String,
  //val ionSeries: String,
  //val aaPosition: Int,
  var `type`: Option[String] = None,
  //var charge: Int,
  var moz: Double,
  var calculatedMoz: Double,
  var intensity: Float,
  var neutralLossMass: Option[Double] = None
) {
  
  // Plain constructor needed for MessagePack
  def this() = this("",None,Double.NaN,Double.NaN,Float.NaN,None)
  
  @transient private var _ionSeries: String = null
  @transient private var _aaPosition: Int = 0
  @transient private var _charge: Int = 1
  
  private def _parseLabelIfNotDone() {
    if( _ionSeries == null ) {
      val FullLabelRegex = """(\w+)\((\d+)\)([\w\+]*)""".r      
      val FullLabelRegex(ionSeries, aaPositionStr, chargeStr) = label
      
      this._ionSeries = ionSeries
      this._aaPosition = aaPositionStr.toInt
      
      if( chargeStr.length > 0 )
        this._charge = chargeStr.length
    }
  }
  
  def ionSeries: String = {_parseLabelIfNotDone(); this._ionSeries}
  def aaPosition: Int = {_parseLabelIfNotDone(); this._aaPosition}
  def charge: Int = {_parseLabelIfNotDone(); this._charge}
  
}

object FragmentMatchType extends Enumeration {
  val INTERNAL = Value("IN")
  val IMMONIUM = Value("IM")
}

/** 
* @param fragmentationTable 
* @param fragmentMatches 
**/
//@JsonInclude( Include.NON_NULL )
//@Message
case class SpectrumMatch (
  @transient val msQueryInitialId: Int,
  @transient val peptideMatchRank: Int,
  var fragTable: Array[TheoreticalFragmentSeries],
  var fragMatches: Array[FragmentMatch]
) {
  // Plain constructor needed for MessagePack
  def this() = this(0,0,Array.empty[TheoreticalFragmentSeries],Array.empty[FragmentMatch])
}

//@JsonInclude( Include.NON_NULL )
//@Message
case class TheoreticalFragmentSeries (
  var fragSeries: String, // begins by an ionSeries name and may be followed by a "++" for doubly charge state
  var masses: Array[Double]
) {
  
  // Plain constructor needed for MessagePack
  def this() = this("",Array.empty[Double])
  
  @transient lazy val isReverse = Fragmentation.isReverseSeries(fragSeries)
  
  @transient private var _ionSeries: String = null
  @transient private var _charge: Int = 1
  
  private def _parseFragSeriesIfNotDone() {
    if( _ionSeries == null ) {
      val FragSeriesRegex = """(\w+\*?)([+]*).*""".r
      val FragSeriesRegex(ionSeries, chargeStr) = fragSeries
      
      this._ionSeries = ionSeries.replace("0","-H2O").replace("*","-NH3")
      
      if( chargeStr.length > 0 )
        this._charge = chargeStr.length
    }
  }
  
  def ionSeries: String = {_parseFragSeriesIfNotDone(); this._ionSeries}
  def charge: Int = {_parseFragSeriesIfNotDone(); this._charge}
}





