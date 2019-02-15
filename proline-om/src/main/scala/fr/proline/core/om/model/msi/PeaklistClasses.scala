package fr.proline.core.om.model.msi

import scala.beans.BeanProperty
import scala.collection.mutable.HashMap
import com.typesafe.scalalogging.LazyLogging
import fr.profi.util.misc.InMemoryIdGen
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

object Peaklist extends InMemoryIdGen

case class Peaklist(
  var id: Long,
  val fileType: String,
  val path: String,
  val rawFileIdentifier: String,
  val msLevel: Int,
  val spectrumDataCompression: String = "none",
  var peaklistSoftware: PeaklistSoftware = null,
  
  var properties: Option[PeaklistProperties] = None
)

case class PeaklistProperties (
  @BeanProperty var spectrumDataCompressionLevel: Option[Int] = None,
  @BeanProperty var putativePrecursorCharges: Option[Seq[Int]] = None,
  @BeanProperty var polarity: Option[Char] = None // +/-
)


object PeaklistSoftware extends InMemoryIdGen

case class PeaklistSoftware(
  var id: Long,
  val name: String,
  val version: String,
  var specTitleParsingRule: Option[SpectrumTitleParsingRule] = None,
  
  var properties: Option[PeaklistSoftwareProperties] = None
)

case class PeaklistSoftwareProperties()


object Spectrum extends InMemoryIdGen

case class Spectrum (
  var id: Long,
  val title: String,
  val precursorMoz: Double,
  val precursorIntensity: Float = Float.NaN,
  val precursorCharge: Int,
  val isSummed: Boolean = false,
  var firstCycle: Int = 0,
  var lastCycle: Int = 0,
  var firstScan: Int = 0,
  var lastScan: Int = 0,
  var firstTime: Float = -1,
  var lastTime: Float = -1,
  var mozList: Option[Array[Double]],
  var intensityList: Option[Array[Float]],
  val peaksCount: Int,
  val fragmentationRuleSetId: Option[Long],
  val peaklistId: Long,
  
  var properties: Option[SpectrumProperties] = None
)

case class SpectrumProperties(
   @JsonDeserialize(contentAs = classOf[java.lang.Float] )
   @BeanProperty var rtInSeconds: Option[Float] = None
)

object SpectrumTitleFields extends Enumeration {
  val RAW_FILE_IDENTIFIER = Value("RAW_FILE_IDENTIFIER")
  val FIRST_CYCLE = Value("FIRST_CYCLE")
  val LAST_CYCLE = Value("LAST_CYCLE")
  val FIRST_SCAN = Value("FIRST_SCAN")
  val LAST_SCAN = Value("LAST_SCAN")
  val FIRST_TIME = Value("FIRST_TIME")
  val LAST_TIME = Value("LAST_TIME")
}

object SpectrumTitleParsingRule extends InMemoryIdGen

case class SpectrumTitleParsingRule (
  val id: Long = SpectrumTitleParsingRule.generateNewId(),
  val rawFileIdentifierRegex: Option[String] = None,
  val firstCycleRegex: Option[String] = None,
  val lastCycleRegex: Option[String] = None,
  val firstScanRegex: Option[String] = None,
  val lastScanRegex: Option[String] = None,
  val firstTimeRegex: Option[String] = None,
  val lastTimeRegex: Option[String] = None
) extends LazyLogging {
  
  def getFieldNames() = SpectrumTitleFields.values.toArray.map(_.toString())
  
  lazy val regexByFieldName: Map[SpectrumTitleFields.Value,String] = {
    val tmpMap = Map.newBuilder[SpectrumTitleFields.Value,String]
    
    rawFileIdentifierRegex.map( rx => tmpMap += SpectrumTitleFields.RAW_FILE_IDENTIFIER -> rx )
    firstCycleRegex.map( rx => tmpMap += SpectrumTitleFields.FIRST_CYCLE -> rx )
    lastCycleRegex.map( rx => tmpMap += SpectrumTitleFields.LAST_CYCLE -> rx )
    firstScanRegex.map( rx => tmpMap += SpectrumTitleFields.FIRST_SCAN -> rx )
    lastScanRegex.map( rx => tmpMap += SpectrumTitleFields.LAST_SCAN -> rx )
    firstTimeRegex.map( rx => tmpMap += SpectrumTitleFields.FIRST_TIME -> rx )
    lastTimeRegex.map( rx => tmpMap += SpectrumTitleFields.LAST_TIME -> rx )
    
    tmpMap.result
  }
  
  def parseTitle(title: String): Map[SpectrumTitleFields.Value,String] = {
    
    import fr.profi.util.regex.RegexUtils._
    
    // Parse all spectrum title fields defined in the specTitleParsingRule
    val specTitleFieldMap = new HashMap[SpectrumTitleFields.Value,String]
    
    var lastThrowable: Throwable = null
    
    for( (fieldName, fieldRegex) <- this.regexByFieldName ) {
      
      val fieldNameAsStr = fieldName.toString
      var fieldFound = false
      
      fieldRegex.split("""\|\|""").foreach { atomicFieldRegex =>
        
        if (atomicFieldRegex.nonEmpty && fieldFound == false) {
          
          // Try to capture the corresponding regex group
          try {
            val matcherOpt = title =# (atomicFieldRegex,fieldNameAsStr)
            
            if( matcherOpt.isDefined && matcherOpt.get.groupNames.contains(fieldNameAsStr) ) {
              specTitleFieldMap.put( fieldName, matcherOpt.get.group(fieldNameAsStr) )
              fieldFound = true
            }
          } catch {
            
            case t: Throwable => {
              lastThrowable = t
              //logger.trace("Error during spectrum title parsing")
            }
          }
        }
      }
    }
    
    if (lastThrowable != null ) {
      lastThrowable match {
        case ae: ArrayIndexOutOfBoundsException => {
          logger.trace("Bug in scala.util.matching.Regex", ae)
        }
        case t: Throwable => {
          /* Log this one as WARN with full Exception stack-trace */
          logger.warn("Last error while parsing spectrum title", t )
        }
      }
    }
    
    Map() ++ specTitleFieldMap
    
  }
  
}

