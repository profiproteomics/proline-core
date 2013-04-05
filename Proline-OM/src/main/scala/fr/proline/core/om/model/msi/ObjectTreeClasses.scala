package fr.proline.core.om.model.msi

import scala.reflect.BeanProperty
import com.codahale.jerkson.JsonSnakeCase
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include

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
@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class FragmentMatch (  
  var label: String,
  //val ionSeries: String,
  //val aaPosition: Int,
  var `type`: String = FragmentMatchType.REGULAR.toString,
  //var charge: Int,
  var moz: Double,
  var calculatedMoz: Double,  
  var intensity: Float,
  var neutralLossMass: Option[Double] = None
) {
  
  @transient private var _ionSeries: String = null
  @transient private var _aaPosition: Int = 0
  @transient private var _charge: Int = 1
  
  private def _parseLabelIfNotDone() {
    if( _ionSeries == null ) {
      val FullLabelRegex = """(\w+)\((\d+)\)(\w*)""".r      
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
  val REGULAR = Value("REGULAR")
  val INTERNAL = Value("INTERNAL")
  val IMMONIUM = Value("IMMONIUM")
}

/** 
* @param fragmentationTable 
* @param fragmentMatches 
**/
@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class SpectrumMatch (
  val msQueryInitialId: Int,
  val fragmentationTable: Array[TheoreticalFragmentSeries],
  val fragmentMatches: Array[FragmentMatch]
)

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class TheoreticalFragmentSeries (
  val fragSeries: String, // begins by an ionSeries name and may be followed by a "++" for doubly charge state
  val masses: Array[Double]
) {
  @transient lazy val isReverse = Fragmentation.isReverseSeries(fragSeries)
  
  @transient private var _ionSeries: String = null
  @transient private var _charge: Int = 1
  
  private def _parseFragSeriesIfNotDone() {
    if( _ionSeries == null ) {
      val FragSeriesRegex = """(\w+)([+]*).*""".r      
      val FragSeriesRegex(ionSeries, chargeStr) = fragSeries
      
      this._ionSeries = ionSeries.replace("0","-H2O").replace("*","-NH3")
      
      if( chargeStr.length > 0 )
        this._charge = chargeStr.length
    }
  }
  
  def ionSeries: String = {_parseFragSeriesIfNotDone(); this._ionSeries}
  def charge: Int = {_parseFragSeriesIfNotDone(); this._charge}
}





