package fr.proline.core.algo.msq

import scala.collection.mutable.ArrayBuffer
import scala.math.{exp,log}

import org.apache.commons.math3.distribution.{CauchyDistribution,NormalDistribution}
import org.apache.commons.math3.stat.descriptive.rank.Percentile
import org.apache.commons.math3.stat.descriptive.moment.GeometricMean
import org.apache.commons.math3.stat.descriptive.StatisticalSummary
import org.apache.commons.math3.stat.descriptive.StatisticalSummaryValues
import org.apache.commons.math3.stat.inference.TTest
import org.apache.commons.math3.special.Erf

import fr.profi.util.math.linearInterpolation
import fr.profi.util.stat.{Bin,EntityHistogramComputer}


trait IErrorModel {
  val errorDistribution: Seq[IErrorBin]
  require(errorDistribution.length > 0, "at least one value must be present in the error distribution")  
}

trait IErrorBin { val abundance: Float }
case class AbsoluteErrorBin( abundance: Float, stdDev: Float ) extends IErrorBin
case class RelativeErrorBin( abundance: Float, ratioQuartiles: Tuple3[Float,Float,Float] ) extends IErrorBin

class AbsoluteErrorModel( val errorDistribution: Seq[AbsoluteErrorBin] ) extends IErrorModel {
  
  private val abundanceCVPairs = errorDistribution.map( bin => 
    bin.abundance.toFloat -> bin.stdDev/bin.abundance // compute the CV for each abundance
  )
  private val tTestComputer = new TTest()
  
  def getStdevForAbundance( abundance: Float ): Float = {
    linearInterpolation(abundance,abundanceCVPairs) * abundance
  }
  
  def tTest( statSummary1: StatisticalSummary, statSummary2: StatisticalSummary, applyVarianceCorrection: Boolean = true ): Double = {
    
    // Check we have enough replicates
    // TODO: is this needed ? (because of the correction to be applied)
    require( statSummary1.getN > 2 && statSummary2.getN > 2, "not enough replicates for T statistics" )

    if( applyVarianceCorrection == false ) CommonsStatHelper.tTestComputer.tTest( statSummary1, statSummary2 )
    else {
      // Compute the T-Test using corrected variances from error model
      CommonsStatHelper.tTestComputer.tTest(
        _applyErrorCorrectionToStatSummary(statSummary1),
        _applyErrorCorrectionToStatSummary(statSummary2)
      )
    }    
  }
  
  private def _applyErrorCorrectionToStatSummary( statSum: StatisticalSummary ): StatisticalSummary = {
    
    // Estimate standard deviations using the error model
    val errorStdDev = this.getStdevForAbundance(statSum.getMean.toFloat)
    val errorVariance = errorStdDev * errorStdDev
    
    // Check if we need to apply correction
    var N = statSum.getN()
    val needCorrection = if( statSum.getN() < 3 ) { N = 2L; true }
    else if( statSum.getVariance < errorVariance ) true else false
    
    // Apply standard deviations corrections if needed
    if( needCorrection == false ) statSum
    else CommonsStatHelper.copyStatSummary(statSum, variance = errorVariance, n = N)
  }
  
  /*
  def tTest( statSummary: StatisticalSummary, errorAbundance: Float ): Double = {
    
    val stdDevError = this.getStdevForAbundance( errorAbundance )
    
    val errorSummary = CommonsStatHelper.copyStatSummary(statSummary, variance = stdDevError * stdDevError)
    //buildStatSummary(errorAbundance,stdDevError,statSummary.getN)
    //val summaryTest = this._buildStatisticalSummary(abundance,stdDev,count)
    
    tTestComputer.tTest(errorSummary, statSummary)
  }*/
  
}

class RelativeErrorModel( val errorDistribution: Seq[RelativeErrorBin] ) extends IErrorModel {
  
  private val abundanceRatioBoundsPairs = errorDistribution.map( bin => bin.abundance -> bin.ratioQuartiles )
  private val abundanceQ1Pairs = abundanceRatioBoundsPairs.map( pair => pair._1 -> pair._2._1 )
  private val abundanceQ2Pairs = abundanceRatioBoundsPairs.map( pair => pair._1 -> pair._2._2 )
  private val abundanceQ3Pairs = abundanceRatioBoundsPairs.map( pair => pair._1 -> pair._2._3 )
  
