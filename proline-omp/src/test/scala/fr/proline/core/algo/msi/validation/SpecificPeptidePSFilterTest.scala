package fr.proline.core.algo.msi.validation

import scala.collection.mutable.ListBuffer
import org.junit.Assert._
import org.junit.Test
import org.scalatest.junit.JUnitSuite
import com.typesafe.scalalogging.StrictLogging
import fr.proline.core.algo.msi.filtering.proteinset.SpecificPeptidesPSFilter
import fr.proline.core.algo.msi.inference.ParsimoniousProteinSetInferer
import fr.proline.core.algo.msi.scoring.MascotStandardScoreUpdater
import fr.proline.core.algo.msi.validation.pepinstance.BasicPepInstanceBuilder
import fr.proline.core.om.model.msi.Peptide
import fr.proline.core.om.model.msi.ProteinMatch
import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.util.generator.msi.ResultSetFakeGenerator

class SpecificPeptidePSFilterTest extends JUnitSuite with StrictLogging {

  val ppsi = new ParsimoniousProteinSetInferer(new BasicPepInstanceBuilder())

  /**
   * P1 = (pep1, pep2, pep3,pep4, pep5)
   * P2 = (pep6, pep7, pep8,pep9, pep10)
   */
  @Test
  def simpleCheckWithGenData() {
    var rs: ResultSet = new ResultSetFakeGenerator(nbPeps = 10, nbProts = 2).toResultSet()
    var rsu = ppsi.computeResultSummary(resultSet = rs)
    assert(rsu != null)
    assertEquals(2, rsu.peptideSets.length)
    assertEquals(2, rsu.proteinSets.length)
    val filter = new SpecificPeptidesPSFilter(minNbrPep = 1)
    filter.filterProteinSets(protSets = rsu.proteinSets, incrementalValidation = true, traceability = true)
    assertEquals(2, rsu.proteinSets.filter(_.isValidated).length)
  }

  /**
   * 500 Prot having 2 specific peptides
   */
  @Test
  def largerGenData() {
    var rs: ResultSet = new ResultSetFakeGenerator(nbPeps = 1000, nbProts = 500).toResultSet()
    var rsu = ppsi.computeResultSummary(resultSet = rs)
    assert(rsu != null)
    assertEquals(500, rsu.peptideSets.length)
    assertEquals(500, rsu.proteinSets.length)
    val filter = new SpecificPeptidesPSFilter(minNbrPep = 1)
    filter.filterProteinSets(protSets = rsu.proteinSets, incrementalValidation = true, traceability = true)
    assertEquals(500, rsu.proteinSets.filter(_.isValidated).length)

  }

  /**
   * P1 = (pep1, pep2)
   * P2 = (pep3,pep4)
   * P3 = ( pep5,pep6)
   * P4= (pep1, pep3,pep5)
   */
  @Test
  def simpleCheckWithGenData4() {
    val rsb = new ResultSetFakeGenerator(nbPeps = 6, nbProts = 3)
    var sharedPeptides2 = ListBuffer[Peptide]()
    for ((proSeq, peptides) <- rsb.allPepsByProtSeq) {
      sharedPeptides2 += peptides(0)
    }

    rsb.createNewProteinMatchFromPeptides(sharedPeptides2)

    rsb.printForDebug

    var rs: ResultSet = rsb.toResultSet()
    var rsu = ppsi.computeResultSummary(resultSet = rs)
    assert(rsu != null)
    assertEquals(4, rsu.peptideSets.length)
    assertEquals(4, rsu.proteinSets.length)
    val filter = new SpecificPeptidesPSFilter(minNbrPep = 1)

    filter.filterProteinSets(protSets = rsu.proteinSets, incrementalValidation = true, traceability = true)
    assertEquals(3, rsu.proteinSets.filter(_.isValidated).length)

  }

