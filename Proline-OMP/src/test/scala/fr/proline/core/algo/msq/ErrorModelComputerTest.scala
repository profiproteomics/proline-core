package fr.proline.core.algo.msq

import org.junit.Assert._
import org.junit.Test

@Test
class ErrorModelComputerTest {

  //@Test
  def cauchySimulation {
    
    import scala.util.Random
    
    val maxVals = 1000000
    
    //val stdDevFactor = 2
    for( cv <- 1 to 100) {
      
      val meanOffset = 10000
      val errorObservations = Array.newBuilder[RelativeErrorObservation]
      errorObservations.sizeHint(maxVals)
      
      def denormalizeGaussian( g: Double ): Double = {
        val d = (g * (meanOffset*cv/100) ) + meanOffset
        if( d < 0 ) 1e-5 else d
      }
      
      for (i <- 1 to maxVals ) {
        errorObservations += RelativeErrorObservation(
          meanOffset,
          ( denormalizeGaussian(Random.nextGaussian) / denormalizeGaussian(Random.nextGaussian) ).toFloat
        )
      }
      
      val errorModel = ErrorModelComputer.computeRelativeErrorModel(errorObservations.result)
      
      // output the CV and the IQR of LOG RATIOS
      println( cv.toFloat/100 + "\t" + math.log(errorModel.getRatioQuartilesForAbundance(meanOffset)._2)*2 )
    }
    
    // Results: the linear regression of the obtained scatter plot shown a strong correlation with IQR = 2.0681 CV

  }
  
  @Test
  def testAbsoluteModel {
    
    val errorObservations = Array(
      AbsoluteErrorObservation( 1e5f, 1e5f * 0.30f ),
      AbsoluteErrorObservation( 1e5f, 1e5f * 0.29f ),
      AbsoluteErrorObservation( 1e5f, 1e5f * 0.28f ),
      AbsoluteErrorObservation( 1e5f, 1e5f * 0.27f),
      AbsoluteErrorObservation( 1e6f, 1e6f * 0.26f ),
      AbsoluteErrorObservation( 1e6f, 1e6f * 0.25f ),
      AbsoluteErrorObservation( 1e6f, 1e6f * 0.24f ),
      AbsoluteErrorObservation( 1e6f, 1e6f * 0.23f ),
      AbsoluteErrorObservation( 1e7f, 1e7f * 0.22f ),
      AbsoluteErrorObservation( 1e7f, 1e7f * 0.21f ),
      AbsoluteErrorObservation( 1e7f, 1e7f * 0.20f ),
      AbsoluteErrorObservation( 1e7f, 1e7f * 0.19f ),
      AbsoluteErrorObservation( 1e8f, 1e8f * 0.10f ),
      AbsoluteErrorObservation( 1e8f, 1e8f * 0.09f ),
      AbsoluteErrorObservation( 1e8f, 1e8f * 0.08f ),
      AbsoluteErrorObservation( 1e8f, 1e8f * 0.07f ),
      AbsoluteErrorObservation( 1e9f, 1e9f * 0.06f ),
      AbsoluteErrorObservation( 1e9f, 1e9f * 0.05f ),
      AbsoluteErrorObservation( 1e9f, 1e9f * 0.05f ),
      AbsoluteErrorObservation( 1e9f, 1e9f * 0.04f ),
      AbsoluteErrorObservation( 1e9f, 1e9f * 0.04f ),
      AbsoluteErrorObservation( 1e9f, 1e9f * 0.04f ),
      AbsoluteErrorObservation( 1e9f, 1e9f * 0.03f ),
      AbsoluteErrorObservation( 1e9f, 1e9f * 0.03f )
    )
    
    val errorModel = ErrorModelComputer.computeAbsoluteErrorModel(errorObservations,nbins = Some(5))
    
    val s1 = CommonsStatHelper.buildStatSummary(1e5f, 0, 4)
    val s2 = CommonsStatHelper.buildStatSummary(3e5f, 0, 4)
    
    assertEquals(0.01, errorModel.tTest( s1, s2 ), 0.01)
    /*import scala.runtime.ScalaRunTime.stringOf
    println( stringOf(errorModel.errorDistribution) )*/
  }
  
  @Test
  def testRelativeModel {
    
    val errorObservations = Array(
      RelativeErrorObservation( 1e5f, 0.7f ),
      RelativeErrorObservation( 1e5f, 0.75f ),
      RelativeErrorObservation( 1e5f, 1.4f ),
      RelativeErrorObservation( 1e5f, 1.5f ),
      RelativeErrorObservation( 1e6f, 0.75f ),
      RelativeErrorObservation( 1e6f, 0.8f ),
      RelativeErrorObservation( 1e6f, 1.3f ),
      RelativeErrorObservation( 1e6f, 1.4f ),
      RelativeErrorObservation( 1e7f, 0.8f ),
      RelativeErrorObservation( 1e7f, 0.85f ),
      RelativeErrorObservation( 1e7f, 1.2f ),
      RelativeErrorObservation( 1e7f, 1.3f ),
      RelativeErrorObservation( 1e8f, 0.85f ),
      RelativeErrorObservation( 1e8f, 0.9f ),
      RelativeErrorObservation( 1e8f, 1.1f ),
      RelativeErrorObservation( 1e8f, 1.2f ),
      RelativeErrorObservation( 1e9f, 0.95f ),
      RelativeErrorObservation( 1e9f, 1.0f ),
      RelativeErrorObservation( 1e9f, 1.0f ),
      RelativeErrorObservation( 1e9f, 1.0f ),
      RelativeErrorObservation( 1e9f, 1.0f ),
      RelativeErrorObservation( 1e9f, 1.05f ),
      RelativeErrorObservation( 1e9f, 0.98f ),
      RelativeErrorObservation( 1e9f, 1.1f )
    )
    

    val errorModel = ErrorModelComputer.computeRelativeErrorModel(errorObservations, nbins = Some(5))
    val pValue = 1.0 + errorModel.zTest(1e5f, 1/3f )._2
    assertEquals(0.03, pValue, 0.001)
    
   /* import scala.runtime.ScalaRunTime.stringOf    
    println( stringOf(errorModel.errorDistribution) )
    
    val absErrorModel = errorModel.toAbsoluteErrorModel()
    println( stringOf(absErrorModel.errorDistribution) )
    
    val s1 = CommonsStatHelper.buildStatSummary(1e5f, 0, 4)
    val s2 = CommonsStatHelper.buildStatSummary(1.7e5f, 0, 4)
    println( absErrorModel.tTest( s1, s2 ) )*/
    
    /*println( errorModel.zTest1(5e5f, 1/2.2f ) )
    println( errorModel.zTest2(5e5f, 1/2.2f ) )
    
    var now = System.currentTimeMillis()
    for( i <- 1 to 100000 ) {
      errorModel.zTest1(5e5f, 2.2f ) * 2
    }
    var took = System.currentTimeMillis() - now
    println(took)
    
    now = System.currentTimeMillis()
    for( i <- 1 to 100000 ) {
      errorModel.zTest2(5e5f, 2.2f )
    }
    took = System.currentTimeMillis() - now
    println(took)*/
    
  }
  
}