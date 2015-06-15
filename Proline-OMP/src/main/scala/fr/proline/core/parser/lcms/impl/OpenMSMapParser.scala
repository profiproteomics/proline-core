package fr.proline.core.parser.lcms.impl

import java.util.Date

import scala.collection.immutable.HashMap
import scala.collection.mutable.ArrayBuffer
import scala.xml.XML

import fr.profi.mzdb.model.Peak
import fr.profi.util.primitives._
import fr.proline.core.om.model.lcms._
import fr.proline.core.parser.lcms.ILcmsMapParserParameters
import fr.proline.core.parser.lcms.ILcmsMapFileParser

object OpenMSMapParser {
  val targetLabel = "feature"
  val dimension = HashMap("rt" -> "0", "moz" -> "1")
}

/*
 * Got an overall quality score. not usable
 */

class OpenMSMapParser extends ILcmsMapFileParser {

  def getRawMap(filePath: String, lcmsScanSeq: LcMsScanSequence, extraParams: ILcmsMapParserParameters): Option[RawMap] = {
    val node = XML.load(io.Source.fromFile(filePath).getLines.toString)

    val nodeSequence = node \ OpenMSMapParser.targetLabel

    val features = new ArrayBuffer[Feature]( nodeSequence.size )

    for (n <- nodeSequence) {

      val moz = ((n \ "position").filter(v => (v \ "@dim") == OpenMSMapParser.dimension("moz")))(0).text.toDouble
      val elutionTime = ((n \ "position").filter(v => (v \ "@dim") == OpenMSMapParser.dimension("rt")))(0).text.toFloat
      val intensity = (n \ "intensity").text.toFloat
      val charge = (n \ "charge").text.toInt

      val dataPoints = (n \ "convexhull").map( p =>
        new Peak(
          (p \ "hposition").filter(v => (v \ "@dim") == OpenMSMapParser.dimension("moz"))(0).text.toDouble,
          (p \ "hposition").filter(v => (v \ "@dim") == OpenMSMapParser.dimension("rt"))(0).text.toFloat
         )
      ).toArray

      val scanMs1 = lcmsScanSeq.getScanAtTime(elutionTime, 1)
      val scanMs2 = lcmsScanSeq.getScanAtTime(elutionTime, 2)

      var idxTmp: Int = toInt(scanMs2.id + 1) // WARN Array index must be Int
      while (lcmsScanSeq.scans(idxTmp).msLevel == 2) {
        idxTmp += 1
      }

      //idea to estimate scan start id and scan last id
      val lastTime = lcmsScanSeq.scans(idxTmp).time
      val estimatedBeginTime = scanMs1.time - (math.abs(lastTime - scanMs1.time))
      //Or use rt in file begin last dataPoints

      val ip = new IsotopicPattern( //id = id,
        mz = moz,
        intensity = intensity,
        charge = charge,
        peaks = dataPoints,
        scanInitialId = scanMs1.initialId
      )

      val ms2EventIds = getMs2Events(lcmsScanSeq, lcmsScanSeq.scans.indexOf(scanMs2))

      val ftRelations = new FeatureRelations(
        ms2EventIds = ms2EventIds,
        firstScanInitialId = lcmsScanSeq.getScanAtTime(estimatedBeginTime, 1).initialId,
        lastScanInitialId = lcmsScanSeq.getScanAtTime(lastTime, 1).initialId,
        apexScanInitialId = scanMs1.initialId
      )

      val feature = Feature(
        id = Feature.generateNewId(),
        moz = moz,
        intensity = intensity,
        charge = charge,
        elutionTime = elutionTime,
        duration = 0, // FIXME
        qualityScore = Float.NaN,
        ms1Count = math.abs(lcmsScanSeq.getScanAtTime(lastTime, 1).cycle - lcmsScanSeq.getScanAtTime(estimatedBeginTime, 1).cycle) + 1,
        ms2Count = ms2EventIds.length,
        isOverlapping = false,
        isotopicPatterns = Some(Array(ip)),
        relations = ftRelations
      )

      features += feature
    }

    Some(
      RawMap(
        id = lcmsScanSeq.runId,
        name = lcmsScanSeq.rawFileIdentifier,
        isProcessed = false,
        creationTimestamp = new Date(),
        features = features toArray,
        runId = lcmsScanSeq.runId,
        peakPickingSoftware = new PeakPickingSoftware(
          1,
          "OpenMS",
          "unknown",
          "unknown"
        )
      )
    )
  }

}