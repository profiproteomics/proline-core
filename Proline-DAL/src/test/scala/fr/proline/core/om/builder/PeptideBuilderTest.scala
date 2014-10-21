package fr.proline.core.om.builder

import org.junit.Assert._
import org.junit.Test

import fr.proline.core.om.model.msi.LocatedPtm
import fr.proline.core.om.model.msi.Peptide
import fr.profi.util.primitives.AnyMap

class PeptideBuilderTest {

  @Test
  def testBuildMultiplePeptideIDs() = {
    val pep1 = new Peptide( sequence = "STLLIR", ptms = null, calculatedMass = 100.0 )
    val pep2 = new Peptide( sequence = "LEANK", ptms = null, calculatedMass = 563.15)

    /* Just check we have two different Peptide.id here */
    assertNotEquals(pep1.id, pep2.id)
  }

  @Test
  def testBuildEmptyPtmPeptide() = {
    val pep1 = new Peptide(sequence = "STLLIR", ptms = null, calculatedMass = 100.0)
    assertNotNull(pep1)
  }

  @Test
  def testPtmStringGenerated() = {

    val ptmRecord = new AnyMap()
    ptmRecord += ("id" -> 1)
    ptmRecord += ("short_name" -> "phospho")
    ptmRecord += ("full_name" -> "phospho")
    ptmRecord += ("unimod_id" -> 1)
    

    val ptmSpecifRecord = new AnyMap()
    ptmSpecifRecord += ("residue" -> "A")
    ptmSpecifRecord += ("location" -> "Anywhere")

    val ptmEvidencePrecursor = new AnyMap()
    ptmEvidencePrecursor += ("type" -> "Precursor")
    ptmEvidencePrecursor += ("composition" -> "HA")
    ptmEvidencePrecursor += ("mono_mass" -> 10.0)
    ptmEvidencePrecursor += ("average_mass" -> 10.0)
    ptmEvidencePrecursor += ("is_required" -> true)

    val ptmDef = PtmDefinitionBuilder.buildPtmDefinition(
      ptmRecord = ptmRecord,
      ptmSpecifRecord = ptmSpecifRecord,
      ptmEvidenceRecords = Seq(ptmEvidencePrecursor),
      ptmClassification = ""
    )
    val ptmLoc = PtmDefinitionBuilder.buildLocatedPtm(ptmDef = ptmDef, seqPos = 1)
    val locatedPtms = new Array[LocatedPtm](1)
    locatedPtms.update(0, ptmLoc)
    val pep1 = new Peptide( sequence = "STLLIR", ptms = locatedPtms, calculatedMass = 100.0 )

    assertNotNull(pep1)
    assertEquals("1[HA]", pep1.ptmString)
  }

}