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

@Test
class ParsimoniousProteinSetInfererTest extends JUnitSuite with Logging {
	
  val proNb:Int = 2
  val pepNb:Int = 10
  	
	@Test
	def simpleCheckWithGenData() = {
	  var rs:ResultSet = new ResultSetFakeBuilder(pepNb=pepNb, proNb=proNb).toResultSet()
	  var ppsi = new ParsimoniousProteinSetInferer()
	  var rsu = ppsi.computeResultSummary(resultSet=rs) 
	  assert(rsu != null)
	  assertEquals(2, rsu.peptideSets.length)
	  assertEquals(proNb, rsu.proteinSets.length)	  
	}


	@Test
	def simpleCheckWithGenData2() = {
	  val rsb = new ResultSetFakeBuilder(pepNb=pepNb, proNb=proNb)
	  rsb.addSharedPeptide(rsb.allProtMatches)
	  var rs:ResultSet = rsb.toResultSet()
	  var ppsi = new ParsimoniousProteinSetInferer()
	  var rsu = ppsi.computeResultSummary(resultSet=rs) 
	  assert(rsu != null)
	  assertEquals(2, rsu.peptideSets.length)
	  assertEquals(proNb, rsu.proteinSets.length)	  
	}

	@Test
	def simpleCheckWithGenData3() = {
	  val rsb = new ResultSetFakeBuilder(pepNb=6, proNb=3)
	  rsb.createNewProteinMatchFromPeptides(rsb.allPeps)
	  var rs:ResultSet = rsb.toResultSet()
	  var ppsi = new ParsimoniousProteinSetInferer()
	  var rsu = ppsi.computeResultSummary(resultSet=rs) 
	  assert(rsu != null)
	  assertEquals(3+1, rsu.peptideSets.length)
	  // 1 = because the added prot is a superset
	  assertEquals(1, rsu.proteinSets.length)	  
	}


	@Test
	def simpleCheckWithGenData4() = {
	  val rsb = new ResultSetFakeBuilder(pepNb=6, proNb=3)
	  var sharedPeptides2 = ListBuffer[Peptide]()
	  for((proSeq, peptides) <- rsb.allPepsByProtSeq)  {
	    sharedPeptides2 += peptides(0)
	  }

	  rsb.createNewProteinMatchFromPeptides(sharedPeptides2)

//	  val sharedPeptides = Seq(rsb.allProtMatches(0).sequenceMatches(0).bestPeptideMatch.get.peptide, 
//			  							rsb.allProtMatches(1).sequenceMatches(0).bestPeptideMatch.get.peptide)			  							
//	  rsb.createNewProteinMatchFromPeptides(sharedPeptides)

//	  rsb.printForDebug  
	  
	  var rs:ResultSet = rsb.toResultSet()
	  var ppsi = new ParsimoniousProteinSetInferer()
	  var rsu = ppsi.computeResultSummary(resultSet=rs) 
	  assert(rsu != null)
	  assertEquals(4, rsu.peptideSets.length)
	  assertEquals(/*3+1*/3, rsu.proteinSets.length)	  
	}

}

