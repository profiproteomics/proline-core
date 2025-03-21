package fr.proline.core.om.provider.msi.impl

import com.typesafe.scalalogging.StrictLogging
import fr.proline.context.MsiDbConnectionContext
import fr.proline.core.om.model.msi._
import fr.proline.repository.ProlineDatabaseType
import fr.proline.repository.util.DatabaseTestCase
import org.junit.Assert._
import org.junit.{After, Before, Test}

import scala.collection.mutable.ArrayBuffer

@Test
class ORMPeptideProviderTest extends DatabaseTestCase with StrictLogging {

  private val SEQ_TO_FOUND: String = "LTGMAFR"

  override def getProlineDatabaseType() = ProlineDatabaseType.MSI

  @Before
  @throws(classOf[Exception])
  def setUp() = {
    logger.info("Initializing MSIdb")
    initDatabase()
    // TODO: fix file paths (was ps instead of msi)
    loadCompositeDataSet(Array("/dbunit/Init/msi-db.xml","/dbunit/datasets/msi/Peptides_Dataset.xml"))
  }

  override def getPropertiesFileName(): String = "db_settings/h2/db_msi.properties"
  
  @Test
  def getSinglePeptide() = {
    val msiDb = new MsiDbConnectionContext(this.getConnector)

    try {
      val ormPepProvider = new ORMPeptideProvider(msiDb)

      val pepOpt = ormPepProvider.getPeptide(4)
      assertNotNull(pepOpt)
      assertNotSame(pepOpt, None)
      assertEquals(pepOpt.get.calculatedMass, 810.405807, 0.0001)
    } finally {
      msiDb.close()
    }

  }
  
  @Test
  def getPeptideWithMultiplePtmSpecificities() = {
    val msiDb = new MsiDbConnectionContext(getConnector)

    try {
      val ormPepProvider = new ORMPeptideProvider(msiDb)

      val pep: Option[Peptide] = ormPepProvider.getPeptide(7);
      assertNotNull(pep);
      assertNotSame(pep, None);
      assertEquals(pep.get.ptms.head.definition.neutralLosses.size,2)
      assertEquals(pep.get.ptms.head.definition.classification, "Post-translational")
    } finally {
      msiDb.close()
    }

  }

  @Test
  def getMultiplePeptides() = {
    val ids = new ArrayBuffer[Long]
    ids += 0
    ids += 1
    ids += 4

   val msiDb = new MsiDbConnectionContext(getConnector)

    try {
      val ormPepProvider = new ORMPeptideProvider(msiDb)

      val peps: Array[Option[Peptide]] = ormPepProvider.getPeptidesAsOptions(ids)
      assertNotNull(peps)
      assertEquals(peps.length,3)
      assertEquals(peps.apply(2).get.id, 4L)
      assertEquals(peps(2).get.calculatedMass, 810.405807,0.0001)
    } finally {
      msiDb.close()
    }

  }

  @Test
  def getPeptideWithNTermPTM() = {
    val msiDb = new MsiDbConnectionContext(getConnector)

    try {
      val ormPepProvider = new ORMPeptideProvider(msiDb)

      val pep: Option[Peptide] = ormPepProvider.getPeptide(6)
      assertNotNull(pep)
      assertNotSame(pep, None)

      assertEquals(pep.get.id, 6L)
      assertEquals(pep.get.ptms.length, 1)
      assertEquals(pep.get.ptms(0).definition.names.shortName, "Acetyl")
      assertTrue(pep.get.ptms(0).isNTerm)
    } finally {      
      msiDb.close()
    }

  }

  @Test
  def getPeptideOnSeqAndNoPtms() = {
   val msiDb = new MsiDbConnectionContext(getConnector)

    try {
      val ormPepProvider = new ORMPeptideProvider(msiDb)

      val ptms = new Array[LocatedPtm](0)
      val pep: Option[Peptide] = ormPepProvider.getPeptide(SEQ_TO_FOUND, ptms)
      assertNotNull(pep)
      assertNotSame(pep, None)
      assertTrue(pep.get.ptms == null || pep.get.ptms.length == 0)
    } finally {     
      msiDb.close()
    }

  }

  @Test
  def getPeptideOnSeqAndPtms() = {
   val msiDb = new MsiDbConnectionContext(getConnector)

    try {
      val ormPepProvider = new ORMPeptideProvider(msiDb)

      val ptmsBuilder = Array.newBuilder[LocatedPtm]

      val ptmEvi: PtmEvidence = new PtmEvidence(IonTypes.Precursor, "", Double.MaxValue, Double.MaxValue, false)
      val ptmEvidences = Array[PtmEvidence](ptmEvi)

      val ptmDef = new PtmDefinition(0, "ANYWHERE", new PtmNames("Oxidation", "Oxidation or Hydroxylation"), ptmEvidences, 'M', null, 0);
      ptmsBuilder += new LocatedPtm(ptmDef, 3, Double.MaxValue, Double.MaxValue, "O", false, false)

      /*val provTest = new fr.proline.core.om.provider.msi.impl.ORMPTMProvider( this.em )
	  val ptmDefs = provTest.getPtmDefinitions(List(1,2,30))
	  ptmDefs.foreach { ptm => println(ptm.get.names.shortName ) }*/

      val pep: Option[Peptide] = ormPepProvider.getPeptide(SEQ_TO_FOUND, ptmsBuilder.result())
      assertNotNull(pep)
      assertNotSame(pep, None)
      assertEquals(pep.get.ptms.length,1)
      assertEquals(pep.get.ptms(0).seqPosition, 3)
    } finally {
      msiDb.close()
    }

  }

  @After
  override def tearDown() = {
    super.tearDown()
  }

}