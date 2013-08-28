package fr.proline.core.algo.msq

import org.junit.Assert._
import org.junit.Test

@Test
class AbundanceNormalizerTest {

  @Test
  def testNormalizeAbundances {
    
    val abundanceMatrix = Array(
      Array(1f,1.2f,0.8f),
      Array(1f,1.15f,0.82f),
      Array(1f,1.25f,0.78f),
      Array(1f,1.15f,Float.NaN),
      Array(1f,0,0.8f)
    )
    
    val normalizedMatrix = AbundanceNormalizer.normalizeAbundances(abundanceMatrix)
    
    //import scala.runtime.ScalaRunTime.stringOf
    //println( stringOf(normalizedMatrix) )
    
    assertEquals( abundanceMatrix.length, normalizedMatrix.length )
    assertArrayEquals( Array(1f,1f,Float.NaN), normalizedMatrix(3), 0.01f)
    assertArrayEquals( Array(1f,0f,1f), normalizedMatrix(4), 0.01f)
    
  }
  
}