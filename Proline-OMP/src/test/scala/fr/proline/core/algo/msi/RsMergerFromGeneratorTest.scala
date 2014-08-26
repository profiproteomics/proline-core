package fr.proline.core.algo.msi

import org.junit.Test
import org.junit.Assert._
import com.typesafe.scalalogging.slf4j.Logging
import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.util.generator.msi.ResultSetFakeGenerator

class RsMergerFromGeneratorTest extends RsAdderFromGeneratorTesting with Logging {

  val nbPepsToGenerate = 800
  val nbProtsToGenerate = 100

  @Test
  def mergeOneRS() {
    val generatedRS = generatedRSForAllTests

    val rsMergerAlgo = new ResultSetMerger()
    val builtRS = rsMergerAlgo.mergeResultSets(Array(generatedRS))

    checkBuiltResultSet(generatedRS, builtRS)
  }

  @Test
  def mergeOneRSTwice() {
    val generatedRS = generatedRSForAllTests
    
    val rsMergerAlgo = new ResultSetMerger()
    val builtRS = rsMergerAlgo.mergeResultSets(Seq(generatedRS, generatedRS))
    
    checkBuiltResultSet(generatedRS, builtRS)
  }

  @Test
  def mergeTwoRS() {
    val generatedRS1 = generatedRSForAllTests
    val generatedRS2 = new ResultSetFakeGenerator(nbPeps = 200, nbProts = 10).toResultSet()
    val rsMergerAlgo = new ResultSetMerger()
    val rs = rsMergerAlgo.mergeResultSets(Seq(generatedRS1, generatedRS2))
    assert(rs != null)
    assertEquals(800 + 200, rs.peptideMatches.length)
    assertEquals(100 + 10, rs.proteinMatches.length)
  }

  @Test
  def addOneModifiedRS() {
    val rsfb = new ResultSetFakeGenerator(nbPeps = nbPepsToGenerate, nbProts = nbProtsToGenerate)
    rsfb.addDuplicatedPeptideMatches(50)
    val generatedRS = rsfb.toResultSet()
    val rsMergerAlgo = new ResultSetMerger()
    
    val builtRS = rsMergerAlgo.mergeResultSets(Seq(generatedRS))
    checkBuiltResultSetIsNew(generatedRS, builtRS)

    assertEquals(nbPepsToGenerate, builtRS.peptideMatches.length)
    assertEquals(nbProtsToGenerate, builtRS.proteinMatches.length)
    
    val peptides = builtRS.proteinMatches.flatMap(_.sequenceMatches).map(_.peptide.get.id)
    assertEquals(nbPepsToGenerate, peptides.length)
    
    checkBuiltPeptideMatchesHaveRightId(builtRS)
    checkBuiltSeqMatchesHaveRightId(builtRS)
  }

}

