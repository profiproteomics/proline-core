package fr.proline.core.utils

import org.junit._
import org.junit.Assert._
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitSuite
import org.scalatest.junit.JUnitRunner
import fr.proline.core.utils.ms._

@RunWith(classOf[JUnitRunner])
@Test
class MsUtilsTest extends JUnitSuite {
  
  @Test
  def testMozToMass() = {    
    assert( mozToMass( 482.2, 1 ) === 481.19272353312 )
    assert( mozToMass( 482.2, 2 ) === 962.38544706624 )
    assert( mozToMass( 482.2, 3 ) === 1443.57817059936 )
  }
  

  @Test
  def testMassToMoz() = {
    assert( massToMoz( 481.19272353312, 1 ) === 482.2 )
    assert( massToMoz( 962.38544706624, 2 ) === 482.2 )
    assert( massToMoz( 1443.57817059936, 3 ) === 482.2 )
  }
  
  @Test
  def testCalcMozTolInDalton() = {    
    assert( calcMozTolInDalton( 482.2, 0.1, "Da" ) === 0.1 )
    assert( calcMozTolInDalton( 482.2, 10, "ppm" ) === 0.004822 )
  }
  
}
