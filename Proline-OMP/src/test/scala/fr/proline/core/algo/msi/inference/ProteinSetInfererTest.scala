package fr.proline.core.algo.msi.inference

import scala.collection.mutable.ListBuffer

import org.junit.Assert._

import org.junit.Test
import org.scalatest.junit.JUnitSuite

import com.typesafe.scalalogging.slf4j.Logging

import fr.proline.core.om.model.msi.Peptide
import fr.proline.core.om.model.msi.ProteinMatch
import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.util.generator.msi.ResultSetFakeGenerator

class ParsimoniousProteinSetInfererTest extends Logging {

  val ppsi = new ParsimoniousProteinSetInferer()

  /**
   * P1 = (pep1, pep2, pep3,pep4, pep5)
   * P2 = (pep6, pep7, pep8,pep9, pep10)
   */
  @Test
  def simpleCheckWithGenData() = {
    val rs: ResultSet = new ResultSetFakeGenerator(nbPeps = 10, nbProts = 2).toResultSet()
    val rsm = ppsi.computeResultSummary(resultSet = rs)
    assert(rsm != null)
    assertEquals(2, rsm.peptideSets.length)
    assertEquals(2, rsm.proteinSets.length)
  }

  /**
   * 500 Prot having 2 specific peptides
   */
  @Test
  def largerGenData() = {
    val rs: ResultSet = new ResultSetFakeGenerator(nbPeps = 1000, nbProts = 500).toResultSet()
    val rsm = ppsi.computeResultSummary(resultSet = rs)
    assert(rsm != null)
    assertEquals(500, rsm.peptideSets.length)
    assertEquals(500, rsm.proteinSets.length)
  }

  @Test
  def simpleCheckWithGenData2() = {
    val rsb = new ResultSetFakeGenerator(nbPeps = 10, nbProts = 2)
    rsb.addSharedPeptide(rsb.allProtMatches)
    val rs: ResultSet = rsb.toResultSet()
    val rsm = ppsi.computeResultSummary(resultSet = rs)
    assert(rsm != null)
    assertEquals(2, rsm.peptideSets.length)
    assertEquals(2, rsm.proteinSets.length)
  }

  /**
   * P1 = (pep1, pep2)
   * P2 = (pep3,pep4)
   * P3 = ( pep5,pep6)
   * P4 = (pep1, pep2,pep3,pep4, pep5,pep6)
   */
  @Test
  def simpleCheckWithGenData3() = {
    val rsb = new ResultSetFakeGenerator(nbPeps = 6, nbProts = 3)
    rsb.createNewProteinMatchFromPeptides(rsb.allPeps)
    val rs: ResultSet = rsb.toResultSet()
    val rsm = ppsi.computeResultSummary(resultSet = rs)
    assert(rsm != null)
    assertEquals(3 + 1, rsm.peptideSets.length)
    // 1 = because the added prot is a superset
    assertEquals(1, rsm.proteinSets.length)
    assertEquals("ProteinMatches related to ProteinSet should contain sameset and subset", 4, rsm.proteinSets(0).getProteinMatchIds.length)
  }
  
  /**
   * P1 = (pep1, pep2)
   * P2 = (pep3,pep4)
   * P3 = ( pep5,pep6)
   * P4= (pep1, pep3,pep5)
   */
  @Test
  def simpleCheckWithGenData4WithSubsummableSubsets() = {
    val rsb = new ResultSetFakeGenerator(nbPeps = 6, nbProts = 3)
    val sharedPeptides2 = ListBuffer[Peptide]()
    for ((proSeq, peptides) <- rsb.allPepsByProtSeq) {
      sharedPeptides2 += peptides(0)
    }

    rsb.createNewProteinMatchFromPeptides(sharedPeptides2)

    rsb.printForDebug

    val rs: ResultSet = rsb.toResultSet()
    val rsm = ppsi.computeResultSummary(resultSet = rs, keepSubsummableSubsets = true )
    assert(rsm != null)
    assertEquals(4, rsm.peptideSets.length)
    assertEquals(4, rsm.proteinSets.length)
  }
  
  @Test
  def simpleCheckWithGenData4WithoutSubsummableSubsets() = {
    val rsb = new ResultSetFakeGenerator(nbPeps = 6, nbProts = 3)
    val sharedPeptides2 = ListBuffer[Peptide]()
    for ((proSeq, peptides) <- rsb.allPepsByProtSeq) {
      sharedPeptides2 += peptides(0)
    }

    rsb.createNewProteinMatchFromPeptides(sharedPeptides2)

    //    val sharedPeptides = Seq(rsb.allProtMatches(0).sequenceMatches(0).bestPeptideMatch.get.peptide, 
    //                      rsb.allProtMatches(1).sequenceMatches(0).bestPeptideMatch.get.peptide)                      
    //    rsb.createNewProteinMatchFromPeptides(sharedPeptides)

    //    rsb.printForDebug  

    val rs: ResultSet = rsb.toResultSet()
    val rsm = ppsi.computeResultSummary(resultSet = rs, keepSubsummableSubsets = false )
    assert(rsm != null)
    assertEquals(4, rsm.peptideSets.length)
    assertEquals( /*3+1*/ 3, rsm.proteinSets.length)
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
    val rsb = new ResultSetFakeGenerator(nbPeps = 6, nbProts = 3)
    val sharedPeptides = ListBuffer[Peptide]()

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

    val rs: ResultSet = rsb.toResultSet()
    val rsm = ppsi.computeResultSummary(resultSet = rs)
    assert(rsm != null)
    assertEquals(6, rsm.peptideSets.length)
    assertEquals(6, rsm.proteinSets.length)
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
    val rsb = new ResultSetFakeGenerator(nbPeps = 2, nbProts = 2)
    val sharedPeptides = ListBuffer[Peptide]()

    sharedPeptides += rsb.allProtMatches(0).sequenceMatches(0).bestPeptideMatch.get.peptide
    sharedPeptides += rsb.allProtMatches(1).sequenceMatches(0).bestPeptideMatch.get.peptide

    rsb.addSharedPeptide(Seq(rsb.allProtMatches(0), rsb.allProtMatches(1)))

    rsb.createNewProteinMatchFromPeptides(sharedPeptides)

    //	  rsb.printForDebug  

    val rs: ResultSet = rsb.toResultSet()
    val rsm = ppsi.computeResultSummary(resultSet = rs)
    assert(rsm != null)
    assertEquals(3, rsm.peptideSets.length)
    assertEquals(3, rsm.proteinSets.length)
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
    val rsb = new ResultSetFakeGenerator(nbPeps = 4, nbProts = 2)

    rsb.createNewProteinMatchFromPeptides(Seq(rsb.allProtMatches(0).sequenceMatches(0).bestPeptideMatch.get.peptide))
    rsb.createNewProteinMatchFromPeptides(Seq(rsb.allProtMatches(1).sequenceMatches(0).bestPeptideMatch.get.peptide))

    val pms = ListBuffer[ProteinMatch]()

    for (pm <- rsb.allProtMatches) {
      if (pm.sequenceMatches.length == 1) pms += pm
    }
    rsb.addSharedPeptide(pms)

    //    rsb.printForDebug  

    val rs: ResultSet = rsb.toResultSet()
    val rsm = ppsi.computeResultSummary(resultSet = rs)
    assert(rsm != null)
    assertEquals(4, rsm.peptideSets.length)
    assertEquals(4, rsm.proteinSets.length)
  }

}