  /*import scala.runtime.ScalaRunTime.stringOf
  println( stringOf(abundanceRatioBoundsPairs) )*/
  
  def getRatioQuartilesForAbundance( abundance: Float ): Tuple3[Float,Float,Float] = {
    
    val q1 = linearInterpolation( abundance.toFloat, abundanceQ1Pairs )
    val q2 = linearInterpolation( abundance.toFloat, abundanceQ2Pairs )
    val q3 = linearInterpolation( abundance.toFloat, abundanceQ3Pairs )
    
    (q1,q2,q3)
  }
  
  def toAbsoluteErrorModel(): AbsoluteErrorModel = {
    // Warning: this is very experimental
    // We assume to understand the relation between the ratio distribution and the original Gaussian distributions.
    // This relation has been obtained by using simulations.
    
    val absoluteErrorDistribution = errorDistribution.map { bin =>
      val iqr = log(bin.ratioQuartiles._2) - log(bin.ratioQuartiles._1)
      val cv = iqr / 2.0681
      val stdDev = bin.abundance * cv
      
      AbsoluteErrorBin( bin.abundance, stdDev.toFloat )
    }
    
    new AbsoluteErrorModel( absoluteErrorDistribution )
  }
  
  // From : http://en.wikipedia.org/wiki/Cauchy_distribution#Estimation_of_parameters
  // One simple method is to take the median value of the sample as an estimator of x0
  // and half the sample interquartile range as an estimator of γ
  def cauchyTest( abundance: Float, ratio: Float ): Double = {
    
    val quartiles = this.getRatioQuartilesForAbundance(abundance)
    val( logQ1, logQ2, logQ3 ) = ( log(quartiles._1), log(quartiles._2), log(quartiles._3) )
    val logIQR = (logQ3 - logQ1)
    
    val cauchyDistri = new CauchyDistribution(logQ2, logIQR / 2)
    
    val p = cauchyDistri.cumulativeProbability( log(ratio) )
    if( ratio >= 1 ) 1 - p else p
  }
  
  /*
  def zTest1( abundance: Float, ratio: Float ): Double = {
   
    val quartiles = this.getRatioQuartilesForAbundance(abundance)
    val( logQ1, logQ3 ) = log(quartiles._1) -> log(quartiles._2)    
    val logIQR = (logQ3 - logQ1)
    val logSigma = logIQR / 1.349
    
    val normalDist = new NormalDistributionImpl(0.0,logSigma)
    
    val p = normalDist.cumulativeProbability( log(ratio) )
    if( ratio >= 1 ) 1 - p else p
  }*/
  
  def zTest( abundance: Float, ratio: Float ): (Double,Double) = {
   
    val quartiles = this.getRatioQuartilesForAbundance(abundance)
    val( logQ1, logQ2, logQ3 ) = ( log(quartiles._1), log(quartiles._2), log(quartiles._3) )
    val logIQR = (logQ3 - logQ1)
    val logSigma = logIQR / 1.349
    
    val zScore = ( log(ratio) - logQ2 ) / logSigma
    
    val p = zValueToPvalue(zScore)
    //if( ratio >= 1 ) (1-p)/2 else (1+p)/2 // was used with Erf ; do we need to divide by 2 ???
    val pValue = if( ratio >= 1 ) (1-p) else p
    
    zScore -> pValue
  }
 
  //private val normalDist = new NormalDistribution(0.0,1)
  
  protected def zValueToPvalue(zValue: Double): Double = {
    Erf.erf(zValue / math.sqrt(2.0) )
    //normalDist.cumulativeProbability( zValue )
  }

}


trait IErrorObservation {
  
  val abundance: Float  
  require( abundance > 0, "abundance must be greater than zero" )
  
  def getErrorValue(): Float
  
  val abundanceLog = log(abundance)
}

case class AbsoluteErrorObservation( abundance: Float, stdDev: Float ) extends IErrorObservation {
  require( stdDev >= 0, "standard deviation must be positive" )
  
  def getErrorValue = stdDev
}

case class RelativeErrorObservation( abundance: Float, ratio: Float ) extends IErrorObservation {
  require( ratio > 0, "ratio must be greater than zero" )
  
  def getErrorValue = ratio
}

