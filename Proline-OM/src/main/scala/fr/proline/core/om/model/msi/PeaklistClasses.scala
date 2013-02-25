package fr.proline.core.om.model.msi

import scala.reflect.BeanProperty

import com.codahale.jerkson.JsonSnakeCase
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include

import fr.proline.util.misc.InMemoryIdGen


object Peaklist extends InMemoryIdGen

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class Peaklist(
  var id: Int,
  val fileType: String,
  val path: String,
  val rawFileName: String,
  val msLevel: Int,
  val spectrumDataCompression: String = "none",
  var peaklistSoftware: PeaklistSoftware = null,
  
  var properties: Option[PeaklistProperties] = None
)

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class PeaklistProperties (
  @BeanProperty var spectrumDataCompressionLevel: Option[Int] = None,
  @BeanProperty var putativePrecursorCharges: Option[Seq[Int]] = None,
  @BeanProperty var polarity: Option[Char] = None // +/-
)


object PeaklistSoftware extends InMemoryIdGen

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class PeaklistSoftware(
  var id: Int,
  val name: String,
  val version: String,
  
  var properties: Option[PeaklistSoftwareProperties] = None
)

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class PeaklistSoftwareProperties


object Spectrum extends InMemoryIdGen

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class Spectrum (
  var id: Int,
  val title: String,
  val precursorMoz: Double,
  val precursorIntensity: Float = Float.NaN,
  val precursorCharge: Int,
  val isSummed: Boolean = false,
  val firstCycle: Int = 0,
  val lastCycle: Int = 0,
  val firstScan: Int = 0,
  val lastScan: Int = 0,
  val firstTime: Float = 0,
  val lastTime: Float = 0,
  var mozList: Option[Array[Double]],
  var intensityList: Option[Array[Float]],
  val peaksCount: Int,
  val instrumentConfigId: Int,
  val peaklistId: Int,
  
  var properties: Option[SpectrumProperties] = None
)

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class SpectrumProperties
