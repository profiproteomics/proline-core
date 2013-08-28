package fr.proline.core.algo.msq

import org.apache.commons.math.stat.StatUtils
import org.apache.commons.math.stat.descriptive.rank.Percentile
import fr.proline.util.random.randomGaussian

object MissingAbundancesInferer {
  
  val percentileComputer = new Percentile()

  protected def isZeroOrNaN( value: Float ) = value.isNaN || value == 0f
  protected def isZeroOrNaN( value: Double ) = value.isNaN || value == 0.
  
  def inferAbundances( abundanceMatrix: Array[Array[Float]], errorModel: AbsoluteErrorModel ): Array[Array[Float]] = {
    
    // Retrieve quartiles from flattened abundance matrix
    val allDefinedAbundances = abundanceMatrix.flatten.filter( isZeroOrNaN(_) == false ).map(_.toDouble).sorted
    val q1 = percentileComputer.evaluate(allDefinedAbundances,25).toFloat
    val q3 = percentileComputer.evaluate(allDefinedAbundances,75).toFloat
    
    // Convert quartiles into theoretical maximal bounds
    var(lb,ub) = ErrorModelComputer.quartilesToBounds(q1,q3)
    
    // Re-Compute Lower Bound using the first percentile if it is lower than the lowest observed abundance
    if( lb < allDefinedAbundances(0) ) lb = {
      val firstPercentileIdx = 1 + (allDefinedAbundances.length / 100).toInt
      allDefinedAbundances.take( firstPercentileIdx ).sum.toFloat / firstPercentileIdx
    }
    
    abundanceMatrix.map { abundanceRow =>
      
      // Retrieve defined abundances
      val defAbundances = abundanceRow.filter( isZeroOrNaN(_) == false )      
      val nbDefValues = defAbundances.length
      
      // Compute the mean abundance for these defined abundances
      val meanAbundance = if( nbDefValues == 0 ) lb
      else if( nbDefValues == 1 && defAbundances(0) > q3 ) lb
      else defAbundances.sum / nbDefValues
      
      // Retrieve the standard deviation corresponding to this abundance level
      val stdDev = errorModel.getStdevForAbundance(meanAbundance)
      
      // Estimate the missing abundances
      abundanceRow.map { abundance =>
        if( isZeroOrNaN(abundance) == false ) abundance
        else randomGaussian(meanAbundance, stdDev).toFloat
      }

    }
    
  }
  

  
}