package fr.proline.core.om.model.msi

import scala.collection.mutable.HashMap
import scala.beans.BeanProperty
//import com.fasterxml.jackson.annotation.JsonInclude
//import com.fasterxml.jackson.annotation.JsonInclude.Include
import fr.proline.util.misc.InMemoryIdGen

object Peaklist extends InMemoryIdGen

//@JsonInclude( Include.NON_NULL )
case class Peaklist(
  var id: Long,
  val fileType: String,
  val path: String,
  val rawFileName: String,
  val msLevel: Int,
  val spectrumDataCompression: String = "none",
  var peaklistSoftware: PeaklistSoftware = null,
  
  var properties: Option[PeaklistProperties] = None
)

//@JsonInclude( Include.NON_NULL )
case class PeaklistProperties (
  @BeanProperty var spectrumDataCompressionLevel: Option[Int] = None,
  @BeanProperty var putativePrecursorCharges: Option[Seq[Int]] = None,
  @BeanProperty var polarity: Option[Char] = None // +/-
)


object PeaklistSoftware extends InMemoryIdGen

//@JsonInclude( Include.NON_NULL )
case class PeaklistSoftware(
  var id: Long,
  val name: String,
  val version: String,
  var specTitleParsingRule: Option[SpectrumTitleParsingRule] = None,
  
  var properties: Option[PeaklistSoftwareProperties] = None
)

//@JsonInclude( Include.NON_NULL )
case class PeaklistSoftwareProperties


object Spectrum extends InMemoryIdGen

//@JsonInclude( Include.NON_NULL )
case class Spectrum (
  var id: Long,
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
  val instrumentConfigId: Long,
  val peaklistId: Long,
  
  var properties: Option[SpectrumProperties] = None
)

//@JsonInclude( Include.NON_NULL )
case class SpectrumProperties

object SpectrumTitleFields extends Enumeration {
  val RAW_FILE_NAME = Value("RAW_FILE_NAME")
  val FIRST_CYCLE = Value("FIRST_CYCLE")
  val LAST_CYCLE = Value("LAST_CYCLE")
  val FIRST_SCAN = Value("FIRST_SCAN")
  val LAST_SCAN = Value("LAST_SCAN")
  val FIRST_TIME = Value("FIRST_TIME")
  val LAST_TIME = Value("LAST_TIME")
}

object SpectrumTitleParsingRule extends InMemoryIdGen

//@JsonInclude( Include.NON_NULL )
case class SpectrumTitleParsingRule (
  val id: Long = SpectrumTitleParsingRule.generateNewId(),
  val rawFileNameRegex: Option[String] = None,
  val firstCycleRegex: Option[String] = None,
  val lastCycleRegex: Option[String] = None,
  val firstScanRegex: Option[String] = None,
  val lastScanRegex: Option[String] = None,
  val firstTimeRegex: Option[String] = None,
  val lastTimeRegex: Option[String] = None
) {
  
  def getFieldNames() = SpectrumTitleFields.values.toArray.map(_.toString())
  
  lazy val regexByFieldName: Map[SpectrumTitleFields.Value,String] = {
    val tmpMap = Map.newBuilder[SpectrumTitleFields.Value,String]
    
    rawFileNameRegex.map( rx => tmpMap += SpectrumTitleFields.RAW_FILE_NAME -> rx )
    firstCycleRegex.map( rx => tmpMap += SpectrumTitleFields.FIRST_CYCLE -> rx )
    lastCycleRegex.map( rx => tmpMap += SpectrumTitleFields.LAST_CYCLE -> rx )
    firstScanRegex.map( rx => tmpMap += SpectrumTitleFields.FIRST_SCAN -> rx )
    lastScanRegex.map( rx => tmpMap += SpectrumTitleFields.LAST_SCAN -> rx )
    firstTimeRegex.map( rx => tmpMap += SpectrumTitleFields.FIRST_TIME -> rx )
    lastTimeRegex.map( rx => tmpMap += SpectrumTitleFields.LAST_TIME -> rx )
    
    tmpMap.result
  }
  
  def parseTitle(title: String): Map[SpectrumTitleFields.Value,String] = {
    
    import fr.proline.util.regex.RegexUtils._
    
    // Parse all spectrum title fields defined in the specTitleParsingRule
    val specTitleFieldMap = new HashMap[SpectrumTitleFields.Value,String]
    
    for( (fieldName, fieldRegex) <- this.regexByFieldName ) {
      
      fieldRegex.split("""\|\|""").foreach { atomicFieldRegex =>
        
        if( specTitleFieldMap.contains(fieldName) == false ) {
          val fieldNameAsStr = fieldName.toString
          
          // Try to capture the corresponding regex group
          val matches = title =# (atomicFieldRegex,fieldNameAsStr)
          
          if( matches.isDefined) {
            specTitleFieldMap += fieldName -> matches.get.group(fieldNameAsStr)
          }
        }
      }
    }
    
    Map() ++ specTitleFieldMap
    
  }
  
}

