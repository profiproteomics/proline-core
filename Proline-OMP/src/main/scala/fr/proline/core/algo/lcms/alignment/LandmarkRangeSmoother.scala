package fr.proline.core.algo.lcms.alignment

import fr.proline.core.algo.lcms.AlnSmoothingParams

class LandmarkRangeSmoother extends IAlnSmoother {

  import fr.proline.core.om.model.lcms._
  import scala.collection.mutable.ArrayBuffer
  
  def smoothLandmarks( landmarks: Seq[Landmark], smoothingParams: AlnSmoothingParams ): Seq[Landmark] = {
   
    val smoothingWindowSize = smoothingParams.windowSize
    val smoothingWindowOverlap = smoothingParams.windowOverlap
    
    // Create an array of landmarks
    val nbLandmarks = landmarks.length
    val landmarksSortedByTime = landmarks.toList.sortBy( _.time )
    
    val newLandmarks = new ArrayBuffer[Landmark](nbLandmarks)
    
    // Define an anonymous function for landmark window processing
    val processWindowFn = new Function2[Float, Float, Unit] {
      
      def apply(minVal: Float, maxVal: Float): Unit = {
        
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