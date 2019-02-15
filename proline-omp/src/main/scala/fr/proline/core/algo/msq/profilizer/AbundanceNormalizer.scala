package fr.proline.core.algo.msq.profilizer

import fr.profi.util.math.median
import scala.collection.mutable.ArrayBuffer
import fr.profi.util.primitives.isZeroOrNaN

// Note: by convention the first column is taken as the reference
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
      
      val defRatios = ratioColumn.filter( isZeroOrNaN(_) == false )
      
      val nf = if( defRatios.isEmpty ) 1 else median( defRatios )
      
      require( isZeroOrNaN(nf) == false, "error during normalization factor computation: the median should be defined" )
      
      nf
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
      
      // Add reference (first column) to the normalized abundances
      normalizedAbundances += abundanceRow(0)

      // Apply each NF to each column
      for ( i <- 1 until abundanceRow.length ) {
        val nf = nfs(i-1)
        normalizedAbundances += abundanceRow(i) * nf
      }
      
      normalizedAbundances.toArray
    }
    
  }
  
  private def _computeRatios( abundanceMatrix: Array[Array[Float]] ): Array[Array[Float]] = {
    
    abundanceMatrix.map { abundanceRow =>
      val abundanceRef = abundanceRow.head
      val ratioRow = new ArrayBuffer[Float](abundanceRow.length-1)
      val refIsUndef = isZeroOrNaN(abundanceRef)
      
      for ( i <- 1 until abundanceRow.length ) {
        if( refIsUndef ) ratioRow += Float.NaN
        else {
          val ab = abundanceRow(i)
          ratioRow += ( if( isZeroOrNaN(ab) ) Float.NaN else abundanceRef / ab )
        }
      }
      
      ratioRow.toArray
    }
    
  }

}