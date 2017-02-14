package fr.proline.core.algo.msi

import org.junit.Assert._
import org.junit.BeforeClass
import org.junit.Test
import com.typesafe.scalalogging.StrictLogging
import fr.proline.core.algo.msi.filtering.pepmatch.ScorePSMFilter
import fr.proline.core.algo.msi.validation.BasicTDAnalyzer
import fr.proline.core.algo.msi.validation.TargetDecoyModes
import fr.proline.core.dal.STR_F122817_Mascot_v2_3_TEST_CASE
import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.om.provider.msi.impl.SQLResultSummaryProvider
import fr.proline.core.service.msi.ResultSetValidator

/*//object RsAdderFromResultFileTest extends AbstractResultSetTestCase with StrictLogging {
object RsAdderFromResultFileTest extends AbstractDbUnitResultFileTestCase with StrictLogging {

  // Define the interface to be implemented
  val driverType = DriverType.H2
  val dbUnitResultFile = STR_F122817_Mascot_v2_3
  val targetRSId = 1L
  val decoyRSId = Option.empty[Long]
  
}*/

object RsAdderFromResultFileTest extends StrictLogging {
  
  var readRS: ResultSet = null
  
  @BeforeClass
  def init() {
    readRS = STR_F122817_Mascot_v2_3_TEST_CASE.getRS
  }

}

class RsAdderFromResultFileTest extends StrictLogging with RsAdderFromResultFileTesting {
  
  val executionContext = STR_F122817_Mascot_v2_3_TEST_CASE.executionContext
  require( executionContext != null, "executionContext is null" )
  
  val readRS = RsAdderFromResultFileTest.readRS
  
  @Test
  def addOneRS() {
    
    val rsId = ResultSet.generateNewId()
    val rsAddAlgo = new ResultSetAdder(resultSetId = rsId)
    rsAddAlgo.addResultSet(readRS)
    
    val builtRS = rsAddAlgo.toResultSet()
    
    checkBuiltResultSet(builtRS)

    storeBuiltResultSet(builtRS)
  }

  @Test
  def addOneRSTwice() {
    
    val rsId = ResultSet.generateNewId()
    val rsAddAlgo = new ResultSetAdder(resultSetId = rsId)
    rsAddAlgo.addResultSet(readRS)
    rsAddAlgo.addResultSet(readRS)
    
    val builtRS = rsAddAlgo.toResultSet()
    
    checkBuiltResultSet(builtRS)

    storeBuiltResultSet(builtRS)
  }

  @Test
  def mergeTwiceRSAndValidate() {
    
    val rsId = ResultSet.generateNewId()
    val rsAddAlgo = new ResultSetAdder(resultSetId = rsId)
    rsAddAlgo.addResultSet(readRS)
    rsAddAlgo.addResultSet(readRS)
    
    val builtRS = rsAddAlgo.toResultSet()
    
    checkBuiltResultSet(builtRS)

    storeBuiltResultSet(builtRS)

    val rsValidation = new ResultSetValidator(
      execContext = executionContext,
      targetRs = builtRS,
      tdAnalyzer = Some(new BasicTDAnalyzer(TargetDecoyModes.CONCATENATED, useTdCompetition = true)),
      pepMatchPreFilters = Some(Seq(new ScorePSMFilter(scoreThreshold = 22.0f))),
      pepMatchValidator = None,
      protSetFilters = None,
      storeResultSummary = true
    )

    val result = rsValidation.runService
    assertTrue("ResultSet validation result", result)
    logger.info(" End Run ResultSetValidator Service with Score Filter, in Test ")

    val tRSM = rsValidation.validatedTargetRsm
    logger.info(" rsValidation.validatedTargetRsm " + tRSM.id)
    assertNotNull(tRSM)

    val provider = new SQLResultSummaryProvider(
      executionContext.getMSIDbConnectionContext(),
      executionContext.getPSDbConnectionContext(),
      executionContext.getUDSDbConnectionContext()
    )
    val readRSM = provider.getResultSummary(tRSM.id, false)
    assertNotNull(readRSM)

  }

}

