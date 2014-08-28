package fr.proline.core.algo.msi

import org.junit.Assert._
import org.junit.Test

import com.typesafe.scalalogging.slf4j.Logging

/*//object RsMergerFromResultFileTest extends AbstractResultSetTestCase with Logging {
object RsMergerFromResultFileTest extends AbstractDbUnitResultFileTestCase with Logging {

  val driverType = DriverType.H2
  val dbUnitResultFile = STR_F122817_Mascot_v2_3
  val targetRSId = 1L
  val decoyRSId = Option.empty[Long]
  
}*/

class RsMergerFromResultFileTest extends Logging {
  
  val executionContext = STR_F122817_Mascot_v2_3_TEST_CASE.executionContext
  require( executionContext != null, "executionContext is null" )
  val readRS = STR_F122817_Mascot_v2_3_TEST_CASE.getRS
  
  @Test
  def addOneRS() = {

    val rsMergerAlgo = new ResultSetMerger()
    val rs2 = rsMergerAlgo.mergeResultSets(Seq(readRS))
    assert(rs2 != null)
    assert(readRS != rs2)
    val peptides = rs2.proteinMatches.map(_.sequenceMatches).flatten.map(_.peptide.get.id).distinct
    assertEquals(peptides.length, readRS.peptides.length)
    assertEquals(peptides.length, readRS.peptideMatches.map(_.peptide.id).distinct.length)
    assertEquals(rs2.proteinMatches.map(_.sequenceMatches).length, readRS.proteinMatches.map(_.sequenceMatches).length)
    val ids = rs2.peptideMatches.map(_.resultSetId).distinct
    assertEquals(1, ids.length)
  }

}

