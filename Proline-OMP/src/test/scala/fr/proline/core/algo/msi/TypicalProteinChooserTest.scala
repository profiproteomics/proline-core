package fr.proline.core.algo.msi

import scala.collection.JavaConversions.collectionAsScalaIterable
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import com.weiglewilczek.slf4s.Logging
import fr.proline.context.IExecutionContext
import fr.proline.core.dal.ContextFactory
import fr.proline.core.om.utils.AbstractMultipleDBTestCase
import fr.proline.core.orm.msi.ProteinSet
import fr.proline.repository.DriverType
import fr.proline.core.service.msi.RSMTypicalProteinChooser

class TypicalProteinChooserTest extends AbstractMultipleDBTestCase with Logging {
  // Define the interface to be implemented
  val driverType = DriverType.H2
  val fileName = "Merged_RSM_Test"
  val targetRSMId: Long = 2

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
  def testChangeTypicalProt() = {

	//Verify which proteinSets should be modified by algo
	var nbrTremblTPM:Int = 964    // # proteinSet with Trembl typical protein match
	var nbrTremblShouldChange : Int = 951 // # proteinSet with Trembl typical protein match but which have "sp" proteinmatch in their sameset 
	
    
	val ruleDesc = new TypicalProteinChooserRule(ruleName="Sp chooser", applyToAcc=false,rulePattern="^sp.*" )
	val typicalChooserService = new RSMTypicalProteinChooser(executionContext,targetRSMId,ruleDesc)
	typicalChooserService.runService
//  	typicalChooser.changeTypical(targetRSMId,ruleDesc,executionContext.getMSIDbConnectionContext().getEntityManager())
  	
  	val nbrChangedTyp = typicalChooserService.getChangedProteinSetsCount

    Assert.assertEquals(nbrTremblShouldChange, nbrChangedTyp)
	
    val msiEM = executionContext.getMSIDbConnectionContext().getEntityManager()
    msiEM.clear()
    val ormProtSetRSM = msiEM.createQuery("FROM fr.proline.core.orm.msi.ProteinSet protSet WHERE resultSummary.id = :rsmId", 
    		  	classOf[fr.proline.core.orm.msi.ProteinSet]).setParameter("rsmId",targetRSMId).getResultList().toList
    var nbrTblTypicalPS = 0		  	
    ormProtSetRSM.foreach(ps =>{
    	val typicalPSPMI  = ps.getProteinSetProteinMatchItems().filter(pspmi => { pspmi.getProteinMatch().getId() == ps.getProteinMatchId()}).toSeq
    	var currentTypicalDesc =typicalPSPMI(0).getProteinMatch().getDescription()
    	if(currentTypicalDesc.startsWith("tr"))
    	  nbrTblTypicalPS+=1
    })
  
      Assert.assertEquals(nbrTblTypicalPS, nbrTremblTPM-nbrTremblShouldChange)
  }
  
  
}
