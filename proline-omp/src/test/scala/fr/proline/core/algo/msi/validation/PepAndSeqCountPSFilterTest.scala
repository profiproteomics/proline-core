package fr.proline.core.algo.msi.validation

import fr.proline.core.util.generator.msi.ResultSetFakeGenerator
import fr.proline.core.algo.msi.filtering.proteinset.SpecificPeptidesPSFilter
import fr.proline.core.algo.msi.inference.ParsimoniousProteinSetInferer
import fr.proline.core.om.model.msi.ResultSet
import org.junit.Test
import org.junit.Assert._

import scala.collection.mutable.ListBuffer
import fr.proline.core.om.model.msi.Peptide
import fr.proline.core.algo.msi.filtering.proteinset.PeptidesCountPSFilter
import fr.proline.core.algo.msi.filtering.proteinset.PepSequencesCountPSFilter
import fr.proline.core.algo.msi.validation.pepinstance.BasicPepInstanceBuilder

class PepAndSeqCountPSFilterTest {
 
  val ppsi = new ParsimoniousProteinSetInferer(new BasicPepInstanceBuilder())
 
  /**
   * P1 = (pep1, pep2, pep3,pep4)
   * P2 = (pep5, pep6, pep7, pep8)
   * P3 = (pep1, pep5)
   */
  @Test
  def testPepCountFilter() {
    val rsb = new ResultSetFakeGenerator(nbPeps = 8, nbProts = 2)
    
    var sharedPeptides = ListBuffer[Peptide]()

    sharedPeptides += rsb.allProtMatches(0).sequenceMatches(0).bestPeptideMatch.get.peptide
    sharedPeptides += rsb.allProtMatches(1).sequenceMatches(0).bestPeptideMatch.get.peptide

    rsb.createNewProteinMatchFromPeptides(sharedPeptides)
    
    var rs: ResultSet = rsb.toResultSet()
    var rsu = ppsi.computeResultSummary(resultSet = rs)
    assert(rsu != null)
    assertEquals(3, rsu.peptideSets.length)
    assertEquals(3, rsu.proteinSets.length)
    
    val filter = new PeptidesCountPSFilter(minNbrPep = 3)
    filter.filterProteinSets(protSets = rsu.proteinSets, incrementalValidation = true, traceability = true)
    assertEquals(2, rsu.proteinSets.filter(_.isValidated).length)
  }
  
 /**
   * P1 = (pep1, pep2, pep3,pep4)
   * P2 = (pep5, pep6, pep7, pep8)
   * P3 = (pep1, pep5)
   */
  @Test
  def testPepSeqCountFilter() {
    val rsb = new ResultSetFakeGenerator(nbPeps = 8, nbProts = 2)
    
    var sharedPeptides = ListBuffer[Peptide]()

    sharedPeptides += rsb.allProtMatches(0).sequenceMatches(0).bestPeptideMatch.get.peptide
    sharedPeptides += rsb.allProtMatches(1).sequenceMatches(0).bestPeptideMatch.get.peptide

    rsb.createNewProteinMatchFromPeptides(sharedPeptides)
    
    var rs: ResultSet = rsb.toResultSet()
    var rsu = ppsi.computeResultSummary(resultSet = rs)
    assert(rsu != null)
    assertEquals(3, rsu.peptideSets.length)
    assertEquals(3, rsu.proteinSets.length)
    
    val filter = new PepSequencesCountPSFilter(minNbrSeq = 3)
    filter.filterProteinSets(protSets = rsu.proteinSets, incrementalValidation = true, traceability = true)
    assertEquals(2, rsu.proteinSets.filter(_.isValidated).length)
  }
}