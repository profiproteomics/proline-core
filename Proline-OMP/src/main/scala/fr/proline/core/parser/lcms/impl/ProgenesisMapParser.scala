package fr.proline.core.parser.lcms.impl

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.util.control.Breaks._
import java.util.Date
import fr.proline.core.om.model.lcms._
import fr.proline.core.parser.lcms.ILcmsMapFileParser
import fr.proline.core.parser.lcms.ExtraParameters

case class ProgenesisExtraParams extends ExtraParameters {
  /**
   * actually empty because no parameters needed
   */
  //val mapNb: Option[Int] = None
  //val mapName: Option[String] = None
}

object ProgenesisMapParser {
  val fields = HashMap("mapNb" -> Int, "mapName" -> AnyRef)
}

class ProgenesisMapParser extends ILcmsMapFileParser {

  def getRawMap(filePath: String, lcmsScanSeq: LcMsScanSequence, extraParams: ExtraParameters): Option[RawMap] = {
    val lines = io.Source.fromFile(filePath).getLines();

    //skip the first 2 lines
    for (i <- 0 until 2)
      lines.next()

    val columnNames = lines.next().stripLineEnd.split(";")

    //the seven first columns are reserved  
    val sampleNames = columnNames.slice(7, columnNames.length) //for (k <- 7 until columnNames.length) yield columnNames(k)

    val (found, mapName) = format(sampleNames, lcmsScanSeq.rawFileName)
    if (!found)
      throw new Exception("requested file not found")

    var features = new ArrayBuffer[Feature]

    while (lines.hasNext) {

      val l = lines.next.stripLineEnd.split(";")
      val rowValueMap = columnNames.zip(l) toMap

      val time = rowValueMap("Retention time (min)").toFloat * 60f
      val timeSpan = rowValueMap("Retention time window (min)").toFloat * 60f
      val (t1, t2) = (time - timeSpan / 2f, time + timeSpan / 2f)
      val firstScanInitialId = lcmsScanSeq.getScanAtTime(t1, 1).initialId
      val lastScanInitialId = lcmsScanSeq.getScanAtTime(t2, 1).initialId

      val scanms1 = lcmsScanSeq.getScanAtTime(time, 1)
      val scanms2 = lcmsScanSeq.getScanAtTime(time, 2)

      val idx = lcmsScanSeq.scans.indexOf(scanms2)
      val ms2IdEvents = getMs2Events(lcmsScanSeq, idx)

      //dont know what to do for ID...
      val biggestIp = new IsotopicPattern( //id = id,
        moz = rowValueMap("m/z").toDouble,
        intensity = rowValueMap(mapName).toFloat,
        charge = rowValueMap("Charge").toInt,
        scanInitialId = scanms1.initialId
      )

      val featureRelation = FeatureRelations(ms2EventIds = ms2IdEvents toArray,
        firstScanInitialId = firstScanInitialId,
        lastScanInitialId = lastScanInitialId,
        apexScanInitialId = scanms1.initialId)

      val feature = Feature(
        id = Feature.generateNewId(),
        moz = rowValueMap("m/z").toDouble,
        intensity = rowValueMap(mapName).toFloat,
        charge = rowValueMap("Charge").toInt,
        elutionTime = time,
        duration = 0, // FIXME
        qualityScore = Double.NaN,
        ms1Count = lcmsScanSeq.scanById(lastScanInitialId).cycle - scanms1.cycle + 1, //number of ms1
        ms2Count = ms2IdEvents.length, //give the number of ms2
        isOverlapping = false,
        isotopicPatterns = Some(Array[IsotopicPattern](biggestIp)),
        overlappingFeatures = Array[Feature](),
        relations = featureRelation)

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
        "Progenesis",
        "unknown",
        "unknown"
      )
    )

    Some(rawMap)
  }

  def getAllRawMap(filePath: String, lcmsScanSeqs: Array[LcMsScanSequence]) {
    val lines = io.Source.fromFile(filePath).getLines();

    //skip the first 2 lines
    for (i <- 0 until 2)
      lines.next()

    val columnNames = lines.next().split(";")

    //the seven first columns are reserved  
    val sampleNames = (for (k <- 7 until columnNames.length - 1) yield columnNames(k))

    if (sampleNames.length != lcmsScanSeqs.length)
      throw new Exception("Errors too much or less lcmsRun provided")

    //order mapping namefile rawMaps
    var nameFileRawMap = new HashMap[String, LcMsScanSequence]

    for (namefile <- sampleNames)
      breakable {
        for (lcmsScanSeq <- lcmsScanSeqs) {
          if (lcmsScanSeq.rawFileName.contains(namefile)) {
            nameFileRawMap += (namefile -> lcmsScanSeq)
            break
          }
        }
      }

    var runmaps = new ArrayBuffer[Option[RawMap]]
    nameFileRawMap.par.foreach(key => (runmaps += getRawMap(key._1, key._2, ProgenesisExtraParams())))

  }

  def format(sampleNames: IndexedSeq[String], filename: String): (Boolean, String) = {

    var sepChar: String = ""
    if (filename.contains("\\")) {
      sepChar = "\\"
    } else if (filename.contains("/")) {
      sepChar = "/"
    }

    val f = filename.split(sepChar).last.split(".").head
    for (s <- sampleNames; if (s.matches(f)) ) return (true, f)        
    
    (false, f)
  }

}