  /**
   * 5 Prot Matches : aucun pep specifique
   * P1 = (pep1, pep2)
   * P2 = (pep3,pep4)
   * P3 = ( pep5,pep6)
   *
   * P4= (pep1, pep3)
   * P5= (pep2,pep5)
   * P5= (pep4,pep6)
   */
  @Test
  def simpleCheckWithGenData5() {
    val rsb = new ResultSetFakeGenerator(nbPeps = 6, nbProts = 3)
    var sharedPeptides = ListBuffer[Peptide]()

    sharedPeptides += rsb.allProtMatches(0).sequenceMatches(0).bestPeptideMatch.get.peptide
    sharedPeptides += rsb.allProtMatches(1).sequenceMatches(0).bestPeptideMatch.get.peptide

    rsb.createNewProteinMatchFromPeptides(sharedPeptides)

    sharedPeptides(0) = rsb.allProtMatches(0).sequenceMatches(1).bestPeptideMatch.get.peptide
    sharedPeptides(1) = rsb.allProtMatches(2).sequenceMatches(0).bestPeptideMatch.get.peptide
    rsb.createNewProteinMatchFromPeptides(sharedPeptides)

    sharedPeptides(0) = rsb.allProtMatches(1).sequenceMatches(1).bestPeptideMatch.get.peptide
    sharedPeptides(1) = rsb.allProtMatches(2).sequenceMatches(1).bestPeptideMatch.get.peptide
    rsb.createNewProteinMatchFromPeptides(sharedPeptides)

    //	  rsb.printForDebug  

    var rs: ResultSet = rsb.toResultSet()
    var rsu = ppsi.computeResultSummary(resultSet = rs)
    assert(rsu != null)
    assertEquals(6, rsu.peptideSets.length)
    assertEquals(6, rsu.proteinSets.length)

    //    val pepMatchesByProtMatch =  rsb.allProtMatches.map{pm =>(pm, pm.sequenceMatches.flatMap(sm => sm.bestPeptideMatch))}.toMap
    //    
    //    pepMatchesByProtMatch.foreach(entry => {
    //      var score = 0f
    //      entry._2.foreach(pm => score += pm.score)
    //      entry._1.score = score
    //    })
    val scoring = new MascotStandardScoreUpdater()
    scoring.updateScoreOfPeptideSets(rsu)

    val filter = new SpecificPeptidesPSFilter(minNbrPep = 1)

    filter.filterProteinSets(protSets = rsu.proteinSets, incrementalValidation = true, traceability = true)
    assertTrue(rsu.proteinSets.filter(_.isValidated).length < 5)

  }

  /**
   * Triangles Prot Matches : aucun pep specifique
   * P1 = (pep1, pep3)
   * P2 = (pep2, pep3)
   * P3 = (pep1, pep2)
   *
   */

  @Test
  def simpleCheckWithGenData6() {
    val rsb = new ResultSetFakeGenerator(nbPeps = 2, nbProts = 2)
    var sharedPeptides = ListBuffer[Peptide]()

    sharedPeptides += rsb.allProtMatches(0).sequenceMatches(0).bestPeptideMatch.get.peptide
    sharedPeptides += rsb.allProtMatches(1).sequenceMatches(0).bestPeptideMatch.get.peptide

    rsb.addSharedPeptide(Seq(rsb.allProtMatches(0), rsb.allProtMatches(1)))

    rsb.createNewProteinMatchFromPeptides(sharedPeptides)

    //	  rsb.printForDebug  

    var rs: ResultSet = rsb.toResultSet()
    var rsu = ppsi.computeResultSummary(resultSet = rs)
    assert(rsu != null)
    assertEquals(3, rsu.peptideSets.length)
    assertEquals(3, rsu.proteinSets.length)

    val scoring = new MascotStandardScoreUpdater()
    scoring.updateScoreOfPeptideSets(rsu)

    val filter = new SpecificPeptidesPSFilter(minNbrPep = 1)

    filter.filterProteinSets(protSets = rsu.proteinSets, incrementalValidation = true, traceability = true)
    assertEquals(2, rsu.proteinSets.filter(_.isValidated).length)

  }

  /**
   * 2 ProtSet wo specific pepMatches
   * P1 = (pep1, pep2)
   * P2 = (pep3, pep4)
   *
   * P3 = (pep1, pep5)
   * P4 = (pep3, pep5)
   *
   */
  @Test
  def simpleCheckWithGenData7() {
    val rsb = new ResultSetFakeGenerator(nbPeps = 4, nbProts = 2)

    rsb.createNewProteinMatchFromPeptides(Seq(rsb.allProtMatches(0).sequenceMatches(0).bestPeptideMatch.get.peptide))
    rsb.createNewProteinMatchFromPeptides(Seq(rsb.allProtMatches(1).sequenceMatches(0).bestPeptideMatch.get.peptide))

    var pms = ListBuffer[ProteinMatch]()

    for (pm <- rsb.allProtMatches) {
      if (pm.sequenceMatches.length == 1) pms += pm
    }
    rsb.addSharedPeptide(pms)

    //    rsb.printForDebug  

    var rs: ResultSet = rsb.toResultSet()
    var rsu = ppsi.computeResultSummary(resultSet = rs)
    assert(rsu != null)
    assertEquals(4, rsu.peptideSets.length)
    assertEquals(4, rsu.proteinSets.length)

    val scoring = new MascotStandardScoreUpdater()
    scoring.updateScoreOfPeptideSets(rsu)

    val filter = new SpecificPeptidesPSFilter(minNbrPep = 1)
    filter.filterProteinSets(protSets = rsu.proteinSets, incrementalValidation = true, traceability = true)
    assertEquals(3, rsu.proteinSets.filter(_.isValidated).length)

  }

}

