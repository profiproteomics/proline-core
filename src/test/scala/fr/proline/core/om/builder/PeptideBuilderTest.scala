package fr.proline.core.om.builder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

import fr.proline.core.om.model.msi.LocatedPtm

@Test
class PeptideBuilderTest {
  
  	  	 
  	@Test
    def testBuildMultiplePeptideIDs() = {	  
	  
	  val pep1 = PeptideBuilder.buildPeptide(seq = "STLLIR", locatedPtms = None, calcMass = 100  )
	  val pep2 = PeptideBuilder.buildPeptide(seq = "LEANK", locatedPtms = None, calcMass = 563.15  )
	  //NOTE: may not be OK if peptides have been created elsewhere in tests before this test is called!!!! 
	  assertEquals( -1, pep1.id)
	  assertEquals(-2, pep2.id)
	}

  	@Test
    def testBuildEmptyPtmPeptide() = {	  
	  val pep1 = PeptideBuilder.buildPeptide(seq = "STLLIR", locatedPtms = None , calcMass = 100  )
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
      	val pep1 = PeptideBuilder.buildPeptide(seq = "STLLIR", locatedPtms=Some(locatedPtm), calcMass = 100 )
      	
      	assertNotNull(pep1)
      	assertEquals("1[HA]", pep1.ptmString)
  	}  	

}