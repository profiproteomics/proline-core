package fr.proline.core.algo.msi

import com.typesafe.scalalogging.StrictLogging
import fr.proline.context.IExecutionContext
import fr.proline.core.algo.msi.filtering.pepmatch.ScorePSMFilter
import fr.proline.core.algo.msi.validation.{BasicTDAnalyzer, TargetDecoyModes}
import fr.proline.core.dal.AbstractDatastoreTestCase
import fr.proline.core.dbunit.{DbUnitResultFileLocation, STR_F122817_Mascot_v2_3}
import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.om.provider.PeptideCacheExecutionContext
import fr.proline.core.om.provider.msi.impl.SQLResultSummaryProvider
import fr.proline.core.service.msi.ResultSetValidator
import fr.proline.repository.DriverType
import org.junit.Assert._
import org.junit.Test


object RsAdderFromResultFileTest extends AbstractDatastoreTestCase {

  override val driverType: DriverType = DriverType.H2
  override val useJPA: Boolean = true
  override val dbUnitResultFile: DbUnitResultFileLocation = STR_F122817_Mascot_v2_3

  val targetRSId = 1L

}

/**
  * VDS FIXME ===> Suite au Merge, le probleme a disparu... si ca se confirme, Commentaire A SUPPRIMER.
  * While init STR_F122817_Mascot_v2_3_TEST_CASE => Error getting PtmDef for used_PTM :
  *  java.util.NoSuchElementException: key not found: when searching PTM_Classification for used_PTM : classification = ""
  *  JPAPtmDefinitionWriter.convertPtmDefinitionToMsiPtmSpecificity(JPAPtmDefinitionStorer.scala:196)
  */
@Test
class RsAdderFromResultFileTest extends StrictLogging with RsAdderFromResultFileTesting {

  override val executionContext: IExecutionContext = RsAdderFromResultFileTest.executionContext
  override val readRS: ResultSet = RsAdderFromResultFileTest.getRS(RsAdderFromResultFileTest.targetRSId)
  
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

    val result = rsValidation.runService()
    assertTrue("ResultSet validation result", result)
    logger.info(" End Run ResultSetValidator Service with Score Filter, in Test ")

    val tRSM = rsValidation.validatedTargetRsm
    logger.info(" rsValidation.validatedTargetRsm " + tRSM.id)
    assertNotNull(tRSM)
    
    val provider = new SQLResultSummaryProvider(PeptideCacheExecutionContext(executionContext))
    val readRSM = provider.getResultSummary(tRSM.id, loadResultSet = false)
    assertNotNull(readRSM)

  }

}

