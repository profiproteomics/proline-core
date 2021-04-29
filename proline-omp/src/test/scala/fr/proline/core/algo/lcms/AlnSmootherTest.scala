package fr.proline.core.algo.lcms

import scala.collection.mutable.ArrayBuffer
import org.junit.Before
import org.junit.Test
import org.scalatest.junit.JUnitSuite
import org.scalatest.MustMatchers
import com.typesafe.scalalogging.StrictLogging
import fr.proline.core.om.model.lcms._

class AlnSmootherTest extends JUnitSuite with MustMatchers with StrictLogging {
  
  val landmarkRangeSmoother = AlnSmoother(AlnSmoothing.LANDMARK_RANGE.toString)
  val loessSmoother = AlnSmoother(AlnSmoothing.LOESS.toString)
  val timeWindowSmoother = AlnSmoother(AlnSmoothing.TIME_WINDOW.toString)
  
  val timeList = Array(1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20).map(_.toFloat)
  val deltaTimeList = timeList.map( math.sin(_).toFloat )
  
  val landmarks: Seq[Landmark] = {
    
    val reducedDeltaTimeList = deltaTimeList.map( _ * 0.9f )
    val increasedDeltaTimeList = deltaTimeList.map( _ * 1.1f )
    
    // Note: we force the production of an unsorted landmarks in order to check if it is still handled correctly
    val fullTimeList = timeList ++ timeList ++ timeList
    val fullDeltaTimeList = reducedDeltaTimeList ++ deltaTimeList ++ increasedDeltaTimeList
    
    fullTimeList.zip(fullDeltaTimeList).map { case (time,delta) =>
      Landmark(time, delta)
    }
    /*new MapAlignment(
      refMapId = 1L,
      targetMapId = 2L,
      massRange = Pair(0,10000),
      timeList = timeList ++ timeList ++ timeList,
      deltaTimeList = reducedDeltaTimeList ++ deltaTimeList ++ increasedDeltaTimeList
    )*/
    
  }
  
  @Test
  def compareSmoothings() {
    
    val lmRangeSmoothingParams = new AlnSmoothingParams( windowSize = 3, windowOverlap = 0 )
    val timeWindowSmoothingParams = new AlnSmoothingParams( windowSize = 1, windowOverlap = 0, minWindowLandmarks = Some(3) )
    
    val lmRangeMapLandmarks = landmarkRangeSmoother.smoothLandmarks(landmarks, Some(lmRangeSmoothingParams))
    val timeWindowMaplandmarks = timeWindowSmoother.smoothLandmarks(landmarks, Some(timeWindowSmoothingParams))
    
    lmRangeMapLandmarks must equal (timeWindowMaplandmarks)
  }
  
  @Test
  def smoothWithLandmarksAndNoOverlap() {
    
    val smoothingParams = new AlnSmoothingParams( windowSize = 3, windowOverlap = 0 )
    
    val newLandmarks = landmarkRangeSmoother.smoothLandmarks(landmarks, Some(smoothingParams))
    
    // Test requirements
    newLandmarks.length must equal (20)
    newLandmarks(0).x must equal (1)
    newLandmarks(0).dx must be ( 0.841 +- 1e-3 )
    newLandmarks.map(_.dx).toArray must equal (deltaTimeList)
    
    ()
  }
  
  @Test
  def smoothWithLandmarksAndOverlap() {
    
    val smoothingParams = new AlnSmoothingParams( windowSize = 3, windowOverlap = 50 )
    
    val newLandmarks = landmarkRangeSmoother.smoothLandmarks(landmarks, Some(smoothingParams))
    
    // Test requirements
    newLandmarks.length must equal (39)
    newLandmarks(0).x must equal (1)
    newLandmarks(38).dx must be ( 0.913 +- 1e-3 )
    
    ()
  }
  
  @Test
  def smoothWithTimeWindowAndNoOverlap() {
    
    val smoothingParams = new AlnSmoothingParams( windowSize = 1, windowOverlap = 0, minWindowLandmarks = Some(3) )
    
    val newLandmarks = timeWindowSmoother.smoothLandmarks(landmarks, Some(smoothingParams))
    
    // Test requirements
    newLandmarks.length must equal (20)
    newLandmarks(0).x must equal (1)
    newLandmarks(0).dx must be ( 0.841 +- 1e-3 )
    newLandmarks.map(_.dx).toArray must equal (deltaTimeList)
  }
  
  @Test
  def smoothWithTimeWindowAndOverlap() {
    
    val smoothingParams = new AlnSmoothingParams( windowSize = 1, windowOverlap = 50, minWindowLandmarks = Some(3) )
    
    val newLandmarks = timeWindowSmoother.smoothLandmarks(landmarks, Some(smoothingParams))
    
    // Test requirements
    newLandmarks.length must equal (40)
    newLandmarks(0).x must equal (1)
    newLandmarks(39).dx must be ( 0.913 +- 1e-3 )
    
  }
  
  @Test
  def smoothWithLoess() {
    
    val newLandmarks = loessSmoother.smoothLandmarks(landmarks, null)
    
    // Test requirements
    newLandmarks.length must equal (20)
    newLandmarks(0).x must equal (1)
    newLandmarks(19).dx must be ( 0.752 +- 1e-3 )
    
  }
  
}