package fr.proline.core.service.msq

import java.util.ArrayList
import org.junit.Assert._
import org.junit.Test
import com.typesafe.scalalogging.StrictLogging
import fr.proline.core.algo.msq.config._
import fr.proline.core.dal.AbstractResultSummaryTestCase
import fr.proline.core.dbunit.STR_F063442_F122817_MergedRSMs
import fr.proline.core.orm.uds.BiologicalSample
import fr.proline.core.orm.uds.Dataset
import fr.proline.core.orm.uds.MasterQuantitationChannel
import fr.proline.core.orm.uds.Project
import fr.proline.core.orm.uds.QuantitationChannel
import fr.proline.core.orm.uds.SampleAnalysis
import fr.proline.core.service.msq.quantify.WeightedSpectralCountQuantifier
import fr.proline.repository.DriverType
import fr.proline.core.orm.uds.BiologicalSplSplAnalysisMap
import java.util.HashSet

object WeightedSCQuantifierTest extends AbstractResultSummaryTestCase with StrictLogging {

  // Define required parameters
  val driverType = DriverType.H2
  val dbUnitResultFile = STR_F063442_F122817_MergedRSMs
  val targetRSMId: Long = 33L
  val useJPA = true

  /*
  // Define the interface to be implemented
  val driverType = DriverType.H2
  val fileName = "STR_F063442_F122817_MergedRSMs"
  val targetRSMId: Long = 33

  var executionContext: IExecutionContext = null

  @Before
  @throws(classOf[Exception])
  def setUp() = {

    logger.info("Initializing DBs")
    super.initDBsDBManagement(driverType)

    //Load Data
    pdiDBTestCase.loadDataSet("/dbunit/datasets/pdi/Proteins_Dataset.xml")
    psDBTestCase.loadDataSet("/dbunit_samples/" + fileName + "/ps-db.xml")
    msiDBTestCase.loadDataSet("/dbunit_samples/" + fileName + "/msi-db.xml")
    udsDBTestCase.loadDataSet("/dbunit_samples/" + fileName + "/uds-db.xml")

    logger.info("PDI, PS, MSI and UDS dbs succesfully initialized !")

    val execContext = buildJPAContext()
    executionContext = execContext
  }

  @After
  override def tearDown() {
    if (executionContext != null) executionContext.closeAll()
    super.tearDown()
  }

  def buildJPAContext() = {
    val executionContext = ContextFactory.buildExecutionContext(dsConnectorFactoryForTest, 1, true) // Full JPA

    executionContext
  }*/
  
}
  
class WeightedSCQuantifierTest extends StrictLogging {
  
  val executionContext = WeightedSCQuantifierTest.executionContext
  require( executionContext != null, "executionContext is null")
  
  val targetRSMId = WeightedSCQuantifierTest.targetRSMId

