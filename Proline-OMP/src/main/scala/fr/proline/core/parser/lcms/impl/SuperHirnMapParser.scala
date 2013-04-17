package fr.proline.core.parser.lcms.impl

import java.util.Date
import scala.xml.XML
import scala.xml.Elem
import scala.collection.mutable.ArrayBuffer
import scala.collection.immutable.HashMap

import fr.proline.core.om.model.lcms._
import fr.proline.core.parser.lcms.ILcmsMapFileParser
import fr.proline.core.parser.lcms.ExtraParameters

class SuperHirnMapParser extends ILcmsMapFileParser {
  
  def getRunMap(filePath: String, lcmsScanSeq: LcMsScanSequence, extraParams: ExtraParameters): Option[RunMap] = {
    val node = XML.load(io.Source.fromFile(filePath).getLines.toString)

    val features = ArrayBuffer[Feature]()

    val nodeSequence = node \ OpenMSMapParser.targetLabel

    for (n <- nodeSequence) {
      val coord = n \ "coordinate"
      val mz = (coord \ "@mz").toString.toDouble
      val elutionTime = (coord \ "@rt").toString.toFloat
      val intensity = (coord \ "@intensity").toString.toFloat
      val charge = (coord \ "@charge").toString.toInt

      val firstScan = lcmsScanSeq.scanById((coord \ "scan_range" \ "@min").toString().toInt)
      val lastScan = lcmsScanSeq.scanById((coord \ "scan_range" \ "@max").toString.toInt)
      val apexScan = lcmsScanSeq.getScanAtTime(elutionTime, 1)

      val ip = new IsotopicPattern(
        moz = mz,
        intensity = intensity,
        charge = charge,
        scanInitialId = apexScan.initialId
      ) //take the first scan for id ? or apex ?

      val ms2EventIds = getMs2Events(lcmsScanSeq, lcmsScanSeq.getScanAtTime(elutionTime, 2).initialId)

      val feature = Feature(id = Feature.generateNewId(),
        moz = mz,
        intensity = intensity,
        elutionTime = elutionTime,
        charge = charge,
        qualityScore = Double.NaN,
        ms1Count = lastScan.initialId - firstScan.initialId + 1,
        ms2Count = ms2EventIds.length,
        isOverlapping = false,
        isotopicPatterns = Some(Array[IsotopicPattern](ip)),
        overlappingFeatures = null,
        relations = FeatureRelations(
          ms2EventIds = ms2EventIds,
          firstScanInitialId = firstScan.initialId,
          lastScanInitialId = lastScan.initialId,
          apexScanInitialId = apexScan.initialId
        )
      )
      features += feature

    }
    val runMap = new RunMap(
      id = lcmsScanSeq.id,
      name = lcmsScanSeq.rawFileName,
      isProcessed = false,
      creationTimestamp = new Date(),
      features = features toArray,
      runId = lcmsScanSeq.id,
      peakPickingSoftware = new PeakPickingSoftware(1,
        "SuperHirn",
        "unknown",
        "unknown"
      )
    )

    Some(runMap)

  }

}