package fr.proline.core.algo.lcms

import scala.collection.mutable.ArrayBuffer
import org.junit.Before
import org.junit.Test
import org.scalatest.junit.JUnitSuite
import org.scalatest.matchers.MustMatchers
import com.weiglewilczek.slf4s.Logging
import fr.proline.core.om.model.lcms.MapAlignment
import fr.proline.core.algo.lcms.alignment.AlnSmoothingParams

class AlnSmootherTest extends JUnitSuite with MustMatchers with Logging {
  
  val landmarkRangeSmoother = AlnSmoother(AlnSmoothing.LANDMARK_RANGE.toString)
  val loessSmoother = AlnSmoother(AlnSmoothing.LOESS.toString)
  val timeWindowSmoother = AlnSmoother(AlnSmoothing.TIME_WINDOW.toString)
  
  val timeList = Array(1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20).map(_.toFloat)
  val deltaTimeList = timeList.map( math.sin(_).toFloat )
  
  var mapAlignment: MapAlignment = {
    
    val reducedDeltaTimeList = deltaTimeList.map( _ * 0.9f )
    val increasedDeltaTimeList = deltaTimeList.map( _ * 1.1f )
    
    // Note: we force the production of an unsorted alignment in order to check if it is still handled correctly
    new MapAlignment(
      refMapId = 1L,
      targetMapId = 2L,
      massRange = Pair(0.,10000.),
      timeList = timeList ++ timeList ++ timeList,
      deltaTimeList = reducedDeltaTimeList ++ deltaTimeList ++ increasedDeltaTimeList
    )
    
  }
  
  @Test
  def compareSmoothings() {
    
    val landmarkRangeSmoothingParams = new AlnSmoothingParams( windowSize = 3, windowOverlap = 0 )
    val timeWindowSmoothingParams = new AlnSmoothingParams( windowSize = 1, windowOverlap = 0, minWindowLandmarks = 3 )
    
    val landmarkRangeMapAln = landmarkRangeSmoother.smoothMapAlignment(mapAlignment, landmarkRangeSmoothingParams)
    val timeWindowMapAln = timeWindowSmoother.smoothMapAlignment(mapAlignment, timeWindowSmoothingParams)
    
    landmarkRangeMapAln.deltaTimeList must equal (timeWindowMapAln.deltaTimeList)
  }
  
  @Test
  def smoothWithLandmarksAndNoOverlap() {
    
    val smoothingParams = new AlnSmoothingParams( windowSize = 3, windowOverlap = 0 )
    
    val newMapAln = landmarkRangeSmoother.smoothMapAlignment(mapAlignment, smoothingParams)
    
    // Test requirements
    newMapAln.timeList(0) must equal (1)
    newMapAln.timeList.length must equal (20)
    newMapAln.deltaTimeList(0) must be ( 0.841f plusOrMinus 1e-3f )
    newMapAln.deltaTimeList must equal (deltaTimeList)
    
    ()
  }
  
  @Test
  def smoothWithLandmarksAndOverlap() {
    
    val smoothingParams = new AlnSmoothingParams( windowSize = 3, windowOverlap = 50 )
    
    val newMapAln = landmarkRangeSmoother.smoothMapAlignment(mapAlignment, smoothingParams)
    
    // Test requirements
    newMapAln.timeList(0) must equal (1)
    newMapAln.timeList.length must equal (39)
    newMapAln.deltaTimeList(38) must be ( 0.913f plusOrMinus 1e-3f )
    
    ()
  }
  
  @Test
  def smoothWithTimeWindowAndNoOverlap() {
    
    val smoothingParams = new AlnSmoothingParams( windowSize = 1, windowOverlap = 0, minWindowLandmarks = 3 )
    
    val newMapAln = timeWindowSmoother.smoothMapAlignment(mapAlignment, smoothingParams)
    
    // Test requirements
    newMapAln.timeList(0) must equal (1)
    newMapAln.timeList.length must equal (20)
    newMapAln.deltaTimeList(0) must be ( 0.841f plusOrMinus 1e-3f )
    newMapAln.deltaTimeList must equal (deltaTimeList)
  }
  
  @Test
  def smoothWithTimeWindowAndOverlap() {
    
    val smoothingParams = new AlnSmoothingParams( windowSize = 1, windowOverlap = 50, minWindowLandmarks = 3 )
    
    val newMapAln = timeWindowSmoother.smoothMapAlignment(mapAlignment, smoothingParams)
    
    // Test requirements
    newMapAln.timeList(0) must equal (1)
    newMapAln.timeList.length must equal (40)
    newMapAln.deltaTimeList(39) must be ( 0.913f plusOrMinus 1e-3f )
    
  }
  
  @Test
  def smoothWithLoess() {
    
    val newMapAln = loessSmoother.smoothMapAlignment(mapAlignment, null)
    
    // Test requirements
    newMapAln.timeList(0) must equal (1)
    newMapAln.timeList.length must equal (20)
    newMapAln.deltaTimeList(19) must be ( 0.752f plusOrMinus 1e-3f )
    
  }
  
}