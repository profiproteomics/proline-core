package fr.proline.core.algo.msq.profilizer

import org.apache.commons.math3.stat.StatUtils
import org.junit.Test
import org.scalatest.Assertions._

@Test
class MissingAbundancesInfererTest {
  
  val errorDistrib = Array(
    AbsoluteErrorBin(1384250.2f,181723.66f),
    AbsoluteErrorBin(1.5205098E7f,1514529.2f),
    AbsoluteErrorBin(7.2869936E7f,3588676.5f),
    AbsoluteErrorBin(3.77133312E8f,1.21684E7f),
    AbsoluteErrorBin(2.81598771E9f,7.8032992E7f)
  )
  
  val errorModel = new AbsoluteErrorModel( errorDistrib )
  
  val originalMatrix = Array(
    Array(3.99E+08f,3.97E+08f,3.90E+08f),
    Array(3.03E+08f,3.25E+08f,3.15E+08f),
    Array(1.45E+08f,1.45E+08f,1.46E+08f),
    Array(3.46E+08f,3.50E+08f,3.37E+08f),
    Array(3.73E+08f,3.82E+08f,3.67E+08f),
    Array(5.64E+07f,5.91E+07f,5.62E+07f),
    Array(4.13E+07f,3.70E+07f,3.46E+07f),
    Array(5527081f,5496194f,6203527.5f),
    Array(5.05E+07f,5.12E+07f,5.23E+07f),
    Array(3858065.8f,5431060.5f,4047427.8f)
  )
  
  // Copy the array and inject some missing values
  val matrixWithMissingValues = originalMatrix.clone
  matrixWithMissingValues(0)(1) = Float.NaN
  matrixWithMissingValues(0)(2) = Float.NaN
  matrixWithMissingValues(1)(0) = Float.NaN
  matrixWithMissingValues(2)(0) = 0
  matrixWithMissingValues(3)(0) = Float.NaN
  matrixWithMissingValues(3)(2) = 0
  matrixWithMissingValues(4)(0) = Float.NaN
  matrixWithMissingValues(5)(0) = 0
  matrixWithMissingValues(6) = Array(Float.NaN,Float.NaN,Float.NaN)
  matrixWithMissingValues(7)(2) = 0
  matrixWithMissingValues(8)(2) = 0
  matrixWithMissingValues(9) = Array(Float.NaN,0,Float.NaN)
  
  @Test
  def testInferAbundances {
    
    // Fill a PSM count matrix
    val psmCountMatrix = Array.fill(10, 3)(0)
    
    // Infer the missing values
    val inferedMatrix = MissingAbundancesInferer.inferAbundances(matrixWithMissingValues, psmCountMatrix, errorModel)
    assertResult(30)( inferedMatrix.flatten.count( v => v.isNaN == false && v > 0f ) )
    
    //println( inferedMatrix.map( _.mkString("\t") ).mkString("\n") )
    
    val cvMatrix = _calcCvMatrix(inferedMatrix)
    
    //assert( cvMatrix(0) > 100f )
    assert( cvMatrix(1) < 100f )
    assert( cvMatrix.last < 50f )
    
  }
  
  @Test
  def testFixedNoiseMissingAbundancesReplacer {
    
    val config = MissingAbundancesInferenceConfig(noisePercentile = Some(1) )
    
    // Infer the missing values
    val inferer = new FixedNoiseMissingAbundancesReplacer(config)
    val inferedMatrix = inferer.inferAbundances(matrixWithMissingValues)
    assertResult(30)( inferedMatrix.flatten.count( v => v.isNaN == false && v > 0f ) )
    
    //println( inferedMatrix.map( _.mkString("\t") ).mkString("\n") )
    
    val cvMatrix = _calcCvMatrix(inferedMatrix)
    
    //assert( cvMatrix(0) > 100f )
    assert( cvMatrix(1) < 100f )
  }
  
  private def _calcCvMatrix(abMatrix: Array[Array[Float]]): Array[Double] = {
    abMatrix.map( row => 100f * math.sqrt( StatUtils.variance(row.map(_.toDouble)) ) / ( row.sum / row.length ) )
  }

}