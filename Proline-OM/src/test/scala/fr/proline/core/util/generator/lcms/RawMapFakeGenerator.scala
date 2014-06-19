package fr.proline.core.util.generator.lcms

import scala.collection.mutable.ArrayBuffer
import scala.util.control.Breaks._

import com.typesafe.scalalogging.slf4j.Logging

import fr.proline.core.om.model.lcms._
import fr.profi.util.random._

/**
 * Utility class to generate a faked RawMap.
 *
 * @param nbFeatures Number of features to create
 * @param minMoz Minimum feature m/z
 * @param maxMoz Maximum feature m/z
 * @param minTime Minimum feature elution time
 * @param maxTime Maximum feature elution time
 */
class RawMapFakeGenerator (
  nbFeatures: Int,
  minMoz: Int,
  maxMoz: Int,
  minTime: Int,
  maxTime: Int
) extends Logging {
  
  require(nbFeatures > 0)
  require(minMoz >= 0 && maxMoz > minMoz)
  require(minTime >= 0 && maxTime > minTime)
  
  private final val MS1_MOZ_CENTER = 500
  private final val FT_CHARGE_RANGE = (2,4)
  private final val FT_DURATION_RANGE = (5,120)
  private final val FT_DURATION_STDDEV = 20 // seconds
  private final val FT_RELATIVE_INTENSITY_RANGE = (10e-4,10e-2)
  
  def generateRawMap( lcMsRun: LcMsRun ): RawMap = {
    
    val rawMapId = RawMap.generateNewId
    val scanSeq = lcMsRun.scanSequence.get
    val( minRunIntensity, maxRunIntensity ) = (scanSeq.minIntensity,scanSeq.maxIntensity)
    val( ms1Scans, ms2Scans ) = scanSeq.scans.partition(_.msLevel == 1)
    val ms1ScanByInitialId = Map() ++ ms1Scans.map(s => s.initialId -> s)
    val ms2ScansByCycle =  Map() ++ ms2Scans.groupBy( _.cycle )
    val ms1TIC = ms1Scans.map( s => s.initialId -> s.tic )
    
    val scanNumberHisto = new ArrayBuffer[Int](ms1TIC.length)
    for( dp <- ms1TIC ) {
      var weight = (100*dp._2/maxRunIntensity).toInt
      if( weight < 1 ) weight = 1
      for( i <- 1 to weight ) scanNumberHisto += dp._1
    }
    
    val features = new ArrayBuffer[Feature](nbFeatures)
    
    var needNewFeature = true
    while( needNewFeature ) {
      
      breakable {
      
        val randomIdx = randomInt(0,scanNumberHisto.length-1)
        val randomScanNumber = scanNumberHisto(randomIdx)
        val randomScan = ms1ScanByInitialId(randomScanNumber)
        val ms2ScansInCycle = ms2ScansByCycle.get(randomScan.cycle)
        
        val ftTime = randomScan.time
        
        // Continue if time is out of range
        if( ftTime < minTime || ftTime > maxTime ) break
        
        val( ftMoz, ftCharge ) = if (ms2ScansInCycle.isDefined) {
          val ms2Idx = randomInt(0,ms2ScansInCycle.get.length-1)
          val ms2Scan = ms2ScansInCycle.get(ms2Idx)
          (ms2Scan.precursorMoz.get, ms2Scan.precursorCharge.get)
        } else {
          LcMsRandomator.randomMoz(minMoz, maxMoz, MS1_MOZ_CENTER) ->
          LcMsRandomator.randomCharge(FT_CHARGE_RANGE._1,FT_CHARGE_RANGE._2)
        }
        
        // Continue if m/z is out of range
        if( ftMoz < minMoz || ftMoz > maxMoz ) break
        
        val ftIntensity = LcMsRandomator.randomIntensity(
          (randomScan.tic*FT_RELATIVE_INTENSITY_RANGE._1).toFloat, (randomScan.tic*FT_RELATIVE_INTENSITY_RANGE._2).toFloat
        )
        
        val ftDuration = LcMsRandomator.randomDuration(FT_DURATION_RANGE._1,FT_DURATION_RANGE._2,FT_DURATION_STDDEV)
        val( firstTime, lastTime ) = ( ftTime - ftDuration/2, ftTime + ftDuration/2)
        val firstScanIdx = ms1Scans.indexWhere( _.time > firstTime )
        var lastScanIdx = ms1Scans.indexWhere( _.time > lastTime )
        if( lastScanIdx == -1 ) lastScanIdx = ms1Scans.length - 1
        
        val firstScan = ms1Scans(firstScanIdx)
        val lastScan = ms1Scans(lastScanIdx)
        val ms1Count = 1 + lastScanIdx - firstScanIdx
        val ftArea = ftIntensity * ftDuration
        
        // Generate feature      
        features += Feature(
          id = Feature.generateNewId,
          moz = ftMoz,
          intensity = ftArea,
          charge = ftCharge,
          elutionTime = ftTime,
          duration = ftDuration,
          qualityScore = 0,
          ms1Count = ms1Count,
          ms2Count = math.sqrt( ftIntensity / scanSeq.minIntensity ).toInt,
          isOverlapping = false,
          isotopicPatterns = None,
          relations = new FeatureRelations(
            ms2EventIds = Array.empty[Long],
            firstScanInitialId = firstScan.initialId,
            lastScanInitialId = lastScan.initialId,
            apexScanInitialId = randomScan.initialId,
            firstScanId = firstScan.id,
            lastScanId = lastScan.id,
            apexScanId = randomScan.id,
            rawMapId = rawMapId
          )
        )
        
        // Check if we still need to create new features
        needNewFeature = features.size < nbFeatures
      }
  
    }
    
    RawMap(
      id = rawMapId,
      name = lcMsRun.rawFile.name,
      isProcessed = false,
      creationTimestamp = new java.util.Date,
      features = features.toArray,
      runId = lcMsRun.id,
      peakPickingSoftware = PeakPickingSoftware(
        id = PeakPickingSoftware.generateNewId,
        name = "simulated PPS",
        version = "1.0",
        algorithm = "RawMapFakeGenerator"
      )
    )
  }
  
}