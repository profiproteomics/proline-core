package fr.proline.core.algo.msi

import org.junit.Assert.assertEquals
import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.util.generator.msi.ResultSetFakeGenerator

trait RsAdderFromGeneratorTesting {
  
  val nbPepsToGenerate: Int
  val nbProtsToGenerate: Int
  
  def newRsFakeGenerator() = new ResultSetFakeGenerator(nbPeps = nbPepsToGenerate, nbProts = nbProtsToGenerate)
  lazy val generatedRSForAllTests = newRsFakeGenerator().toResultSet()
  
  def checkBuiltResultSetIsNew( generatedRS: ResultSet, builtRS: ResultSet ) {
    // Check built result set is not null
    assert(builtRS != null)
    
    // Check build result set is different than read one
    assert(generatedRS != builtRS)
  }
  
  def checkBuiltResultSetHasEqualNbEntities( generatedRS: ResultSet, builtRS: ResultSet ) {    
    assertEquals(generatedRS.peptideMatches.length,builtRS.peptideMatches.length)
    assertEquals(generatedRS.proteinMatches.length,builtRS.proteinMatches.length)
    
    val peptides = builtRS.proteinMatches.flatMap(_.sequenceMatches).map(_.peptide.get.id)
    assertEquals(generatedRS.peptides.length, peptides.length)
  }
  
  def checkBuiltPeptideMatchesHaveRightId( builtRS: ResultSet ) {
    val ids = builtRS.peptideMatches.map(_.resultSetId).distinct
    assertEquals(1, ids.length)
    assertEquals(builtRS.id, ids.head)
  }
  
  def checkBuiltSeqMatchesHaveRightId( builtRS: ResultSet ) {
    val seqMatchRsIds = builtRS.proteinMatches.flatMap(_.sequenceMatches).map(_.resultSetId).distinct
    assertEquals(1, seqMatchRsIds.length)
    assertEquals(builtRS.id, seqMatchRsIds.head)
  }
  
  def checkBuiltResultSet( generatedRS: ResultSet, builtRS: ResultSet ) {
    
    checkBuiltResultSetIsNew(generatedRS,builtRS)
    
    checkBuiltResultSetHasEqualNbEntities(generatedRS,builtRS)
    
    checkBuiltPeptideMatchesHaveRightId( builtRS )
    
    checkBuiltSeqMatchesHaveRightId( builtRS )
  }

}