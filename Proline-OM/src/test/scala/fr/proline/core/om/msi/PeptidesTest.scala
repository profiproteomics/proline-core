package fr.proline.core.om.msi

import org.junit._
import org.junit.Assert._
import org.junit.runner.RunWith
import org.scalatest.junit.{ JUnitRunner, JUnitSuite }
import fr.proline.core.om.model.msi._
import com.typesafe.scalalogging.StrictLogging
import fr.proline.core.util.DigestionUtils

@Test
class PeptidesTest extends JUnitSuite with StrictLogging {
  
	@Test
    def testGetPeptideMatchIDs() = {
	  
	  val pep1 = new Peptide( id = 0, sequence = "STLLIR", ptmString = "", ptms = null, calculatedMass = 100 )
	  val pepInst1 = new PeptideInstance( id = 0, peptide = pep1, peptideMatchIds = Array(1,2) )	  
	  val pepMatch1 = new PeptideMatch( id = 1, rank=1, score = 45.5f, scoreType=PeptideMatchScoreType.MASCOT_IONS_SCORE, charge = 2, deltaMoz=0.05f, isDecoy= false, peptide=pep1,missedCleavage=1  )
	  val pepMatch2 = new PeptideMatch( id = 2, rank=2, score = 13.21f, scoreType=PeptideMatchScoreType.MASCOT_IONS_SCORE, charge = 2, deltaMoz=0.15f, isDecoy= false, peptide=pep1,missedCleavage=1 )
	  
	  assertEquals(pepInst1.getPeptideMatchIds.length, 2);
	}
	
	@Test
    def testGetPeptideMatchIDs2() = {
	  	  
	  val pep1 = new Peptide( id = 0, sequence = "STLLIR", ptmString = "", ptms = null, calculatedMass = 100 )	 	 
	  val pepMatch1 = new PeptideMatch( id = 1, rank=1, score = 45.5f, scoreType=PeptideMatchScoreType.MASCOT_IONS_SCORE, charge = 2, deltaMoz=0.05f, isDecoy= false, peptide=pep1, missedCleavage=1  )
	  val pepMatch2 = new PeptideMatch( id = 2, rank=2, score = 13.21f, scoreType=PeptideMatchScoreType.MASCOT_IONS_SCORE, charge = 2, deltaMoz=0.15f, isDecoy= false, peptide=pep1, missedCleavage=1 )
	  val pepInst1 = new PeptideInstance( id = 0, peptide = pep1, peptideMatches = Array(pepMatch1, pepMatch2) )
	  
	  assertEquals(pepInst1.getPeptideMatchIds.length, 2);
	}
	
	@Test
	def testCountingOfMissedCleavages() = {
	  // prepare enzymes cleavage sites
	  val ecAspn = new EnzymeCleavage(id = -1, site = "N-term", residues = "BD", restrictiveResidues = Some(""))
	  val ecTryp = new EnzymeCleavage(id = -2, site = "C-term", residues = "KR", restrictiveResidues = Some("P"))
	  val ecLysc = new EnzymeCleavage(id = -3, site = "C-term", residues = "K", restrictiveResidues = Some("P"))
	  // prepare enzymes
	  val aspn = new Enzyme(id = -1, name = "Asp-N", enzymeCleavages = Array(ecAspn), isIndependant = false) // BD
	  val trypsin = new Enzyme(id = -2, name = "Trypsin", enzymeCleavages = Array(ecTryp), isIndependant = false) // KR
	  val lysc_aspn = new Enzyme(id = -3, name = "LysC+AspN", enzymeCleavages = Array(ecLysc, ecAspn), isIndependant = false) // KBD
	  val lysc_aspn_dep = new Enzyme(id = -4, name = "LysC+AspN (ind)", enzymeCleavages = Array(ecLysc, ecAspn), isIndependant = true) // KBD

	  assertEquals(countMC("R.AAAAAAAR.A", trypsin, 0), 0)
	  assertEquals(countMC("R.AAAKAAAR.A", trypsin, 1), 1) // K(4)
	  assertEquals(countMC("R.AAAKPAAR.A", trypsin, 0), 0)
	  assertEquals(countMC("R.BAAAAAAA.A", aspn, 0), 0)
	  assertEquals(countMC("R.BAAAAAAD.A", aspn, 1), 1) // D(8)
	  assertEquals(countMC("R.BAAABAAD.A", aspn, 2), 2) // B(5), D(8)
	  assertEquals(countMC("A.BAAAAAAA.D", lysc_aspn, 0), 0)
	  assertEquals(countMC("A.BABAAADA.D", lysc_aspn, 2), 2) // B(3), D(7)
	  assertEquals(countMC("A.BABAAKDA.D", lysc_aspn, 3), 3) // K(6), B(3), D(7)
	  assertEquals(countMC("A.BAADAAAA.D", lysc_aspn_dep, 1), 1) // D(4)
	  assertEquals(countMC("K.ABADKAAK.M", lysc_aspn_dep, 1), 1) // K(5)
	  assertEquals(countMC("K.ABADKPAK.M", lysc_aspn_dep, 0), 0)
	  assertEquals(countMC("K.BAAAAAAK.B", lysc_aspn_dep, 0), 0)
	  assertEquals(countMC("K.BAADAAAK.B", lysc_aspn_dep, 1), 1) // D(4)
	  assertEquals(countMC("_.AAAKAAAA.B", lysc_aspn_dep, 0), 0)
	  assertEquals(countMC("_.ABAKAAAA.B", lysc_aspn_dep, 1), 1) // B(2)
	  assertEquals(countMC("_.ABADAAAK.M", lysc_aspn_dep, 0), 0)
	  assertEquals(countMC("_.ABAKAKAK.M", lysc_aspn_dep, 2), 2) // K(4), K(6)
	  assertEquals(countMC("_.ABAKAKPK.M", lysc_aspn_dep, 1), 1) // K(4)
	  assertEquals(countMC("M.BAAKAAAA._", lysc_aspn_dep, 0), 0)
	  assertEquals(countMC("M.BAAKADAA._", lysc_aspn_dep, 1), 1) // D(6)
	  assertEquals(countMC("K.ABAAAAAA._", lysc_aspn_dep, 0), 0)
	  assertEquals(countMC("K.ABAKAAKA._", lysc_aspn_dep, 2), 2) // K(4), K(7)
	  assertEquals(countMC("_.AAAAAAAK.B", lysc_aspn_dep, 0), 0)
	  assertEquals(countMC("_.AKAAAAAK.B", lysc_aspn_dep, 1), 1) // K(2)
	  assertEquals(countMC("_.AKABAADK.B", lysc_aspn_dep, 2), 2) // B(4), D(7)
	  assertEquals(countMC("K.DAAAAAAA._", lysc_aspn_dep, 0), 0)
	  assertEquals(countMC("K.DAABAAAA._", lysc_aspn_dep, 1), 1) // B(4)
	  assertEquals(countMC("K.DAABKPKA._", lysc_aspn_dep, 1), 1) // K(7) or B(4)
	  assertEquals(countMC("K.DAABKAKA._", lysc_aspn_dep, 2), 2) // K(5), K(7)

	}
	
