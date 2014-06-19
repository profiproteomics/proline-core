package fr.proline.core.parser.lcms.impl

import java.util.Date

import scala.xml.XML
import scala.xml.Elem
import scala.collection.mutable.ArrayBuffer
import scala.collection.immutable.HashMap

import fr.proline.core.om.model.lcms._
import fr.proline.core.parser.lcms.ILcmsMapFileParser
import fr.proline.core.parser.lcms.ExtraParameters
import fr.profi.util.primitives._

object OpenMSMapParser {
  val targetLabel = "feature"
  val dimension = HashMap("rt" -> "0", "moz" -> "1")
}

/*
 * Got an overall quality score. not usable
 */

class OpenMSMapParser extends ILcmsMapFileParser {

  def getRawMap(filePath: String, lcmsScanSeq: LcMsScanSequence, extraParams: ExtraParameters): Option[RawMap] = {
    val node = XML.load(io.Source.fromFile(filePath).getLines.toString)

    val nodeSequence = node \ OpenMSMapParser.targetLabel

    var features = new ArrayBuffer[Feature]

    for (n <- nodeSequence) {

      val moz = ((n \ "position").filter(v => (v \ "@dim") == OpenMSMapParser.dimension("moz")))(0).text.toDouble
      val elutionTime = ((n \ "position").filter(v => (v \ "@dim") == OpenMSMapParser.dimension("rt")))(0).text.toFloat
      val intensity = (n \ "intensity").text.toFloat
      val charge = (n \ "charge").text.toInt

      var dataPoints = (n \ "convexhull").map( p =>
        new Peak(
          (p \ "hposition").filter(v => (v \ "@dim") == OpenMSMapParser.dimension("moz"))(0).text.toDouble,
          (p \ "hposition").filter(v => (v \ "@dim") == OpenMSMapParser.dimension("rt"))(0).text.toFloat,
          Float.NaN,
          Float.NaN
         )
      ).toArray

      val scanMs1 = lcmsScanSeq.getScanAtTime(elutionTime, 1)
      val scanMs2 = lcmsScanSeq.getScanAtTime(elutionTime, 2)

      var idxTmp: Int = toInt(scanMs2.id + 1) // WARN Array index must be Int
      while (lcmsScanSeq.scans(idxTmp).msLevel == 2) {
        idxTmp += 1
      }

      //idea to estimate scan start id and scan last id
      var lastTime = lcmsScanSeq.scans(idxTmp).time
      var estimatedBeginTime = scanMs1.time - (math.abs(lastTime - scanMs1.time))
      //Or use rt in file begin last dataPoints

      val ip = new IsotopicPattern( //id = id,
        moz = moz,
        intensity = intensity,
        charge = charge,
        peaks = Some(dataPoints),
        scanInitialId = scanMs1.initialId
      )

      val ms2EventIds = getMs2Events(lcmsScanSeq, lcmsScanSeq.scans.indexOf(scanMs2))

      val ftRelation = new FeatureRelations(ms2EventIds = ms2EventIds,
        firstScanInitialId = lcmsScanSeq.getScanAtTime(estimatedBeginTime, 1).initialId,
        lastScanInitialId = lcmsScanSeq.getScanAtTime(lastTime, 1).initialId,
        apexScanInitialId = scanMs1.initialId)

      val feature = Feature(
        id = Feature.generateNewId(),
        moz = moz,
        intensity = intensity,
        charge = charge,
        elutionTime = elutionTime,
        duration = 0, // FIXME
        qualityScore = Double.NaN,
        ms1Count = math.abs(lcmsScanSeq.getScanAtTime(lastTime, 1).cycle - lcmsScanSeq.getScanAtTime(estimatedBeginTime, 1).cycle) + 1,
        ms2Count = ms2EventIds.length,
        isOverlapping = false,
        isotopicPatterns = Some(Array[IsotopicPattern](ip)),
        overlappingFeatures = Array[Feature](),
        relations = ftRelation)

      features += feature
    }

    val rawMap = new RawMap(
      id = lcmsScanSeq.runId,
      name = lcmsScanSeq.rawFileName,
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

    Some(rawMap)
  }

}