package fr.proline.core.algo.lcms.alignment

import fr.proline.core.algo.lcms.AlnSmoothingParams
import fr.proline.core.algo.msq.profilizer.CommonsStatHelper

class TimeWindowSmoother extends IAlnSmoother {

  import fr.proline.core.om.model.lcms._
  import scala.collection.mutable.ArrayBuffer
  
  def smoothLandmarks( landmarks: Seq[Landmark], smoothingParams: Option[AlnSmoothingParams]): Seq[Landmark] = {

    require(smoothingParams.isDefined, "Time range smoother requires window size, window overlaps and minWindowLandmarks parameters")
    require(smoothingParams.get.minWindowLandmarks.isDefined, "Time range smoother requires minWindowLandmarks parameter")

    val smoothingTimeInterval = smoothingParams.get.windowSize
    val smoothingWindowOverlap = smoothingParams.get.windowOverlap
    val minWindowLandmarks = smoothingParams.get.minWindowLandmarks.get
    
    // Create an array of landmarks
    val nbLandmarks = landmarks.length
    val landmarksSortedByTime = landmarks.sortBy( _.x)
    
    // last landmark time
    val totalTime = landmarksSortedByTime(nbLandmarks-1).x
    
    val newLandmarks = new ArrayBuffer[Landmark](nbLandmarks)
    
    // Define an anonymous function for time window processing
    val processWindowFn = new Function2[Double, Double, Unit] {
      
      def apply(minVal: Double, maxVal: Double): Unit = {
        
        var nextLandmarkTime = minVal
        var landmarkIdx = landmarksSortedByTime.indexWhere(_.x >= minVal)
        
        val landmarkGroup = new ArrayBuffer[Landmark](100)
        while (landmarkIdx < nbLandmarks && landmarksSortedByTime(landmarkIdx).x < maxVal) {

          landmarkGroup += landmarksSortedByTime(landmarkIdx)
          landmarkIdx += 1
        }

        // If the landmark group is filled enough
        if (landmarkGroup.length >= minWindowLandmarks) {
          val lmStats = CommonsStatHelper.calcExtendedStatSummary(landmarkGroup.map(_.dx).toArray)
          val medianLm = computeMedianLandmark(landmarkGroup)
          val newLandmark = medianLm.copy(tx = math.abs(3*lmStats.getInterQuartileRange()))
          newLandmarks += newLandmark
        }
      }
    }
    
    this.eachSlidingWindow( totalTime, smoothingTimeInterval, smoothingWindowOverlap, processWindowFn )
  
    // Instantiate a new map alignment
    //mapAln.copy( timeList = newTimeList.toArray, deltaTimeList = newDeltaTimeList.toArray )
    newLandmarks
  }

}