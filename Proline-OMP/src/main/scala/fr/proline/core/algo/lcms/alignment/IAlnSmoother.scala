package fr.proline.core.algo.lcms.alignment

import fr.proline.core.algo.lcms.AlnSmoothingParams

trait IAlnSmoother {
  
  import fr.proline.core.om.model.lcms._
  
  def smoothLandmarks( landmarks: Seq[Landmark], smoothingParams: AlnSmoothingParams ): Seq[Landmark]
  
  @deprecated("0.1.1","use smoothLandmarks instead")
  def smoothMapAlignment(mapAlignment: MapAlignment, smoothingParams: AlnSmoothingParams ): MapAlignment = {
    
    val smoothedLandmarks = this.smoothLandmarks(mapAlignment.getLandmarks,smoothingParams)
    
    val( timeList, deltaTimeList ) = ( new Array[Float](smoothedLandmarks.length), new Array[Float](smoothedLandmarks.length) )
    smoothedLandmarks.indices.foreach { i =>
      val lm = smoothedLandmarks(i)
      timeList(i) = lm.time
      deltaTimeList(i) = lm.deltaTime
    }
    
    mapAlignment.copy( timeList = timeList, deltaTimeList = deltaTimeList )
  }

  protected def computeMedianLandmark( landmarkGroup: Seq[Landmark] ): Landmark = {
    
    val lmSortedByDeltaTime = landmarkGroup.sortBy( _.deltaTime )
    val nbLandmarks = landmarkGroup.length
    
    // Compute median landmark considering the delta time
    var medianLm: Landmark = null
    if( nbLandmarks % 2 == 0 ) { // even number of landmarks
      
      val secondIndex = nbLandmarks/2
      val firstIndex = secondIndex - 1
      val firstLm = lmSortedByDeltaTime(firstIndex)
      val secondLm = lmSortedByDeltaTime(secondIndex)
      
      medianLm = Landmark( time = ( firstLm.time + secondLm.time )/2,
                           deltaTime = ( firstLm.deltaTime + secondLm.deltaTime )/2 )
    } // odd number of landmarks
    else { medianLm = lmSortedByDeltaTime( (nbLandmarks-1)/2 ) }
    
    medianLm
  }
  
  protected def eachSlidingWindow( dataSetSize: Float, windowSize: Int, windowOverlap: Int, onEachWindow: Function2[Float,Float,Unit] ): Unit = {
    require( windowOverlap >= 0 && windowOverlap < 100 )
    
    val stepSize = windowSize * ((100-windowOverlap).toFloat/100)
    val nbIntervals = (dataSetSize/stepSize).toInt
    
    for( step <- 0 to nbIntervals ) {
      
      val minVal = step * stepSize
      val maxVal = minVal + windowSize
      
      onEachWindow( minVal, maxVal )
    }

  }

}
  
  