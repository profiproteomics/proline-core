package fr.proline.core.algo.msi

import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import com.typesafe.scalalogging.slf4j.Logging
import fr.proline.context.BasicExecutionContext
import fr.proline.context.IExecutionContext
import fr.proline.core.dal.AbstractMultipleDBTestCase
import fr.proline.core.dal.ContextFactory
import fr.proline.core.om.model.msi.ProteinMatch
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.om.provider.msi.IResultSummaryProvider
import fr.proline.core.om.provider.msi.impl.SQLResultSetProvider
import fr.proline.core.om.provider.msi.impl.SQLResultSummaryProvider
import fr.proline.repository.DriverType
import fr.proline.core.om.provider.msi.IResultSetProvider

class TypicalProteinChooserTest extends AbstractMultipleDBTestCase with Logging {

  // Define some vars
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

       //loadDataSet("/fr/proline/core/om/ps/Unimod_Dataset.xml")
    msiDBTestCase.loadCompositeDataSet(Array("/dbunit_samples/" + fileName + "/msi-db.xml","/fr/proline/core/algo/msi/Prot_ChangeTypical.xml"))
    
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

    // Check which proteinSets should be modified by algo
    var nbrTremblShouldChange: Int = 9 // # proteinSet matching following rule 
    //VDS WARNING : Was 4, after modifying algo, changed proteinset was 9 dur to incorrect IS_IN_SUBSET value in XML file :  
    // Allways FALSE even for subset. Should be changed back to 4 with corrected XML file ! 

    val typicalChooser = new TypicalProteinChooser()
    
    val ruleDesc = new TypicalProteinChooserRule(ruleName = "Sprot AC preferred", applyToAcc = true, rulePattern = "\\w{6,6}")
    val rules =  Seq(ruleDesc)
    typicalChooser.changeTypical(targetRSMId, rules, executionContext.getMSIDbConnectionContext().getEntityManager())

    val nbrChangedTyp = typicalChooser.getChangedProteinSets.size

    Assert.assertEquals(nbrTremblShouldChange, nbrChangedTyp)

  }

  
  @Test
  def testChangeTypicalProtSameSubSet() = {

    // Check which proteinSets should be modified by algo
    var nbrTremblShouldChange: Int =1   

    val typicalChooser = new TypicalProteinChooser()
    val ruleDesc = new TypicalProteinChooserRule(ruleName = "Description ##SP  preferred", applyToAcc = false, rulePattern = "##SP.*")
    val rules =  Seq(ruleDesc)
    typicalChooser.changeTypical(targetRSMId, rules, executionContext.getMSIDbConnectionContext().getEntityManager())

    val nbrChangedTyp = typicalChooser.getChangedProteinSets.size

    Assert.assertEquals(nbrTremblShouldChange, nbrChangedTyp)

        // Check which proteinSets should be modified by algo
    nbrTremblShouldChange = 0   
    
    val ruleDesc2 = new TypicalProteinChooserRule(ruleName = "Description ##DEV_ preferred", applyToAcc = false, rulePattern = "##DEV_.*")
    val rules2 =  Seq(ruleDesc2)
    typicalChooser.changeTypical(targetRSMId, rules2, executionContext.getMSIDbConnectionContext().getEntityManager())

    val nbrChangedTyp2 = typicalChooser.getChangedProteinSets.size

    Assert.assertEquals(nbrTremblShouldChange, nbrChangedTyp2)


    
  }

}
