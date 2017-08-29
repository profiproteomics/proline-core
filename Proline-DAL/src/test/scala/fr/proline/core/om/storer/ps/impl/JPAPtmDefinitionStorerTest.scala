package fr.proline.core.om.storer.ps.impl

import org.junit._
import org.junit.Assert._
import com.typesafe.scalalogging.StrictLogging
import fr.proline.context.IExecutionContext
import fr.proline.core.dal.BuildLazyExecutionContext
import fr.proline.core.om.model.msi._
import fr.proline.core.dal.AbstractMultipleDBTestCase
import fr.proline.repository.DriverType
import org.scalatest.MustMatchers
import fr.proline.context.DatabaseConnectionContext
import javax.persistence.EntityManager

/**
 * @author David Bouyssie
 *
 */
case class JPAPtmDefinitionStorerTestContext(
  executionContext: IExecutionContext
) {
  require( executionContext != null )
  
  val psDbCtx = executionContext.getPSDbConnectionContext()
  val psEM = psDbCtx.getEntityManager
  
  // Retrieve the last PTM specificity id 
  val lastPtmSpecifId = psEM.createQuery(
    "SELECT ptmSpecif.id FROM fr.proline.core.orm.ps.PtmSpecificity ptmSpecif ORDER BY ptmSpecif.id DESC"
  ).setMaxResults(1).getSingleResult().asInstanceOf[Long]
  
  // Create a PTM def which will be used for the tests
  val amidatedPtmDef = new PtmDefinition(
    id = PtmDefinition.generateNewId,
    location = "Protein C-term",
    names = PtmNames("Amidated","Amidation"),
    ptmEvidences = Array(PtmEvidence( IonTypes.Precursor, "H N O(-1)", -0.984016, -0.9848) ),
    classification = "Post-translational",
    ptmId = 0,
    unimodId = 2
  )
}

object JPAPtmDefinitionStorerTest extends AbstractMultipleDBTestCase with StrictLogging {

  // Define some vars
  val driverType = DriverType.H2
  val fileName = "STR_F122817_Mascot_v2.3"
  val targetRSMId: Long = 1
  var testContext: JPAPtmDefinitionStorerTestContext = null
  
  @BeforeClass
  @throws(classOf[Exception])
  def setUp() = {

    logger.info("Initializing DBs")
    super.initDBsDBManagement(driverType)
    
    // Load DataSets
    psDBTestCase.loadDataSet("/dbunit_samples/" + fileName + "/ps-db.xml")
    
    logger.info("PS db successfully initialized !")
    
    val execCtx = BuildLazyExecutionContext(dsConnectorFactoryForTest, 1, true) // Full JPA
    testContext = JPAPtmDefinitionStorerTestContext( execCtx )
  }

  @AfterClass
  override def tearDown() {
    if (testContext != null) testContext.executionContext.closeAll()
    super.tearDown()
  }
  
}

class JPAPtmDefinitionStorerTest extends MustMatchers with StrictLogging {
  
  val testCtx = JPAPtmDefinitionStorerTest.testContext
  
  // --- Case #1: a PTM which already exists in the database (same name, same specificity) ---
  @Test
  def test1_3StoreDuplicatedPTM() {
    
    // Retrieve the tested PTM
    val amidatedPtmDef = testCtx.amidatedPtmDef
    
    // Store the PTM definition
    testCtx.psDbCtx.beginTransaction()
    val insertedPtmCount = JPAPtmDefinitionStorer.storePtmDefinitions(Seq(amidatedPtmDef), testCtx.executionContext)
    testCtx.psDbCtx.commitTransaction()
    
    // Check that no PTM definition has been inserted
    assertEquals(0, insertedPtmCount)
    
    // Check that the PTM definition id has been updated and that no new PTM specificity has been inserted
    amidatedPtmDef.id must be > 0L
    amidatedPtmDef.id must be <= testCtx.lastPtmSpecifId
    
    ()
  }
  
  // --- Case #2: a PTM which already exists in the database but with a new specificity ---
  @Test
  def test1_2StoreExistingPTMButNewSpecif() {
    
    // Location "Protein C-term" is replaced by "C-term"
    val amidatedPtmDefCterm = testCtx.amidatedPtmDef.copy(
      id = PtmDefinition.generateNewId,
      location = "C-term"
    )
    
    // Store the PTM definition
    testCtx.psDbCtx.beginTransaction()
    val insertedPtmCount = JPAPtmDefinitionStorer.storePtmDefinitions(Seq(amidatedPtmDefCterm), testCtx.executionContext)
    testCtx.psDbCtx.commitTransaction()
    
    // Check that one PTM definition has been inserted
    assertEquals(1, insertedPtmCount)
    
    // Check that the PTM definition id has been updated and that a new PTM specificity has been inserted
    amidatedPtmDefCterm.id must be > testCtx.lastPtmSpecifId
    
    ()
  }
  
  // --- Case #3: a PTM which already exists in the database but with a new specificity ---
  @Test
  def test1_1StoreExistingPTMButDifferentComposition() {
    
    // Composition "H N O(-1)" is replaced by "N O(-1)"
    val amidatedPtmDefNewComp = testCtx.amidatedPtmDef.copy(
      id = PtmDefinition.generateNewId,
      ptmEvidences = Array( testCtx.amidatedPtmDef.precursorDelta.copy(composition = "N O(-1)") )
    )
    
    // Check that an IllegalArgumentException is thrown for this case
    intercept[IllegalArgumentException] {
      // Try to store the PTM definition
      JPAPtmDefinitionStorer.storePtmDefinitions(Seq(amidatedPtmDefNewComp), testCtx.executionContext)
    }
    
    ()
  }
  
  // --- Case #4: a new PTM without composition ---
  @Test
  def test2_1StoreNewPTMButWithoutComposition() {
    
    // Names are replaced and composition is emptied
    val newPtmDefNoComp = testCtx.amidatedPtmDef.copy(
      id = PtmDefinition.generateNewId,
      names = PtmNames("ProFI Amidated","ProFI Amidation"),
      ptmEvidences = Array( testCtx.amidatedPtmDef.precursorDelta.copy(composition = "") )
    )
    
    // Check that an IllegalArgumentException is thrown for this case
    intercept[IllegalArgumentException] {
      // Try to store the PTM definition
      JPAPtmDefinitionStorer.storePtmDefinitions(Seq(newPtmDefNoComp), testCtx.executionContext)
    }
    
    ()
  }
  
  // --- Case #5: a new PTM without composition ---
  @Test
  def test2_2StorePTMAlias() {
    
    // Names are replaced and composition is emptied
    val amidatedPtmAlias = testCtx.amidatedPtmDef.copy(
      id = PtmDefinition.generateNewId,
      names = PtmNames("ProFI Amidated","ProFI Amidation")
    )
    
    // Store the PTM definition
    testCtx.psDbCtx.beginTransaction()
    val insertedPtmCount = JPAPtmDefinitionStorer.storePtmDefinitions(Seq(amidatedPtmAlias), testCtx.executionContext)
    testCtx.psDbCtx.commitTransaction()
    
    // Check that one PTM definition has been inserted
    assertEquals(1, insertedPtmCount)
    
    // Check that the PTM definition id has been updated and that a new PTM specificity has been inserted
    amidatedPtmAlias.id must be > testCtx.lastPtmSpecifId
    
    ()
  }

}