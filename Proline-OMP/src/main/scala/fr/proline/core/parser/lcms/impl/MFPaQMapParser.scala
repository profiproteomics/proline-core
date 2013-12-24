package fr.proline.core.parser.lcms.impl

import java.util.Date

import scala.collection.mutable.ArrayBuffer

import fr.proline.core.om.model.lcms._
import fr.proline.core.parser.lcms.ILcmsMapFileParser
import fr.proline.core.parser.lcms.ExtraParameters

case class MFPaQMapParams extends ExtraParameters {
  var mapNumber: Int = 1
}

object MFPaQMapParser {
  var sepChar = "\t"
  var nbColumns = 6
}

class MFPaQMapParser extends ILcmsMapFileParser {

  def getRawMap(filePath: String, lcmsScanSeq: LcMsScanSequence, extraParams: ExtraParameters): Option[RawMap] = {
    val lines = io.Source.fromFile(filePath).getLines
    val columnNames = lines.next.stripLineEnd.split(MFPaQMapParser.sepChar).slice(1, MFPaQMapParser.nbColumns + 1)

    val mapNumber = extraParams.asInstanceOf[MFPaQMapParams].mapNumber
    val startingPoint = (mapNumber - 1) * MFPaQMapParser.nbColumns + 1 //interesting columns

    val features = ArrayBuffer[Feature]()

    while (lines.hasNext) {

      val l = lines.next.stripLineEnd.split(MFPaQMapParser.sepChar)

      val data = columnNames.zip(l.slice(startingPoint, startingPoint + MFPaQMapParser.nbColumns + 1)) toMap

      if (data("Score") != "") {

        val charge = l(0).replace("+", "") toInt
        val moz = data("m/z") toDouble
        val intensity = data("Area") toFloat
        val firstRt = data("First RT") toFloat
        val firstScan = lcmsScanSeq.getScanAtTime(firstRt, 1)
        val lastRt = data("Last RT") toFloat
        val lastScan = lcmsScanSeq.getScanAtTime(lastRt, 1)
        val apexRt = data("Apex RT") toFloat
        val apexScan = lcmsScanSeq.getScanAtTime(apexRt, 1)

        val ip = new IsotopicPattern(
          moz = moz,
          intensity = intensity,
          charge = charge,
          scanInitialId = apexScan.initialId
        )

        val ms2EventIds = getMs2Events(lcmsScanSeq, apexScan.initialId)

        val feature = Feature(id = Feature.generateNewId(),
          moz = moz,
          intensity = intensity,
          elutionTime = apexRt,
          duration = 0, // FIXME
          charge = charge,
          qualityScore = Double.NaN,
          ms1Count = lastScan.cycle - firstScan.cycle + 1,
          ms2Count = ms2EventIds.length,
          isOverlapping = false,
          isotopicPatterns = Some(Array[IsotopicPattern](ip)),
          overlappingFeatures = Array[Feature](),
          relations = FeatureRelations(
            ms2EventIds = ms2EventIds,
            firstScanInitialId = firstScan.initialId,
            lastScanInitialId = lastScan.initialId,
            apexScanInitialId = apexScan.initialId))
        features += feature
      }
      //nothing to do

    } //end while

    val rawMap = new RawMap(
      id = lcmsScanSeq.runId,
      name = lcmsScanSeq.rawFileName,
      isProcessed = false,
      creationTimestamp = new Date(),
      features = features toArray,
      runId = lcmsScanSeq.runId,
      peakPickingSoftware = new PeakPickingSoftware(
        1,
        "MFPaQ",
        "4.5",
        "unknown"
      )
    )

    Some(rawMap)
  }

}