package fr.proline.core.algo.msq

import org.apache.commons.math.stat.StatUtils
import org.apache.commons.math.stat.descriptive.rank.Percentile
import fr.profi.util.primitives.isZeroOrNaN
import fr.profi.util.random.randomGaussian

object MissingAbundancesInferer {
  
  val percentileComputer = new Percentile()
  
  def inferAbundances(
    abundanceMatrix: Array[Array[Float]],
    psmCountMatrix: Array[Array[Int]],
    errorModel: AbsoluteErrorModel
  ): Array[Array[Float]] = {
    require( abundanceMatrix.length >= 10, "at least 10 abundance rows are required for missing abundance inference")
    
    // Retrieve quartiles from flattened abundance matrix
    val allDefinedAbundances = abundanceMatrix.flatten.withFilter( isZeroOrNaN(_) == false ).map(_.toDouble).sorted
    
    val q1 = percentileComputer.evaluate(allDefinedAbundances,25).toFloat
    val q3 = percentileComputer.evaluate(allDefinedAbundances,75).toFloat
    
    // Convert quartiles into theoretical maximal bounds
    var(lb,ub) = ErrorModelComputer.quartilesToBounds(q1,q3)
    
    // Re-Compute Lower Bound using the first percentile if it is lower than the lowest observed abundance
    if( lb < allDefinedAbundances.head ) lb = {
      val firstPercentileIdx = 1 + (allDefinedAbundances.length / 100).toInt
      allDefinedAbundances.take( firstPercentileIdx ).sum.toFloat / firstPercentileIdx
    }
    
    abundanceMatrix.zip(psmCountMatrix).map { case (abundanceRow,psmCountsRow) =>

      val totalPsmCount = psmCountsRow.sum
      
      // Noise is taken as mean abudance if no PSM has been identified
      val meanAbundance = if( totalPsmCount == 0 ) lb
      // Else we compute the mean abundance of the defined abundances if freq > 50%
      else {
        
        // Retrieve defined abundances
        val defAbundances = abundanceRow.filter( isZeroOrNaN(_) == false )
        val nbDefValues = defAbundances.length
        
        // Compute defined abundances frequency
        val defAbFreq = nbDefValues / abundanceRow.length

        // Return estimated noise level if frequency is lower than 50%
        if( defAbFreq < 0.5 ) lb
        // TODO: re-enable this condition ?
        //else if( nbDefValues == 1 && defAbundances(0) > q3 ) lb
        // Compute the mean value of the defined abundances
        else defAbundances.sum / nbDefValues
      }
      
      // Retrieve the standard deviation corresponding to this abundance level
      val stdDev = errorModel.getStdevForAbundance(meanAbundance)
      
      // Estimate the missing abundances
      abundanceRow.map { abundance =>
        if( isZeroOrNaN(abundance) == false ) abundance
        else randomGaussian(meanAbundance, stdDev).toFloat.max(0)
      }

    }
    
  }
  
}