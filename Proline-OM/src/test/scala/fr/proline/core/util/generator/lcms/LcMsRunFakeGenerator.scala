package fr.proline.core.util.generator.lcms

import scala.collection.mutable.ArrayBuffer
import com.typesafe.scalalogging.slf4j.Logging
import fr.proline.core.om.model.lcms._
import fr.proline.util.math.calcLineParams
import fr.proline.util.random._

/**
 * Utility class to generate a faked LcMsRun.
 *
 * @param ms1RelativeLandmarks Relative MS landmarks as an array of data points [time,intensity]
 * @param minMoz Minimum feature m/z
 * @param maxMoz Maximum feature m/z
 * @param minTime Minimum feature elution time
 * @param maxTime Maximum feature elution time
 */
class LcMsRunFakeGenerator(
  rawFile: RawFile,
  minMoz: Int,
  maxMoz: Int,
  maxTime: Int,
  ms1MaxIntensity: Int,
  ms2MaxIntensity: Int,
  ms1RelativeLandmarks: Array[Pair[Float,Float]],
  intensityRelativeVariability: Float = 0.2f,
  ms1Duration: Float = 2,
  maxMs2PerCycle: Int = 20
) extends Logging {
  require( ms1RelativeLandmarks(0)._1 > 0, "first landmark time value must be greater than zero" ) 
  
  private final val MS1_MAX_DYNAMIC_RANGE = 10e5
  private final val MS1_MIN_RELATIVE_INTENSITY = 1/MS1_MAX_DYNAMIC_RANGE
  private final val MS1_BASE_PEAK_FACTORS = (0.01f,1f)
  private final val MS2_BASE_PEAK_FACTORS = (10,80)
  private final val MS1_MOZ_CENTER = 500
  private final val MS2_MOZ_CENTER = 500
  private final val PRECURSOR_CHARGE_RANGE = (2,4)
  private final val MS2_DURATION = ms1Duration / (maxMs2PerCycle+1)
  
  //private val elutionTimes = (0 to (maxTime/ms1Duration).toInt ).map( _ * ms1Duration )
  
  def generateLcMsRun(): LcMsRun = {
    
    val relativeTic = _generateRelativeTIC()
    val runId = LcMsRun.generateNewId
    val scanSeq = _generateScanSequence(relativeTic,runId)
    
    /*for( scan <- scanSeq.scans ) {
      println("initialId=%d, cycle=%d, time=%f, msLevel=%d, tic=%f, basePeakMoz=%f, basePeakIntensity=%f".format(
        scan.initialId, scan.cycle, scan.time, scan.msLevel, scan.tic, scan.basePeakMoz, scan.basePeakIntensity
      ))
      
      if( scan.msLevel == 2 ) {
        println(scan.precursorCharge.get + " " + scan.precursorMoz.get)
      }
    }*/
    
    
    LcMsRun(
      id = runId,
      number = 1,
      runStart = 0,
      runStop = scanSeq.scans.last.time,
      duration = maxTime,
      rawFile = rawFile,
      scanSequence = Some(scanSeq)
    )
  }
  
  private def _generateRelativeTIC(): Array[Pair[Float,Float]] = {
    
    var prevLandmark = (0f,0f)
    val tic = new ArrayBuffer[Pair[Float,Float]]
    
    // Loop over landmarks in order to generate the TIC
    for( landmark <- ms1RelativeLandmarks ) {
      val( landmarkTime, landmarkIntensity ) = landmark
      require(landmarkIntensity >= MS1_MIN_RELATIVE_INTENSITY && landmarkIntensity <= 1)
      
      val lineParams = calcLineParams(prevLandmark._1,prevLandmark._2,landmarkTime,landmarkIntensity)
      
      // Interpolate the intensity between the previous and current landmark
      for( scanNumber <- (prevLandmark._1/ms1Duration).toInt + 1 to (landmarkTime/ms1Duration).toInt ) {
        
        val scanTime = scanNumber * ms1Duration
        val interpolatedIntensity = lineParams._1 * scanTime + lineParams._2
        val intensityError = interpolatedIntensity * intensityRelativeVariability
        
        var adjustedIntensity = LcMsRandomator.fluctuateValue(interpolatedIntensity, intensityError)
        if( adjustedIntensity > 1 ) adjustedIntensity = 1
        else if( adjustedIntensity < MS1_MIN_RELATIVE_INTENSITY ) adjustedIntensity = MS1_MIN_RELATIVE_INTENSITY
        
        tic += (scanTime -> adjustedIntensity.toFloat)
      }
      
      prevLandmark = landmark
    }
    
    tic.toArray
  }
  
  
  private def _generateScanSequence(tic: Array[Pair[Float,Float]], runId: Long): LcMsScanSequence = {
    
    var initialId, cycle, ms2ScansCount = 0
    val scans = new ArrayBuffer[LcMsScan](tic.length)
    
    var minIntensity = ms1MaxIntensity.toDouble
    for( dataPoint <- tic ) {
      initialId += 1
      cycle += 1
      
      val ms1Time = dataPoint._1
      
      // Estimate the number of MS2 spectra for this cycle
      val ms2Count = _ms1RelIntensityToCycleMs2Count( dataPoint._2 )
      //println(ms2Count)
      
      // Select a random factor between 50 and 80 %
      val ms1TIC = dataPoint._2 * ms1MaxIntensity
      val ms1BasePeakIntFactor = Function.tupled(randomFloat _)(MS1_BASE_PEAK_FACTORS).toFloat / 100
      
      if( ms1TIC < minIntensity ) minIntensity = ms1TIC.toDouble
      
      // Add MS1 scan
      scans += LcMsScan(
        id = LcMsScan.generateNewId,
        initialId = initialId,
        cycle = cycle,
        time = ms1Time,
        msLevel = 1,
        tic = ms1TIC,
        basePeakMoz = LcMsRandomator.randomMoz(minMoz,maxMoz,MS1_MOZ_CENTER),
        basePeakIntensity = LcMsRandomator.randomIntensity(0, ms1TIC * ms1BasePeakIntFactor ),
        runId = runId
      )
      
      // Add MS2 scans
      var ms2ScanTimeOffset = 0f
      for( i <- 1 to ms2Count ) {
        ms2ScansCount += 1
        initialId += 1
        ms2ScanTimeOffset += MS2_DURATION
        
        // Select a random factor between 10 and 80 %
        val ms2BasePeakIntFactor = Function.tupled(randomInt _)(MS2_BASE_PEAK_FACTORS).toFloat / 100
        val ms2TIC = LcMsRandomator.randomIntensity(0, ms2MaxIntensity )
        
        scans += LcMsScan(
          id = LcMsScan.generateNewId,
          initialId = initialId,
          cycle = cycle,
          time = ms1Time + ms2ScanTimeOffset,
          msLevel = 2,
          tic = ms2TIC,
          basePeakMoz = LcMsRandomator.randomMoz(minMoz,maxMoz, MS2_MOZ_CENTER ),
          basePeakIntensity = LcMsRandomator.randomIntensity(0f, ms2TIC * ms2BasePeakIntFactor ),
          precursorMoz = Some(LcMsRandomator.randomMoz(minMoz,maxMoz, MS1_MOZ_CENTER)),
          precursorCharge = Some(Function.tupled(LcMsRandomator.randomCharge _)(PRECURSOR_CHARGE_RANGE)),
          runId = runId
        )
      }
    }
    
    val scanSeq = LcMsScanSequence(
      runId = runId,
      rawFileName = rawFile.name,
      minIntensity = minIntensity,
      maxIntensity = ms1MaxIntensity.toDouble,
      ms1ScansCount = tic.length,
      ms2ScansCount = ms2ScansCount,
      scans = scans.toArray
    )
    
    scanSeq
  }
  
  private def _ms1RelIntensityToCycleMs2Count(relIntensity: Float): Int = {
    require( relIntensity <= 1, "got %f but intensity value must be relative (i.e. between 0 and 1)".format(relIntensity) )
    
    if( relIntensity == 0 ) 0
    else {
      val ms2Count = maxMs2PerCycle + (2 * math.log(relIntensity).toInt)
      if( ms2Count < 0 ) 0 else ms2Count
    }
  }
  
}