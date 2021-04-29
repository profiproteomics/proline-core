package fr.proline.core.algo.lcms.alignment

import fr.proline.core.algo.lcms.AlnSmoothingParams

class LandmarkRangeSmoother extends IAlnSmoother {

  import fr.proline.core.om.model.lcms._
  import scala.collection.mutable.ArrayBuffer
  
  def smoothLandmarks( landmarks: Seq[Landmark], smoothingParams: Option[AlnSmoothingParams] ): Seq[Landmark] = {

    require(smoothingParams.isDefined, "Landmarks range smoother requires window size and window overlaps parameters")

    val smoothingWindowSize = smoothingParams.get.windowSize
    val smoothingWindowOverlap = smoothingParams.get.windowOverlap
    
    // Create an array of landmarks
    val nbLandmarks = landmarks.length
    val landmarksSortedByTime = landmarks.toList.sortBy( _.x)
    
    val newLandmarks = new ArrayBuffer[Landmark](nbLandmarks)
    
    // Define an anonymous function for landmark window processing
    val processWindowFn = new Function2[Double, Double, Unit] {
      
      def apply(minVal: Double, maxVal: Double): Unit = {
        
        val minIndex = minVal.toInt
        val maxIndex = if (maxVal < nbLandmarks) maxVal.toInt else nbLandmarks

        val landmarkGroup = new ArrayBuffer[Landmark](1 + maxIndex - minIndex)
        for (index <- minIndex until maxIndex) {
          landmarkGroup += landmarksSortedByTime(index)
        }

        // If the landmark group is completely filled
        if (landmarkGroup.length == smoothingWindowSize) {
          val medianLm = computeMedianLandmark(landmarkGroup)
          newLandmarks += medianLm
        }
        
        ()
      }
      
    }
  
    // Perform smoothing on each sliding window
    this.eachSlidingWindow( nbLandmarks, smoothingWindowSize, smoothingWindowOverlap, processWindowFn )
    
    // Instantiate a new map alignment
    //mapAln.copy( timeList = newTimeList.toArray, deltaTimeList = newDeltaTimeList.toArray )
    newLandmarks
  }

}