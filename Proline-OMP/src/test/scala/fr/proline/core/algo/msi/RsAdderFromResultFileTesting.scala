package fr.proline.core.algo.msi

import org.junit.Assert.assertEquals

import fr.proline.context.IExecutionContext
import fr.proline.core.om.storer.msi.RsStorer
import fr.proline.core.om.storer.msi.impl.StorerContext
import fr.proline.core.om.model.msi.ResultSet

trait RsAdderFromResultFileTesting extends RsAdderFromResultChecking with RsAdderFromResultStoring

trait RsAdderFromResultChecking {
  
  val readRS: ResultSet
  
  def checkBuiltResultSetIsNew( builtRS: ResultSet ) {
    // Check built result set is not null
    assert(builtRS != null)
    
    // Check build result set is different than read one
    assert(readRS != builtRS)
  }
  
  def checkBuiltResultSetHasEqualNbPeptides( builtRS: ResultSet ) {    
    val peptideIds = builtRS.proteinMatches.flatMap(_.sequenceMatches).map(_.getPeptideId).distinct
    assertEquals(readRS.peptides.length, peptideIds.length )
    assertEquals(readRS.peptideMatches.map(_.peptide.id).distinct.length, peptideIds.length )
  }
  
  def checkBuiltResultSetHasEqualsSeqMatches( builtRS: ResultSet ) {    
    assertEquals(readRS.proteinMatches.flatMap(_.sequenceMatches).length, builtRS.proteinMatches.flatMap(_.sequenceMatches).length )
  }
  
  def checkBuiltPeptideMatchesHaveRightId( builtRS: ResultSet ) {    
    val ids = builtRS.peptideMatches.map(_.resultSetId).distinct
    assertEquals(1, ids.length)
    assertEquals(builtRS.id, ids.head)
  }
  
  def checkBuiltResultSet( builtRS: ResultSet ) {
    
    checkBuiltResultSetIsNew( builtRS: ResultSet )

    checkBuiltResultSetHasEqualNbPeptides( builtRS: ResultSet )

    checkBuiltResultSetHasEqualsSeqMatches( builtRS: ResultSet )
    
    // Check that result set ids have been correctly assigned
    checkBuiltPeptideMatchesHaveRightId( builtRS: ResultSet )
  }
  
}

trait RsAdderFromResultStoring {
  
  val executionContext: IExecutionContext
  
  def storeBuiltResultSet( builtRS: ResultSet ) = {
    val storerContext = StorerContext(executionContext) // Use Object factory
    val rsStorer = RsStorer(storerContext.getMSIDbConnectionContext)
    rsStorer.storeResultSet(builtRS, storerContext)
  }
  
  
}
