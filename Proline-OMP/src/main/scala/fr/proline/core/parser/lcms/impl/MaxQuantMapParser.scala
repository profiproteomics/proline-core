package fr.proline.core.parser.lcms.impl

import java.util.Date

import collection.mutable.ArrayBuffer

import fr.proline.core.om.model.lcms.LcMsRun
import fr.proline.core.om.model.lcms.RunMap
import fr.proline.core.parser.lcms.ExtraParameters
import fr.proline.core.parser.lcms.ILcmsMapFileParser
import fr.proline.core.om.model.lcms.Feature
import fr.proline.core.om.model.lcms.IsotopicPattern
import fr.proline.core.om.model.lcms.FeatureRelations
import fr.proline.core.om.model.lcms.PeakPickingSoftware

object MaxQuantMapParser {
  var sepChar = "\t"
}

class MaxQuantMapParser extends ILcmsMapFileParser {

  def getRunMap(filePath: String, lcmsRun: LcMsRun, extraParams: ExtraParameters): Option[RunMap] = {

    def toStandardName(s: String): String = {
      //to put in ILcmsMapFileParser maybe
      s.replaceAll("\\", "/")
      s.replaceAll("\\\\", "/")
      s
    }

    val shortFileName = toStandardName(lcmsRun.rawFileName).split("/").last.split(".").first

    val lines = io.Source.fromFile(filePath).getLines

    val columnNames = lines.next.stripLineEnd.split(MaxQuantMapParser.sepChar)

    val features = ArrayBuffer[Feature]()

    def processLine(l: String): Unit = {
      val data = columnNames.zip(l.stripLineEnd.split(MaxQuantMapParser.sepChar)) toMap

      if (data("Raw File").equals(shortFileName)) {
        val moz = data("m/z") toDouble
        val charge = data("charge") toInt
        val intensity = data("Intensity") toFloat
        val elutionTime = data("Retention Time") * 60 toFloat
        val retentionLength = data("Retention Length") * 60 toFloat
        val ms2Count = data("MS/MS Count") toInt

        val intensities = data("Intensities").split(";").map(_.toFloat).sortBy(f => f)

        val apexScan = lcmsRun.getScanAtTime(elutionTime, 1)
        val firstScan = lcmsRun.getScanAtTime(elutionTime - retentionLength / 2f, 1)
        val lastScan = lcmsRun.getScanAtTime(elutionTime + retentionLength / 2f, 1)

        val ms2EventIds = getMs2Events(lcmsRun, apexScan.initialId)

        val ips = ArrayBuffer[IsotopicPattern]()
        if (intensities.length == 0) {
          val ip = new IsotopicPattern(
            moz = moz,
            intensity = intensity,
            charge = charge,
            scanInitialId = apexScan.initialId,
            overlappingIPs = null
          )
          ips += ip
        } else {
          ips ++ intensities.map { i =>
            new IsotopicPattern(
              moz = moz,
              intensity = i,
              charge = charge,
              scanInitialId = apexScan.initialId
            )
          }

        }

        val feature = Feature(id = Feature.generateNewId(),
          moz = moz,
          intensity = intensity,
          elutionTime = elutionTime,
          charge = charge,
          qualityScore = Double.NaN,
          ms1Count = lastScan.cycle - firstScan.cycle + 1,
          ms2Count = ms2Count,
          isOverlapping = false,
          isotopicPatterns = Some(ips.toArray),
          overlappingFeatures = null,
          relations = FeatureRelations(ms2EventIds,
            firstScanInitialId = firstScan.initialId,
            lastScanInitialId = lastScan.initialId,
            apexScanInitialId = apexScan.initialId))
        features += feature

      }

    } //end function

    lines.map(s => processLine(s))

    val runMap = new RunMap(id = lcmsRun.id,
      name = lcmsRun.rawFileName,
      isProcessed = false,
      creationTimestamp = new Date(),
      features = features toArray,
      runId = lcmsRun.id,
      peakPickingSoftware = new PeakPickingSoftware(1,
        "MaxQuant",
        "unknown",
        "unknown"))

    Some(runMap)
  }

}