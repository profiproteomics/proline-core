package fr.proline.core.parser.lcms.impl

/*
import java.util.Date
import fr.proline.core.om.model.lcms.RawMap

import scala.collection.mutable.ArrayBuffer
import scala.beans.BeanProperty

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include
import fr.profi.util.serialization.ProfiJson

import fr.proline.core.parser.lcms.{ ILcmsMapFileParser, ExtraParameters }
import fr.proline.core.om.model.lcms._

/**
 * mzDBaccess file parser
 */

//define some case class to read json encoded properties and not pollute model
@JsonInclude(Include.NON_NULL)
case class mzPeak(
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

@JsonInclude(Include.NON_NULL)
case class mzIsotopicPattern(

  @BeanProperty var scanId: Int = 0, // TODO LMN Long or initialId int
  @BeanProperty var mz: Double = Double.NaN,
  @BeanProperty var intensity: Float = Float.NaN,
  @BeanProperty var charge: Int = 0,
  @BeanProperty var peaks: Option[Array[mzPeak]] = None) {

  def toIsotopicPattern(): IsotopicPattern = {
    return new IsotopicPattern( //id = 0,
      moz = mz,
      intensity = intensity,
      charge = charge,
      peaks = Some(peaks.get.map(p => p.toPeak)),
      scanInitialId = scanId
    )
  }
}

@JsonInclude(Include.NON_NULL)
case class mzFeature(

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

  def toFeature(lcmsScanSeq: LcMsScanSequence, id: Long, ms2Events: Array[Long]): Feature = {
    Feature(
      id = id,
      moz = moz,
      intensity = area,
      charge = charge,
      elutionTime = elutionTime,
      duration = 0, // FIXME
      qualityScore = qualityScore,
      ms1Count = ms1Count,
      ms2Count = ms2Count,
      isOverlapping = if (overlappingFeature.isDefined) true else false,
      isotopicPatterns = if (isotopicPatterns.isDefined) Some(Array[IsotopicPattern](apexIp.toIsotopicPattern) ++ isotopicPatterns.get.map(ip => ip.toIsotopicPattern)) else Some(Array[IsotopicPattern](apexIp.toIsotopicPattern)),
      overlappingFeatures = if (overlappingFeature.isDefined) Array[Feature](overlappingFeature.get.toFeature(lcmsScanSeq, id, ms2Events)) else Array[Feature](),
      relations = FeatureRelations(
        ms2EventIds = ms2Events,
        firstScanInitialId = lcmsScanSeq.scanById.get(firstScan).get.initialId,
        lastScanInitialId = lcmsScanSeq.scanById.get(lastScan).get.initialId,
        apexScanInitialId = lcmsScanSeq.scanById.get(apexScan).get.initialId
      )
    )
  }
}

object mzTSVParser {
  val sepChar = "\t"
}

class mzTSVParser extends ILcmsMapFileParser {

  def getRawMap(filePath: String, lcmsScanSeq: LcMsScanSequence, extraParams: ExtraParameters): Option[RawMap] = {

    val lineIterator = io.Source.fromFile(filePath).getLines()
    val columnNames = lineIterator.next.stripLineEnd.split(mzTSVParser.sepChar)
    val features = new ArrayBuffer[Feature]

    def treatOneLine(data: Map[String, String]): Unit = {

      val id = data("id")
      val mz = data("mz").toDouble
      val area = data("area").toFloat
      val intensity = data("intensity_sum").toFloat
      val qualityScore = data("quality_score").toFloat
      val elutionTime = data("elution_time").toFloat
      val charge = data("charge").toInt
      val firstScanId = data("first_scan").toInt
      val lastScanId = data("last_scan").toInt
      val apexScanId = data("apex_scan").toInt
      val ms1Count = data("ms1_count").toInt
      val ms2Count = data("ms2_count").toInt
      val isOverlapping = if (data("overlapping_feature") == "") true else false
      val isotopicPatterns = data("isotopic_patterns")
      val overlappingFeatures = data("overlapping_feature")

      //properties
      val peakelsCount = Some(data("peakels_count").toInt)
      val peakelsRatios = if (data("peakels_ratios") != "") Some(ProfiJson.deserialize[Array[Float]](data("peakels_ratio"))) else None
      val overlapCorrelation = Some(data("overlap_correlation").toFloat)
      val overlapFactor = Some(data("overlap_factor").toFloat)

      val ms2EventIds = getMs2Events(lcmsScanSeq, lcmsScanSeq.getScanAtTime(apexScanId, 2).initialId)
      val featureId = Feature.generateNewId()

      val feature = Feature(
        id = featureId,
        moz = mz,
        intensity = area,
        charge = charge,
        elutionTime = elutionTime,
        duration = 0, // FIXME
        qualityScore = qualityScore,
        ms1Count = ms1Count,
        ms2Count = ms2Count,
        isOverlapping = isOverlapping,
        isotopicPatterns = Some(ProfiJson.deserialize[Array[mzIsotopicPattern]](isotopicPatterns).map(ip => ip.toIsotopicPattern)),
        overlappingFeatures = Array[Feature](ProfiJson.deserialize[mzFeature](overlappingFeatures).toFeature(lcmsScanSeq, featureId, ms2EventIds)),
        relations = FeatureRelations(
          ms2EventIds = ms2EventIds,
          firstScanInitialId = lcmsScanSeq.scanById.get(firstScanId).get.initialId,
          lastScanInitialId = lcmsScanSeq.scanById.get(lastScanId).get.initialId,
          apexScanInitialId = lcmsScanSeq.scanById.get(apexScanId).get.initialId))

      feature.properties = Some(FeatureProperties(peakelsCount = peakelsCount,
        peakelsRatios = peakelsRatios,
        overlapCorrelation = overlapCorrelation,
        overlapFactor = overlapFactor))
      features += feature

    }

    lineIterator.map(s => treatOneLine(columnNames.zip((s.split(mzTSVParser.sepChar))) toMap))

    val rawMap = new RawMap(
      id = lcmsScanSeq.runId,
      name = lcmsScanSeq.rawFileName,
      isProcessed = false,
      creationTimestamp = new Date(),
      features = features toArray,
      runId = lcmsScanSeq.runId,
      peakPickingSoftware = new PeakPickingSoftware(
        1,
        "MzDbAccess",
        "0.1",
        "unknown"
      )
    )

    Some(rawMap)
  }

}*/