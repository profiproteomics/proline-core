package fr.proline.core.algo.msq

import org.apache.commons.math3.stat.descriptive.StatisticalSummary

object AbundanceRatioState extends Enumeration {
  val OverAbundant = Value(1)
  val UnderAbundant = Value(-1)
  val Invariant = Value(0)
}

case class AverageAbundanceRatio(
  val entityId: Long,
  val numeratorSummary: StatisticalSummary,
  val denominatorSummary: StatisticalSummary
) {
  
  def numeratorMean = numeratorSummary.getMean()
  def denominatorMean = denominatorSummary.getMean()
  
  def this(entityId: Long, numeratorValues: Array[Double], denominatorValues: Array[Double]) {    
    this(
      entityId,
      CommonsStatHelper.calcStatSummary(numeratorValues),
      CommonsStatHelper.calcStatSummary(denominatorValues)
    )
  }
  
  lazy val maxAbundance = math.max(numeratorMean,denominatorMean)
  
  lazy val ratioValue: Option[Float] = {
    if( numeratorMean > 0 && denominatorMean > 0 ) Some( (numeratorMean / denominatorMean).toFloat )
    else None
  }
  
  lazy val foldValue: Option[Float] = {
    
    if( numeratorMean > 0 && denominatorMean > 0 ) {
      if( numeratorMean > denominatorMean )
        Some( (numeratorMean / denominatorMean).toFloat )
      else
        Some( (denominatorMean / numeratorMean).toFloat )
    } else None
    
  }
  
  var tTestPValue = Option.empty[Double]
  var zTestPValue = Option.empty[Double]
  var zScore = Option.empty[Float]
  var state = Option.empty[AbundanceRatioState.Value]
}

object AbundanceRatiolizer {
  
  def updateRatioStates(
    ratios: Seq[AverageAbundanceRatio],
    relativeVariationModel: RelativeErrorModel,
    absoluteNoiseModel: Option[AbsoluteErrorModel],
    pValueThreshold: Float,
    applyVarianceCorrection: Boolean = true,
    applyTTest: Boolean = true,
    applyZTest: Boolean = true
  ) {
    
    ratios.foreach { ratio =>
      
      val foldValue = ratio.foldValue
      
      // If we don't have a fold value
      if( foldValue.isEmpty ) {
        if( ratio.numeratorMean > 0 ) ratio.state = Some(AbundanceRatioState.OverAbundant)
        else if ( ratio.denominatorMean > 0 ) ratio.state = Some(AbundanceRatioState.UnderAbundant)
        else ratio.state = None
      }
      // Else if we have a valid ratio
      else {
        
        // Apply the right noise model
        /*val noisePValue = noiseModel match {
          
          case absNoiseModel: AbsoluteNoiseModel => {
            //println( ratio.entityId )
            // Compute the T-Test using the noise model
            absNoiseModel.tTest(ratio.numeratorSummary,ratio.denominatorSummary)
          }
          case relNoiseModel: RelativeNoiseModel => {
            
            val absNoiseModel = relNoiseModel.toAbsoluteNoiseModel
            
            absNoiseModel.tTest(ratio.numeratorSummary,ratio.denominatorSummary)
            
            // Compute the Z-Test using the noise model
            relNoiseModel.zTest(ratio.maxAbundance.toFloat, ratio.ratioValue.get)
          }
        }*/
        
        if( applyTTest && ratio.numeratorSummary.getN > 2 && ratio.denominatorSummary.getN > 2 ) {
          ratio.tTestPValue = absoluteNoiseModel.map( _.tTest(ratio.numeratorSummary,ratio.denominatorSummary,applyVarianceCorrection) )
        }
        
        // Apply the variation model
        if( applyZTest ) {
          val( zScore, zTestPValue ) = relativeVariationModel.zTest(ratio.maxAbundance.toFloat, ratio.foldValue.get)
          ratio.zScore = Some(zScore)
          ratio.zTestPValue = if( zTestPValue.isNaN ) None else Some( zTestPValue )
          //println( "z-test=" + foldChangePValue )
        }
        
        // Check the pValue of T-Test and Z-Test
        if( ratio.tTestPValue.getOrElse(0.0) <= pValueThreshold && ratio.zTestPValue.getOrElse(0.0) <= pValueThreshold ) {
          if( ratio.numeratorMean > ratio.denominatorMean ) ratio.state = Some(AbundanceRatioState.OverAbundant)
          else ratio.state = Some(AbundanceRatioState.UnderAbundant)
        } else {
          ratio.state = Some(AbundanceRatioState.Invariant)
        }
        
      }
      
    }
    
  }

}