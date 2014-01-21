package fr.proline.core.algo.msi

import org.junit.Assert.assertEquals
import org.junit.Test
import org.scalatest.junit.JUnitSuite

import com.typesafe.scalalogging.slf4j.Logging

import fr.proline.core.util.generator.msi.ResultSetFakeGenerator

@Test
class ResultSetBuilderTest extends JUnitSuite with Logging {
	  	
	@Test
	def addOneRS() = {
	  val rs1 = new ResultSetFakeGenerator(nbPeps = 800, nbProts = 100).toResultSet()
	  val rsAddAlgo = new ResultSetBuilder(resultSetId = 99)
	  rsAddAlgo.addResultSet(rs1)
	  val rs2 = rsAddAlgo.toResultSet()
	  assert(rs2 != null)
	  assert(rs1 != rs2)
	  assertEquals(rs1.peptideMatches.length,rs2.peptideMatches.length)
	  assertEquals(rs1.proteinMatches.length,rs2.proteinMatches.length)
	  val peptides = rs2.proteinMatches.map(_.sequenceMatches).flatten.map(_.peptide.get.id)
	  assertEquals(800, peptides.length)
	  val ids = rs2.peptideMatches.map(_.resultSetId).distinct
	  assertEquals(1, ids.length)
	  assertEquals(99, ids(0))
  }
	
		@Test
	def addOneRSTwice() = {
	  val rs1 = new ResultSetFakeGenerator(nbPeps = 800, nbProts = 100).toResultSet()
	  val rsAddAlgo = new ResultSetBuilder(resultSetId = 99)
	  rsAddAlgo.addResultSet(rs1)
	  rsAddAlgo.addResultSet(rs1)
	  val rs = rsAddAlgo.toResultSet()
	  assert(rs != null)
	  assert(rs1 != rs)
	  assertEquals(rs1.peptideMatches.length,rs.peptideMatches.length)
	  assertEquals(rs1.proteinMatches.length,rs.proteinMatches.length)
	  val peptides = rs.proteinMatches.map(_.sequenceMatches).flatten.map(_.peptide.get.id)
	  assertEquals(800, peptides.length)
	  val ids = rs.peptideMatches.map(_.resultSetId).distinct
	  assertEquals(1, ids.length)
	  assertEquals(99, ids(0))
  }

	@Test
	def addTwoRS() = {
	  val rs1 = new ResultSetFakeGenerator(nbPeps = 800, nbProts = 100).toResultSet()
	  val rs2 = new ResultSetFakeGenerator(nbPeps = 200, nbProts = 10).toResultSet()
	  val rsAddAlgo = new ResultSetBuilder(resultSetId = 99)
	  rsAddAlgo.addResultSet(rs1)
	  rsAddAlgo.addResultSet(rs2)	  
	  val rs = rsAddAlgo.toResultSet()
	  assert(rs != null)
	  assertEquals(800 + 200, rs.peptideMatches.length)
	  assertEquals(100 + 10, rs.proteinMatches.length)
	  val peptides = rs.proteinMatches.map(_.sequenceMatches).flatten.map(_.peptide.get.id)
	  assertEquals(800+200, peptides.length)
	  val ids = rs.peptideMatches.map(_.resultSetId).distinct
	  assertEquals(1, ids.length)
	  assertEquals(99, ids(0))
	  val bestPMs = rs.proteinMatches.map(_.sequenceMatches).flatten.map(_.bestPeptideMatchId)
	  for(pm<-rs.peptideMatches) {
	    assert(bestPMs.contains(pm.id))
	  }
  }
	
	@Test
	def addOneModifiedRS() = {
	  val rsfb = new ResultSetFakeGenerator(nbPeps = 800, nbProts = 100)
	  rsfb.addDuplicatedPeptideMatches(50)
	  val rs1 = rsfb.toResultSet()
	  val rsAddAlgo = new ResultSetBuilder(resultSetId = 99)
	  rsAddAlgo.addResultSet(rs1)
	  val rs2 = rsAddAlgo.toResultSet()
	  assert(rs2 != null)
	  assert(rs1 != rs2)
	  assertEquals(800,rs2.peptideMatches.length)
	  assertEquals(100,rs2.proteinMatches.length)
	  val peptides = rs2.proteinMatches.map(_.sequenceMatches).flatten.map(_.peptide.get.id)
	  assertEquals(800, peptides.length)
	  var ids = rs2.peptideMatches.map(_.resultSetId).distinct
	  assertEquals(1, ids.length)
	  assertEquals(99, ids(0))
	  ids = rs2.proteinMatches.map(_.sequenceMatches).flatten.map(_.resultSetId).distinct
	  assertEquals(1, ids.length)
  }

	@Test
	def addOneModifiedRSWithUnionMode() = {
	  val rsfb = new ResultSetFakeGenerator(nbPeps = 800, nbProts = 100)
	  rsfb.addDuplicatedPeptideMatches(50)
	  val rs1 = rsfb.toResultSet()
	  val rsAddAlgo = new ResultSetBuilder(resultSetId = 99, mode = AdditionMode.Union)
	  rsAddAlgo.addResultSet(rs1)
	  val rs2 = rsAddAlgo.toResultSet()
	  assert(rs2 != null)
	  assert(rs1 != rs2)
	  assertEquals(850,rs2.peptideMatches.length)
	  assertEquals(100,rs2.proteinMatches.length)
	  val peptides = rs2.proteinMatches.map(_.sequenceMatches).flatten.map(_.peptide.get.id)
	  assertEquals(800, peptides.length)
	  var ids = rs2.peptideMatches.map(_.resultSetId).distinct
	  assertEquals(1, ids.length)
	  assertEquals(99, ids(0))
	  ids = rs2.proteinMatches.map(_.sequenceMatches).flatten.map(_.resultSetId).distinct
	  assertEquals(1, ids.length)
  }

		
}
