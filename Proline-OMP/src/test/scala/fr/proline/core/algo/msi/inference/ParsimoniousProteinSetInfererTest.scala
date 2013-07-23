package fr.proline.core.algo.msi.inference

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

@Test
class ParsimoniousProteinSetInfererTest extends JUnitSuite with Logging {

  var ppsi = new ParsimoniousProteinSetInferer()    

  
  @Test
  def simpleCheckWithGenData() = {
    var rs: ResultSet = new ResultSetFakeBuilder(nbPeps = 10, nbProts = 2).toResultSet()
    var rsu = ppsi.computeResultSummary(resultSet = rs)
    assert(rsu != null)
    assertEquals(2, rsu.peptideSets.length)
    assertEquals(2, rsu.proteinSets.length)
  }
  
   @Test
  def largerGenData() = {
    var rs: ResultSet = new ResultSetFakeBuilder(nbPeps = 10000, nbProts = 5000).toResultSet()
    var rsu = ppsi.computeResultSummary(resultSet = rs)
    assert(rsu != null)
    assertEquals(5000, rsu.peptideSets.length)
    assertEquals(5000, rsu.proteinSets.length)
  }

  @Test
  def simpleCheckWithGenData2() = {
    val rsb = new ResultSetFakeBuilder(nbPeps = 10, nbProts = 2)
    rsb.addSharedPeptide(rsb.allProtMatches)
    var rs: ResultSet = rsb.toResultSet()
    var rsu = ppsi.computeResultSummary(resultSet = rs)
    assert(rsu != null)
    assertEquals(2, rsu.peptideSets.length)
    assertEquals(2, rsu.proteinSets.length)
  }

  @Test
  def simpleCheckWithGenData3() = {
    val rsb = new ResultSetFakeBuilder(nbPeps = 6, nbProts = 3)
    rsb.createNewProteinMatchFromPeptides(rsb.allPeps)
    var rs: ResultSet = rsb.toResultSet()
    var rsu = ppsi.computeResultSummary(resultSet = rs)
    assert(rsu != null)
    assertEquals(3 + 1, rsu.peptideSets.length)
    // 1 = because the added prot is a superset
    assertEquals(1, rsu.proteinSets.length)
    assertEquals("ProteinMatches related to ProteinSet should contain sameset and subset", 4 , rsu.proteinSets(0).getProteinMatchIds.length )
  }

  @Test
  def simpleCheckWithGenData4() = {
    val rsb = new ResultSetFakeBuilder(nbPeps = 6, nbProts = 3)
    var sharedPeptides2 = ListBuffer[Peptide]()
    for ((proSeq, peptides) <- rsb.allPepsByProtSeq) {
      sharedPeptides2 += peptides(0)
    }

    rsb.createNewProteinMatchFromPeptides(sharedPeptides2)

    //	  val sharedPeptides = Seq(rsb.allProtMatches(0).sequenceMatches(0).bestPeptideMatch.get.peptide, 
    //			  							rsb.allProtMatches(1).sequenceMatches(0).bestPeptideMatch.get.peptide)			  							
    //	  rsb.createNewProteinMatchFromPeptides(sharedPeptides)

    //	  rsb.printForDebug  

    var rs: ResultSet = rsb.toResultSet()
    var rsu = ppsi.computeResultSummary(resultSet = rs)
    assert(rsu != null)
    assertEquals(4, rsu.peptideSets.length)
    assertEquals( /*3+1*/ 3, rsu.proteinSets.length)
  }

  @Test
  def simpleCheckWithGenData5() = {
    val rsb = new ResultSetFakeBuilder(nbPeps = 6, nbProts = 3)
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

    rsb.printForDebug  

    var rs: ResultSet = rsb.toResultSet()
    var rsu = ppsi.computeResultSummary(resultSet = rs)
    assert(rsu != null)
    assertEquals(6, rsu.peptideSets.length)
    assertEquals(6, rsu.proteinSets.length)  
  }

  @Test
  def simpleCheckWithGenData6() = {
    val rsb = new ResultSetFakeBuilder(nbPeps = 2, nbProts = 2)
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
  }

    @Test
  def simpleCheckWithGenData7() = {
    val rsb = new ResultSetFakeBuilder(nbPeps = 4, nbProts = 2)

    rsb.createNewProteinMatchFromPeptides(Seq(rsb.allProtMatches(0).sequenceMatches(0).bestPeptideMatch.get.peptide))
    rsb.createNewProteinMatchFromPeptides(Seq(rsb.allProtMatches(1).sequenceMatches(0).bestPeptideMatch.get.peptide))

    var pms = ListBuffer[ProteinMatch]()
    
    for (pm <- rsb.allProtMatches) {
   	 if (pm.sequenceMatches.length == 1) pms += pm
    }
    rsb.addSharedPeptide(pms)


    rsb.printForDebug  

    var rs: ResultSet = rsb.toResultSet()
    var rsu = ppsi.computeResultSummary(resultSet = rs)
    assert(rsu != null)
    assertEquals(4, rsu.peptideSets.length)
    assertEquals(4, rsu.proteinSets.length)
  }

  
}

