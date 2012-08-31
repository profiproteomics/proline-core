package fr.proline.core.parser.lcms.impl

import fr.proline.core.om.model.lcms.LcmsRun
import fr.proline.core.parser.lcms.ILcmsMapFileParser
import fr.proline.core.om.model.lcms.RunMap
import scala.xml.XML
import scala.xml.Elem
import scala.collection.mutable.ArrayBuffer
import fr.proline.core.om.model.lcms.Feature
import scala.collection.immutable.HashMap
import fr.proline.core.om.model.lcms.Peak
import fr.proline.core.om.model.lcms.IsotopicPattern
import fr.proline.core.om.model.lcms.FeatureRelations
import fr.proline.core.om.model.lcms.PeakPickingSoftware
import java.util.Date

object OpenMSMapParser {
  val targetLabel = "feature"
  val dimension = HashMap("rt" -> "0", "moz" -> "1")
}

/*
 * Got an overall quality score. not usable
 */

class OpenMSMapParser extends ILcmsMapFileParser {

  def getRunMap(filePath: String, lcmsRun: LcmsRun, extraParams: Map[String, Any]): Option[RunMap] = {
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

      var ip = new IsotopicPattern(id,
        moz,
        intensity,
        charge,
        Float.NaN,
        dataPoints,
        scanMs1.initialId,
        Array[IsotopicPattern]())

      var ftRelation = new FeatureRelations(getMs2Events(lcmsRun, lcmsRun.scans.indexOf(scanMs2)),
        lcmsRun.getScanAtTime(estimatedBeginTime, 1).initialId,
        lcmsRun.getScanAtTime(lastTime, 1).initialId,
        scanMs1.initialId)

      var feature = new Feature(Feature.generateNewId(),
        moz,
        intensity,
        charge,
        elutionTime,
        Double.NaN,
        scanMs1.cycle,
        scanMs1.id - scanMs1.cycle,
        false,
        None,
        Array[Feature](),
        ftRelation)

      features += feature
      id += 1
    }

    var runMap = new RunMap(lcmsRun.id,
      lcmsRun.rawFileName,
      false,
      new Date(),
      features toArray,
      lcmsRun.id,
      new PeakPickingSoftware(1,
        "OpenMS",
        "unknown",
        "unknown"))

    Some(runMap)
  }

}