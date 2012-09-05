package fr.proline.core.parser.lcms.impl

import java.util.Date
import fr.proline.core.om.model.lcms.RunMap

import scala.collection.mutable.ArrayBuffer
import scala.reflect.BeanProperty

import com.codahale.jerkson.JsonSnakeCase
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.codahale.jerkson.Json.parse

import fr.proline.core.parser.lcms.{ ILcmsMapFileParser, ExtraParameters }
import fr.proline.core.om.model.lcms.{ Feature, IsotopicPattern, RunMap, LcmsRun, Peak, FeatureRelations, FeatureProperties, PeakPickingSoftware }

/**
 * mzDBaccess file parser
 */

//define some case class to read json encoded properties and not pollute model
@JsonSnakeCase
@JsonInclude(Include.NON_NULL)
case class mzPeak (
  @BeanProperty var moz: Double,
  @BeanProperty var intensity: Float, 
  @BeanProperty var leftHwhm: Float, 
  @BeanProperty var rightHwhm: Float) {
  
  def toPeak(): Peak = {
    return new Peak(moz = moz,
      intensity = intensity,
      leftHwhm = leftHwhm,
      rightHwhm = rightHwhm)
  }
}

@JsonSnakeCase
@JsonInclude(Include.NON_NULL)
case class mzIsotopicPattern (

  @BeanProperty var scanId: Int = 0,
  @BeanProperty var mz: Double = Double.NaN,
  @BeanProperty var intensity: Float = Float.NaN,
  @BeanProperty var charge: Int = 0,
  @BeanProperty var peaks: Option[Array[mzPeak]] = None) {

  def toIsotopicPattern(): IsotopicPattern = {
    return new IsotopicPattern(//id = 0,
      moz = mz,
      intensity = intensity,
      charge = charge,
      fitScore = Float.NaN,
      peaks = peaks.get.map(p => p.toPeak),
      scanInitialId = scanId,
      overlappingIPs = Array[IsotopicPattern]())
  }
}

@JsonSnakeCase
@JsonInclude(Include.NON_NULL)
case class mzFeature (

  @BeanProperty var apexIp: mzIsotopicPattern = null,
  @BeanProperty var apexScan: Int = 0,
  @BeanProperty var firstScan: Int = 0,
  @BeanProperty var lastScan: Int = 0,
  @BeanProperty var elutionTime: Float = Float.NaN,
  @BeanProperty var charge: Int = 0,
  @BeanProperty var moz: Double = Double.NaN,
  @BeanProperty var area: Float = Float.NaN,
  @BeanProperty var qualityScore: Float = Float.NaN,
  @BeanProperty var peakelRatios: Float = Float.NaN,
  @BeanProperty var peakelCount: Int = 0,
  @BeanProperty var ms1Count: Int = 0,
  @BeanProperty var ms2Count: Int = 0,
  @BeanProperty var overlapFactor: Float = Float.NaN,
  @BeanProperty var overlapCorreltation: Float = Float.NaN,
  @BeanProperty var overlappingFeature: Option[mzFeature] = None,
  @BeanProperty var isotopicPatterns: Option[Array[mzIsotopicPattern]] = None) {

  def toFeature(lcmsRun: LcmsRun, id: Int, ms2Events: Array[Int]): Feature = {

    return Feature(id = id,
      moz = moz,
      intensity = area,
      charge = charge,
      elutionTime = elutionTime,
      qualityScore = qualityScore,
      ms1Count = ms1Count,
      ms2Count = ms2Count,
      isOverlapping = if (overlappingFeature != None) true else false,
      isotopicPatterns = if (isotopicPatterns != None) Some(Array[IsotopicPattern](apexIp.toIsotopicPattern) ++ isotopicPatterns.get.map(ip => ip.toIsotopicPattern)) else Some(Array[IsotopicPattern](apexIp.toIsotopicPattern)),
      overlappingFeatures = if (overlappingFeature.get != None) Array[Feature](overlappingFeature.get.toFeature(lcmsRun, id, ms2Events)) else Array[Feature](),
      relations = FeatureRelations(ms2EventIds = ms2Events,
        firstScanInitialId = lcmsRun.scanById.get(firstScan).get.initialId,
        lastScanInitialId = lcmsRun.scanById.get(lastScan).get.initialId,
        apexScanInitialId = lcmsRun.scanById.get(apexScan).get.initialId))

  }
}

