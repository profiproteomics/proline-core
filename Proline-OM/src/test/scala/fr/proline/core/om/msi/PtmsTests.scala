package fr.proline.core.om.msi

import org.junit._
import org.junit.Assert._
import org.junit.runner.RunWith
import org.scalatest.junit.{ JUnitRunner, JUnitSuite }
import fr.proline.core.om.model.msi._
import com.typesafe.scalalogging.StrictLogging

@Test
class PtmsTest extends JUnitSuite with StrictLogging {
  
	@Test
  def testToReadablePtmString() = {
	  
	  val oxidation = PtmDefinition(
      id = -1,
      location = PtmLocation.ANYWHERE.toString(),
      names = PtmNames("Oxidation","Oxidation"),
      ptmEvidences = Array(
        PtmEvidence(
         ionType = IonTypes.Precursor,
         composition = "O",
         monoMass = 16.0,
         averageMass= 16.0
       )
     ),
     residue = 'M'
	  )
	  val nTermOxidation = oxidation.copy( location = PtmLocation.ANY_N_TERM.toString() )
	  
	  assertEquals(oxidation.toReadableString(), "Oxidation (M)")
	  assertEquals(nTermOxidation.toReadableString(), "Oxidation (Any N-term M)")
	  
	  val locatedOxidation = LocatedPtm(
	    definition = oxidation,
	    seqPosition = 0,
	    monoMass = oxidation.precursorDelta.monoMass,
	    averageMass = oxidation.precursorDelta.averageMass,
	    composition = oxidation.precursorDelta.composition,
	    isNTerm = false,
	    isCTerm = false
	  )
	  
	  val locatedNtermOxidation = locatedOxidation.copy( definition = nTermOxidation, isNTerm = true )
	  
	  assertEquals(locatedOxidation.toReadableString(), "Oxidation (M0)")
	  assertEquals(locatedNtermOxidation.toReadableString(), "Oxidation (Any N-term)")
	}
	
}
