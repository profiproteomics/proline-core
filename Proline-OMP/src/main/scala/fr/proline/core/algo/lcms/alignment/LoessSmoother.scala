package fr.proline.core.algo.lcms.alignment

import scala.collection.mutable.ArrayBuffer
import org.apache.commons.math3.analysis.interpolation.LoessInterpolator
import fr.proline.core.algo.lcms.AlnSmoothingParams
import fr.proline.core.om.model.lcms._

class LoessSmoother extends IAlnSmoother {
  
  // TODO: smoothingParams are ignored here, maybe we should change the API
  def smoothLandmarks( landmarks: Seq[Landmark], smoothingParams: AlnSmoothingParams ): Seq[Landmark] = {
    
    // Create an array of landmarks
    val filteredLandmarks = landmarks.groupBy(_.time).map { lmg => computeMedianLandmark(lmg._2) } toArray
    val landmarksSortedByTime = filteredLandmarks.sortBy(_.time)
    
    // Extract values into two vectors
    val( xVals, yVals ) = (new ArrayBuffer[Double],new ArrayBuffer[Double])
    landmarksSortedByTime.foreach { lm =>
      xVals += lm.time.toDouble
      yVals += lm.deltaTime.toDouble
    }
    
    // Apply the loess smoothing
    val smoothedYVals = new LoessInterpolator().smooth(xVals.toArray, yVals.toArray)    
    
    // Fill buffers with smoothed values    
    val newLandmarks = xVals.zip(smoothedYVals).map { xy =>
      Landmark(xy._1.toFloat,xy._2.toFloat)
    }
    
    //mapAln.copy( timeList = newTimeList.toArray, deltaTimeList = newDeltaTimeList.toArray )
    newLandmarks
  }

}