package fr.proline.core.algo.lcms.alignment

class TimeWindowSmoother extends IAlnSmoother {

  import fr.proline.core.om.lcms._
  import scala.collection.mutable.ArrayBuffer
  
  def smoothMapAlignment( mapAln: MapAlignment, smoothingParams: AlnSmoothingParams ): MapAlignment = {
   
    val smoothingTimeInterval = smoothingParams.windowSize
    val smoothingWindowOverlap = smoothingParams.windowOverlap
    val minWindowLandmarks = smoothingParams.minWindowLandmarks
    
    // Create an array of landmarks
    val landmarks = mapAln.getLandmarks
    val nbLandmarks = landmarks.length
    val landmarksSortedByTime = landmarks.toList.sort { (a,b) => a.time <= b.time } 
    
    // last landmark time
    val totalTime = landmarksSortedByTime(nbLandmarks-1).time
    
    val( newTimeList, newDeltaTimeList) = ( new ArrayBuffer[Float](0), new ArrayBuffer[Float](0) )
    
    // Define an anonymous function for time window processing
    val processWindowFn = new Function2[Float, Float, Unit] {
      
      def apply(minVal: Float, maxVal: Float): Unit = {
        
        var nextLandmarkTime = minVal        
        var landmarkIndex = 0
        
        val landmarkGroup = new ArrayBuffer[Landmark](0)
        while( landmarkIndex < nbLandmarks && landmarksSortedByTime(landmarkIndex).time <= maxVal ) {
          
          landmarkGroup += landmarksSortedByTime(landmarkIndex)
          
          landmarkIndex += 1
        }
      
        // If the landmark group is filled enough
        if( landmarkGroup.length > minWindowLandmarks ) {
        
          val medianLm = computeMedianLandmark( landmarkGroup )
          newTimeList += medianLm.time
          newDeltaTimeList += medianLm.deltaTime
        }
        
      }
    }
    
    this.eachSlidingWindow( totalTime, smoothingTimeInterval, smoothingWindowOverlap, processWindowFn )
  
    // Instantiate a new map alignment
    val newMapAln = new MapAlignment (
                          fromMapId = mapAln.fromMapId,
                          toMapId = mapAln.toMapId,
                          massRange = mapAln.massRange,
                          timeList = newTimeList.toArray,
                          deltaTimeList = newDeltaTimeList.toArray
                        )
    
    newMapAln
  }

}