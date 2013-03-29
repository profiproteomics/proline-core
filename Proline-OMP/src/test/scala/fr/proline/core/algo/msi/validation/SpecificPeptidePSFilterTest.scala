package fr.proline.core.algo.msi.validation

import org.junit.Test
import org.junit.Assert._
import org.scalatest.junit.JUnitSuite
import com.weiglewilczek.slf4s.Logging
import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.utils.generator.ResultSetFakeBuilder
import scala.collection.mutable.ListBuffer
import fr.proline.core.om.model.msi.Peptide
import fr.proline.core.om.model.msi.ProteinMatch
import org.junit.Before
import fr.proline.core.algo.msi.filtering.proteinset.SpecificPeptidesPSFilter
import fr.proline.core.algo.msi.scoring.MascotStandardScoreUpdater
import fr.proline.core.algo.msi.inference.CommunistProteinSetInferer

@Test
class SpecificPeptidePSFilterTest extends JUnitSuite with Logging {

  var ppsi = new CommunistProteinSetInferer()

  /**
   * P1 = (pep1, pep2, pep3,pep4, pep5)
   * P2 = (pep6, pep7, pep8,pep9, pep10)
   */
  @Test
  def simpleCheckWithGenData() = {
    var rs: ResultSet = new ResultSetFakeBuilder(pepNb = 10, proNb = 2).toResultSet()
    var rsu = ppsi.computeResultSummary(resultSet = rs)
    assert(rsu != null)
    assertEquals(2, rsu.peptideSets.length)
    assertEquals(2, rsu.proteinSets.length)
    val filter = new SpecificPeptidesPSFilter(minNbrPep = 1)
    filter.filterProteinSets(protSets = rsu.proteinSets, incrementalValidation = true, traceability = true)
    assertEquals(2, rsu.proteinSets.filter(_.isValidated).length)
  }

  /**
   * 5000 Prot avec 2 pep specifique chacunes
   */
  @Test
  def largerGenData() = {
    var rs: ResultSet = new ResultSetFakeBuilder(pepNb = 10000, proNb = 5000).toResultSet()
    var rsu = ppsi.computeResultSummary(resultSet = rs)
    assert(rsu != null)
    assertEquals(5000, rsu.peptideSets.length)
    assertEquals(5000, rsu.proteinSets.length)
    val filter = new SpecificPeptidesPSFilter(minNbrPep = 1)
    filter.filterProteinSets(protSets = rsu.proteinSets, incrementalValidation = true, traceability = true)
    assertEquals(5000, rsu.proteinSets.filter(_.isValidated).length)

  }

  /**
   * P1 = (pep1, pep2)
   * P2 = (pep3,pep4)
   * P3 = ( pep5,pep6)
   * P4= (pep1, pep3,pep5)
   */
  @Test
  def simpleCheckWithGenData4() = {
    val rsb = new ResultSetFakeBuilder(pepNb = 6, proNb = 3)
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
  def simpleCheckWithGenData5() = {
    val rsb = new ResultSetFakeBuilder(pepNb = 6, proNb = 3)
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
    scoring.updateScoreOfProteinSets(rsu)

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
  def simpleCheckWithGenData6() = {
    val rsb = new ResultSetFakeBuilder(pepNb = 2, proNb = 2)
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
    scoring.updateScoreOfProteinSets(rsu)

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
  def simpleCheckWithGenData7() = {
    val rsb = new ResultSetFakeBuilder(pepNb = 4, proNb = 2)

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
    scoring.updateScoreOfProteinSets(rsu)

    val filter = new SpecificPeptidesPSFilter(minNbrPep = 1)
    filter.filterProteinSets(protSets = rsu.proteinSets, incrementalValidation = true, traceability = true)
    assertEquals(3, rsu.proteinSets.filter(_.isValidated).length)

  }

}

