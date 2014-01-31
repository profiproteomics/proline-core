package fr.proline.core.algo.msq

import org.apache.commons.math.stat.descriptive.StatisticalSummaryValues
import org.apache.commons.math.stat.descriptive.StatisticalSummary
import org.apache.commons.math.stat.StatUtils
import org.apache.commons.math.stat.inference.TTestImpl


object CommonsStatHelper {
  
  val tTestComputer = new TTestImpl()
  
  def calcMean( values: Array[Float] ): Float = {
    if( values.length == 0 ) 0
    
    val positiveValuesCount = values.count { v => require(v >= 0); v > 0 }
    values.reduceLeft(_ + _) / positiveValuesCount
  }
  
  def calcStatSummary(values: Array[Double]): StatisticalSummary = {
    
    val mean = StatUtils.mean(values)
    val variance = StatUtils.variance(values, mean)
    var ( max, min, sum ) = (Double.MinValue,Double.MaxValue,0.0)
    values.foreach { value =>
      sum += value
      if( value > max ) max = value
      if( value < min ) min = value
    }
    
    new StatisticalSummaryValues(
      mean, 
      variance, 
      
      values length, 
      max, 
      min,
      sum
    )
  }
  
  def buildStatSummary(abundance: Float, stdDev: Float, count: Long): StatisticalSummary = {
    new StatisticalSummaryValues(
      abundance,
      stdDev * stdDev,
      count,
      abundance + 3 * stdDev,
      abundance - 3 * stdDev,
      abundance * count
    )
  }
  
  def copyStatSummary(
    statSum: StatisticalSummary,
    mean: Double = Double.NaN,
    variance: Double = Double.NaN,
    n: Long = 0L,
    max: Double = Double.NaN,
    min: Double = Double.NaN,
    sum: Double = Double.NaN
  ): StatisticalSummary = {
    new StatisticalSummaryValues(
      if( mean.isNaN ) statSum.getMean() else mean,
      if( variance.isNaN ) statSum.getVariance() else variance,
      if( n == 0L ) statSum.getN() else n,
      if( max.isNaN ) statSum.getMax() else max,
      if( min.isNaN ) statSum.getMin() else min,
      if( sum.isNaN ) statSum.getSum() else sum
    )
  }
  
}
