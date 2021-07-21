package fr.proline.core.algo.lcms.alignment

import scala.collection.mutable.ArrayBuffer
import org.apache.commons.math3.analysis.interpolation.LoessInterpolator
import fr.proline.core.algo.lcms.AlnSmoothingParams
import fr.proline.core.om.model.lcms._
import fr.proline.core.Settings


class LoessSmoother extends IAlnSmoother {

  def smoothLandmarks( landmarks: Seq[Landmark], smoothingParams: Option[AlnSmoothingParams]): Seq[Landmark] = {

    // Create an array of landmarks
    val filteredLandmarks = landmarks.groupBy(_.x).map { lmg => computeMedianLandmark(lmg._2) } toArray
    val landmarksSortedByTime = filteredLandmarks.sortBy(_.x)

    // Extract values into two vectors
    var( xVals, yVals ) = (new ArrayBuffer[Double],new ArrayBuffer[Double])
    landmarksSortedByTime.foreach { lm =>
      xVals += lm.x
      yVals += lm.dx
    }

    // Apply the loess smoothing
      var smoothedYVals = {
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

    // filter (xVals, yVals, smoothedYVals) to remove potential NaNs
    val filteredVals = (xVals, yVals, smoothedYVals).zipped.filter{ case(x, y, ys) => !ys.isNaN }
    xVals = filteredVals._1
    yVals = filteredVals._2
    smoothedYVals = filteredVals._3

    val residuals = yVals.zip(smoothedYVals).map{ yy => (yy._1-yy._2)*(yy._1-yy._2) }
    val tolerances = try {
      val smoothedResiduals = new LoessInterpolator(math.max(0.5, Settings.LoessSmoother.defaultBandwidth), LoessInterpolator.DEFAULT_ROBUSTNESS_ITERS).smooth(xVals.toArray, residuals.toArray)
      var tol = smoothedResiduals.map(r => 4.5 * math.sqrt(math.max(0.0, r)))
      if (tol.count(_.isNaN) > 0) {
        tol = tol.map(v => if(v.isNaN) 0.0 else v )
      }
      tol
    } catch {
      case e: RuntimeException => Array.fill(xVals.size)(0.0)
    }

    // Fill buffers with smoothed values
    val newLandmarks = (xVals, smoothedYVals, tolerances).zipped.map{ (x,y,r) =>
      Landmark(x, y, r)
    }

    //mapAln.copy( timeList = newTimeList.toArray, deltaTimeList = newDeltaTimeList.toArray )
    newLandmarks
  }

}