	private def countMC(fullSequence: String, enzyme: Enzyme, expectedMC: Int): Int = {
	  val items = fullSequence.split('.')
	  val beforeOpt: Option[Char] = if(items.head == "_") None else Some(items.head.charAt(0))
	  val afterOpt: Option[Char] = if(items.last == "_") None else Some(items.last.charAt(0))
	  val mc = PeptideMatch.countMissedCleavages(items(1), beforeOpt, afterOpt, Array(enzyme))
	  //logger.debug("ABU "+beforeOpt.getOrElse("^")+"."+items(1)+"."+afterOpt.getOrElse("$")+" => "+mc+"/"+expectedMC+" missed cleavage(s) with "+enzyme.name + "[" + enzyme.enzymeCleavages.map(_.toString).mkString(" ") + " independant="+enzyme.isIndependant + " semiSpecific="+enzyme.isSemiSpecific + "]")
	  mc
	}

  @Test 
  def testDigestion() = {
    // prepare enzymes cleavage sites
	  val ecAspn = new EnzymeCleavage(id = -1, site = "N-term", residues = "BD", restrictiveResidues = Some(""))
	  val ecTryp = new EnzymeCleavage(id = -2, site = "C-term", residues = "KR", restrictiveResidues = Some("P"))
	  val ecLysc = new EnzymeCleavage(id = -3, site = "C-term", residues = "K", restrictiveResidues = Some("P"))
	  // prepare enzymes
	  val aspn = new Enzyme(id = -1, name = "Asp-N", enzymeCleavages = Array(ecAspn), isIndependant = false) // BD
	  val trypsin = new Enzyme(id = -2, name = "Trypsin", enzymeCleavages = Array(ecTryp), isIndependant = false) // KR
	  val lysc_aspn = new Enzyme(id = -3, name = "LysC+AspN", enzymeCleavages = Array(ecLysc, ecAspn), isIndependant = false) // KBD
	  val lysc_aspn_dep = new Enzyme(id = -4, name = "LysC+AspN (ind)", enzymeCleavages = Array(ecLysc, ecAspn), isIndependant = true) // KBD
	  
    val albu = "MKWVTFISLLFLFSSAYSRGVFRRDAHKSEVAHRFKDLGEENFKALVLIAFAQYLQQCPFEDHVKLVNEVTEFAKTCVADESAENCDKSLHTLFGDKLCTVATLRETYGEMADCCAKQEPERNECFLQHKDDNPNLPRLVRPEVDVMCTAFHDNEETFLKKYLYEIARRHPYFYAPELLFFAKRYKAAFTECCQAADKAACLLPKLDELRDEGKASSAKQRLKCASLQKFGERAFKAWAVARLSQRFPKAEFAEVSKLVTDLTKVHTECCHGDLLECADDRADLAKYICENQDSISSKLKECCEKPLLEKSHCIAEVENDEMPADLPSLAADFVESKDVCKNYAEAKDVFLGMFLYEYARRHPDYSVVLLLRLAKTYETTLEKCCAAADPHECYAKVFDEFKPLVEEPQNLIKQNCELFEQLGEYKFQNALLVRYTKKVPQVSTPTLVEVSRNLGKVGSKCCKHPEAKRMPCAEDYLSVVLNQLCVLHEKTPVSDRVTKCCTESLVNRRPCFSALEVDETYVPKEFNAETFTFHADICTLSEKERQIKKQTALVELVKHKPKATKEQLKAVMDDFAAFVEKCCKADDKETCFAEEGKKLVAASQAALGL"
    
    var cl = DigestionUtils.getCleavagePositions(albu, trypsin)
    logger.info(trypsin.name + " peptides Count = " + cl.length)
    logger.info(trypsin.name + " peptides = " + cl.mkString(","))    
    logger.info(trypsin.name + " observable = "+DigestionUtils.getObservablePeptidesCount(albu, trypsin))
    
    cl = DigestionUtils.getCleavagePositions(albu, aspn)
    logger.info(aspn.name + " peptides Count = " + cl.length)

    cl = DigestionUtils.getCleavagePositions(albu, lysc_aspn)
    logger.info(lysc_aspn.name + " peptides Count = " + cl.length)

  }
}
