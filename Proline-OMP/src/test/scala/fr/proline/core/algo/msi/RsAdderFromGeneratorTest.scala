package fr.proline.core.algo.msi

import org.junit.Assert.assertEquals
import org.junit.Test

import com.typesafe.scalalogging.StrictLogging

import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.util.generator.msi.ResultSetFakeGenerator

class RsAdderFromGeneratorTest extends RsAdderFromGeneratorTesting with StrictLogging {
  
  val nbPepsToGenerate = 800
  val nbDuplicatedPepMatches = 50
  val nbProtsToGenerate = 100
	  	
	@Test
	def addOneRS() {
	  val generatedRS = generatedRSForAllTests
	  
	  val rsAddAlgo = new ResultSetAdder(resultSetId = ResultSet.generateNewId() )
	  rsAddAlgo.addResultSet(generatedRS)
	  
	  val builtRS = rsAddAlgo.toResultSet()
    checkBuiltResultSet(generatedRS,builtRS)
  }
	
  @Test
	def addOneRSTwice() {
	  val generatedRS = generatedRSForAllTests
	  
	  val rsAddAlgo = new ResultSetAdder(resultSetId = ResultSet.generateNewId())
	  rsAddAlgo.addResultSet(generatedRS)
	  rsAddAlgo.addResultSet(generatedRS)
	  
	  val builtRS = rsAddAlgo.toResultSet() 
    checkBuiltResultSet(generatedRS,builtRS)
	  
	  builtRS.peptideMatches.map(_.getChildrenIds.size).foreach( assertEquals(1, _) )
  }

	@Test
	def addTwoRS() {
	  val generatedRS1 = generatedRSForAllTests
	  val generatedRS2 = new ResultSetFakeGenerator(nbPeps = 200, nbProts = 10).toResultSet()
	  
	  val rsAddAlgo = new ResultSetAdder(resultSetId = ResultSet.generateNewId())
	  rsAddAlgo.addResultSet(generatedRS1)
	  rsAddAlgo.addResultSet(generatedRS2)
	  
	  val builtRS = rsAddAlgo.toResultSet()
	  checkBuiltResultSetIsNew(generatedRS1,builtRS)
	  
	  assertEquals(nbPepsToGenerate + 200, builtRS.peptideMatches.length)
	  assertEquals(nbProtsToGenerate + 10, builtRS.proteinMatches.length)
	  
	  val peptides = builtRS.proteinMatches.flatMap(_.sequenceMatches).map(_.peptide.get.id)
	  assertEquals(nbPepsToGenerate+200, peptides.length)
	  
	  checkBuiltPeptideMatchesHaveRightId( builtRS )
	  
	  val bestPMs = builtRS.proteinMatches.map(_.sequenceMatches).flatten.map(_.bestPeptideMatchId)
	  for(pm <- builtRS.peptideMatches) {
	    assert(bestPMs.contains(pm.id))
	  }
  }
	
	@Test
	def addOneModifiedRS() {
	  
	  val rsfb = newRsFakeGenerator()
	  rsfb.addDuplicatedPeptideMatches(nbDuplicatedPepMatches)	  
	  val generatedRS = rsfb.toResultSet()
	  
	  val rsAddAlgo = new ResultSetAdder(resultSetId = ResultSet.generateNewId())
	  rsAddAlgo.addResultSet(generatedRS)
	  
	  val builtRS = rsAddAlgo.toResultSet()	  
	  checkBuiltResultSetIsNew(generatedRS,builtRS)
	  
	  assertEquals(nbPepsToGenerate,builtRS.peptideMatches.length)
	  assertEquals(nbProtsToGenerate,builtRS.proteinMatches.length)
	  
	  val peptides = builtRS.proteinMatches.flatMap(_.sequenceMatches).map(_.peptide.get.id)
	  assertEquals(nbPepsToGenerate, peptides.length)
	  
	  checkBuiltPeptideMatchesHaveRightId( builtRS )
	  
	  checkBuiltSeqMatchesHaveRightId( builtRS )
  }

	@Test
	def addOneModifiedRSWithUnionMode() {
	  val rsfb = newRsFakeGenerator()
	  rsfb.addDuplicatedPeptideMatches(nbDuplicatedPepMatches)
	  val generatedRS = rsfb.toResultSet()
	  
	  val rsAddAlgo = new ResultSetAdder(resultSetId = ResultSet.generateNewId(), additionMode = AdditionMode.UNION)
	  rsAddAlgo.addResultSet(generatedRS)
	  
	  val builtRS = rsAddAlgo.toResultSet()	  
    checkBuiltResultSet(generatedRS,builtRS)
  }

		
}
