package fr.proline.core.algo.lcms.alignment

class LandmarkRangeSmoother extends IAlnSmoother {

  import fr.proline.core.om.model.lcms._
  import scala.collection.mutable.ArrayBuffer
  
  def smoothMapAlignment( mapAln: MapAlignment, smoothingParams: AlnSmoothingParams ): MapAlignment = {
   
    val smoothingWindowSize = smoothingParams.windowSize
    val smoothingWindowOverlap = smoothingParams.windowOverlap
    
    // Create an array of landmarks
    val landmarks = mapAln.getLandmarks
    val nbLandmarks = landmarks.length
    val landmarksSortedByTime = landmarks.toList.sortBy( _.time )
    
    val( newTimeList, newDeltaTimeList) = ( new ArrayBuffer[Float](0), new ArrayBuffer[Float](0) )
    
    // Define an anonymous function for landmark window processing
    val processWindowFn = new Function2[Float, Float, Unit] {
      
      def apply(minVal: Float, maxVal: Float): Unit = {
        
        val minIndex = minVal.toInt
        val maxIndex = if( maxVal < nbLandmarks ) maxVal.toInt else nbLandmarks
        
        val landmarkGroup = new ArrayBuffer[Landmark](0)
        for( index <- minIndex until maxIndex ) {
          landmarkGroup += landmarksSortedByTime(index)
        }
        
        // If the landmark group is completely filled
        if( landmarkGroup.length == smoothingWindowSize ) {
        
          val medianLm = computeMedianLandmark( landmarkGroup )
          newTimeList += medianLm.time
          newDeltaTimeList += medianLm.deltaTime
          
        }
        
        ()
      }
      
    }
  
    // Perform smoothing on each sliding window
    this.eachSlidingWindow( nbLandmarks, smoothingWindowSize, smoothingWindowOverlap, processWindowFn )
    
    // Instantiate a new map alignment
    mapAln.copy( timeList = newTimeList.toArray, deltaTimeList = newDeltaTimeList.toArray )
    
  }

}