package fr.proline.core.parser.lcms.impl

import java.util.Date

import scala.util.control.Breaks._
import collection.mutable.HashMap
import scala.collection.mutable.{ ArrayBuffer, Set }

import fr.proline.core.parser.lcms.{ ILcmsMapFileParser, ExtraParameters }
import fr.proline.core.om.model.lcms._
import fr.proline.core.om.model.lcms.Peak
import fr.proline.core.om.model.lcms.RawMap
import fr.proline.core.om.model.lcms.Feature

case class Decon2LSParams(
  ms1MozTol: Double = 0.01,
  ms1MosTolUnit: String = "ppm",
  gapTol: Int = 2,
  minFtScanCount: Int = 2,
  elutionTimeUnit: String = "seconds")

object Decon2LSMapParser {

  val sepChar = ","

  def getMozInDalton(mozOfRef: Double, ms1_mozTol: Double, ms1_mozTolUnit: String) = {
    mozOfRef * ms1_mozTol / 1e6
  }
}

class Decon2LSMapParser extends ILcmsMapFileParser {

  def getRawMap(filePath: String, lcmsScanSeq: LcMsScanSequence, extraParams: ExtraParameters) = {

    ////// Retrieve some vars from extra hash params
    val params = extraParams.asInstanceOf[Decon2LSParams]
    val ms1MozTol = params.ms1MozTol //extraParams.getOrElse("ms1_moz_tol", 0.01 ).asInstanceOf[Double]
    val ms1MozTolUnit = params.ms1MosTolUnit
    val gapTol = params.gapTol
    val minFtScanCount = params.minFtScanCount
    val elutionTimeUnit = params.elutionTimeUnit ////// may be minutes

    var timeConversionFactor: Int = 0
    elutionTimeUnit.toLowerCase match {
      case "seconds" => timeConversionFactor = 60
      case "minutes" => timeConversionFactor = 1
      case _         => throw new Exception("Invalid elution time unit")
    }

    val lines = io.Source.fromFile(filePath).getLines()
    val columnNames = lines.next.stripLineEnd.split(Decon2LSMapParser.sepChar)
    val isotopicPatterns = new ArrayBuffer[IsotopicPattern]

    //treatment of one row
    def getFeatureFromRow(row: String) {
      val values = row.split(Decon2LSMapParser.sepChar)
      val valuesAsHash = columnNames.zip(values) toMap
      //val ipProps = ( fwhm = valuesAsHash("fwhm"), snr = valuesAsHash("signal_noise"))

      val ip = new IsotopicPattern(
        moz = valuesAsHash("mz") toDouble,
        intensity = valuesAsHash("abundance") toFloat,
        charge = valuesAsHash("charge") toInt,
        fitScore = Some(valuesAsHash("fit") toFloat),
        scanInitialId = valuesAsHash("scan_num") toInt
      )

      isotopicPatterns += ip
    } //end

    lines.foreach(getFeatureFromRow)

    //start rough stuff
    var ipsByCharge = new HashMap[Int, ArrayBuffer[IsotopicPattern]]()
    isotopicPatterns.foreach(isp => ipsByCharge.update(isp.charge, ipsByCharge.getOrElseUpdate(isp.charge, new ArrayBuffer[IsotopicPattern]) += isp))

    var features = new ArrayBuffer[Feature]

    for ((charge, sameChargeIps) <- ipsByCharge) {

      sameChargeIps.sortWith(_.intensity < _.intensity)
      val ipsGroupedByMoz = new HashMap[Int, ArrayBuffer[IsotopicPattern]]()
      sameChargeIps.foreach(value => ipsGroupedByMoz.update(value.moz.toInt, ipsGroupedByMoz.getOrElse(value.moz.toInt, new ArrayBuffer[IsotopicPattern]) += value))

      var processedIp = Set[IsotopicPattern]()
      for (ips <- sameChargeIps) {

        ////// don't process a previously processed isotopic pattern
        if (!processedIp.contains(ips)) {

          ////// Take the current ip as reference
          //processedIp(ipId) = 1
          val mozOfRef = ips.moz
          val scanIdOfRef = ips.scanInitialId
          val cycleNumOfRef = lcmsScanSeq.scanById(scanIdOfRef).cycle

          ////// Retrieve putative isotopic patterns which belong to the same feature (same moz range)
          val mozInt = mozOfRef.toInt
          val sameMozRangeIps = new ArrayBuffer[IsotopicPattern] //same charge, same MozRange

          for (mozIndex <- mozInt - 1 to mozInt + 1) {
            sameMozRangeIps ++ ipsGroupedByMoz.getOrElse(mozIndex, new ArrayBuffer[IsotopicPattern])
          }

          val sameMozIpsByCycle = new HashMap[Int, ArrayBuffer[IsotopicPattern]]()
          sameMozRangeIps.filter(ip => (math.abs(ip.moz - mozOfRef) < Decon2LSMapParser.getMozInDalton(mozOfRef, ms1MozTol, ms1MozTolUnit)))
            .map(ip => sameMozIpsByCycle.getOrElseUpdate(lcmsScanSeq.scanById(ip.scanInitialId).cycle, new ArrayBuffer[IsotopicPattern]() += ip))

          ////// For each scan keep only the nearest moz from the reference moz

          var sameFtIpByCycleNum = sameMozIpsByCycle.map { tuple => tuple._1 -> (tuple._2.sortBy(ip => math.abs(ip.moz - mozOfRef) < math.abs(ip.moz - mozOfRef))) }.map { t => t._1 -> t._2(0) }

          ////// Sort cycle nums to retrieve cycle num range
          val sortedCycleNums = sameFtIpByCycleNum.keysIterator.toSeq.sortBy(x => x)
          val firstCycleNum = sortedCycleNums.first
          val lastCycleNum = sortedCycleNums.last

          var sameFtIps = ArrayBuffer[IsotopicPattern](ips) //not sur to add ips cause we will found it in our array

          var apex: IsotopicPattern = null
          var apexIntensity = 0f
          var gapCount = 0

          def extractIsotopicPattern(cycleNum: Int, ipPickerCb: IsotopicPattern => Unit): Option[IsotopicPattern] = {

            var curIp: IsotopicPattern = null

            try {
              curIp = sameFtIpByCycleNum(cycleNum)
              if (processedIp.contains(curIp)) {
                return Some(curIp)
              }
              ////// reset the gap counter
              gapCount = 0
              ipPickerCb(curIp)

              ////// check if this IP is higher than the current apex
              val curIpIntensity = curIp.intensity
              if (curIpIntensity > apexIntensity) {
                apexIntensity = curIpIntensity
                apex = curIp
              }

              ////// Mark the current ip as processed
              processedIp += curIp
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
          //end extractIsotopicPattern

          val ipPicker: IsotopicPattern => Unit = { sameFtIps += _ }
          breakable {
            for (curCycleNum <- cycleNumOfRef + 1 to lastCycleNum) {
              var ipId = extractIsotopicPattern(curCycleNum, ipPicker)
              if (ipId.isEmpty) break
            }
          }

          ////// backward extraction
          val ipPicker_2: IsotopicPattern => Unit = { sameFtIps.insert(0, _) }
          gapCount = 0
          breakable {
            for (curCycleNum <- cycleNumOfRef - 1 to firstCycleNum) {
              var ipId = extractIsotopicPattern(curCycleNum, ipPicker_2)
              if (ipId.isEmpty) break
            }
          }

          val ftScanCount = sameFtIps.length
          if (ftScanCount < minFtScanCount) {

            val dataPoints = sameFtIps.map(ip => (lcmsScanSeq.scanById(ip.scanInitialId).time -> ip.intensity)) toMap //map { (timeByScanId(_.scanInitialId),_.intensity) } sameFtIps

            val area = calcArea(dataPoints)

            val ms2EventIds = getMs2Events(lcmsScanSeq, lcmsScanSeq.getScanAtTime(lcmsScanSeq.scanById(scanIdOfRef).time, 2).initialId)
            ////// new FT with sameFtIps
            val ft = Feature(
              id = Feature.generateNewId(),
              moz = mozOfRef,
              intensity = area,
              charge = ips.charge,
              elutionTime = lcmsScanSeq.scanById(scanIdOfRef).time,
              duration = 0, // FIXME
              qualityScore = Double.NaN,
              ms1Count = sameFtIps.length,
              ms2Count = ms2EventIds.length,
              isOverlapping = false,
              isotopicPatterns = Some(sameFtIps.toArray),
              overlappingFeatures = null,
              relations = FeatureRelations(ms2EventIds = null,
                firstScanInitialId = sameFtIps.first.scanInitialId,
                lastScanInitialId = sameFtIps.last.scanInitialId,
                apexScanInitialId = apex.scanInitialId)
              )
            //die Dumper ft if !defined ft.firstScanInitialId
            features += ft
            processedIp += ips
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

  def calcArea(data: Map[Float, Float]): Float = {
    if (data.size == 0) {
      return 0f
    }
    val orderedKeys = data.keysIterator.toList.sort(_ < _)
    var xa = 0f
    var ya = 0f
    var i = 0
    var area = 0f
    while (i < orderedKeys.length - 1) {
      xa = orderedKeys(i)
      ya = data(orderedKeys(i))
      val xb = orderedKeys(i + 1)
      val yb = data(orderedKeys(i + 1))
      area += ((yb - ya) + (xb - xa)) / 2 * (yb - ya)
      xa = xb
      ya = yb
      i += 1
    }
    area
  }
}

