package fr.proline.core.algo.msq

import fr.proline.util.math.median
import scala.collection.mutable.ArrayBuffer

object AbundanceNormalizer {
  
  def normalizeAbundances( abundanceMatrix: Array[Array[Float]] ): Array[Array[Float]] = {
    
    // Check the matrix is not empty
    if( abundanceMatrix.length == 0 ) return Array.empty[Array[Float]]
    
    val nfs = calcNormalizationFactors(abundanceMatrix)
    applyNormalizationFactors(abundanceMatrix, nfs)
  }
  
  def calcNormalizationFactors( abundanceMatrix: Array[Array[Float]] ): Array[Float] = {
    
    // Check the matrix is not empty
    if( abundanceMatrix.length == 0 ) return Array.empty[Float]
    
    // Check the matrix dimensions
    require( abundanceMatrix(0).length > 1, "the matrix must contain two columns at least" )
    
    // Compute the matrix of ratios (first abundance column is used as the reference) and transpose it
    val transposedRatioMatrix = _computeRatios(abundanceMatrix).transpose
    
    // Compute the normalization factors matrix as the median of the ratios
    transposedRatioMatrix.map { ratioColumn =>
      median( ratioColumn.filter( r => r != Float.NaN && r != 0f ) )
    }
    
  }
  
  def applyNormalizationFactors( abundanceMatrix: Array[Array[Float]], nfs: Array[Float] ): Array[Array[Float]] = {
    
    // Check the matrix is not empty
    if( abundanceMatrix.length == 0 ) return Array.empty[Array[Float]]
 
    // Compare the matrix and factors dimensions
    require(
      nfs.length == abundanceMatrix(0).length - 1,
      "the number of factors must be equal to number of matrix columns minus one"
    )
    
    // Apply the normalization factors to the abundance matrix
    abundanceMatrix.map { abundanceRow =>

      val normalizedAbundances = new ArrayBuffer[Float](abundanceRow.length)
      normalizedAbundances += abundanceRow(0)

      for ( i <- 1 until abundanceRow.length ) {
        val nf = nfs(i-1)
        normalizedAbundances += abundanceRow(i) * nf
      }
      
      normalizedAbundances.toArray
    }
    
  }
  
  private def _computeRatios( abundanceMatrix: Array[Array[Float]]): Array[Array[Float]] = {
    
    abundanceMatrix.map { abundanceRow =>
      val abundanceRef = abundanceRow(0)
      val ratios = new ArrayBuffer[Float](abundanceRow.length-1)

      for ( i <- 1 until abundanceRow.length ) {
        if( abundanceRef.isNaN || abundanceRef == 0f ) ratios += Float.NaN
        else {
          val ab = abundanceRow(i)
          ratios += ( if( ab.isNaN || ab == 0f ) Float.NaN else abundanceRef / ab )
        }
      }
      
      ratios.toArray
    }
    
  }

}