package fr.proline.core.om.msi

import org.junit._
import org.junit.Assert._
import org.junit.runner.RunWith
import org.scalatest.junit.{ JUnitRunner, JUnitSuite }

import fr.proline.core.om.model.msi.PeptideMatch
import fr.proline.core.om.model.msi.PeptideInstance
import fr.proline.core.om.model.msi.Peptide

@RunWith(classOf[JUnitRunner])
@Test
class PeptidesTest extends JUnitSuite {
  
  
	@Test
    def testGetPeptideMatchIDs() = {
	  
	  val pep1 = new Peptide( id = 0, sequence = "STLLIR", ptmString = "", ptms = null, calculatedMass = 100 )
	  val pepInst1 = new PeptideInstance( id = 0, peptide = pep1, peptideMatchIds = Array(1,2) )	  
	  val pepMatch1 = new PeptideMatch( id = 1, rank=1, score = 45.5f, scoreType="Mascot", deltaMoz=0.05, isDecoy= false, peptide=pep1,missedCleavage=1  )
	  val pepMatch2 = new PeptideMatch( id = 2, rank=2, score = 13.21f, scoreType="Mascot", deltaMoz=0.15, isDecoy= false, peptide=pep1,missedCleavage=1 )
	  
	  assertEquals(pepInst1.getPeptideMatchIds.length, 2);
	}
	
	@Test
    def testGetPeptideMatchIDs2() = {
	  	  
	  val pep1 = new Peptide( id = 0, sequence = "STLLIR", ptmString = "", ptms = null, calculatedMass = 100 )	 	 
	  val pepMatch1 = new PeptideMatch( id = 1, rank=1, score = 45.5f, scoreType="Mascot", deltaMoz=0.05, isDecoy= false, peptide=pep1, missedCleavage=1  )
	  val pepMatch2 = new PeptideMatch( id = 2, rank=2, score = 13.21f, scoreType="Mascot", deltaMoz=0.15, isDecoy= false, peptide=pep1, missedCleavage=1 )
	  val pepInst1 = new PeptideInstance( id = 0, peptide = pep1, peptideMatches = Array(pepMatch1, pepMatch2) )
	  
	  assertEquals(pepInst1.getPeptideMatchIds.length, 2);
	}
}