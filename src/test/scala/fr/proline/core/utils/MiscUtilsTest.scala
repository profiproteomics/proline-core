package fr.proline.core.utils

import org.junit._
import org.junit.Assert._
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitSuite
import org.scalatest.junit.JUnitRunner
import fr.proline.core.utils.misc._

@RunWith(classOf[JUnitRunner])
@Test
class MiscUtilsTest extends JUnitSuite {
  
  @Test
  def testMedian() = {
    val evenListOfFloats: List[Float] = List(1,2,3,4,5,6)
    val evenListOfDoubles: List[Double] = List(1,2,3,4,5,6)
    val oddListOfFloats: List[Float] = List(1,2,3,4,5,6,7)
    val oddListOfDoubles: List[Double] = List(1,2,3,4,5,6,7)
    
    assert( median( evenListOfFloats ) === 3.5 )
    assert( median( evenListOfDoubles ) === 3.5 )
    assert( median( oddListOfFloats ) === 4 )
    assert( median( oddListOfDoubles ) === 4 )
  }
  
  @Test
  def testGetMedianObject() = {
    
    case class Item( value: Int )
    
    val evenListOfObjects: List[Item] = List( Item(4), Item(1), Item(2), Item(3) )
    val oddListOfObjects: List[Item] = List( Item(3), Item(1), Item(2) )
    
    val sortingFunc = new Function2[Item,Item,Boolean] {
      def apply(a: Item, b: Item): Boolean = if (a.value < b.value) true else false
    }
    
    assert( getMedianObject( evenListOfObjects, sortingFunc ).value === 3 )
    assert( getMedianObject( oddListOfObjects, sortingFunc ).value === 2 )

  }
  
  @Test
  def testCombinations() = {
    val combi = combinations( 2, List(1,2,3) )
    assert( combinations( 2, List(1,2,3) ).toSet === Set( List(1,2), List(1,3), List(2,3) ) )
  }
  
  @Test
  def testCalcLineParams() = {
    
    val lineParamsNoIntercept = calcLineParams( -1,-2, 10, 20 )
    assert( lineParamsNoIntercept._1 === 2 ) // test a value
    assert( lineParamsNoIntercept._2 === 0 ) // test b value
    
    val lineParamsWithIntercept = calcLineParams( -1,-3, 10, 19 )
    assert( lineParamsWithIntercept._1 === 2 ) // test a value
    assert( lineParamsWithIntercept._2 === -1 ) // test b value
  }
  
}
