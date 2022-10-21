package fr.proline.core.algo.lcms.alignment

import com.typesafe.scalalogging.LazyLogging
import fr.proline.core.algo.lcms.AlnSmoothingParams
import fr.proline.core.algo.msq.profilizer.CommonsStatHelper

class LandmarkRangeSmoother extends IAlnSmoother with LazyLogging {

  import fr.proline.core.om.model.lcms._

  import scala.collection.mutable.ArrayBuffer
  
  def smoothLandmarks( landmarks: Seq[Landmark], smoothingParams: Option[AlnSmoothingParams] ): Seq[Landmark] = {

    require(smoothingParams.isDefined, "Landmarks range smoother requires window size and window overlaps parameters")

    val smoothingWindowSize = smoothingParams.get.windowSize
    val smoothingWindowOverlap = smoothingParams.get.windowOverlap
    
    // Create an array of landmarks
    val nbLandmarks = landmarks.length
    val landmarksSortedByTime = landmarks.toList.sortBy( _.x)
    
    val newLandmarks = new ArrayBuffer[Landmark](nbLandmarks)
    
    // Define an anonymous function for landmark window processing
    val processWindowFn = new Function2[Double, Double, Unit] {
      
      def apply(minVal: Double, maxVal: Double): Unit = {
        
        val minIndex = minVal.toInt
        val maxIndex = if (maxVal < nbLandmarks) maxVal.toInt else nbLandmarks

        val landmarkGroup = new ArrayBuffer[Landmark](1 + maxIndex - minIndex)
        for (index <- minIndex until maxIndex) {
          landmarkGroup += landmarksSortedByTime(index)
        }

        // If the landmark group is completely filled
        if (landmarkGroup.length == smoothingWindowSize) {
          val lmStats = CommonsStatHelper.calcExtendedStatSummary(landmarkGroup.map(_.dx).toArray)
          val medianLm = computeMedianLandmark(landmarkGroup)
          val t = Math.max(math.abs(lmStats.getMedian() - lmStats.getQ1() - 1.5*lmStats.getInterQuartileRange()),
                          math.abs(lmStats.getQ3() + 1.5*lmStats.getInterQuartileRange() - lmStats.getMedian()))
          val newLandmark = medianLm.copy(tx = t)

//          val (in, out) = landmarkGroup.map(_.dx).partition(dx => math.abs(dx - lmStats.getMedian()) < t)
//          logger.info("Inner fence tolerance ({}-{}) contains {}% of the {} landmarks", landmarkGroup.head.x/60.0, landmarkGroup.last.x/60.0, 100*in.length/(in.length+out.length), landmarkGroup.size)

          newLandmarks += newLandmark
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