package fr.proline.core.om.builder

import org.junit._
import Assert._
import _root_.fr.proline.core.om.msi.PeptideClasses._
import fr.proline.core.om.msi.PtmClasses.LocatedPtm

@Test
class PeptideBuilderTest {
  

  	@Test
    def testBuildEmptyPtmPeptide() = {	  
	  val pep1 = PeptideBuilder.buildPeptide(seq = "STLLIR", locatedPtms=new Array[LocatedPtm](0) , calcMass = 100  )
	  assertNotNull(pep1)
  	}
  	
  	@Test
    def testBuildNullPtmPeptide() = {	  
	  val pep1 = PeptideBuilder.buildPeptide(seq = "STLLIR", locatedPtms=null , calcMass = 100  )
	  assertNotNull(pep1)
  	}

  	@Test
    def testPtmStringGenerated() = {	  
  		var ptmRecord = scala.collection.immutable.Map.newBuilder[String,Any]
      	ptmRecord += ("id" -> 1) 
      	ptmRecord += ("short_name" -> "phospho")
      	ptmRecord += ("full_name" -> "phospho")
      	  
      	var ptmSpecifRecord = scala.collection.immutable.Map.newBuilder[String,Any]
      	ptmSpecifRecord += ("residue" -> "A")
      	ptmSpecifRecord += ("location" -> "Anywhere")
      	        	
      	var ptmEvidencePrecursor = scala.collection.immutable.Map.newBuilder[String,Any]
      	ptmEvidencePrecursor += ("type" -> "Precursor")
      	ptmEvidencePrecursor += ("composition" -> "HA")
      	ptmEvidencePrecursor += ("mono_mass" -> 10.0)
      	ptmEvidencePrecursor += ("average_mass" -> 10.0)
      	ptmEvidencePrecursor += ("is_required" -> true)
      	
      	        	
      	val ptmDef = PtmDefinitionBuilder.buildPtmDefinition(ptmRecord = ptmRecord.result(),ptmSpecifRecord = ptmSpecifRecord.result(), ptmEvidenceRecords = Seq[Map[String,Any]](ptmEvidencePrecursor.result()),ptmClassification = "" )
      	val ptmLoc = PtmDefinitionBuilder.buildLocatedPtm(ptmDef= ptmDef , seqPos=1)  
      	val locatedPtm = new Array[LocatedPtm](1)
      	locatedPtm.update(0,ptmLoc)
      	val pep1 = PeptideBuilder.buildPeptide(seq = "STLLIR", locatedPtms=locatedPtm , calcMass = 100  )
      	assertNotNull(pep1)
      	assertEquals("1(A)", pep1.ptmString)
  	}
  	  	 
  	@Test
    def testBuildMultiplePeptideIDs() = {	  
	  
	  val pep1 = PeptideBuilder.buildPeptide(seq = "STLLIR", locatedPtms=new Array[LocatedPtm](0) , calcMass = 100  )
	  val pep2 = PeptideBuilder.buildPeptide(seq = "LEANK", locatedPtms=null , calcMass = 563.15  )	  
	  assertEquals(pep1 id , -1);
	  assertEquals(pep2 id , -2);
	}

}