package fr.proline.core.service.msq

import com.typesafe.scalalogging.StrictLogging

import fr.proline.core.algo.msq.config.{AbundanceComputationMethod, AggregationQuantConfig, QuantChannelMapping}
import fr.proline.core.dal._
import fr.proline.core.dal.context.execCtxToTxExecCtx
import fr.proline.core.dbunit._
import fr.proline.core.om.model.msq
import fr.proline.core.om.model.msq._
import fr.proline.core.om.provider.PeptideCacheExecutionContext
import fr.proline.core.om.provider.msq.impl.SQLQuantResultSummaryProvider
import fr.proline.core.orm.uds.{Dataset => UdsDataset}
import fr.proline.core.service.msq.quantify.AggregationQuantifier
import fr.proline.core.service.uds.CreateQuantitation
import fr.proline.repository.DriverType
import org.junit.{Assert, Test}

import scala.collection.JavaConverters._

object AggregationQuantifierTest extends AbstractDatastoreTestCase with StrictLogging {

  override val driverType = DriverType.H2
  override val dbUnitResultFile = SmallRuns_XIC
  override val useJPA = true

  var lcmsDBTestCase: LCMSDatabaseTestCase = null

  /*
   * redefine initDBsDBManagement to init LCMS database for this test
   */
  override def initDBsDBManagement(driverType: DriverType) {

    m_testCaseLock.synchronized {

      if (m_toreDown) {
        throw new IllegalStateException("TestCase ALREADY torn down")
      }

      logger.info("Creating UDS and MSI Database TestCases")

      udsDBTestCase = new UDSDatabaseTestCase(driverType)
      udsDBTestCase.initDatabase()

      msiDBTestCase = new MSIDatabaseTestCase(driverType)
      msiDBTestCase.initDatabase()

      lcmsDBTestCase = new LCMSDatabaseTestCase(driverType)
      lcmsDBTestCase.initDatabase()

      dsConnectorFactoryForTest = new DataStoreConnectorFactoryForTest(
        udsDBTestCase.getConnector,
        msiDBTestCase.getConnector,
        lcmsDBTestCase.getConnector,
        initialize = false
      )

    } // End of synchronized block on m_testCaseLock

  }

}

@Test
class AggregationQuantifierTest extends StrictLogging {

  val quantDS1Id = 5L
  val quantDS2Id = 6L

  val executionContext = AggregationQuantifierTest.executionContext
  val dsConnectorFactoryForTest = AggregationQuantifierTest.dsConnectorFactoryForTest

  @Test
  def test(): Unit = {

    val xicRSM = AggregationQuantifierTest.getResultSummaryProvider().getResultSummary(5L, false)
    Assert.assertNotNull(xicRSM)

    val msiEM = executionContext.getMSIDbConnectionContext.getEntityManager
    val udsEM = executionContext.getUDSDbConnectionContext.getEntityManager

    val quantitation1 = udsEM.find(classOf[UdsDataset], quantDS1Id)
    val quantitation2 = udsEM.find(classOf[UdsDataset], quantDS2Id)

    Assert.assertNotNull(quantitation1)
    Assert.assertNotNull(quantitation2)

    val quantChannelIds = quantitation1.getQuantitationChannels.asScala.map(qc => qc.getId)

    val quantRSMProvider = new SQLQuantResultSummaryProvider(PeptideCacheExecutionContext(executionContext))
    val quantRSM = quantRSMProvider.getQuantResultSummary(
      quantitation1.getMasterQuantitationChannels.get(0).getQuantResultSummaryId,
      quantChannelIds,
      false
    )

    Assert.assertNotNull(quantRSM)

  }

  @Test
  def testAggregation(): Unit = {

    // AggregationQuantifierTest.lcmsDBTestCase.loadDataSet(AggregationQuantifierTest.dbUnitResultFile.lcmsDbDatasetPath)

    executionContext.tryInTransactions(udsTx = true, msiTx = true, txWork = {

      val udsEM = executionContext.getUDSDbConnectionContext.getEntityManager

      val experimentalDesign = new SimplifiedExperimentalDesign(
        biologicalSamples = Array( new msq.BiologicalSample(name = "Sample 1", number = 1)),
        biologicalGroups = Array(new BiologicalGroup(name = "Group 1", number = 1, sampleNumbers = Array(1))),
        masterQuantChannels = Array {
          new MasterQuantChannel(
            id = 0,
            number = 1,
            identResultSummaryId = None,
            quantChannels = Array(new QuantChannel(id = -1L, number = 1, name = "", sampleNumber = 1, identResultSummaryId = -1L))
          )
        }
      )

      val quantiCreator = new CreateQuantitation(
        executionContext = executionContext,
        name = "AggregatedXIC",
        description = "a simple description",
        projectId = 1L,
        methodId = 3,
        experimentalDesign = experimentalDesign.toExperimentalDesign()
      )
      quantiCreator.runService()

      val quantiId = quantiCreator.getUdsQuantitation.getId
      val udsQuantitation = udsEM.find(classOf[UdsDataset], quantiId)
      val udsMasterQuantChannel = udsQuantitation.getMasterQuantitationChannels.get(0)

      val aggQuantConfig = new AggregationQuantConfig(
        quantitationIds = Array(quantDS1Id, quantDS2Id),
        quantChannelsMapping = Array(
          // map child MCQ to child QC that will be quantified in quantChannelNumber 1
          new QuantChannelMapping(quantChannelNumber = 1, Map(1L -> 1L, 2L -> 2L))
        ),
        intensityComputationMethodName = AbundanceComputationMethod.MOST_INTENSE
      )

      val quantifier = new AggregationQuantifier(
        executionContext = executionContext,
        experimentalDesign = experimentalDesign.toExperimentalDesign(),
        udsMasterQuantChannel = udsMasterQuantChannel,
        quantConfig = aggQuantConfig
      )

      quantifier.quantify()

      // Read the quantitation dataset again
      val quantitation = udsEM.find(classOf[UdsDataset], quantiId)
      val quantChannelIds = quantitation.getQuantitationChannels.asScala.map(qc => qc.getId)
      val quantRSMProvider = new SQLQuantResultSummaryProvider(PeptideCacheExecutionContext(executionContext))
      val quantRSM = quantRSMProvider.getQuantResultSummary(
        udsMasterQuantChannel.getQuantResultSummaryId,
        quantChannelIds,
        false
      )

      Assert.assertNotNull(quantRSM)

    })
  }
}