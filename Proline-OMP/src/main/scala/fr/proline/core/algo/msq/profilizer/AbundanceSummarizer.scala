package fr.proline.core.algo.msq.profilizer

import fr.profi.util.lang.EnhancedEnum
import fr.profi.util.math.median
import fr.profi.util.primitives.isZeroOrNaN
import scala.collection.mutable.ArrayBuffer

object AbundanceSummarizer {
  
  object Method extends EnhancedEnum {
    val BEST_SCORE = Value // has no implementation, should be called before
    val MAX_ABUNDANCE_SUM = Value // return one single row
    val MEAN = Value
    val MEAN_OF_TOP3 = Value
    val MEDIAN = Value
    val MEDIAN_PROFILE = Value
    val SUM = Value
  }
  
  private val EMPTY_MATRIX_MESSAGE = "abundanceMatrix is empty"
  
  def summarizeAbundanceMatrix( abundanceMatrix: Array[Array[Float]], method: Method.Value ): Array[Float] = {
    
    val matrixLength = abundanceMatrix.length
    require( matrixLength > 0, EMPTY_MATRIX_MESSAGE )
    
    if( matrixLength == 1 ) return abundanceMatrix.head
    
    method match {
      case Method.BEST_SCORE =>  {
        // Note: a BEST_SCORE approach could be implemented here, but this will lead to a more complicated the API
        throw new IllegalArgumentException(
          "This method should not be called when BEST_SCORE method is used. "+
          "Please perform the appropriate filtering in a separate step"
        )
      }
      case Method.MAX_ABUNDANCE_SUM => summarizeUsingMaxAbundanceSum(abundanceMatrix)
      case Method.MEAN => summarizeUsingMean(abundanceMatrix)
      case Method.MEAN_OF_TOP3 => summarizeUsingMeanOfTop3(abundanceMatrix)
      case Method.MEDIAN => summarizeUsingMedian(abundanceMatrix)
      case Method.MEDIAN_PROFILE => summarizeUsingMedianProfile(abundanceMatrix)
      case Method.SUM => summarizeUsingSum(abundanceMatrix)
    }
    
  }
  
  private def summarizeUsingMaxAbundanceSum( abundanceMatrix: Array[Array[Float]] ): Array[Float] = {
    abundanceMatrix.maxBy(_.sum)
  }
  
  private def summarizeUsingMean( abundanceMatrix: Array[Array[Float]] ): Array[Float] = {    
    abundanceMatrix.transpose.map( _calcMeanAbundance( _ ) )
  }
  
  private def summarizeUsingMeanOfTop3( abundanceMatrix: Array[Array[Float]] ): Array[Float] = {
    
    // Sort rows by descending median abundance
    val sortedMatrix = abundanceMatrix.sortBy( - _calcMedianAbundance(_) )
    
    // Take 3 highest rows
    val top3Matrix = sortedMatrix.take(3)
    
    // Mean the rows
    summarizeUsingMean(top3Matrix)
  }
  
  private def summarizeUsingMedian( abundanceMatrix: Array[Array[Float]] ): Array[Float] = {    
    abundanceMatrix.transpose.map( _calcMedianAbundance( _ ) )
  }
  
  private def summarizeUsingMedianProfile( abundanceMatrix: Array[Array[Float]] ): Array[Float] = {
    
    // Transpose the matrix
    val transposedMatrix = abundanceMatrix.transpose
    
    // Select columns eligible for ratio computations (discard columns of without defined values)
    val colObsFreq = transposedMatrix.map( col => col.count( isZeroOrNaN(_) == false ).toFloat / col.length )
    val colObsFreqWithIdx = colObsFreq.zipWithIndex
    val eligibleColIndices = colObsFreqWithIdx.withFilter( _._1 > 0f ).map( _._2 )
    val matrixWithEligibleCols = eligibleColIndices.map( i => transposedMatrix(i) ).transpose
    
    // Compute the ratio matrix
    val ratioMatrix = for( abundanceRow <- matrixWithEligibleCols ) yield {
      
      val ratioRow = for ( twoValues <- abundanceRow.sliding(2) ) yield {
        val ( a, b ) = (twoValues.head, twoValues.last)
        if( isZeroOrNaN(a) || isZeroOrNaN(b) ) Float.NaN else b / a
      }
      
      ratioRow.toArray
    }
    
    // Compute the median of ratios
    val medianRatios = summarizeUsingMedian(ratioMatrix)
    
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
  
  private def summarizeUsingSum( abundanceMatrix: Array[Array[Float]] ): Array[Float] = {
    abundanceMatrix.transpose.map( _calcAbundanceSum( _ ) )
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