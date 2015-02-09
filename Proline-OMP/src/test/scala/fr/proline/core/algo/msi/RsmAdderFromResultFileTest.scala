package fr.proline.core.algo.msi

import org.junit.Assert.assertEquals
import org.junit.Test

import com.typesafe.scalalogging.slf4j.Logging

import fr.proline.core.algo.msi.inference.ParsimoniousProteinSetInferer
import fr.proline.core.om.model.msi.ResultSet

//object RsmAdderFromResultFileTest extends AbstractResultSetTestCase with Logging {
/*object RsmAdderFromResultFileTest extends AbstractDbUnitResultFileTestCase with Logging {

  // Define the interface to be implemented
  val driverType = DriverType.H2
  val dbUnitResultFile = STR_F122817_Mascot_v2_3
  val targetRSId = 1L
  val decoyRSId = Option.empty[Long]
  
}*/

class RsmAdderFromResultFileTest extends Logging with RsAdderFromResultFileTesting {
  
  val executionContext = STR_F122817_Mascot_v2_3_TEST_CASE.executionContext
  require( executionContext != null, "executionContext is null" )
  val readRS = STR_F122817_Mascot_v2_3_TEST_CASE.getRS
  
  val ppsi = new ParsimoniousProteinSetInferer()
  val rsm = ppsi.computeResultSummary( resultSet = readRS )

  @Test
  def addOneNonFilteredRSM() {
    
    val rsId = ResultSet.generateNewId()
    val rsAddAlgo = new ResultSetAdder(resultSetId = rsId)
    val selector = new ResultSummarySelector(rsm)
    rsAddAlgo.addResultSet(readRS, selector)
    
    val builtRS = rsAddAlgo.toResultSet()
    
    checkBuiltResultSet(builtRS)

    storeBuiltResultSet(builtRS)
  }

  @Test
  def addOneFilteredRSM() {

    val pepMatches = readRS.peptideMatches
    // Simulate rank filtering
    pepMatches.filter(_.rank > 1).foreach(_.isValidated = false)
    logger.info("Validated PepMatches " + readRS.peptideMatches.count(_.isValidated))
    val rsmAfterFiltering = this.ppsi.computeResultSummary(resultSet = readRS)

    // Check RSM after filtering
    val matches = rsmAfterFiltering.peptideInstances.flatMap(_.peptideMatches)
    assertEquals(matches.length, matches.filter(_.isValidated).length)
    assertEquals(matches.length, matches.filter(_.rank <= 1).length)

    val rsId = ResultSet.generateNewId()
    val rsAddAlgo = new ResultSetAdder(resultSetId = rsId)
    val selector = new ResultSummarySelector(rsmAfterFiltering)
    rsAddAlgo.addResultSet(readRS, selector)
    
    val builtRS = rsAddAlgo.toResultSet()
    checkBuiltResultSetIsNew( builtRS )
    
    val peptides = builtRS.proteinMatches.flatMap(_.sequenceMatches).map(_.getPeptideId).distinct
    assertEquals(peptides.length, builtRS.peptideMatches.map(_.peptide.id).distinct.length)
    
    val pepMatchesCount = readRS.peptideMatches.withFilter( _.rank <= 1 ).map(_.peptide.id).distinct.length
    assertEquals(pepMatchesCount, peptides.length)
    assert(readRS.proteinMatches.flatMap(_.sequenceMatches).length > builtRS.proteinMatches.flatMap(_.sequenceMatches).length)
    
    checkBuiltPeptideMatchesHaveRightId(builtRS)

    storeBuiltResultSet(builtRS)
  }

  @Test
  def addOneNonFilteredRSMTwice() {
    
    val rsId = ResultSet.generateNewId()
    val rsAddAlgo = new ResultSetAdder(resultSetId = rsId)
    rsAddAlgo.addResultSet(readRS, new ResultSummarySelector(rsm))
    rsAddAlgo.addResultSet(readRS, new ResultSummarySelector(rsm))
    
    val builtRS = rsAddAlgo.toResultSet()
    
    checkBuiltResultSet(builtRS)

    storeBuiltResultSet(builtRS)
  }

}

