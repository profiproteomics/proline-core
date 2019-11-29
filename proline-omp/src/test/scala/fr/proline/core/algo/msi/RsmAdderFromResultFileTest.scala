package fr.proline.core.algo.msi

import com.typesafe.scalalogging.StrictLogging
import fr.proline.core.algo.msi.inference.ParsimoniousProteinSetInferer
import fr.proline.core.algo.msi.scoring._
import fr.proline.core.algo.msi.validation.pepinstance.BasicPepInstanceBuilder
import fr.proline.core.dal.AbstractDatastoreTestCase
import fr.proline.core.dbunit.{DbUnitResultFileLocation, STR_F122817_Mascot_v2_3}
import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.repository.DriverType
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test

object RsmAdderFromResultFileTest extends AbstractDatastoreTestCase {

  override val driverType: DriverType = DriverType.H2
  override val useJPA: Boolean = true
  override val dbUnitResultFile: DbUnitResultFileLocation = STR_F122817_Mascot_v2_3

  val targetRSId = 1L

  lazy val proteinSetInferer = new ParsimoniousProteinSetInferer(new BasicPepInstanceBuilder())
  var rsm: ResultSummary = null

  @BeforeClass
  override def setUp() {
    super.setUp()
    rsm = proteinSetInferer.computeResultSummary(resultSet = getRS(targetRSId))
  }

}

/**
  * VDS FIXME :  ===> Suite au Merge, le probleme a disparu... si ca se confirme, Commentaire A SUPPRIMER.
  * While init STR_F122817_Mascot_v2_3_TEST_CASE => Error getting PtmDef for used_PTM :
  *  java.util.NoSuchElementException: key not found: when searching PTM_Classification for used_PTM : classification = ""
  *  JPAPtmDefinitionWriter.convertPtmDefinitionToMsiPtmSpecificity(JPAPtmDefinitionStorer.scala:196)
  */
@Test
class RsmAdderFromResultFileTest extends StrictLogging with RsAdderFromResultFileTesting {

  override val executionContext = RsmAdderFromResultFileTest.executionContext
  override val readRS = RsmAdderFromResultFileTest.getRS(RsmAdderFromResultFileTest.targetRSId)

  val ppsi = RsmAdderFromResultFileTest.proteinSetInferer
  val pepSetScoreUpdater = PeptideSetScoreUpdater(PepSetScoring.MASCOT_MODIFIED_MUDPIT_SCORE)
  val nonFilteredRSM = RsmAdderFromResultFileTest.rsm

  @Test
  def addOneNonFilteredRSM() {

    val rsId = ResultSet.generateNewId()
    val rsmAdderAlgo = new ResultSummaryAdder(resultSetId = rsId, pepSetScoreUpdater = pepSetScoreUpdater)
    rsmAdderAlgo.addResultSummary(nonFilteredRSM)

    val builtRSM = rsmAdderAlgo.toResultSummary()

    checkBuiltResultSet(builtRSM.resultSet.get)

    //storeBuiltResultSet(builtRSM.resultSet.get)
  }

  @Test
  def addOneNonFilteredRSMTwice() {

    val rsId = ResultSet.generateNewId()
    val rsmAdderAlgo = new ResultSummaryAdder(resultSetId = rsId, pepSetScoreUpdater = pepSetScoreUpdater)
    rsmAdderAlgo.addResultSummary(nonFilteredRSM)
    rsmAdderAlgo.addResultSummary(nonFilteredRSM)

    val builtRSM = rsmAdderAlgo.toResultSummary()

    checkBuiltResultSet(builtRSM.resultSet.get)

    //storeBuiltResultSet(builtRS)
  }

  @Test
  def addOneNonFilteredRSMTwiceInUnionMode() {

    val rsId = ResultSet.generateNewId()
    val rsmAdderAlgo = new ResultSummaryAdder(resultSetId = rsId, pepSetScoreUpdater = pepSetScoreUpdater, additionMode = AdditionMode.UNION)
    rsmAdderAlgo.addResultSummary(nonFilteredRSM)
    rsmAdderAlgo.addResultSummary(nonFilteredRSM)

    val builtRSM = rsmAdderAlgo.toResultSummary()

    checkBuiltResultSet(builtRSM.resultSet.get)

    //storeBuiltResultSet(builtRS)
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
    val rsmAdderAlgo = new ResultSummaryAdder(resultSetId = rsId, pepSetScoreUpdater = pepSetScoreUpdater)
    rsmAdderAlgo.addResultSummary(rsmAfterFiltering)

    val builtRSM = rsmAdderAlgo.toResultSummary()
    val builtRS = builtRSM.resultSet.get
    checkBuiltResultSetIsNew(builtRS)

    val peptides = builtRS.proteinMatches.flatMap(_.sequenceMatches).map(_.getPeptideId).distinct
    assertEquals(peptides.length, builtRS.peptideMatches.map(_.peptide.id).distinct.length)

    val pepMatchesCount = readRS.peptideMatches.withFilter(_.rank <= 1).map(_.peptide.id).distinct.length
    assertEquals(pepMatchesCount, peptides.length)
    assert(readRS.proteinMatches.flatMap(_.sequenceMatches).length > builtRS.proteinMatches.flatMap(_.sequenceMatches).length)

    checkBuiltPeptideMatchesHaveRightId(builtRS)

    //storeBuiltResultSet(builtRS)
  }

}

