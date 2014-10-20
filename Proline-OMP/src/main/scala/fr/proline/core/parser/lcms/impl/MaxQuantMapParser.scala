package fr.proline.core.parser.lcms.impl

import java.util.Date

import collection.mutable.ArrayBuffer

import fr.proline.core.om.model.lcms._
import fr.proline.core.parser.lcms.ILcmsMapParserParameters
import fr.proline.core.parser.lcms.ILcmsMapFileParser

object MaxQuantMapParser {
  var sepChar = "\t"
}

class MaxQuantMapParser extends ILcmsMapFileParser {

  def getRawMap(filePath: String, lcmsScanSeq: LcMsScanSequence, extraParams: ILcmsMapParserParameters): Option[RawMap] = {

    def toStandardName(s: String): String = {
      //to put in ILcmsMapFileParser maybe
      s.replaceAll("\\", "/")
      s.replaceAll("\\\\", "/")
      s
    }

    val shortFileName = toStandardName(lcmsScanSeq.rawFileName).split("/").last.split(".").head

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

        val apexScan = lcmsScanSeq.getScanAtTime(elutionTime, 1)
        val firstScan = lcmsScanSeq.getScanAtTime(elutionTime - retentionLength / 2f, 1)
        val lastScan = lcmsScanSeq.getScanAtTime(elutionTime + retentionLength / 2f, 1)

        val ms2EventIds = getMs2Events(lcmsScanSeq, apexScan.initialId)

        val ips = ArrayBuffer[IsotopicPattern]()
        if (intensities.length == 0) {
          val ip = new IsotopicPattern(
            mz = moz,
            intensity = intensity,
            charge = charge,
            scanInitialId = apexScan.initialId
          )
          ips += ip
        } else {
          ips ++ intensities.map { i =>
            new IsotopicPattern(
              mz = moz,
              intensity = i,
              charge = charge,
              scanInitialId = apexScan.initialId
            )
          }

        }

        features += Feature(
          id = Feature.generateNewId(),
          moz = moz,
          intensity = intensity,
          elutionTime = elutionTime,
          duration = 0, // FIXME
          charge = charge,
          qualityScore = Float.NaN,
          ms1Count = lastScan.cycle - firstScan.cycle + 1,
          ms2Count = ms2Count,
          isOverlapping = false,
          isotopicPatterns = Some(ips.toArray),
          relations = FeatureRelations(
            ms2EventIds = ms2EventIds,
            firstScanInitialId = firstScan.initialId,
            lastScanInitialId = lastScan.initialId,
            apexScanInitialId = apexScan.initialId
          )
        )
        
      }

    } //end function

    lines.map(s => processLine(s))

    val rawMap = new RawMap(
      id = lcmsScanSeq.runId,
      name = lcmsScanSeq.rawFileName,
      isProcessed = false,
      creationTimestamp = new Date(),
      features = features toArray,
      runId = lcmsScanSeq.runId,
      peakPickingSoftware = new PeakPickingSoftware(
        1,
        "MaxQuant",
        "unknown",
        "unknown"
      )
    )

    Some(rawMap)
  }

}