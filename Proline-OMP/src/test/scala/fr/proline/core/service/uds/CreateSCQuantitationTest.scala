package fr.proline.core.service.uds

import org.junit.After
import org.junit.Assert._
import org.junit.Before
import org.junit.Test
import com.typesafe.scalalogging.StrictLogging
import fr.proline.context.IExecutionContext
import fr.proline.core.dal._
import fr.proline.core.dal.context._
import fr.proline.repository.DriverType
import fr.proline.core.om.model.msq.BiologicalGroup
import fr.proline.core.om.model.msq.BiologicalSample
import fr.proline.core.om.model.msq.QuantChannel
import fr.proline.core.om.model.msq.MasterQuantChannel
import fr.proline.core.om.model.msq.SimplifiedExperimentalDesign
import fr.proline.core.orm.uds.Project
import fr.proline.core.orm.uds.UserAccount

object CreateSCQuantitationTest extends AbstractEmptyDatastoreTestCase with StrictLogging {
  
  // Define the required parameters
  val driverType = DriverType.H2
  val useJPA = true
  
}

class CreateSCQuantitationTest extends StrictLogging {
  
  val executionContext = CreateSCQuantitationTest.executionContext
  require( executionContext != null, "executionContext is null")
  
  val udsUser = new UserAccount()
  val udsProject = new Project(udsUser)
  
  val udsDbCtx = executionContext.getUDSDbConnectionContext()
  
  udsDbCtx.tryInTransaction {
    
    val udsEM = udsDbCtx.getEntityManager
    
    udsUser.setLogin("proline_user2")
    udsUser.setPasswordHash("993bd037c1ae6b87ea5adb936e71fca01a3b5c0505855444afdfa2f86405db1d")
    udsUser.setCreationMode("MANUAL")
    udsEM.persist(udsUser)
    
    udsProject.setName("New quantitative project")
    udsProject.setDescription("Project created for the CreateSCQuantitationTest")
    udsProject.setCreationTimestamp( new java.sql.Timestamp( new java.util.Date().getTime ) )
    udsEM.persist(udsProject)
    
  }

  /*// Define the interface to be implemented
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

  @Test
  def quantifyRSMSC() {

    // Create BiologicalSamples
    val bioSplsBuilder = Array.newBuilder[BiologicalSample]
    val bioSpl1 = new BiologicalSample(number = 1, name = "WSC Test BioSpl")
    bioSplsBuilder += bioSpl1

    val bioSpl2 = new BiologicalSample(number = 2, name = "WSC Test BioSpl 2")
    bioSplsBuilder += bioSpl2

    val splNbrs = Array.newBuilder[Int]
    splNbrs += 1
    splNbrs += 2

    val bioGrpBuilder = Array.newBuilder[BiologicalGroup]
    val bioGrp1 = new BiologicalGroup(number = 1, name = "WSC Test BioGrp ", sampleNumbers = splNbrs.result)
    bioGrpBuilder += bioGrp1

    val masterQChsBuilder = Array.newBuilder[MasterQuantChannel]
    val qChsBuilder = Array.newBuilder[QuantChannel]

    val qCh1 = new QuantChannel(number = 1, name = "qCh1", sampleNumber = 1, identResultSummaryId = 1)
    qChsBuilder += qCh1
    val qCh2 = new QuantChannel(number = 2, name = "qCh2", sampleNumber = 2, identResultSummaryId = 2)
    qChsBuilder += qCh2

    val mqCh1 = new MasterQuantChannel(number = 1, name = Some("CreateQttDSTest"), quantChannels = qChsBuilder.result)
    masterQChsBuilder += mqCh1

    // TODO: replace me by usual ExperimentalDesign
    val expDesi = SimplifiedExperimentalDesign(
      biologicalSamples = bioSplsBuilder.result,
      biologicalGroups = bioGrpBuilder.result,
      masterQuantChannels = masterQChsBuilder.result
    )

    val service = new CreateSCQuantitation(
      executionContext = executionContext,
      name = "CreateQttDS Test",
      description = "  TEST ",
      projectId = udsProject.getId,
      experimentalDesign = expDesi.toExperimentalDesign()
    )
    service.runService()
    val udsDS = service.getUdsQuantitation

    assertEquals("CreateQttDS Test", udsDS.getName())
    assertEquals(2, udsDS.getBiologicalSamples().size())
    assertEquals(2, udsDS.getSampleReplicates().size())
    assertEquals(1, udsDS.getMasterQuantitationChannels().size())
  }

}