  @Test
  def quantifyRSMSC() {

    val weightRefRSMIds = Seq(33L)
    val spCountCfg = new SpectralCountConfig(identResultSummaryId = Some(targetRSMId), identDatasetId = None, weightsRefRsmIds=weightRefRSMIds)

    val udsEm = executionContext.getUDSDbConnectionContext.getEntityManager
    udsEm.getTransaction().begin()

    //Create Exp Design
    val pj: Project = udsEm.find(classOf[Project], 1l)
    val qtDS = new Dataset(pj)
    qtDS.setNumber(2)
    qtDS.setName("WSC test DS")
    qtDS.setType(Dataset.DatasetType.QUANTITATION)
    qtDS.setChildrenCount(2)

    //Create Sample Analysis
    val splAnalysis1 = new SampleAnalysis()
    splAnalysis1.setDataset(qtDS)

    val splAnalysis2 = new SampleAnalysis()
    splAnalysis2.setDataset(qtDS)
    
    val splAnalysis3 = new SampleAnalysis()
    splAnalysis3.setDataset(qtDS)
    
    
    //Create BiologicalSample
    val bioSpl1 = new BiologicalSample()
    bioSpl1.setName("WSC Test BioSpl")
    bioSpl1.setNumber(1)
    bioSpl1.setDataset(qtDS)

    //Create link between SampleAnalysis  & BiologicalSample
    val replicate2Sample1 = new BiologicalSplSplAnalysisMap()
    replicate2Sample1.setBiologicalSample(bioSpl1)
    replicate2Sample1.setSampleAnalysis(splAnalysis1)
    replicate2Sample1.setSampleAnalysisNumber(1)
    
    val replicate2Sample2 = new BiologicalSplSplAnalysisMap()
    replicate2Sample2.setBiologicalSample(bioSpl1)
    replicate2Sample2.setSampleAnalysis(splAnalysis2)
    replicate2Sample2.setSampleAnalysisNumber(2)

    val replicate2Sample3 = new BiologicalSplSplAnalysisMap()
    replicate2Sample3.setBiologicalSample(bioSpl1)
    replicate2Sample3.setSampleAnalysis(splAnalysis3)
    replicate2Sample3.setSampleAnalysisNumber(3)
    
    val allSplAnalysis = new ArrayList[BiologicalSplSplAnalysisMap](3)
    allSplAnalysis.add(replicate2Sample1)
    allSplAnalysis.add(replicate2Sample2)
    allSplAnalysis.add(replicate2Sample3)
    val allBioSplReplicateMap = new HashSet[BiologicalSplSplAnalysisMap](allSplAnalysis)
    
    bioSpl1.setBiologicalSplSplAnalysisMap(allSplAnalysis)
    splAnalysis1.setBiologicalSplSplAnalysisMap(allBioSplReplicateMap)
    splAnalysis2.setBiologicalSplSplAnalysisMap(allBioSplReplicateMap)
    splAnalysis3.setBiologicalSplSplAnalysisMap(allBioSplReplicateMap)

    //Create QuantitationChannel
    val qCh1 = new QuantitationChannel()
    qCh1.setIdentResultSummaryId(1)
    qCh1.setContextKey("1.1")
    qCh1.setQuantitationDataset(qtDS)

    val qCh2 = new QuantitationChannel()
    qCh2.setIdentResultSummaryId(2)
    qCh2.setContextKey("1.2")
    qCh2.setQuantitationDataset(qtDS)

    val qCh3 = new QuantitationChannel()
    qCh3.setIdentResultSummaryId(targetRSMId)
    qCh3.setContextKey("1.3")
    qCh3.setQuantitationDataset(qtDS)
    
    //Create MasterQuantitationChannel
    val mqCh = new MasterQuantitationChannel()
    mqCh.setName("WSC Test")
    mqCh.setNumber(1)

    //Create link between QuantitationChannel  & BiologicalSample&SampleAnalysis
    val qChs = new ArrayList[QuantitationChannel](2)
    qChs.add(qCh1)
    qChs.add(qCh2)
    qChs.add(qCh3)
    qCh1.setNumber(1)
    qCh1.setSampleReplicate(splAnalysis1)
    qCh1.setBiologicalSample(bioSpl1)
    qCh2.setNumber(2)
    qCh2.setSampleReplicate(splAnalysis2)
    qCh2.setBiologicalSample(bioSpl1)
    qCh3.setNumber(3)
    qCh3.setSampleReplicate(splAnalysis3)
    qCh3.setBiologicalSample(bioSpl1)
    bioSpl1.setQuantitationChannels(qChs)
    splAnalysis1.setQuantitationChannels(qChs)// a verifier vds

    //Create link between MasterQuantitationChannel  & QuantitationChannels
    mqCh.setQuantitationChannels(qChs)
    qCh2.setMasterQuantitationChannel(mqCh)
    qCh3.setMasterQuantitationChannel(mqCh)
    qCh1.setMasterQuantitationChannel(mqCh)

    //Create link between MasterQCh  & Dataset
    mqCh.setDataset(qtDS)
    val mqChs = new ArrayList[MasterQuantitationChannel](1)
    mqChs.add(mqCh)
    qtDS.setMasterQuantitationChannels(mqChs)
    qtDS.setQuantitationChannels(qChs)

    udsEm.persist(qtDS)
    udsEm.persist(splAnalysis1)
    udsEm.persist(splAnalysis2)
    udsEm.persist(splAnalysis3)
    udsEm.persist(bioSpl1)

    udsEm.getTransaction().commit()

    var wsCalculator = new WeightedSpectralCountQuantifier(executionContext = executionContext, udsMasterQuantChannel = mqCh, quantConfig = spCountCfg)
    wsCalculator.quantify
    assertNotNull(mqCh.getQuantResultSummaryId())

  }

}

