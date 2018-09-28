package fr.proline.core.om.storer.msi.impl

import com.typesafe.scalalogging.StrictLogging
import fr.proline.core.dal.AbstractDatastoreTestCase
import fr.proline.core.dbunit.{DbUnitResultFileLocation, STR_F122817_Mascot_v2_3}
import fr.proline.core.om.model.msi._
import fr.proline.repository.DriverType
import org.junit.Assert._
import org.junit._
import org.scalatest.MustMatchers

object JPAPtmDefinitionStorerTest extends AbstractDatastoreTestCase with StrictLogging {

  override val driverType = DriverType.H2
  override val dbUnitResultFile: DbUnitResultFileLocation = STR_F122817_Mascot_v2_3
  override val useJPA: Boolean = true

  val targetRSMId: Long = 1L

  var lastPtmSpecifId: Long = -1L
  var amidatedPtmDef: PtmDefinition = null

  @BeforeClass
  @throws(classOf[Exception])
  override def setUp() = {
    super.setUp()

    val msiDbCtx = executionContext.getMSIDbConnectionContext()
    val msiEM = msiDbCtx.getEntityManager

    // Retrieve the last PTM specificity id
    lastPtmSpecifId = msiEM.createQuery(
      "SELECT ptmSpecif.id FROM fr.proline.core.orm.msi.PtmSpecificity ptmSpecif ORDER BY ptmSpecif.id DESC"
    ).setMaxResults(1).getSingleResult().asInstanceOf[Long]

    // Create a PTM def which will be used for the tests
    amidatedPtmDef = new PtmDefinition(
      id = PtmDefinition.generateNewId,
      location = "Protein C-term",
      names = PtmNames("Amidated","Amidation"),
      ptmEvidences = Array(PtmEvidence( IonTypes.Precursor, "H N O(-1)", -0.984016, -0.9848) ),
      classification = "Post-translational",
      ptmId = 0,
      unimodId = 2
    )
  }
}

class JPAPtmDefinitionStorerTest extends MustMatchers with StrictLogging {
  

  // --- Case #1: a PTM which already exists in the database (same name, same specificity) ---
  @Test
  def test1_3StoreDuplicatedPTM() {
    
    // Retrieve the tested PTM
    val carbamidoPtmDef = new PtmDefinition(
      id = PtmDefinition.generateNewId,
      location = "Anywhere",
      residue = 'C',
      names = PtmNames("Carbamidomethyl","Iodoacetamide derivative"),
      ptmEvidences = Array(PtmEvidence( IonTypes.Precursor, "H(3) C(2) N O", 57.021464, 57.0513) ),
      classification = "Chemical derivative",
      ptmId = 0,
      unimodId = 2
    )
    
    // Store the PTM definition
    JPAPtmDefinitionStorerTest.executionContext.getMSIDbConnectionContext.beginTransaction()
    val insertedPtmCount = JPAPtmDefinitionStorer.storePtmDefinitions(Seq(carbamidoPtmDef), JPAPtmDefinitionStorerTest.executionContext)
    JPAPtmDefinitionStorerTest.executionContext.getMSIDbConnectionContext.commitTransaction()
    
    // Check that no PTM definition has been inserted
    assertEquals(0, insertedPtmCount)
    
    // Check that the PTM definition id has been updated and that no new PTM specificity has been inserted
    carbamidoPtmDef.id must be > 0L
    carbamidoPtmDef.id must be <= JPAPtmDefinitionStorerTest.lastPtmSpecifId
    
    ()
  }
  
  // --- Case #2: a PTM which already exists in the database but with a new specificity ---
  @Test
  def test1_2StoreExistingPTMButNewSpecif() {
    
    // Location "Protein C-term" is replaced by "C-term"
    val amidatedPtmDefCterm = JPAPtmDefinitionStorerTest.amidatedPtmDef.copy(
      id = PtmDefinition.generateNewId,
      location = "C-term"
    )
    
    // Store the PTM definition
    JPAPtmDefinitionStorerTest.executionContext.getMSIDbConnectionContext.beginTransaction()
    val insertedPtmCount = JPAPtmDefinitionStorer.storePtmDefinitions(Seq(amidatedPtmDefCterm), JPAPtmDefinitionStorerTest.executionContext)
    JPAPtmDefinitionStorerTest.executionContext.getMSIDbConnectionContext.commitTransaction()
    
    // Check that one PTM definition has been inserted
    assertEquals(1, insertedPtmCount)
    
    // Check that the PTM definition id has been updated and that a new PTM specificity has been inserted
    amidatedPtmDefCterm.id must be > JPAPtmDefinitionStorerTest.lastPtmSpecifId
    
    ()
  }
  
  // --- Case #3: a PTM which already exists in the database but with a new specificity ---
  @Test
  def test1_1StoreExistingPTMButDifferentComposition() {
    
    // Composition "H N O(-1)" is replaced by "N O(-1)"
    val amidatedPtmDefNewComp = JPAPtmDefinitionStorerTest.amidatedPtmDef.copy(
      id = PtmDefinition.generateNewId,
      ptmEvidences = Array( JPAPtmDefinitionStorerTest.amidatedPtmDef.precursorDelta.copy(composition = "N O(-1)") )
    )
    
    // Check that an IllegalArgumentException is thrown for this case
    intercept[IllegalArgumentException] {
      // Try to store the PTM definition
      JPAPtmDefinitionStorer.storePtmDefinitions(Seq(amidatedPtmDefNewComp), JPAPtmDefinitionStorerTest.executionContext)
    }
    
    ()
  }
  
  // --- Case #4: a new PTM without composition ---
  @Test
  def test2_1StoreNewPTMButWithoutComposition() {
    
    // Names are replaced and composition is emptied
    val newPtmDefNoComp = JPAPtmDefinitionStorerTest.amidatedPtmDef.copy(
      id = PtmDefinition.generateNewId,
      names = PtmNames("ProFI Amidated","ProFI Amidation"),
      ptmEvidences = Array( JPAPtmDefinitionStorerTest.amidatedPtmDef.precursorDelta.copy(composition = "") )
    )
    
    // Check that an IllegalArgumentException is thrown for this case
    intercept[IllegalArgumentException] {
      // Try to store the PTM definition
      JPAPtmDefinitionStorer.storePtmDefinitions(Seq(newPtmDefNoComp), JPAPtmDefinitionStorerTest.executionContext)
    }
    
    ()
  }
  
  // --- Case #5: a new PTM without composition ---
  @Test
  def test2_2StorePTMAlias() {
    
    // Names are replaced and composition is emptied
    val amidatedPtmAlias = JPAPtmDefinitionStorerTest.amidatedPtmDef.copy(
      id = PtmDefinition.generateNewId,
      names = PtmNames("ProFI Amidated","ProFI Amidation")
    )
    
    // Store the PTM definition
    JPAPtmDefinitionStorerTest.executionContext.getMSIDbConnectionContext.beginTransaction()
    val insertedPtmCount = JPAPtmDefinitionStorer.storePtmDefinitions(Seq(amidatedPtmAlias), JPAPtmDefinitionStorerTest.executionContext)
    JPAPtmDefinitionStorerTest.executionContext.getMSIDbConnectionContext.commitTransaction()
    
    // Check that one PTM definition has been inserted
    assertEquals(1, insertedPtmCount)
    
    // Check that the PTM definition id has been updated and that a new PTM specificity has been inserted
    amidatedPtmAlias.id must be > JPAPtmDefinitionStorerTest.lastPtmSpecifId
    
    ()
  }

}