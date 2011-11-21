package fr.proline.core.om.msi

import org.junit._
import Assert._
import fr.proline.core.om.msi.PeptideClasses._

@Test
class PeptidesTest {
  
  
	@Test
    def testOK() = {
	  
	  
	  val pep1 = new Peptide( id = 0, sequence = "STLLIR", ptmString = "", ptms = null, calculatedMass = 100 ,missedCleavage=1 )
	  val pepInst1 = new PeptideInstance( id = 0, peptide = pep1, peptideMatchIds = Array(1,2) )	  
	  val pepMatch1 = new PeptideMatch( id = 1, rank=1, score = 45.5f, scoreType="Mascot", deltaMz=0.05f, isDecoy= false, peptide=pep1 , msQueryId =1 )
	  val pepMatch2 = new PeptideMatch( id = 2, rank=2, score = 13.21f, scoreType="Mascot", deltaMz=0.15f, isDecoy= false, peptide=pep1 ,  msQueryId = 569)
	  
	  assertEquals(pepInst1.getPeptideMatchIds.length, 2);
	}
}