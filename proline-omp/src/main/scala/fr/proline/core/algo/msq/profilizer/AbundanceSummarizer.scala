package fr.proline.core.algo.msq.profilizer

import com.typesafe.scalalogging.LazyLogging
import fr.profi.util.math.median
import fr.profi.util.primitives.isZeroOrNaN
import fr.proline.core.algo.msq.config.profilizer.AbundanceSummarizerMethod

import scala.collection.mutable.ArrayBuffer

object AbundanceSummarizer extends LazyLogging {
  
//  object Method extends EnhancedEnum {
//    val BEST_SCORE = Value // has no implementation here, should be called before
//    val MAX_ABUNDANCE_SUM = Value // return one single row
//    val MEAN = Value
//    val MEAN_OF_TOP3 = Value
//    val MEDIAN = Value
//    val MEDIAN_BIOLOGICAL_PROFILE = Value // has no implementation here, should be called before
//    val MEDIAN_PROFILE = Value
//    val SUM = Value
//    val LFQ = Value
//  }
  
  val advancedMethods = List(AbundanceSummarizerMethod.MEDIAN_BIOLOGICAL_PROFILE)
  
  val EMPTY_MATRIX_MESSAGE = "abundanceMatrix is empty"
  
  def createNoImplemError(methodName: String) = new IllegalArgumentException(
    s"No implementation corresponding to the $methodName method."
  )
  
  def summarizeAbundanceMatrix( abundanceMatrix: Array[Array[Float]], method: AbundanceSummarizerMethod.Value ): Array[Float] = {
    
    val matrixLength = abundanceMatrix.length
    require( matrixLength > 0, EMPTY_MATRIX_MESSAGE )
    
    if( matrixLength == 1 ) return abundanceMatrix.head
    
    method match {
      case AbundanceSummarizerMethod.BEST_SCORE =>  {
        // Note: a BEST_SCORE approach could be implemented here, but this will lead to a more complicated the API
        throw createNoImplemError(AbundanceSummarizerMethod.BEST_SCORE)
      }
      case AbundanceSummarizerMethod.MAX_ABUNDANCE_SUM => summarizeUsingMaxAbundanceSum(abundanceMatrix)
      case AbundanceSummarizerMethod.MEAN => summarizeUsingMean(abundanceMatrix)
      case AbundanceSummarizerMethod.MEAN_OF_TOP3 => summarizeUsingMeanOfTop3(abundanceMatrix)
      case AbundanceSummarizerMethod.MEDIAN => summarizeUsingMedian(abundanceMatrix)
      case AbundanceSummarizerMethod.MEDIAN_PROFILE => summarizeUsingMedianProfile(abundanceMatrix)
      case AbundanceSummarizerMethod.MEDIAN_BIOLOGICAL_PROFILE =>  {
        throw createNoImplemError(AbundanceSummarizerMethod.MEDIAN_BIOLOGICAL_PROFILE)
      }
      case AbundanceSummarizerMethod.SUM => summarizeUsingSum(abundanceMatrix)
      case AbundanceSummarizerMethod.LFQ => LFQSummarizer.summarize(abundanceMatrix)
    }
    
  }
  
  def summarizeUsingMaxAbundanceSum( abundanceMatrix: Array[Array[Float]] ): Array[Float] = {
    abundanceMatrix.maxBy(_.sum)
  }
  
  def summarizeUsingMean( abundanceMatrix: Array[Array[Float]] ): Array[Float] = {    
    abundanceMatrix.transpose.map( _calcMeanAbundance( _ ) )
  }
  
  def summarizeUsingMeanOfTop3( abundanceMatrix: Array[Array[Float]] ): Array[Float] = {
    
    // Sort rows by descending median abundance
    val sortedMatrix = abundanceMatrix.sortBy( - _calcMedianAbundance(_) )
    
    // Take 3 highest rows
    val top3Matrix = sortedMatrix.take(3)
    
    // Mean the rows
    summarizeUsingMean(top3Matrix)
  }
  
  def summarizeUsingMedian( abundanceMatrix: Array[Array[Float]] ): Array[Float] = {    
    abundanceMatrix.transpose.map( _calcMedianAbundance( _ ) )
  }
  
