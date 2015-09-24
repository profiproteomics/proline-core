package fr.proline.core.algo.msq

import scala.collection.mutable.ArrayBuffer
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.math3.stat.StatUtils
import org.apache.commons.math3.stat.descriptive.rank.Percentile
import fr.profi.util.primitives.isZeroOrNaN
import fr.profi.util.random.randomGaussian

object MissingAbundancesInferer extends LazyLogging {
  
  val percentileComputer = new Percentile()
  
  def inferAbundances(
    abundanceMatrix: Array[Array[Float]],
    psmCountMatrix: Array[Array[Int]],
    errorModel: AbsoluteErrorModel
  ): Array[Array[Float]] = {
    require( abundanceMatrix.length >= 10, "at least 10 abundance rows are required for missing abundance inference")
    
    // Retrieve quartiles from flattened abundance matrix
    val allDefinedAbundances = abundanceMatrix.flatten.withFilter( isZeroOrNaN(_) == false ).map(_.toDouble).sorted
    if( allDefinedAbundances.isEmpty ) {
      logger.warn("no defined abundances in the abundanceMatrix: can't infer missing values")
      return abundanceMatrix
    }
    
    val q1 = percentileComputer.evaluate(allDefinedAbundances,25).toFloat
    val q3 = percentileComputer.evaluate(allDefinedAbundances,75).toFloat
    
    // Convert quartiles into theoretical maximal bounds
    var(lb,ub) = CommonsStatHelper.quartilesToBounds(q1,q3)
    //println("lb:" + lb)
    
    // Re-Compute Lower Bound using the first percentile if it is lower than the lowest observed abundance
    if( lb < allDefinedAbundances.head ) lb = {
      val firstPercentileIdx = 1 + (allDefinedAbundances.length / 100).toInt
      val percentileMean = allDefinedAbundances.take( firstPercentileIdx ).sum.toFloat / firstPercentileIdx
      percentileMean
    }
    
    // Decrease noise level by a given factor (hard coded for the moment)
    lb /= 10
    
    logger.info("Inferring missing values using noise level="+ lb)
    
    abundanceMatrix.zip(psmCountMatrix).map { case (abundanceRow,psmCountsRow) =>

      val totalPsmCount = psmCountsRow.sum
      
      // Retrieve defined abundances
      val defAbundances = abundanceRow.filter( isZeroOrNaN(_) == false )
      val nbDefValues = defAbundances.length
      
      // Compute defined abundances frequency
      val defAbFreq = nbDefValues / abundanceRow.length
      
      // Noise is taken as mean abundance if no PSM has been identified or if no abundance detected
      var newAbundanceRow = abundanceRow
      val meanAbundance = if( totalPsmCount == 0 || nbDefValues == 0 ) {
        
        // This code was used to remove values when no PSM identified and low abundance frequency
        /*if( defAbFreq < 0.5 ) {
          newAbundanceRow = Array.fill(abundanceRow.length)( Float.NaN )
        }*/
        
        lb
      }
      // Else we compute the mean abundance of the defined abundances if freq > 50%
      else {

        // Return estimated noise level if frequency is lower than 50%
        /*if( defAbFreq < 0.5 ) lb
        // TODO: re-enable this condition ?
        //else if( nbDefValues == 1 && defAbundances(0) > q3 ) lb
        // Compute the mean value of the defined abundances
        else defAbundances.sum / nbDefValues*/
        
        defAbundances.sum / nbDefValues
      }
      
      // Retrieve the standard deviation corresponding to this abundance level
      //val stdDev = errorModel.getStdevForAbundance(meanAbundance)
      //val defAbundancesAsDoubles = newAbundanceRow.withFilter( isZeroOrNaN(_) == false ).map(_.toDouble)
      
      // Estimate the missing abundances
      val newAbundances = newAbundanceRow.map { abundance =>
        if( isZeroOrNaN(abundance) == false ) abundance
        // TODO: limit the (output stddev) to 2 * (input stddev)
        else {
          
          // Should we use the computed stdDev or a constant error to parameterize the gaussian ???
          randomGaussian(meanAbundance, meanAbundance * 0.05).toFloat.max(0)
          
          /*var tryCount = 0
          var newAbundance = 0f
          var newStdDev = 0f
          var exitLoop = false
          
          // Generate a new random value
          // If the newStdDev is too high (> 2 * stdDev) a new value is generated
          // The loop stops after 3 iterations
          while( exitLoop == false ) {
            // Should we use the computed stdDev or a constant error to parameterize the gaussian ???
            newAbundance = randomGaussian(meanAbundance, meanAbundance * 0.2).toFloat.max(0)
            
            val tmpAbundances = new ArrayBuffer[Double](defAbundances.length+1)
            tmpAbundances ++= defAbundancesAsDoubles
            tmpAbundances += newAbundance.toDouble
            
            val statSummary = CommonsStatHelper.calcStatSummary(tmpAbundances.toArray)
            newStdDev = statSummary.getStandardDeviation().toFloat
            
            println("targetStdDev: "+ stdDev)
            println("realStdDev: "+ CommonsStatHelper.calcStatSummary(defAbundancesAsDoubles).getStandardDeviation())
            println("newStdDev: "+ newStdDev)
            
            tryCount += 1
            
            if( newStdDev < 2 * stdDev ) exitLoop = true
            else if ( tryCount > 3 ) exitLoop = true
          }

          println("tryCount: "+ tryCount)

          
          newAbundance*/
        }
      }
      
      /*if( abundanceRow.count( ! _.isNaN() ) == 2 
          && abundanceRow(0) > 2600000 && abundanceRow(0) < 2700000
          && abundanceRow(2) > 1300000 && abundanceRow(2) < 1400000
      ) {
        println("totalPsmCount: "+ totalPsmCount)
        println("meanAbundance: "+ meanAbundance)
        println("stdDev: "+ stdDev)
        println("abundanceRow: "+ abundanceRow.toList)
        println("abundanceRow: "+ newAbundances.toList)
      }*/

      newAbundances
    }
    
  }
  
}