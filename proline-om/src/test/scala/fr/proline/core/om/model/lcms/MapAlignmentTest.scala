package fr.proline.core.om.model.lcms

import org.junit.Assert._
import org.junit.Test
import org.junit.Test

@Test
class MapAlignmentTest {
  
  private val mapAlignment = new MapAlignment(
      refMapId = 1,
      targetMapId = 2,
      massRange = (0,20000),
      timeList = Array(10,20,30,40,50,60,70,80,90,100).map(_.toFloat),
      deltaTimeList = Array(0,1,2,3,2,1,0,-1,-2,-4).map(_.toFloat)
    )
  
  private val refTimeList = Array(0,10,15,20,25,30,35,40,45,50,55,60,65,70,75,80,85,90,95,100,120).map(_.toFloat)
  private val tarTimeList = Array(0,10,15.5f,21,26.5f,32,37.5f,43,47.5f,52,56.5,61,65.5,70,74.5,79,83.5f,88,92,96,116).map(_.toFloat)
  
  @Test
  def testCalcTargetMapElutionTime {
    
    val calcTarTimeList = refTimeList.map { refTime => mapAlignment.calcTargetMapElutionTime(refTime)._1 }
    //calcTarTimeList.foreach { println(_) }
    assertArrayEquals(tarTimeList, calcTarTimeList, 0f)

  }
  
  @Test
  def testGetReversedAlignment {
    
    val revMapAln = mapAlignment.getReversedAlignment
    val calcRefTimeList = tarTimeList.map( revMapAln.calcTargetMapElutionTime(_)._1 )
    
    assertArrayEquals(refTimeList, calcRefTimeList, 0f)
  }
  
}