  def summarizeUsingMedianProfile( abundanceMatrix: Array[Array[Float]] ): Array[Float] = {
    
    // Transpose the matrix
    val transposedMatrix = abundanceMatrix.transpose
    
    // Select columns eligible for ratio computations (discard columns without defined values)
    val colObsFreq = transposedMatrix.map( col => col.count( isZeroOrNaN(_) == false ).toFloat / col.length )
    val colObsFreqWithIdx = colObsFreq.zipWithIndex
    val eligibleColIndices = colObsFreqWithIdx.withFilter( _._1 > 0f ).map( _._2 )
    val matrixWithEligibleCols = eligibleColIndices.map( i => transposedMatrix(i) ).transpose
    
    if (matrixWithEligibleCols.isEmpty) {
      logger.warn("No eligible columns for median profile computation")
      return Array.fill(abundanceMatrix.head.length)(Float.NaN)
    }
    
    // Compute the ratio matrix
    val ratioMatrix = for( abundanceRow <- matrixWithEligibleCols ) yield {
      
      val ratioRow = for ( twoValues <- abundanceRow.sliding(2) ) yield {
        val ( a, b ) = (twoValues.head, twoValues.last)
        if( isZeroOrNaN(a) || isZeroOrNaN(b) ) Float.NaN else b / a
      }
      
      ratioRow.toArray
    }
    
    // Transform ratios before computing median
    val transformedRatioMatrix = ratioMatrix.map { ratios =>
      transformRatios(ratios)
    }
    
    // Compute the median of ratios
    val medianRatios = untransformRatios(summarizeUsingMedian(transformedRatioMatrix))
    
    // Compute the TOP3 mean abundances
    val top3MeanAbundances = summarizeUsingMeanOfTop3(matrixWithEligibleCols)
    
    // Convert the median ratios into absolute abundances
    var previousAb = top3MeanAbundances.head
    val absoluteAbundances = new ArrayBuffer[Float](medianRatios.length + 1)
    absoluteAbundances += previousAb
    
    for( medianRatio <- medianRatios ) {
      val absoluteValue = previousAb * medianRatio
      absoluteAbundances += absoluteValue
      previousAb = absoluteValue
    }
    
    // Scale up the absolute abundances
    val( top3MaxValue, top3MaxIdx ) = top3MeanAbundances.zipWithIndex.maxBy(_._1)
    val curMaxValue = absoluteAbundances(top3MaxIdx)
    val scalingFactor = top3MaxValue / curMaxValue
    val scaledAbundances = absoluteAbundances.map( _ * scalingFactor )
    
    // Re-integrate empty cols
    val nbCols = transposedMatrix.length
    val eligibleColIndexSet = eligibleColIndices.toSet
    
    var j = 0
    val finalAbundances = for( i <- 0 until nbCols ) yield {
      if( eligibleColIndexSet.contains(i) == false ) Float.NaN
      else {
        val abundance = scaledAbundances(j)
        j += 1
        abundance
      }
    }
    
    finalAbundances.toArray
  }
  
  def summarizeUsingSum( abundanceMatrix: Array[Array[Float]] ): Array[Float] = {
    abundanceMatrix.transpose.map( _calcAbundanceSum( _ ) )
  }
  
  def transformRatios( ratios: Array[Float] ): Array[Float] = {
    ratios.map { ratio =>
      if( ratio >= 1 ) ratio - 1
      else 1 - (1 / ratio)
    }
  }
  
  def untransformRatios( transformedRatios: Array[Float] ): Array[Float] = {
    transformedRatios.map { transformedRatio =>
      if( transformedRatio >= 0 ) transformedRatio + 1
      else 1 / (1 - transformedRatio)
    }
  }
  
  // TODO: this method is duplicated in the Profilizer => put in a shared object ???
  private def _calcMeanAbundance(abundances: Array[Float]): Float = {
    val defAbundances = abundances.filter( isZeroOrNaN(_) == false )
    if( defAbundances.length == 0 ) Float.NaN else defAbundances.sum / defAbundances.length
  }
  
  private def _calcMedianAbundance(abundances: Array[Float]): Float = {
    val defAbundances = abundances.filter( isZeroOrNaN(_) == false )
    if( defAbundances.length == 0 ) Float.NaN else median(defAbundances)
  }
  
  private def _calcAbundanceSum(abundances: Array[Float]): Float = {
    val defAbundances = abundances.filter( isZeroOrNaN(_) == false )
    if( defAbundances.length == 0 ) Float.NaN else defAbundances.sum
  }
  
}