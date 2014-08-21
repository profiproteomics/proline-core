package fr.proline.core.service.uds

import org.junit.After
import org.junit.Assert._
import org.junit.Before
import org.junit.Test
import com.typesafe.scalalogging.slf4j.Logging
import fr.proline.context.IExecutionContext
import fr.proline.core.dal.ContextFactory
import fr.proline.core.dal.AbstractMultipleDBTestCase
import fr.proline.repository.DriverType
import fr.proline.core.om.model.msq.BiologicalGroup
import fr.proline.core.om.model.msq.BiologicalSample
import fr.proline.core.om.model.msq.QuantChannel
import fr.proline.core.om.model.msq.MasterQuantChannel
import fr.proline.core.om.model.msq.ExperimentalDesign2

@Test
class CreateSCQuantitationTest extends AbstractMultipleDBTestCase with Logging {

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
  }

  @Test
  def quantifyRSMSC() = {
    //  Validate RS to generate RSM
    //Create BiologicalSamples
    val bioSplsBuilder = Array.newBuilder[BiologicalSample]
    val bioSpl1 = new BiologicalSample(number=1,name="WSC Test BioSpl")
    bioSplsBuilder+=bioSpl1
    
    val bioSpl2  = new BiologicalSample(number=2,name="WSC Test BioSpl 2")
    bioSplsBuilder+=bioSpl2
       
    val splNbrs = Array.newBuilder[Int]
    splNbrs += 1
    splNbrs += 2
        
  	val bioGrpBuilder = Array.newBuilder[BiologicalGroup]
    val bioGrp1 = new BiologicalGroup(number=1,name="WSC Test BioGrp ",sampleNumbers=splNbrs.result)
    bioGrpBuilder +=bioGrp1
    
    val masterQChsBuilder = Array.newBuilder[MasterQuantChannel]
	val qChsBuilder = Array.newBuilder[QuantChannel]
    
    val qCh1 = new QuantChannel(number=1,sampleNumber=1, identResultSummaryId=1)
    qChsBuilder += qCh1
    val qCh2 = new QuantChannel(number=2,sampleNumber=2, identResultSummaryId=2)
    qChsBuilder += qCh2
    
    val mqCh1 = new MasterQuantChannel(number=1,name=Some("CreateQttDSTest"), quantChannels=qChsBuilder.result)
    masterQChsBuilder += mqCh1
    
    val expDesi = new ExperimentalDesign2(biologicalSamples =bioSplsBuilder.result,biologicalGroups=bioGrpBuilder.result,masterQuantChannels = masterQChsBuilder.result   )
    
    val service = new CreateSCQuantitation(executionContext = executionContext, name="CreateQttDS Test",description="  TEST ", projectId=1,experimentalDesign=expDesi )
    service.runService() 
    val udsDS = service.getUdsQuantitation
    
    assertEquals("CreateQttDS Test", udsDS.getName())
    assertEquals(2, udsDS.getBiologicalSamples().size())
    assertEquals(2, udsDS.getSampleReplicates().size())
    assertEquals(1, udsDS.getMasterQuantitationChannels().size())
  }

}

