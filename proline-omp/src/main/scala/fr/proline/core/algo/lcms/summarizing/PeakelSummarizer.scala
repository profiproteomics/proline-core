package fr.proline.core.algo.lcms.summarizing

import scala.collection.mutable.ArrayBuffer
import fr.profi.util.lang.EnhancedEnum
import fr.profi.util.math.linearInterpolation
import fr.proline.core.om.model.lcms.Peakel

object PeakelSummarizingMethod extends EnhancedEnum {
  val APEX_INTENSITY = Value
  val AREA = Value
  val AREA_ABOVE_HM = Value
  val TOP3_MEAN = Value
}

object PeakelSummarizer {
  
  import PeakelSummarizingMethod._
  
  def computePeakelIntensity( peakel: Peakel, method: PeakelSummarizingMethod.Value ): Float = {
    
    method match {
      case APEX_INTENSITY => summarizeUsingApexIntensity(peakel)
      case AREA => summarizeUsingArea(peakel)
      case AREA_ABOVE_HM => summarizeUsingAreaAboveHalfMaximum(peakel)
      case TOP3_MEAN => summarizeUsingMeanOfTop3(peakel)
    }
    
  }
  
  private def summarizeUsingApexIntensity( peakel: Peakel ): Float = {
    peakel.dataMatrix.intensityValues.max
  }
  
  private def summarizeUsingArea( peakel: Peakel ): Float = {
    peakel.dataMatrix.integratePeakel._2
  }
  
  private def summarizeUsingAreaAboveHalfMaximum( peakel: Peakel ): Float = {    
    
    // --- Interpolate the time of peaks at the half maximum peakel intensity --- //
    val apexIntensity = peakel.apexIntensity
    val halfApexIntensity = apexIntensity / 2
    val elutionTimes = peakel.getElutionTimes
    val lastTime = elutionTimes.last
    
    val intensityElutionTimePairs = peakel.getIntensityValues.zip(elutionTimes)
    val leftTimeAtHalfApex = interpolateFirstElutionTimeAtHalfMaximum(intensityElutionTimePairs, halfApexIntensity)
    val rightTimeAtHalfApex = interpolateFirstElutionTimeAtHalfMaximum(intensityElutionTimePairs.reverse, halfApexIntensity)
    
    val intensitiesAboveHM = peakel.getIntensityValues().zipWithIndex.filter(_._1 >= halfApexIntensity )
    //println( peakel.moz + "\t"+ leftTimeAtHalfApex + "\t" + rightTimeAtHalfApex + "\t" + intensitiesAboveHM.length)
    
    // --- Define some vars --- //
    var computedAAHM = 0f
    var prevPeakTime = 0f
    var prevPeakIntensity = 0f
    var prevPeakIntensityAboveHM = halfApexIntensity
    var prevPeakTimeAboveHM = leftTimeAtHalfApex
 
    // --- Iterate over peaks --- //
    val peakelCursor = peakel.dataMatrix.getNewCursor()
    
    while( peakelCursor.next() ) {
      
      // Compute intensity sum
      val intensity = peakelCursor.getIntensity()
      val curPeakTime = peakelCursor.getElutionTime()
      
      // Compute intensity Area Above Half Maximum
      if (curPeakTime >= leftTimeAtHalfApex && prevPeakTimeAboveHM < lastTime ) {
        
        if( curPeakTime <= rightTimeAtHalfApex)  {
          val deltaTime = curPeakTime - prevPeakTimeAboveHM
          computedAAHM += (peakelCursor.getIntensity + prevPeakIntensityAboveHM - apexIntensity) * deltaTime / 2
          prevPeakTimeAboveHM = curPeakTime
          prevPeakIntensityAboveHM = peakelCursor.getIntensity
        } else {
          val deltaTime = curPeakTime - prevPeakTimeAboveHM
          computedAAHM += (prevPeakIntensityAboveHM - halfApexIntensity) * deltaTime / 2
          prevPeakIntensityAboveHM = halfApexIntensity
          prevPeakTimeAboveHM = lastTime
        }
      }
      
      prevPeakTime = curPeakTime
      prevPeakIntensity = intensity
    }
    
    // TODO: fix peak left/right paddings (add missing areas)
    
    computedAAHM
  }
  
  private def interpolateFirstElutionTimeAtHalfMaximum( intensityTimePairs: Seq[(Float, Float)], halfApexIntensity: Float): Float = {
    
    val firstPeakIndex2 = intensityTimePairs.indexWhere(_._1 >= halfApexIntensity)
    val firstPeakIndex1 = if (firstPeakIndex2 > 0) firstPeakIndex2 - 1 else 0
    
    // If we don't have two distinct peaks, return time value of the first peakel peak
    val firstPeakTime = if (firstPeakIndex2 <= firstPeakIndex1) intensityTimePairs.head._2
    else {
      
      // Linear interpolation
      val interpolatedTime = linearInterpolation(
        halfApexIntensity,
        Seq( intensityTimePairs(firstPeakIndex1), intensityTimePairs(firstPeakIndex2) ),
        fixOutOfRange = false
      )
      
      interpolatedTime
    }
    
    firstPeakTime
  }
  
  private def summarizeUsingMeanOfTop3( peakel: Peakel ): Float = {    
    val top3Intensities = retrieveTop3Intensities( peakel )
    if( top3Intensities.length == 0 ) return 0
    
    top3Intensities.sum / top3Intensities.length
  }
  
  private def retrieveTop3Intensities( peakel: Peakel ): Seq[Float] = {
    val apexIndex = peakel.apexIndex
    val intensityValues = peakel.dataMatrix.intensityValues
    val intensityBuffer = new ArrayBuffer[Float](3)
    
    intensityBuffer += intensityValues(apexIndex)
    if( apexIndex > 0 ) intensityBuffer += intensityValues(apexIndex - 1)
    if( apexIndex < intensityValues.length - 1 ) intensityBuffer += intensityValues(apexIndex + 1)
    
    intensityBuffer
  }

}
