package fr.proline.core.parser.lcms.impl

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.util.control.Breaks._

import java.util.Date

import fr.proline.core.om.model.lcms.LcmsRun
import fr.proline.core.om.model.lcms.Feature
import fr.proline.core.om.model.lcms.IsotopicPattern
import fr.proline.core.om.model.lcms.Peak
import fr.proline.core.om.model.lcms.FeatureRelations
import fr.proline.core.om.model.lcms.RunMap
import fr.proline.core.om.model.lcms.PeakPickingSoftware

import fr.proline.core.parser.lcms.ILcmsMapFileParser

/*
case class ProgenesisExtraParams(
  val mapNb: Int,
  val mapName: Option[String] = None)
 */


object ProgenesisMapParser {
  val fields = HashMap("mapNb" -> Int, "mapName" -> AnyRef) 
}

class ProgenesisMapParser extends ILcmsMapFileParser {

  def getRunMap(filePath: String, lcmsRun: LcmsRun, extraParams: Map[String, Any]): Option[RunMap] = {
    val lines = io.Source.fromFile(filePath).getLines();

    //skip the first 2 lines
    for (i <- 0 until 2)
      lines.next()

    val columnNames = lines.next().split(";")

    //the seven first columns are reserved  
    val sampleNames = for (k <- 7 until columnNames.length) yield columnNames(k)

    var (found, mapName) = format(sampleNames, lcmsRun.rawFileName)
    if (!found)
      throw new Exception("requested file not found")

    var id = 1

    var features = new ArrayBuffer[Feature]

    while (lines.hasNext) {
      var l = lines.next.split(";")
      var rowValueMap = (for (i <- 0 until l.length) yield columnNames(i) -> l(i)) toMap

      var time = rowValueMap("Retention time (min)").toFloat * 60f
      var timeSpan = rowValueMap("Retention time window (min)").toFloat * 60f
      var (t1, t2) = (time - timeSpan / 2f, time + timeSpan / 2f)
      var firstScanInitialId = lcmsRun.getScanAtTime(t1, 1).initialId
      var lastScanInitialId = lcmsRun.getScanAtTime(t2, 1).initialId

      var scanms1 = lcmsRun.getScanAtTime(time, 1)
      var scanms2 = lcmsRun.getScanAtTime(time, 2)

      var idx = lcmsRun.scans.indexOf(scanms2)
      var ms2IdEvents = getMs2Events(lcmsRun, idx)

      //dont know what to do for ID...
      var biggestIp = new IsotopicPattern(id,
        rowValueMap("m/z").toDouble,
        rowValueMap(mapName).toFloat,
        rowValueMap("Charge").toInt,
        Float.NaN,
        Array[Peak](),
        scanms1.initialId,
        Array[IsotopicPattern]())
      id += 1

      var featureRelation = new FeatureRelations(ms2IdEvents toArray,
        firstScanInitialId,
        lastScanInitialId,
        scanms1.initialId)

      var feature = new Feature(Feature.generateNewId(),//rowValueMap("#").toInt,
        rowValueMap("m/z").toDouble,
        rowValueMap(mapName).toFloat,
        rowValueMap("Charge").toInt,
        time,
        Double.NaN,
        scanms1.cycle, //number of ms1
        scanms2.id - scanms2.cycle, //give the number of ms2
        false,
        Some(Array[IsotopicPattern](biggestIp)),
        Array[Feature](),
        featureRelation)
      features += feature
    }

    var runMap = new RunMap(lcmsRun.id,
      lcmsRun.rawFileName,
      false,
      new Date(),
      features toArray,
      lcmsRun.id,
      new PeakPickingSoftware(1,
        "Progenesis",
        "unknown",
        "unknown"))
    
    Some(runMap)
  }
  
  def getAllRunMap(filePath: String, lcmsRunMaps : Array[LcmsRun]) {
    val lines = io.Source.fromFile(filePath).getLines();

    //skip the first 2 lines
    for (i <- 0 until 2)
      lines.next()

    val columnNames = lines.next().split(";")

    //the seven first columns are reserved  
    val sampleNames = (for (k <- 7 until columnNames.length - 1) yield columnNames(k))
    
    if (sampleNames.length != lcmsRunMaps.length)
      throw new Exception("Errors too much or less lcmsRun provided")
    
    //order mapping namefile runMaps
    var nameFileRunMap = new HashMap[String, LcmsRun] 
    
    for (namefile <- sampleNames)
      breakable { for (lcmsRun <- lcmsRunMaps) {
        if (lcmsRun.rawFileName.contains(namefile)) {
          nameFileRunMap += (namefile -> lcmsRun)
          break
        }
      }
    }
    
    var runmaps = new ArrayBuffer[Option[RunMap]]
    nameFileRunMap.par.foreach(key => (runmaps += getRunMap(key._1, key._2, Map[String, Any]())))
    
  }
  

  def format(sampleNames: IndexedSeq[String], filename: String): (Boolean, String) = {

    var sepChar: String = ""
    if (filename.contains("\\")) {
      sepChar = "\\"
    } else if (filename.contains("/")) {
      sepChar = "/"
    }

    var f = filename.split(sepChar).last.split(".").first
    for (s <- sampleNames)
      if (s.matches(f))
        return (true, f)
    (false, f)

  }

}