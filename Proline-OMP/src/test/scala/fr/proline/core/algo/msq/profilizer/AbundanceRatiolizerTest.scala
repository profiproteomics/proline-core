package fr.proline.core.algo.msq.profilizer

import scala.Array.canBuildFrom
import scala.io.Source
import org.junit.Assert.assertEquals
import org.junit.Ignore
import org.junit.Test
import fr.proline.core.algo.msq.ProfilizerStatConfig

@Test
class AbundanceRatiolizerTest {
  
  @Test
  def testUpdateRatioStates {
    
    val source = Source.fromURL( getClass.getResource("lcms_quant_table.tsv") )
    
    val abundanceMatrix = source.getLines.map { line =>
      line.split("\\t").map( v => v.toFloat )
    }.toArray
    
    //val m = abundanceMatrix.result
    val normalizedMatrix = AbundanceNormalizer.normalizeAbundances(abundanceMatrix)
    
    //import scala.runtime.ScalaRunTime.stringOf
    //println( stringOf(  m(0) ) )
    //println( stringOf( normalizedMatrix(0) ) )
    
    val ratiosBuilder = Array.newBuilder[AverageAbundanceRatio]
    val absoluteError = Array.newBuilder[AbsoluteErrorObservation]
    
    var rowNumber = 1L
    normalizedMatrix.foreach { normalizedRow =>
      val groupedValues = normalizedRow.map(_.toDouble).grouped(3).toArray
      
      val numeratorSummary = CommonsStatHelper.calcExtendedStatSummary(groupedValues(1))
      val denominatorSummary = CommonsStatHelper.calcExtendedStatSummary(groupedValues(0))
      absoluteError += AbsoluteErrorObservation( numeratorSummary.getMean.toFloat, numeratorSummary.getStandardDeviation.toFloat )
      absoluteError +=  AbsoluteErrorObservation( numeratorSummary.getMean.toFloat, numeratorSummary.getStandardDeviation.toFloat )
  
      ratiosBuilder += new AverageAbundanceRatio( rowNumber, numeratorSummary, denominatorSummary, Array(1,1,1), Array(1,1,1) )
      
      rowNumber += 1L
    }
    
    val absoluteNoiseModel = ErrorModelComputer.computeAbsoluteErrorModel(absoluteError.result,nbins=Some(5))
   
    /*noiseModel.noiseDistribution.foreach { bin =>
      println( bin.abundance +"\t"+bin.stdDev)
    }*/
    //import scala.runtime.ScalaRunTime.stringOf
    //println( stringOf(absoluteNoiseModel.errorDistribution) )
    
    
    val normalizedRatios = ratiosBuilder.result
    /*normalizedRatios.foreach { r =>
      r.ratioValue.map( v => println(math.log(v)) )
    }*/
    
    /*
    // --- Normalization procedure ---
    // TODO: perform this in a separate package
    val medianRatio = getMedianObject[AverageAbundanceRatio](
      ratios.filter(_.ratioValue.isDefined),
      (a,b) => a.ratioValue.get < b.ratioValue.get
    )
    val nf = medianRatio.ratioValue.get
    //println( nf )
    
    val normalizedRatios = ratios.map { r =>
      val denomSummary = r.denominatorSummary
      r.copy( denominatorSummary = CommonsStatHelper.copyStatSummary(denomSummary, mean = denomSummary.getMean * nf ) )
    }
    
    val medianRatio2 = getMedianObject[AverageAbundanceRatio](
      normalizedRatios.filter(_.ratioValue.isDefined),
      (a,b) => a.ratioValue.get < b.ratioValue.get
    )
    //println( medianRatio2.ratioValue.get )
    // --- End of Normalization procedure ---
    */
    
    val relativeVariationsBuilder = Array.newBuilder[RelativeErrorObservation]
    relativeVariationsBuilder.sizeHint(normalizedRatios.length)
    
    normalizedRatios.foreach { ratio =>
      if( ratio.ratioValue.isDefined )
        relativeVariationsBuilder += RelativeErrorObservation( ratio.maxAbundance.toFloat, ratio.ratioValue.get )
    }
    
   /* val relativeVariations = relativeVariationsBuilder.result
    val ratiosValues = relativeVariations.map( _.ratio )
    val medianRatio = median( ratiosValues )
    val normalizedRelativeVariations*/
    
    val relativeVariationModel = ErrorModelComputer.computeRelativeErrorModel(relativeVariationsBuilder.result,nbins=Some(5))
    
    //import scala.runtime.ScalaRunTime.stringOf
    //println( stringOf(relativeVariationModel.noiseDistribution) )
    
    val config = ProfilizerStatConfig(applyVarianceCorrection = false)
    AbundanceRatiolizer.updateRatioStates(normalizedRatios, relativeVariationModel, Some(absoluteNoiseModel), config)
    
    val ratiosSortedByTPValue = normalizedRatios.sortBy( _.tTestPValue.getOrElse(1.0) )
    
    assertEquals( 443, ratiosSortedByTPValue.count( _.tTestPValue.getOrElse(1.0) <= 0.01 ) )
    assertEquals( 201, ratiosSortedByTPValue.count( _.zTestPValue.getOrElse(1.0) <= 0.01 ) )
    assertEquals( 184, ratiosSortedByTPValue.count( _.state.get == AbundanceRatioState.OverAbundant ) )
    assertEquals( 256, ratiosSortedByTPValue.count( _.state.get == AbundanceRatioState.UnderAbundant ) )
    
    val overAbundantRatios = ratiosSortedByTPValue.filter(  _.state.get == AbundanceRatioState.OverAbundant ).sortBy( _.entityId )
    // The fist 10 rows should be over abundant
    assertEquals( 10, overAbundantRatios(9).entityId )
    
    //val ratiosSortedByZPValue = ratiosSortedByTPValue.filter(  _.state.get == AbundanceRatioState.OverAbundant )
    //                                                .sortBy( _.zTestPValue.getOrElse(1.) )
    //assertEquals( 91, ratiosSortedByZPValue.last.entityId )
    
    //val overRatios = ratiosSortedByTPValue.filter( e => e.state.get == AbundanceRatioState.OverAbundant && e.maxAbundance < 5e7 ).sortBy(_.ratioValue.get)
    
  }
  
}