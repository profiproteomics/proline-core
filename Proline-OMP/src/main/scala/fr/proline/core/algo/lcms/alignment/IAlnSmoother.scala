package fr.proline.core.algo.lcms.alignment

case class AlnSmoothingParams( windowSize: Int, windowOverlap: Int, minWindowLandmarks: Int = 0 )

trait IAlnSmoother {
  
  import fr.proline.core.om.model.lcms._
  
  def smoothMapAlignment( mapAln: MapAlignment, smoothingParams: AlnSmoothingParams ): MapAlignment

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
    
    for( val step <- 0 to nbIntervals ) {
      
      val minVal = step * stepSize
      val maxVal = minVal + windowSize
      
      onEachWindow( minVal, maxVal )
    }

  }

}
  
  