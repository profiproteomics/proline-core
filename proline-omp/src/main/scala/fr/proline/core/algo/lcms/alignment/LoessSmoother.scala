package fr.proline.core.algo.lcms.alignment

import scala.collection.mutable.ArrayBuffer
import org.apache.commons.math3.analysis.interpolation.LoessInterpolator
import fr.proline.core.algo.lcms.AlnSmoothingParams
import fr.proline.core.om.model.lcms._
import fr.proline.core.Settings


class LoessSmoother extends IAlnSmoother {

  // TODO: smoothingParams are ignored here, maybe we should change the API
  def smoothLandmarks( landmarks: Seq[Landmark], smoothingParams: Option[AlnSmoothingParams]): Seq[Landmark] = {

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
//    val smoothedYVals = new LoessInterpolator().smooth(xVals.toArray, yVals.toArray)
      val smoothedYVals = {
        // Adjust the bandwidth parameter B to the number of points N.
        // The LoessInterpolator allows any bandwidth if N <= 2
        // Max value for the bandwidth is 1
        // B * N has to be over 2
        var bandwidth = Settings.LoessSmoother.defaultBandwidth
        val n = yVals.size
        if(n > 2) {
          while (bandwidth * n < 2 && bandwidth < 1) { bandwidth += 0.1 }
        }
        new LoessInterpolator(bandwidth, LoessInterpolator.DEFAULT_ROBUSTNESS_ITERS).smooth(xVals.toArray, yVals.toArray)
      }

    // Fill buffers with smoothed values
    val newLandmarks = xVals.zip(smoothedYVals).map { xy =>
      Landmark(xy._1.toFloat,xy._2.toFloat)
    }

    //mapAln.copy( timeList = newTimeList.toArray, deltaTimeList = newDeltaTimeList.toArray )
    newLandmarks
  }

}