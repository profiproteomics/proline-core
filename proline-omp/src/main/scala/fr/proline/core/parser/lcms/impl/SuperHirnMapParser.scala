package fr.proline.core.parser.lcms.impl

import java.util.Date

import scala.collection.mutable.ArrayBuffer
import scala.xml.XML

import fr.proline.core.om.model.lcms._
import fr.proline.core.parser.lcms.ILcmsMapFileParser
import fr.proline.core.parser.lcms.ILcmsMapParserParameters

class SuperHirnMapParser extends ILcmsMapFileParser {
  
  def getRawMap(filePath: String, lcmsScanSeq: LcMsScanSequence, extraParams: ILcmsMapParserParameters): Option[RawMap] = {
    val node = XML.load(io.Source.fromFile(filePath).getLines.toString)

    val nodeSequence = node \ OpenMSMapParser.targetLabel
    val features = new ArrayBuffer[Feature](nodeSequence.size)

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
        mz = mz,
        intensity = intensity,
        charge = charge,
        scanInitialId = apexScan.initialId
      ) //take the first scan for id ? or apex ?

      val ms2EventIds = getMs2Events(lcmsScanSeq, lcmsScanSeq.getScanAtTime(elutionTime, 2).initialId)

      features += Feature(
        id = Feature.generateNewId(),
        moz = mz,
        intensity = intensity,
        elutionTime = elutionTime,
        duration = 0, // FIXME
        charge = charge,
        qualityScore = Float.NaN,
        ms1Count = lastScan.initialId - firstScan.initialId + 1,
        ms2Count = ms2EventIds.length,
        isOverlapping = false,
        isotopicPatterns = Some(Array(ip)),
        relations = FeatureRelations(
          ms2EventIds = ms2EventIds,
          firstScanInitialId = firstScan.initialId,
          lastScanInitialId = lastScan.initialId,
          apexScanInitialId = apexScan.initialId
        )
      )
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
          "SuperHirn",
          "unknown",
          "unknown"
        )
      )
    )

  }

}