object mzTSVParser {
  val sepChar = "\t"
}

class mzTSVParser extends ILcmsMapFileParser {

  def getRunMap(filePath: String, lcmsRun: LcmsRun, extraParams: ExtraParameters): Option[RunMap] = {

    val lineIterator = io.Source.fromFile(filePath).getLines()
    val columnNames = lineIterator.next.stripLineEnd.split(mzTSVParser.sepChar)
    var features = new ArrayBuffer[Feature]

    def treatOneLine(data: Map[String, String]): Unit = {

      var id = data("id").toInt
      var mz = data("mz").toDouble
      var area = data("area").toFloat
      var intensity = data("intensity_sum").toFloat
      var qualityScore = data("quality_score").toFloat
      var elutionTime = data("elution_time").toFloat
      var charge = data("charge").toInt
      var firstScanId = data("first_scan").toInt
      var lastScanId = data("last_scan").toInt
      var apexScanId = data("apex_scan").toInt
      var ms1Count = data("ms1_count").toInt
      var ms2Count = data("ms2_count").toInt
      var isOverlapping = if (data("overlapping_feature") == "") true else false
      var isotopicPatterns = data("isotopic_patterns")
      var overlappingFeatures = data("overlapping_feature")

      //properties
      var peakelsCount = Some(data("peakels_count").toInt)
      var peakelsRatios = if (data("peakels_ratios") != "") Some(parse[Array[Float]](data("peakels_ratio"))) else None
      var overlapCorrelation = Some(data("overlap_correlation").toFloat)
      var overlapFactor = Some(data("overlap_factor").toFloat)
      
      var ms2EventIds = getMs2Events(lcmsRun, lcmsRun.getScanAtTime(apexScanId, 2).initialId)
      val featureId = Feature.generateNewId()

      val feature = Feature(id = featureId,
        moz = mz,
        intensity = area,
        charge = charge,
        elutionTime = elutionTime,
        qualityScore = qualityScore,
        ms1Count = ms1Count,
        ms2Count = ms2Count,
        isOverlapping = isOverlapping,
        isotopicPatterns = Some(parse[Array[mzIsotopicPattern]](isotopicPatterns).map(ip => ip.toIsotopicPattern)),
        overlappingFeatures = Array[Feature](parse[mzFeature](overlappingFeatures).toFeature(lcmsRun, featureId, ms2EventIds)),
        relations = FeatureRelations(ms2EventIds,
          firstScanInitialId = lcmsRun.scanById.get(firstScanId).get.initialId,
          lastScanInitialId = lcmsRun.scanById.get(lastScanId).get.initialId,
          apexScanInitialId = lcmsRun.scanById.get(apexScanId).get.initialId))

      feature.properties = Some(FeatureProperties(peakelsCount = peakelsCount,
        peakelsRatios = peakelsRatios,
        overlapCorrelation = overlapCorrelation,
        overlapFactor = overlapFactor))
      features += feature

    }

    lineIterator.map(s => treatOneLine(columnNames.zip((s.split(mzTSVParser.sepChar))) toMap))

    var runMap = new RunMap(id = lcmsRun.id,
      name = lcmsRun.rawFileName,
      isProcessed = false,
      creationTimestamp = new Date(),
      features = features toArray,
      runId = lcmsRun.id,
      peakPickingSoftware = new PeakPickingSoftware(1,
        "MzDbAccess",
        "0.1",
        "unknown"))

    Some(runMap)
  }

}