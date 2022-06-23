package fr.proline.core.parser.lcms.impl

import java.util.Date

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.util.control.Breaks._

import fr.proline.core.om.model.lcms._
import fr.proline.core.parser.lcms.ILcmsMapFileParser
import fr.proline.core.parser.lcms.ILcmsMapParserParameters

case class ProgenesisExtraParams(
  mapName: Option[String] = None,
  mapNumber: Option[Int] = None
) extends ILcmsMapParserParameters

object ProgenesisMapParser {
  val fields = HashMap("mapNumber" -> Int, "mapName" -> AnyRef)
}

class ProgenesisMapParser extends ILcmsMapFileParser {

  def getRawMap(filePath: String, lcmsScanSeq: LcMsScanSequence, extraParams: ILcmsMapParserParameters): Option[RawMap] = {
    val lines = io.Source.fromFile(filePath).getLines()

    //skip the first 2 lines
    for (i <- 0 until 2)
      lines.next()

    val columnNames = lines.next().stripLineEnd.split(";")

    //the seven first columns are reserved  
    val sampleNames = columnNames.slice(7, columnNames.length) //for (k <- 7 until columnNames.length) yield columnNames(k)

    val (found, mapName) = format(sampleNames, lcmsScanSeq.rawFileIdentifier)
    if (!found)
      throw new Exception("requested file not found")

    val features = new ArrayBuffer[Feature]

    for( line <- lines ) {

      val l = line.stripLineEnd.split(";")
      val rowValueMap = columnNames.zip(l).toMap

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
        mz = rowValueMap("m/z").toDouble,
        intensity = rowValueMap(mapName).toFloat,
        charge = rowValueMap("Charge").toInt,
        scanInitialId = scanms1.initialId
      )

      val ftRelations = FeatureRelations(
        ms2EventIds = ms2IdEvents.toArray,
        firstScanInitialId = firstScanInitialId,
        lastScanInitialId = lastScanInitialId,
        apexScanInitialId = scanms1.initialId
      )

      features += Feature(
        id = Feature.generateNewId(),
        moz = rowValueMap("m/z").toDouble,
        intensity = rowValueMap(mapName).toFloat,
        charge = rowValueMap("Charge").toInt,
        elutionTime = time,
        duration = 0, // FIXME
        qualityScore = Float.NaN,
        ms1Count = lcmsScanSeq.scanById(lastScanInitialId).cycle - scanms1.cycle + 1, //number of ms1
        ms2Count = ms2IdEvents.length, //give the number of ms2
        isOverlapping = false,
        isotopicPatterns = Some(Array(biggestIp)),
        relations = ftRelations
      )
    }

    Some(
      RawMap(
        id = lcmsScanSeq.runId,
        name = lcmsScanSeq.rawFileIdentifier,
        isProcessed = false,
        creationTimestamp = new Date(),
        features = features.toArray,
        runId = lcmsScanSeq.runId,
        peakPickingSoftware = new PeakPickingSoftware(
          1,
          "Progenesis",
          "unknown",
          "unknown"
        )
      )
    )
  }

  def getAllRawMaps(filePath: String, lcmsScanSeqs: Array[LcMsScanSequence]): Array[RawMap] = {
    val lines = io.Source.fromFile(filePath).getLines()

    //skip the first 2 lines
    for (i <- 0 until 2)
      lines.next()

    val columnNames = lines.next().split(";")

    //the seven first columns are reserved  
    val sampleNames = (for (k <- 7 until columnNames.length - 1) yield columnNames(k))

    if (sampleNames.length != lcmsScanSeqs.length)
      throw new Exception("Errors too much or less lcmsRun provided")

    //order mapping namefile rawMaps
    val scanSeqBySampleName = new HashMap[String, LcMsScanSequence]

    for (sampleName <- sampleNames) {
      breakable {
        for (lcmsScanSeq <- lcmsScanSeqs) {
          if (lcmsScanSeq.rawFileIdentifier.contains(sampleName)) {
            scanSeqBySampleName += (sampleName -> lcmsScanSeq)
            break
          }
        }
      }
    }

    val rawMaps = new ArrayBuffer[RawMap]
    scanSeqBySampleName.par.foreach { case( sampleName, scanSeq) =>
      val rawMapOpt = this.getRawMap(
        filePath,
        scanSeq,
        ProgenesisExtraParams(
          mapName = Some(sampleName)
        )
      )
      
      if( rawMapOpt.isDefined)
        rawMaps += rawMapOpt.get
    }

    rawMaps.toArray
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