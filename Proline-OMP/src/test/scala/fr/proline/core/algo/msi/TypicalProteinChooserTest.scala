package fr.proline.core.algo.msi

import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import com.weiglewilczek.slf4s.Logging
import fr.proline.context.BasicExecutionContext
import fr.proline.context.IExecutionContext
import fr.proline.core.dal.ContextFactory
import fr.proline.core.dal.SQLConnectionContext
import fr.proline.core.om.model.msi.ProteinMatch
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.om.provider.msi.IResultSummaryProvider
import fr.proline.core.om.provider.msi.impl.SQLResultSetProvider
import fr.proline.core.om.provider.msi.impl.SQLResultSummaryProvider
import fr.proline.core.om.utils.AbstractMultipleDBTestCase
import fr.proline.repository.DriverType
import fr.proline.core.om.provider.msi.IResultSetProvider

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
	
    val typicalChooser = new TypicalProteinChooser()
	val ruleDesc = new TypicalProteinChooserRule(ruleName="Sp chooser", applyToAcc=false,rulePattern="^sp.*" )
  	typicalChooser.changeTypical(targetRSMId,ruleDesc,executionContext.getMSIDbConnectionContext().getEntityManager())
  	
  	val nbrChangedTyp = typicalChooser.getChangedProteinSets.size

    Assert.assertEquals(nbrTremblShouldChange, nbrChangedTyp)
	
  }
  
  
}
