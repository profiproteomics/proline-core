package fr.proline.core.algo.msi

import org.junit.Assert._
import org.junit.Test
import com.typesafe.scalalogging.slf4j.Logging
import fr.proline.core.algo.msi.filtering.pepmatch.ScorePSMFilter
import fr.proline.core.algo.msi.validation.BasicTDAnalyzer
import fr.proline.core.algo.msi.validation.TargetDecoyModes
import fr.proline.core.dal.AbstractMultipleDBTestCase
import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.om.provider.msi.impl.SQLResultSummaryProvider
import fr.proline.core.om.storer.msi.RsStorer
import fr.proline.core.om.storer.msi.impl.StorerContext
import fr.proline.core.service.msi.ResultSetValidator
import fr.proline.repository.DriverType
import fr.proline.core.dbunit.STR_F122817_Mascot_v2_3

object RsAdderFromResultFileTest extends AbstractMascotResultFileTestCase with Logging {

  // Define the interface to be implemented
  val driverType = DriverType.H2
  val dbUnitResultFile = STR_F122817_Mascot_v2_3
  val targetRSId = 1L
  val decoyRSId = Option.empty[Long]
  
}

class RsAdderFromResultFileTest extends Logging with RsAdderFromResultFileTesting {
  
  val executionContext = RsAdderFromResultFileTest.executionContext
  val readRS = RsAdderFromResultFileTest.getRS
  
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
      tdAnalyzer = Some(new BasicTDAnalyzer(TargetDecoyModes.CONCATENATED)),
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

