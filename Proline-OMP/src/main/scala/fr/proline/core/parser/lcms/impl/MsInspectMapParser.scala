package fr.proline.core.parser.lcms.impl

import java.text.DateFormat
import java.util.Date

import scala.collection.mutable.ArrayBuffer

import fr.proline.core.parser.lcms.ILcmsMapFileParser
import fr.proline.core.om.model.lcms.LcMsScanSequence
import fr.proline.core.om.model.lcms.RawMap
import fr.proline.core.om.model.lcms.{ Feature, IsotopicPattern, FeatureRelations, Peak, PeakPickingSoftware }
import fr.proline.core.parser.lcms.ExtraParameters

object MsInspectMapParser {
  var sepChar = "\t"
}

class MsInspectMapParser extends ILcmsMapFileParser {

  def getRawMap(filePath: String, lcmsScanSeq: LcMsScanSequence, extraParams: ExtraParameters): Option[RawMap] = {
    val linesIterator = io.Source.fromFile(filePath).getLines()

    var line = if (linesIterator.hasNext) linesIterator.next else return None
    var timeStamp: Date = null

    while (line.startsWith("#")) {
      if (line.startsWith("# data=")) {
        var df: DateFormat = DateFormat.getDateInstance()
        timeStamp = df.parse(line.split("# data=")(1).stripLineEnd)
      }
      line = linesIterator.next
    }

    val columnNames = line.split(MsInspectMapParser.sepChar)

    var features = ArrayBuffer[Feature]()

    while (linesIterator.hasNext) {
      val data = columnNames.zip(linesIterator.next.split(MsInspectMapParser.sepChar)) toMap
      val scanId = data("scan") toInt
      val elutionTime = data("time") toFloat
      val moz = data("mz") toDouble
      val intensity = data("totalIntensity") toFloat
      val charge = data("charge") toInt
      //var nbPeaks = data("peaks") toInt
      val firstScanId = data("scanFirst") toInt
      val lastScanId = data("scanLast") toInt

      val ms2EventIds = getMs2Events(lcmsScanSeq, scanId)

      val ip = new IsotopicPattern(
        //id = id,
        moz = moz,
        intensity = intensity,
        charge = charge,
        scanInitialId = lcmsScanSeq.scanById(scanId).initialId
      )

      val feature = Feature(
        id = Feature.generateNewId(),
        moz = moz,
        intensity = intensity,
        elutionTime = elutionTime,
        duration = 0, // FIXME
        charge = charge,
        qualityScore = Double.NaN,
        ms1Count = math.abs(lcmsScanSeq.scanById(firstScanId).cycle - lcmsScanSeq.scanById(lastScanId).cycle) + 1,
        ms2Count = ms2EventIds.length,
        isOverlapping = false,
        isotopicPatterns = Some(Array[IsotopicPattern](ip)),
        overlappingFeatures = Array[Feature](),
        relations = FeatureRelations(ms2EventIds = ms2EventIds,
          firstScanInitialId = lcmsScanSeq.scanById(firstScanId).initialId,
          lastScanInitialId = lcmsScanSeq.scanById(lastScanId).initialId,
          apexScanInitialId = lcmsScanSeq.scanById(scanId).initialId
        )
      )

      features += feature
    }
    val rawMap = new RawMap(
      id = lcmsScanSeq.runId,
      name = lcmsScanSeq.rawFileName,
      isProcessed = false,
      creationTimestamp = timeStamp,
      features = features toArray,
      runId = lcmsScanSeq.runId,
      peakPickingSoftware = new PeakPickingSoftware(
        1,
        "MsInspect",
        "0.1",
        "unknown"
      )
    )

    Some(rawMap)
  }

}