package fr.proline.core.parser.lcms.impl

import java.util.Date

import scala.xml.XML
import scala.xml.Elem
import scala.collection.mutable.ArrayBuffer
import scala.collection.immutable.HashMap


import fr.proline.core.om.model.lcms.Peak
import fr.proline.core.om.model.lcms.IsotopicPattern
import fr.proline.core.om.model.lcms.FeatureRelations
import fr.proline.core.om.model.lcms.PeakPickingSoftware
import fr.proline.core.om.model.lcms.LcmsRun
import fr.proline.core.parser.lcms.ILcmsMapFileParser
import fr.proline.core.om.model.lcms.RunMap
import fr.proline.core.om.model.lcms.Feature
import fr.proline.core.parser.lcms.ExtraParameters


object OpenMSMapParser {
  val targetLabel = "feature"
  val dimension = HashMap("rt" -> "0", "moz" -> "1")
}

/*
 * Got an overall quality score. not usable
 */

class OpenMSMapParser extends ILcmsMapFileParser {

  def getRunMap(filePath: String, lcmsRun: LcmsRun, extraParams: ExtraParameters): Option[RunMap] = {
    val node = XML.load(io.Source.fromFile(filePath).getLines.toString)

    val nodeSequence = node \ OpenMSMapParser.targetLabel

    var features = new ArrayBuffer[Feature]

    var id = 0
    for (n <- nodeSequence) {

      var moz = ((n \ "position").filter(v => (v \ "@dim") == OpenMSMapParser.dimension("moz")))(0).text.toDouble
      var elutionTime = ((n \ "position").filter(v => (v \ "@dim") == OpenMSMapParser.dimension("rt")))(0).text.toFloat
      var intensity = (n \ "intensity").text.toFloat
      var charge = (n \ "charge").text.toInt

      var dataPoints = (n \ "convexhull").elements.map(p => new Peak((p \ "hposition").filter(v => (v \ "@dim") == OpenMSMapParser.dimension("moz"))(0).text.toDouble,
        (p \ "hposition").filter(v => (v \ "@dim") == OpenMSMapParser.dimension("rt"))(0).text.toFloat,
        Float.NaN,
        Float.NaN)).toArray

      var scanMs1 = lcmsRun.getScanAtTime(elutionTime, 1)
      var scanMs2 = lcmsRun.getScanAtTime(elutionTime, 2)

      var idxTmp = scanMs2.id + 1
      while (lcmsRun.scans(idxTmp).msLevel == 2) {
        idxTmp += 1
      }

      //idea to estimate scan start id and scan last id
      var lastTime = lcmsRun.scans(idxTmp).time
      var estimatedBeginTime = scanMs1.time - (math.abs(lastTime - scanMs1.time))
      //Or use rt in file begin last dataPoints

      var ip = new IsotopicPattern(//id = id,
        moz = moz,
        intensity = intensity,
        charge = charge,
        fitScore = Float.NaN,
        peaks = dataPoints,
        scanInitialId = scanMs1.initialId,
        overlappingIPs = Array[IsotopicPattern]())
      
      var ms2EventIds = getMs2Events(lcmsRun, lcmsRun.scans.indexOf(scanMs2))
      
      var ftRelation = new FeatureRelations(ms2EventIds = ms2EventIds,
        firstScanInitialId = lcmsRun.getScanAtTime(estimatedBeginTime, 1).initialId,
        lastScanInitialId = lcmsRun.getScanAtTime(lastTime, 1).initialId,
        apexScanInitialId = scanMs1.initialId)

      var feature = Feature(id = Feature.generateNewId(),
        moz = moz,
        intensity = intensity,
        charge = charge,
        elutionTime = elutionTime,
        qualityScore = Double.NaN,
        ms1Count = math.abs(lcmsRun.getScanAtTime(lastTime, 1).cycle - lcmsRun.getScanAtTime(estimatedBeginTime, 1).cycle) + 1,
        ms2Count = ms2EventIds.length,
        isOverlapping = false,
        isotopicPatterns = Some(Array[IsotopicPattern](ip)),
        overlappingFeatures = Array[Feature](),
        relations = ftRelation)

      features += feature
      id += 1
    }

    var runMap = new RunMap(id = lcmsRun.id,
      name = lcmsRun.rawFileName,
      isProcessed = false,
      creationTimestamp = new Date(),
      features = features toArray,
      runId = lcmsRun.id,
      peakPickingSoftware = new PeakPickingSoftware(1,
        "OpenMS",
        "unknown",
        "unknown"))

    Some(runMap)
  }

}