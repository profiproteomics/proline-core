package fr.proline.core.algo.lcms.alignment

import fr.proline.core.algo.lcms.AlnSmoothingParams

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
    val landmarksSortedByTime = landmarks.sortBy( _.time )
    
    // last landmark time
    val totalTime = landmarksSortedByTime(nbLandmarks-1).time
    
    val newLandmarks = new ArrayBuffer[Landmark](nbLandmarks)
    
    // Define an anonymous function for time window processing
    val processWindowFn = new Function2[Float, Float, Unit] {
      
      def apply(minVal: Float, maxVal: Float): Unit = {
        
        var nextLandmarkTime = minVal
        var landmarkIdx = landmarksSortedByTime.indexWhere(_.time >= minVal)
        
        val landmarkGroup = new ArrayBuffer[Landmark](100)
        while (landmarkIdx < nbLandmarks && landmarksSortedByTime(landmarkIdx).time < maxVal) {

          landmarkGroup += landmarksSortedByTime(landmarkIdx)

          landmarkIdx += 1
        }

        // If the landmark group is filled enough
        if (landmarkGroup.length >= minWindowLandmarks) {
          val medianLm = computeMedianLandmark(landmarkGroup)
          newLandmarks += medianLm
        }
      }
    }
    
    this.eachSlidingWindow( totalTime, smoothingTimeInterval, smoothingWindowOverlap, processWindowFn )
  
    // Instantiate a new map alignment
    //mapAln.copy( timeList = newTimeList.toArray, deltaTimeList = newDeltaTimeList.toArray )
    newLandmarks
  }

}