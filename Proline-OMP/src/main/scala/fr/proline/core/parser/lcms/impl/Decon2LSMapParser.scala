package fr.proline.core.parser.lcms.impl

import java.util.Date

import scala.collection.mutable.{ArrayBuffer, Set}
import scala.collection.mutable.HashMap
import scala.util.control.Breaks._

import fr.profi.mzdb.model.IsotopicPatternLike
import fr.proline.core.om.model.lcms._
import fr.proline.core.parser.lcms.{ILcmsMapFileParser, ILcmsMapParserParameters}

case class Decon2LSParams(
  ms1MozTol: Double = 0.01,
  ms1MozTolUnit: String = "ppm",
  gapTol: Int = 2,
  minFtScanCount: Int = 2,
  elutionTimeUnit: String = "seconds"
)

object Decon2LSMapParser {

  val sepChar = ","

  def getMozInDalton(mozOfRef: Double, ms1_mozTol: Double, ms1_mozTolUnit: String) = {
    mozOfRef * ms1_mozTol / 1e6
  }
}

class Decon2LSMapParser extends ILcmsMapFileParser {

  def getRawMap(filePath: String, lcmsScanSeq: LcMsScanSequence, extraParams: ILcmsMapParserParameters) = {
    
    val scanById = lcmsScanSeq.scanById

    // Retrieve some vars from extra hash params
    val params = extraParams.asInstanceOf[Decon2LSParams]
    val ms1MozTol = params.ms1MozTol
    val ms1MozTolUnit = params.ms1MozTolUnit
    val gapTol = params.gapTol
    val minFtScanCount = params.minFtScanCount
    val elutionTimeUnit = params.elutionTimeUnit // may be minutes

    var timeConversionFactor: Int = 0
    elutionTimeUnit.toLowerCase match {
      case "seconds" => timeConversionFactor = 60
      case "minutes" => timeConversionFactor = 1
      case _         => throw new Exception("Invalid elution time unit")
    }

    val lines = io.Source.fromFile(filePath).getLines()
    val columnNames = lines.next.stripLineEnd.split(Decon2LSMapParser.sepChar)
    val isotopicPatterns = new ArrayBuffer[IsotopicPattern]

    // Processing of all lines
    for ( row <- lines) {
      val values = row.split(Decon2LSMapParser.sepChar)
      val valuesAsHash = columnNames.zip(values) toMap
      //val ipProps = ( fwhm = valuesAsHash("fwhm"), snr = valuesAsHash("signal_noise"))

      val ip = new IsotopicPattern(
        mz = valuesAsHash("mz").toDouble,
        intensity = valuesAsHash("abundance").toFloat,
        charge = valuesAsHash("charge").toInt,
        scanInitialId = valuesAsHash("scan_num").toInt,
        fittingScore = Some(valuesAsHash("fit").toFloat)
      )

      isotopicPatterns += ip
    }

    // Start rough stuff
    val ipsGroupedByCharge = isotopicPatterns.groupBy(_.charge)
    val features = new ArrayBuffer[Feature]

    for ( (charge, sameChargeIps) <- ipsGroupedByCharge ) {

      //val sortedIps = sameChargeIps.sortBy(_.intensity)
      val ipsGroupedByMoz = sameChargeIps.groupBy( _.mz.toInt )

      val processedIpSet = Set[IsotopicPattern]()
      for (ip <- sameChargeIps) {

        // Don't process a previously processed isotopic pattern
        if ( processedIpSet.contains(ip) == false ) {

          // Take the current ip as reference
          //processedIp(ipId) = 1
          val mozOfRef = ip.mz
          val scanIdOfRef = ip.scanInitialId
          val scanOfRef = scanById(scanIdOfRef)
          val cycleNumOfRef = scanOfRef.cycle

          // Retrieve putative isotopic patterns which belong to the same feature (same moz range)
          val mozInt = mozOfRef.toInt
          val sameMozRangeIps = new ArrayBuffer[IsotopicPattern] //same charge, same MozRange

          for (mozIndex <- mozInt - 1 to mozInt + 1) {
            sameMozRangeIps ++ ipsGroupedByMoz.getOrElse(mozIndex, new ArrayBuffer[IsotopicPattern])
          }

          val sameMozIpsByCycle = sameMozRangeIps
            .filter(ip => (math.abs(ip.mz - mozOfRef) < Decon2LSMapParser.getMozInDalton(mozOfRef, ms1MozTol, ms1MozTolUnit)))
            .groupBy( ip => scanById(ip.scanInitialId).cycle )

          // For each scan keep only the nearest moz from the reference moz
          val sameFtIpByCycleNum = sameMozIpsByCycle
            .map { tuple => tuple._1 -> (tuple._2.sortBy(ip => math.abs(ip.mz - mozOfRef) < math.abs(ip.mz - mozOfRef))) }
            .map { t => t._1 -> t._2.head }

          // Sort cycle nums to retrieve cycle num range
          val sortedCycleNums = sameFtIpByCycleNum.keys.toArray.sorted
          val firstCycleNum = sortedCycleNums.head
          val lastCycleNum = sortedCycleNums.last

          val sameFtIps = ArrayBuffer[IsotopicPattern](ip) // not sure to add ip because we will found it in our array
          var apex: IsotopicPattern = null
          var apexIntensity = 0f
          var gapCount = 0

          def extractIsotopicPattern(cycleNum: Int, ipPickerCb: IsotopicPattern => Unit): Option[IsotopicPattern] = {

            var curIp: IsotopicPattern = null

            try {
              curIp = sameFtIpByCycleNum(cycleNum)
              if (processedIpSet.contains(curIp)) {
                return Some(curIp)
              }
              
              // reset the gap counter
              gapCount = 0
              ipPickerCb(curIp)

              // check if this IP is higher than the current apex
              val curIpIntensity = curIp.intensity
              if (curIpIntensity > apexIntensity) {
                apexIntensity = curIpIntensity
                apex = curIp
              }

              // Mark the current ip as processed
              processedIpSet += curIp
              return Some(curIp)

            } catch {
              case e: NoSuchElementException => {
                gapCount += 1
                if (gapCount > gapTol)
                  return None

              }
            }
            
            None
          }
          //end of extractIsotopicPattern closure

          val ipPicker: IsotopicPattern => Unit = { sameFtIps += _ }
          breakable {
            for (curCycleNum <- cycleNumOfRef + 1 to lastCycleNum) {
              val ipId = extractIsotopicPattern(curCycleNum, ipPicker)
              if (ipId.isEmpty) break
            }
          }

          // backward extraction
          val ipPicker_2: IsotopicPattern => Unit = { sameFtIps.insert(0, _) }
          gapCount = 0
          breakable {
            for (curCycleNum <- cycleNumOfRef - 1 to firstCycleNum) {
              val ipId = extractIsotopicPattern(curCycleNum, ipPicker_2)
              if (ipId.isEmpty) break
            }
          }

          val ftScanCount = sameFtIps.length
          if (ftScanCount < minFtScanCount) {

            val dataPoints = sameFtIps.map(ip => (scanById(ip.scanInitialId).time -> ip.intensity) )

            val area = calcArea(dataPoints)

            val ms2EventIds = getMs2Events(lcmsScanSeq, lcmsScanSeq.getScanAtTime(scanOfRef.time, 2).initialId)
            // new FT with sameFtIps
            val ft = Feature(
              id = Feature.generateNewId(),
              moz = mozOfRef,
              intensity = area,
              charge = ip.charge,
              elutionTime = scanOfRef.time,
              duration = 0, // FIXME: compute the elution duration
              qualityScore = Float.NaN,
              ms1Count = sameFtIps.length,
              ms2Count = ms2EventIds.length,
              isOverlapping = false,
              isotopicPatterns = Some(sameFtIps.toArray),
              relations = FeatureRelations(ms2EventIds = null,
                firstScanInitialId = sameFtIps.head.scanInitialId,
                lastScanInitialId = sameFtIps.last.scanInitialId,
                apexScanInitialId = apex.scanInitialId)
              )
            //die Dumper ft if !defined ft.firstScanInitialId
              
            features += ft
            processedIpSet += ip
          }
        }
      }
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
        "Decon2LS",
        "unknown",
        "unknown"
      )
    )

    Some(rawMap)

  }

  def calcArea(data: Seq[(Float, Float)]): Float = {
    if (data.length == 0) return 0f
    
    var i = 0
    var area = 0f
    val dpA = data.head
	var xa = dpA._1
	var ya = dpA._2
    
    while (i < data.length - 2) {
      val dpB = data(i+1)
      val xb = dpB._1
      val yb = dpB._2
      
      area += ((yb - ya) + (xb - xa)) / 2 * (yb - ya)
      
      xa = xb
      ya = yb
      
      i += 1
    }
    
    area
  }
}

