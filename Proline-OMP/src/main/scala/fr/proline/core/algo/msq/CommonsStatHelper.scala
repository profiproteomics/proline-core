package fr.proline.core.algo.msq

import org.apache.commons.math3.stat.descriptive.StatisticalSummaryValues
import org.apache.commons.math3.stat.descriptive.StatisticalSummary
import org.apache.commons.math3.stat.StatUtils
import org.apache.commons.math3.stat.inference.TTest
import org.apache.commons.math3.distribution.NormalDistribution
import org.apache.commons.math3.special.Erf


object CommonsStatHelper {
  
  val tTestComputer = new TTest()
  
  def calcMean( values: Array[Float] ): Float = {
    if( values.length == 0 ) 0
    
    val positiveValuesCount = values.count { v => require(v >= 0); v > 0 }
    values.reduceLeft(_ + _) / positiveValuesCount
  }
  
  def calcStatSummary(values: Array[Double]): StatisticalSummary = {
    require( values != null, "values is null" )
    
    val defValues = values.filter( _.isNaN == false )
    
    val mean = StatUtils.mean(defValues)
    val variance = StatUtils.variance(defValues, mean)
    var ( max, min, sum ) = (Double.MinValue,Double.MaxValue,0.0)
    defValues.foreach { value =>
      sum += value
      if( value > max ) max = value
      if( value < min ) min = value
    }
    
    new StatisticalSummaryValues(
      mean, 
      variance,
      defValues.length, 
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
  
  def quartilesToBounds( quartiles: Pair[Float,Float] ): Pair[Float,Float] = {
    
    val( q1, q3 ) = quartiles
    val logQ1 = math.log(q1)
    val logQ3 = math.log(q3)
    val logIQR = logQ3 - logQ1
    val logLB = logQ1 - 1.5 * logIQR
    val logUB = logQ3 + 1.5 * logIQR
    val lb = math.exp(logLB).toFloat
    val ub = math.exp(logUB).toFloat
    
    (lb -> ub )
  } 
  
  // The following methods have been found here:
  // https://code.google.com/p/icelogo/source/browse/trunk/src/main/java/com/compomics/icelogo/core/stat/StatisticsConversion.java
  // Static normal distribution dist in order to transform z-socres into probabilities.
  private val normalDist = new NormalDistribution(0.0,1);

  /**
   * The pValue calculation is done here.
   * Example: From 1.96 (Z-score) to 0.95 % (P-value)
   * From -1.96 (Z-score) to -0.95%
   *
   * @return double a p value
   */
  def zValueToPValue(zValue: Double): Double = {
    val erf = Erf.erf(zValue / Math.sqrt(2.0))
    if(zValue < 0) (1 + erf)/2 else (1-erf)/2
  }

  /**
   * The quantile calculation is done here.
   * -1.96 : +1.96  returns 0.95
   *
   * @return double a quantile
   */
  def calcCumulativeProbability(zValue: Double): Double = {
    val quantile = normalDist.cumulativeProbability(zValue)
    quantile
  }

/*
  /**
   * The quantile calculation is done here.
   * From -1.96 returns 0.025
   * While 1.96 returns 0.975
   *
   * @return double a quantile
   */
  def calcCumulativeProbability(lowerZValue: Double, higherZValue: Double): Double = {
     val quantile = normalDist.cumulativeProbability(lowerZValue, higherZValue)
     quantile
  }

  /**
   * The zscore calculation is done here.
   * Example: From 0.95 returns 1.64
   * While 0.05 returns -1.64
   * And 0.975 returns 1.96
   *
   * @param aProbability
   * @return double aZscore
   */
  def calcInverseCumulativeProbability(probability: Double): Double = {
    val zScore = normalDist.inverseCumulativeProbability(probability)
    zScore
  }


  /**
   * This method will calculate the Z score in a one-sided test for a specific quantile.
   *
   * @return double with the calculated Z score
   */
  def calcOneSidedZScore(quantile: Double): Double = {
    _calcZScore(quantile)
  }

  /**
   * This method will calculate the Z score in a two-sided test for a specific quantile.
   * The Quantile will first be transformed
   * Quantile = 0.95
   * => alpha = O.05
   * the quantile will be = 1-(alpha/2)
   *
   * @return double with the calculated Z score
   */
  def calculateTwoSidedZScore(quantile: Double): Double =  {
    val p = 1 - ((1 - quantile) / 2)
    _calcZScore(p)
  }

  /**
   * Algorithm as241  appl. statist. (1988) 37(3):477-484.
   * produces the normal deviate z corresponding to a given lower tail
   * area of p; z is accurate to about 1 part in 10**7.
   * <p/>
   * The hash sums below are the sums of the mantissas of the coefficients.
   * They are included for use in checking transcription.
   * This method is based on the C code that can be found on the following website:
   * http://download.osgeo.org/grass/grass6_progman/as241_8c-source.html .
   *
   * @param aQuantile the quantile to retrieve.
   *                  0.05 returns -1.96 while 0.95 returns 1.96.
   * @return double with the calulated Z score
   */
  private def _calcZScore(quantile: Double): Double = {

    val zero = 0.0; val one = 1.0; val half = 0.5
    val split1 = 0.425; val split2 = 5.0
    val const1 = 0.180625; val const2 = 1.6

    /* coefficients for p close to 0.5 */
    val a = Array(3.3871327179, 5.0434271938e+01, 1.5929113202e+02, 5.9109374720e+01)
    val b = Array(0.0, 1.7895169469e+01, 7.8757757664e+01, 6.72E+01)

    /* hash sum ab    32.3184577772 */
    /* coefficients for p not close to 0, 0.5 or 1. */
    val c = Array(1.4234372777e+00, 2.7568153900e+00, 1.3067284816e+00, 1.7023821103e-01)
    val d = Array(0.0, 7.3700164250e-01, 1.2021132975e-01)

    /* hash sum cd    15.7614929821 */
    /* coefficients for p near 0 or 1. */
    val e = Array(6.6579051150e+00, 3.0812263860e+00, 4.2868294337e-01, 1.7337203997e-02)
    val f = Array(0.0, 2.4197894225e-01, 1.2258202635e-02)

    /* hash sum ef    19.4052910204 */
    var q = 0.0; var r = 0.0; var ret = 0.0

    q = quantile - half;
    if (math.abs(q) <= split1) {
      r = const1 - q * q
      ret = q * (((a(3) * r + a(2)) * r + a(1)) * r + a(0)) /
        (((b(3) * r + b(2)) * r + b(1)) * r + one);

      return ret
    }
    /* else */

    if (q < zero) {
      r = quantile
    } else {
      r = one - quantile
    }
    if (r <= zero) {
      return zero
    }
    r = math.sqrt(-math.log(r))
    if (r <= split2) {
      r = r - const2
      ret = (((c(3) * r + c(2)) * r + c(1)) * r + c(0)) /
        ((d(2) * r + d(1)) * r + one)
    } else {
      r = r - split2
      ret = (((e(3) * r + e(2)) * r + e(1)) * r + e(0)) /
        ((f(2) * r + f(1)) * r + one)
    }

    if (q < zero) {
      ret = -ret;
    }

    ret
  }
*/

}
