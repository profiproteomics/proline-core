package fr.proline.core.algo.msi

import org.junit.Test
import org.junit.Assert._
import org.scalatest.junit.JUnitSuite
import com.weiglewilczek.slf4s.Logging
import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.util.generator.msi.ResultSetFakeGenerator

@Test
class ResultSetMergerTest extends JUnitSuite with Logging {
	  	
	@Test
	def mergeOneRS() = {
	  val rs1 = new ResultSetFakeGenerator(nbPeps = 800, nbProts = 100).toResultSet()
	  val rsMergerAlgo = new ResultSetMerger()
	  val rs2 = rsMergerAlgo.mergeResultSets(Seq(rs1))
	  assert(rs2 != null)
	  assert(rs1 != rs2)
	  assertEquals(rs1.peptideMatches.length,rs2.peptideMatches.length)
	  assertEquals(rs1.proteinMatches.length,rs2.proteinMatches.length)
	  val peptides = rs2.proteinMatches.map(_.sequenceMatches).flatten.map(_.peptide.get.id)
	  assertEquals(800, peptides.length)
	  val ids = rs2.peptideMatches.map(_.resultSetId).distinct
	  assertEquals(1, ids.length)

  }

		@Test
	def mergeOneRSTwice() = {
	  val rs1 = new ResultSetFakeGenerator(nbPeps = 800, nbProts = 100).toResultSet()
	  val rsMergerAlgo = new ResultSetMerger()
	  val rs2 = rsMergerAlgo.mergeResultSets(Seq(rs1, rs1))
	  assert(rs2 != null)
	  assert(rs1 != rs2)
	  assertEquals(rs1.peptideMatches.length,rs2.peptideMatches.length)
	  assertEquals(rs1.proteinMatches.length,rs2.proteinMatches.length)
	  val peptides = rs2.proteinMatches.map(_.sequenceMatches).flatten.map(_.peptide.get.id)
	  assertEquals(800, peptides.length)
	  val ids = rs2.peptideMatches.map(_.resultSetId).distinct
	  assertEquals(1, ids.length)
  }

	@Test
	def mergeTwoRS() = {
	  val rs1 = new ResultSetFakeGenerator(nbPeps = 800, nbProts = 100).toResultSet()
	  val rs2 = new ResultSetFakeGenerator(nbPeps = 200, nbProts = 10).toResultSet()
	  val rsMergerAlgo = new ResultSetMerger()
	  val rs = rsMergerAlgo.mergeResultSets(Seq(rs1, rs2))
	  assert(rs != null)
	  assertEquals(800 + 200, rs.peptideMatches.length)
	  assertEquals(100 + 10, rs.proteinMatches.length)
  }
	
			@Test
	def addOneModifiedRS() = {
	  val rsfb = new ResultSetFakeGenerator(nbPeps = 800, nbProts = 100)
	  rsfb.addDuplicatedPeptideMatches(50)
	  val rs1 = rsfb.toResultSet()
	  val rsMergerAlgo = new ResultSetMerger()
	  val rs2 = rsMergerAlgo.mergeResultSets(Seq(rs1))
	  assert(rs2 != null)
	  assert(rs1 != rs2)
	  assertEquals(800,rs2.peptideMatches.length)
	  assertEquals(100,rs2.proteinMatches.length)
	  val peptides = rs2.proteinMatches.map(_.sequenceMatches).flatten.map(_.peptide.get.id)
	  assertEquals(800, peptides.length)
	  var ids = rs2.peptideMatches.map(_.resultSetId).distinct
	  assertEquals(1, ids.length)
	  ids = rs2.proteinMatches.map(_.sequenceMatches).flatten.map(_.resultSetId).distinct
	  assertEquals(1, ids.length)
  }

}

