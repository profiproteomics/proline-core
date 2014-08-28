package fr.proline.core.service.msi

import org.junit.Assert._
import org.junit.Test

import com.typesafe.scalalogging.slf4j.Logging

import fr.proline.context.IExecutionContext
import fr.proline.core.algo.msi.filtering.IPeptideMatchFilter
import fr.proline.core.algo.msi.filtering.pepmatch.RankPSMFilter
import fr.proline.core.algo.msi.validation.BasicTDAnalyzer
import fr.proline.core.algo.msi.validation.TargetDecoyModes
import fr.proline.core.dal.AbstractEmptyDatastoreTestCase
import fr.proline.core.dal.ContextFactory
import fr.proline.core.dbunit._
import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.om.provider.msi.impl.ORMResultSetProvider
import fr.proline.repository.DriverType

object ResultSummariesMergerTest extends AbstractEmptyDatastoreTestCase with Logging {

  val driverType = DriverType.H2
  // For manual postgres test !! If use, should comment all loadDataSet from setUp and AbstractRFImporterTest_.setUp
  //   val driverType = DriverType.POSTGRESQL
  val useJPA = false

  /*@Before
  override def setUp() = {
    super.setUp()

    //udsDBTestCase.loadDataSet(DbUnitSampleDataset.PROJECT.getResourcePath())
    //logger.info("UDS db succesfully initialized")
  }

  @After
  override def tearDown() {
    super.tearDown()
  }*/
  
}

class ResultSummariesMergerTest extends Logging {
  
  val sqlExecutionContext = ResultSummariesMergerTest.executionContext
  val dsConnectorFactoryForTest = ResultSummariesMergerTest.dsConnectorFactoryForTest

  @Test
  def testMergeTwoRSM() {

    var localJPAExecutionContext: IExecutionContext = null

    try {
      logger.debug("Importing Result Files ...")

      val rs1 = DbUnitResultFileUtils.importDbUnitResultFile(STR_F136482_CTD, sqlExecutionContext)
      val rs2 = DbUnitResultFileUtils.importDbUnitResultFile(TLS_F027737_MTD_no_varmod, sqlExecutionContext)

      logger.debug("Validating ResultSet by Ids ...")
      val rsm1 = validate(sqlExecutionContext, rs1)
      val rsm2 = validate(sqlExecutionContext, rs2)

      val rsms = Seq(rsm1, rsm2)

      logger.debug("Merging two ResultSummaries by objects ...")

      val rsmMergerObj = new ResultSummaryMerger(sqlExecutionContext, None, Some(rsms))

      val resultObj = rsmMergerObj.runService
      assertTrue("ResultSummary merger resultObj", resultObj)
      logger.info("End Run ResultSummaryMerger Service, merge two different RSMs by objects")

      val tMergedRSMObj = rsmMergerObj.mergedResultSummary
      assertNotNull("Merged TARGET ResultSummary Object", tMergedRSMObj)

      val mergedDecoyRSMObjId = tMergedRSMObj.getDecoyResultSummaryId
      assertTrue("Merged DECOY ResultSummary by Object is present", mergedDecoyRSMObjId > 0L)

      val rsm1Id = rsm1.id
      val rsm2Id = rsm2.id

      val rsmIds = Seq(rsm1Id, rsm2Id)

      logger.debug("Merging two ResultSummaries by Ids...")

      val rsmMergerId = new ResultSummaryMerger(sqlExecutionContext, Some(rsmIds), None)

      val resultId = rsmMergerId.runService
      assertTrue("ResultSummary merger resultId", resultId)
      logger.info("End Run ResultSummaryMerger Service, merge two different RSMs by Ids")

      val tMergedRSMId = rsmMergerId.mergedResultSummary
      assertNotNull("Merged TARGET ResultSummary Id", tMergedRSMId)

      val mergedDecoyRSMId = tMergedRSMId.getDecoyResultSummaryId
      assertTrue("Merged DECOY ResultSummary by Id is present", mergedDecoyRSMId > 0L)

      /* Try to reload merged TARGET ResultSet with JPA */
      val mergedRSId = tMergedRSMId.getResultSetId

      localJPAExecutionContext = ContextFactory.buildExecutionContext(dsConnectorFactoryForTest, 1, true)

      val rsProvider = new ORMResultSetProvider(
        localJPAExecutionContext.getMSIDbConnectionContext,
        localJPAExecutionContext.getPSDbConnectionContext,
        localJPAExecutionContext.getPDIDbConnectionContext
      )

      val optionalMergedRS = rsProvider.getResultSet(mergedRSId)
      assertTrue("Reloaded Merged ResultSet", (optionalMergedRS != null) && optionalMergedRS.isDefined)

      val optionalMergedDecoyRS = optionalMergedRS.get.decoyResultSet
      assertTrue("Reloaded Merged DECOY ResultSet", (optionalMergedDecoyRS != null) && optionalMergedDecoyRS.isDefined)

    } finally {

      if (localJPAExecutionContext != null) {
        try {
          localJPAExecutionContext.closeAll()
        } catch {
          case exClose: Exception => logger.error("Error closing local JPA ExecutionContext", exClose)
        }
      }

    }

  }

  private def validate(execContext: IExecutionContext, rs: ResultSet): ResultSummary = {
    /* PeptideMatch pre-filter on Rank */
    val seqBuilder = Seq.newBuilder[IPeptideMatchFilter]
    seqBuilder += new RankPSMFilter(2) // Only 1, 2 ranks

    val rsValidator = new ResultSetValidator(
      execContext = execContext,
      targetRs = rs,
      tdAnalyzer = Some(new BasicTDAnalyzer(TargetDecoyModes.CONCATENATED)),
      pepMatchPreFilters = Option(seqBuilder.result),
      protSetFilters = None,
      protSetValidator = None,
      storeResultSummary = true // FIXME: storeResultSummary = false doesn't work
    )

    val result = rsValidator.runService

    assertTrue("Validation of RS #" + rs.id, result)

    val validatedTargetRSM = rsValidator.validatedTargetRsm
    assertNotNull("Validated Target RSM", validatedTargetRSM)

    val decoyRSMId = validatedTargetRSM.getDecoyResultSummaryId
    println(decoyRSMId)
    assertTrue("Validated Decoy RSM", decoyRSMId > 0)

    validatedTargetRSM
  }

}