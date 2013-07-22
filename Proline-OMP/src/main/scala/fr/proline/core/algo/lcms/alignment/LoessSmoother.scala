package fr.proline.core.algo.lcms.alignment

import fr.proline.core.om.model.lcms._
import scala.collection.mutable.ArrayBuffer
import org.apache.commons.math.analysis.interpolation.LoessInterpolator
import fr.proline.core.algo.lcms.AlnSmoothingParams

class LoessSmoother extends IAlnSmoother {
  
  // TODO: smoothingParams are ignored here, maybe we should change the API
  def smoothMapAlignment( mapAln: MapAlignment, smoothingParams: AlnSmoothingParams ): MapAlignment = {
    
    // Create an array of landmarks
    val landmarks = mapAln.getLandmarks
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
    val( newTimeList, newDeltaTimeList ) = (new ArrayBuffer[Float],new ArrayBuffer[Float])
    xVals.zip(smoothedYVals).foreach { xy =>
      newTimeList += xy._1.toFloat
      newDeltaTimeList += xy._2.toFloat
    }
    
    mapAln.copy( timeList = newTimeList.toArray, deltaTimeList = newDeltaTimeList.toArray )
  }

}