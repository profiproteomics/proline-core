package fr.proline.core.algo.msi.validation

import org.junit.Test
import org.scalatest.junit.JUnitSuite
import com.weiglewilczek.slf4s.Logging
import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.om.model.msi.ResultSummary
import org.junit.Assert

@Test
class MascotValidationHelperTest extends JUnitSuite with Logging {
	
	@Test
	def calcIdentityThreshold() = {
	  var threshold = MascotValidationHelper.calcIdentityThreshold(47, 0.05)
	  assert(Math.abs(threshold - 16.720978) < 0.001)
	  threshold = MascotValidationHelper.calcIdentityThreshold(47, 0.01)
	  assert(Math.abs(threshold - 23.7106f) < 0.001)
	}

	 @Test
	 def caclCandidateCount()= {
	   var nbCandidates = Math.round(MascotValidationHelper.calcCandidatePeptidesCount(16.720978f, 0.05))
	   expect (47) { nbCandidates }
	   nbCandidates = Math.round(MascotValidationHelper.calcCandidatePeptidesCount(23.7106f, 0.01))
	   expect (47) { nbCandidates }
	 }

	 @Test
	 def caclHomologyThreshold()= {
	 
	  val probValue = MascotValidationHelper.calcCandidatePeptidesCount( 13.213378f, 0.05 )
     var ht = MascotValidationHelper.calcIdentityThreshold( probValue, 0.05 ) 
	  assert(Math.abs(ht - 13.213378) < 0.001)
     
	  ht = MascotValidationHelper.calcIdentityThreshold( probValue, 0.01 ) 
	  assert(Math.abs(ht - 20.20307) < 0.001)
     
	 }
	 
	 @Test
	def calcPValue() = {
	  var pVal = MascotValidationHelper.calcProbability(16.720978f, 47)
	  Assert.assertEquals(0.05f, pVal,0.000001f)
	  pVal = MascotValidationHelper.calcProbability(23.7106f, 47)
	   Assert.assertEquals(0.01, pVal,0.001f)
	}


}

