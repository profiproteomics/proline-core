package fr.proline.core.algo.msq.profilizer

import org.junit.Assert._
import org.junit.Test
import org.scalatest.Assertions._
import AbundanceSummarizer._


@Test
class AbundanceSummarizerTest {
  
  val originalMatrix = Array(
    Array(3.99E+08f,Float.NaN,3.97E+08f,3.90E+08f),
    Array(3.03E+08f,Float.NaN,3.25E+08f,3.15E+08f),
    Array(1.45E+08f,Float.NaN,1.45E+08f,1.46E+08f),
    Array(5.05E+07f,Float.NaN,5.12E+07f,5.23E+07f)
  )
  
  val matrixWithMissingValues = originalMatrix.clone
  matrixWithMissingValues(0)(2) = Float.NaN // 3.97E+08f is removed
  matrixWithMissingValues(1)(3) = Float.NaN // 3.15E+08f is removed
  
  @Test
  def testSummarizeUsingBestScore {    
    intercept[IllegalArgumentException] {
      summarizeAbundanceMatrix(matrixWithMissingValues, Method.BEST_SCORE)
    }
  }
  
  @Test
  def testSummarizeUsingMaxAbundanceSum {
    val singleRow = summarizeAbundanceMatrix(matrixWithMissingValues, Method.MAX_ABUNDANCE_SUM)    
    assertArrayEquals( Array(3.99E+08f,Float.NaN,Float.NaN,3.90E+08f), singleRow, 3.99E+08f * 0.01f)
  }

  @Test
  def testSummarizeUsingMean {
    val singleRow = summarizeAbundanceMatrix(matrixWithMissingValues, Method.MEAN)    
    assertArrayEquals( Array(2.24E+08f,Float.NaN,1.74E+08f,1.96E+08f), singleRow, 2.24E+08f * 0.01f)
  }
  
  @Test
  def summarizeUsingMeanOfTop3 {
    val singleRow = summarizeAbundanceMatrix(matrixWithMissingValues, Method.MEAN_OF_TOP3)    
    assertArrayEquals( Array(2.82E+08f,Float.NaN,2.35E+08f,2.68E+08f), singleRow, 2.82E+08f * 0.01f)    
  }
  
  @Test
  def summarizeUsingMedian {
    val singleRow = summarizeAbundanceMatrix(matrixWithMissingValues, Method.MEDIAN)    
    assertArrayEquals( Array(2.24E+08f,Float.NaN,1.45E+08f,1.46E+08f), singleRow, 2.24E+08f * 0.01f)
  }
  
  @Test
  def summarizeUsingMedianProfile {
    val singleRow = summarizeAbundanceMatrix(matrixWithMissingValues, Method.MEDIAN_PROFILE)    
    assertArrayEquals( Array(2.82E+08f,Float.NaN,2.94E+08f,2.99E+08f), singleRow, 2.82E+08f * 0.01f)
  }
  
  @Test
  def summarizeUsingSum {
    val singleRow = summarizeAbundanceMatrix(matrixWithMissingValues, Method.SUM)    
    assertArrayEquals( Array(8.98E+08f,Float.NaN,5.21E+08f,5.88E+08f), singleRow, 8.98E+08f * 0.01f)
  }
  
}