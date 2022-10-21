package fr.proline.core.algo.lcms.alignment

import fr.proline.core.algo.lcms.AlnSmoothingParams

trait IAlnSmoother {
  
  import fr.proline.core.om.model.lcms._
  
  def smoothLandmarks( landmarks: Seq[Landmark], smoothingParams: Option[AlnSmoothingParams]): Seq[Landmark]

  protected def computeMedianLandmark( landmarkGroup: Seq[Landmark] ): Landmark = {
    
    val lmSortedByDx = landmarkGroup.sortBy( _.dx)
    val lmSortedByX = landmarkGroup.sortBy( _.x)
    val nbLandmarks = landmarkGroup.length
    
    // Compute median landmark considering the delta time
    var medianLm: Landmark = null
    if( nbLandmarks % 2 == 0 ) { // even number of landmarks
      
      val secondIndex = nbLandmarks/2
      val firstIndex = secondIndex - 1
      val firstLm = lmSortedByDx(firstIndex)
      val secondLm = lmSortedByDx(secondIndex)
      
      medianLm = Landmark(x = ( lmSortedByX.head.x + lmSortedByX.last.x )/2, dx = ( firstLm.dx + secondLm.dx )/2)
    } // odd number of landmarks
    else { 
      medianLm = lmSortedByDx( (nbLandmarks-1)/2 )
      medianLm = Landmark(x =  ( lmSortedByX.head.x + lmSortedByX.last.x )/2, dx = medianLm.dx)
    }
    
    medianLm
  }
  
  protected def eachSlidingWindow( dataSetSize: Double, windowSize: Int, windowOverlap: Int, onEachWindow: Function2[Double,Double,Unit] ): Unit = {
    require( windowOverlap >= 0 && windowOverlap < 100 )
    
    val stepSize = windowSize * ((100-windowOverlap).toDouble/100)
    val nbIntervals = (dataSetSize/stepSize).toInt
    
    for( step <- 0 to nbIntervals ) {
      
      val minVal = step * stepSize
      val maxVal = minVal + windowSize
      
      onEachWindow( minVal, maxVal )
    }

  }

}
  
  