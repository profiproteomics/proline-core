package fr.proline.core.om.model.msi

import com.fasterxml.jackson.databind.annotation.JsonDeserialize


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
 */
case class FragmentMatch(
  var label: String,
  @JsonDeserialize(contentAs = classOf[java.lang.String])
  var `type`: Option[String] = None,
  var moz: Double,
  var calculatedMoz: Double,
  var intensity: Float,
  @JsonDeserialize(contentAs = classOf[java.lang.Double])
  var neutralLossMass: Option[Double] = None
) {

  // Plain constructor needed for MessagePack
  def this() = this("", None, Double.NaN, Double.NaN, Float.NaN, None)

  @transient private var _ionSeries: String = _
  @transient private var _aaPosition: Int = 0
  @transient private var _charge: Int = 1

  private def _parseLabelIfNotDone() {
    if (_ionSeries == null) {
      val FullLabelRegex = """(\w+\*?)\((\d+)\)([\w\+]*)""".r
      FullLabelRegex.findAllIn(label).matchData.foreach { m =>
        // FIXME: why do we need to make this replacement here ?
        this._ionSeries = m.group(1).replace("0", "-H2O").replace("*", "-NH3")
        this._aaPosition = m.group(2).toInt

        if (m.group(3).length > 0)
          this._charge = m.group(3).length
      }
    }
  }

  // TODO: check usages
  //def ionSeries: String = { _parseLabelIfNotDone(); this._ionSeries }
  def ionSeries: FragmentIonSeries.Value = { _parseLabelIfNotDone(); FragmentIonSeries.withName(this._ionSeries) }
  def aaPosition: Int = { _parseLabelIfNotDone(); this._aaPosition }
  def charge: Int = { _parseLabelIfNotDone(); this._charge }

}

object FragmentMatchType extends Enumeration {
  type MatchType = Value
  val INTERNAL: FragmentMatchType.Value = Value("IN")
  val IMMONIUM: FragmentMatchType.Value = Value("IM")
}

/**
 * @param fragmentationTable
 * @param fragmentMatches
 */
//@Message
case class SpectrumMatch(
            @transient msQueryInitialId: Int,
            @transient peptideMatchRank: Int,
            var fragTable: Array[TheoreticalFragmentSeries],
            var fragMatches: Array[FragmentMatch]
) {
  // Plain constructor needed for MessagePack
  def this() = this(0, 0, Array.empty[TheoreticalFragmentSeries], Array.empty[FragmentMatch])
}

//@Message
case class TheoreticalFragmentSeries(
  var fragSeries: String, // begins by an ionSeries name and may be followed by a "++" for doubly charge state
  var masses: Array[Double]
) {

  // Plain constructor needed for MessagePack
  def this() = this("", Array.empty[Double])

  def this(fragSeries: String, masses: Array[Double], ionSeries: String, charge: Int) = {
    this(fragSeries,Array.empty[Double])
    _ionSeries = ionSeries
    _charge = charge
  }
  
  @transient lazy val isReverse: Boolean = Fragmentation.isReverseSeries(fragSeries)

  @transient private var _ionSeries: String = _
  @transient private var _charge: Int = 1

  private def _parseFragSeriesIfNotDone() {
    if (_ionSeries == null) {
      val z = {
        var index = fragSeries.length() -1
        var z = 0
        while((index >= 0) && fragSeries.charAt(index).equals('+')) {
          z += 1
          index -= 1
        }
        z
      }
      this._charge = if (z == 0) 1 else z
      val ionSeries = fragSeries.substring(0, fragSeries.length() - Math.max(0, z))

      // FIXME: why do we need to make this replacement here ?
      this._ionSeries = ionSeries.replace("0", "-H2O").replace("*", "-NH3")
    }
  }

  // TODO: check usages
  //def ionSeries: String = { _parseFragSeriesIfNotDone(); this._ionSeries }
  def ionSeries: FragmentIonSeries.Value = { 
    _parseFragSeriesIfNotDone()
    FragmentIonSeries.withName(this._ionSeries) 
  }
  
  def charge: Int = { _parseFragSeriesIfNotDone(); this._charge }
}


trait IRocCurve {
  def xValues: Array[Float]
  def yValues: Array[Float]
}

case class MsiRocCurve(
  xValues: Array[Float],
  yValues: Array[Float],
  thresholds: Array[Float]
) extends IRocCurve