object ErrorModelComputer {
  
  val percentileComputer = new Percentile()
  
  protected def computeErrorHistogram( errorObservations: Seq[IErrorObservation], nbins: Option[Int] ): Array[Pair[Bin, Seq[IErrorObservation]]] = {
    
    // Filter and sort observations
    val sortedObservations = errorObservations.filter( _.abundance > 0 ).sortBy(_.abundance)
    require( sortedObservations.length > 3, "at least 3 error observations are needed" )
    
    val nbVals = sortedObservations.length
    // We want fourth number of values than number of bins
    val nbinsAsInt = nbins.getOrElse( if( nbVals > 100 ) (math.sqrt(nbVals)/4).toInt else 1 + (nbVals/20).toInt )
    
    // Compute the histogram of observations    
    val errorHistoComputer = new EntityHistogramComputer[IErrorObservation]( sortedObservations, obs => obs.abundanceLog )
    errorHistoComputer.calcHistogram( nbins = nbinsAsInt )
  }
  
  protected def summarizeErrorObsGroup(errorObsGroup: Seq[IErrorObservation] ): Pair[Double,Seq[Double]] = {
    
    var( abLogSum, errorValues ) = (0.0,new ArrayBuffer[Double])
    
    errorObsGroup.foreach { errorObs =>
      if( errorObs.abundance > 0 ) {
        abLogSum += errorObs.abundanceLog          
        errorValues += errorObs.getErrorValue.toDouble
      }
    }
    
    (abLogSum,errorValues)
  }
  
  def computeAbsoluteErrorModel( errorObservations: Seq[AbsoluteErrorObservation], nbins: Option[Int] = None ): AbsoluteErrorModel = {
    
    val errorDistribution = new ArrayBuffer[AbsoluteErrorBin](errorObservations.length)
    
    this.computeErrorHistogram( errorObservations, nbins ).foreach { case (bin,errorObsGroup) =>
      
      val(abLogSum,stdDevValues) = summarizeErrorObsGroup(errorObsGroup)
      
      if( abLogSum > 0 && stdDevValues.length > 0 ) {
        val abLogMean = abLogSum / stdDevValues.length
        val stdDevMedian = percentileComputer.evaluate(stdDevValues.toArray, 50).toFloat
        
        errorDistribution += AbsoluteErrorBin(
          exp(abLogMean).toFloat, // the mean abundance corresponding to this error bin
          stdDevMedian            // the median standard deviation observed for this error bin
        )
      }

    }
    
    new AbsoluteErrorModel( errorDistribution )
  }
  
  def computeRelativeErrorModel( errorObservations: Seq[RelativeErrorObservation], nbins: Option[Int] = None ): RelativeErrorModel = {
    
    val errorDistribution = new ArrayBuffer[RelativeErrorBin](errorObservations.length)
    
    this.computeErrorHistogram( errorObservations, nbins ).foreach { case (bin,errorObsGroup) =>
      
      val(abLogSum,ratioValues) = summarizeErrorObsGroup(errorObsGroup)
      
      if( abLogSum > 0 && ratioValues.length > 3 ) {
        
        val abLogMean = abLogSum / ratioValues.length
        val ratioQ1 = percentileComputer.evaluate(ratioValues.toArray, 25).toFloat
        val ratioQ2 = percentileComputer.evaluate(ratioValues.toArray, 50).toFloat
        val ratioQ3 = percentileComputer.evaluate(ratioValues.toArray, 75).toFloat
        
        errorDistribution += RelativeErrorBin(
          exp(abLogMean).toFloat,     // the mean abundance corresponding to this error bin
          (ratioQ1, ratioQ2, ratioQ3) // the ratio quartiles estimated for this error bin
        )
      }
    }
    
    new RelativeErrorModel( errorDistribution )
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
  

  // Assume that values are already sorted
  /*protected def calcQuantile(values: Array[Float], lowerPercent: Float ) {
    require(values != null && values.length >= 0, "The data array either is null or does not contain any data.")
    require( lowerPercent >= 0 && lowerPercent < 1 )
    
    val nbValues = values.length
    var idx = math.round( nbValues * lowerPercent )
    if( idx == nbValues ) idx -= 1
    
    values(idx)
  }*/
  
}


