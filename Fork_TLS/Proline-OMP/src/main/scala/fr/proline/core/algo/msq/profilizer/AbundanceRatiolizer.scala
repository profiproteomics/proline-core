package fr.proline.core.algo.msq.profilizer

import fr.proline.core.algo.msq.ProfilizerStatConfig

object AbundanceRatioState extends Enumeration {
  val OverAbundant = Value(1)
  val UnderAbundant = Value(-1)
  val Invariant = Value(0)
}

case class AverageAbundanceRatio(
  val entityId: Long,
  val numeratorSummary: ExtendedStatisticalSummary,
  val denominatorSummary: ExtendedStatisticalSummary,
  val numeratorPsmCounts: Array[Int],
  val denominatorPsmCounts: Array[Int]
) {
  
  // TODO: parameterize the use of mean/median
  def numerator = numeratorSummary.getMedian()
  def denominator = denominatorSummary.getMedian()
  
  def this(
    entityId: Long,
    numeratorValues: Array[Double],
    denominatorValues: Array[Double],
    numeratorPsmCount: Array[Int],
    denominatorPsmCount: Array[Int]
  ) {    
    this(
      entityId,
      CommonsStatHelper.calcExtendedStatSummary(numeratorValues),
      CommonsStatHelper.calcExtendedStatSummary(denominatorValues),
      numeratorPsmCount,
      denominatorPsmCount
    )
  }
  
  lazy val maxAbundance = math.max(numerator,denominator)
  
  lazy val ratioValue: Option[Float] = {
    if( numerator > 0 && denominator > 0 ) Some( (numerator / denominator).toFloat )
    else None
  }
  
  lazy val foldValue: Option[Float] = {
    
    if( numerator > 0 && denominator > 0 ) {
      if( numerator > denominator )
        Some( (numerator / denominator).toFloat )
      else
        Some( (denominator / numerator).toFloat )
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
    config: ProfilizerStatConfig    
  ) {
    
    val pValueThreshold = config.statTestsAlpha
    val minZScore = config.minZScore
    val minPsmCountPerRatio = config.minPsmCountPerRatio
    val applyVarianceCorrection = config.applyVarianceCorrection
    val applyTTest = config.applyTTest
    val applyZTest = config.applyZTest
    
    var i = 0
    ratios.foreach { ratio =>
      
      // If the total number of PSM per group is too low
      if( ratio.numeratorPsmCounts.sum < minPsmCountPerRatio && ratio.denominatorPsmCounts.sum < minPsmCountPerRatio ) {
        ratio.state = None
      }
      // Else if we don't have a fold value
      else if( ratio.foldValue.isEmpty ) {
        if( ratio.numerator > 0 ) ratio.state = Some(AbundanceRatioState.OverAbundant)
        else if ( ratio.denominator > 0 ) ratio.state = Some(AbundanceRatioState.UnderAbundant)
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
        var isZScoreOK = true
        if( applyZTest ) {
          val( zScore, zTestPValue ) = relativeVariationModel.zTest(ratio.maxAbundance.toFloat, ratio.foldValue.get)
          if( zScore < minZScore ) isZScoreOK = false
          ratio.zScore = Some(zScore)
          ratio.zTestPValue = if( zTestPValue.isNaN ) None else Some( zTestPValue )
          //println( "z-test=" + foldChangePValue )
        }
        
        // Check the pValue of T-Test and Z-Test
        if( ratio.tTestPValue.getOrElse(0.0) <= pValueThreshold && isZScoreOK ) { // ratio.zTestPValue.getOrElse(0.0) <= pValueThreshold
          if( ratio.numerator > ratio.denominator ) ratio.state = Some(AbundanceRatioState.OverAbundant)
          else ratio.state = Some(AbundanceRatioState.UnderAbundant)
        } else {
          ratio.state = Some(AbundanceRatioState.Invariant)
        }
        
      }
      
      i += 1
    }
    
  }

}