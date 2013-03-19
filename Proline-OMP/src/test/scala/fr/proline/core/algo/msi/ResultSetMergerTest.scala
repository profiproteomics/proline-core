package fr.proline.core.algo.msi

import org.junit.Test
import org.junit.Assert._
import org.scalatest.junit.JUnitSuite
import com.weiglewilczek.slf4s.Logging
import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.utils.generator.ResultSetFakeBuilder

@Test
class ResultSetMergerTest extends JUnitSuite with Logging {
	  	
	@Test
	def mergeOneRS() = {
	  val rs1 = new ResultSetFakeBuilder(pepNb = 800, proNb = 100).toResultSet()
	  val rsMergerAlgo = new ResultSetMerger()
	  val rs2 = rsMergerAlgo.mergeResultSets(Seq(rs1))
	  assert(rs2 != null)
	  assert(rs1 != rs2)
	  assert(rs1.peptideMatches.length==rs2.peptideMatches.length)
  }

	@Test
	def mergeRS() = {
	  val rs1 = new ResultSetFakeBuilder(pepNb = 800, proNb = 100).toResultSet()
	  val rs2 = new ResultSetFakeBuilder(pepNb = 200, proNb = 10).toResultSet()
	  val rsMergerAlgo = new ResultSetMerger()
	  val rs = rsMergerAlgo.mergeResultSets(Seq(rs1, rs2))
	  assert(rs != null)
	  assertEquals(800 + 200, rs.peptideMatches.length)
	  assertEquals(100 + 10, rs.proteinMatches.length)
  }

}

