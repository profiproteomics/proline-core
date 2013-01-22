package fr.proline.core.om.provider.msi.impl

import scala.collection.mutable.ArrayBuffer
import org.hamcrest.CoreMatchers
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertThat
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import fr.proline.core.om.model.msi._
import fr.proline.repository.util.JPAUtils
import fr.proline.repository.utils.DatabaseUtils
import fr.proline.repository.utils.DatabaseTestCase
import fr.proline.repository.Database
import fr.proline.repository.DriverType
import fr.proline.core.dal.ProlineEzDBC
import fr.proline.repository.IDatabaseConnector
import fr.proline.repository.DatabaseContext

@Test
class SQLPeptideProviderTest extends DatabaseTestCase {

  private val SEQ_TO_FOUND: String = "LTGMAFR"

  override def getDatabase() = Database.PS

  @Before
  @throws(classOf[Exception])
  def setUp() = {
    initDatabase()

    loadDataSet("/fr/proline/core/om/ps/Unimod_Dataset.xml")
  }

  def newSqlDbContext(connector: IDatabaseConnector) = {
    new DatabaseContext(connector.getDataSource().getConnection(),connector.getDriverType())
  }
  
  @Test
  def getSinglePeptide() = {
    val psDb = newSqlDbContext( getConnector() )

    try {
      val ezDbc = ProlineEzDBC(psDb)

      val sqlPepProvider = new SQLPeptideProvider(psDb,ezDbc)

      val pep: Option[Peptide] = sqlPepProvider.getPeptide(4)
      assertThat(pep, CoreMatchers.notNullValue());
      assertNotSame(pep, None);
      assertThat(pep.get.calculatedMass, CoreMatchers.equalTo(810.405807));

    } finally {
      psDb.close
    }

  }

  @Test
  def getMultiplePeptides() = {
    val ids = new ArrayBuffer[Int]
    ids += 0
    ids += 1
    ids += 4

    val psDb = newSqlDbContext( getConnector() )

    try {
      val ezDbc = ProlineEzDBC(psDb)

      val sqlPepProvider = new SQLPeptideProvider(psDb,ezDbc)

      val peps: Array[Option[Peptide]] = sqlPepProvider.getPeptidesAsOptions(ids)
      assertThat(peps, CoreMatchers.notNullValue())
      assertThat(peps.length, CoreMatchers.equalTo(3))
      assertThat(peps.apply(2).get.id, CoreMatchers.equalTo(4))
      assertThat(peps(2).get.calculatedMass, CoreMatchers.equalTo(810.405807))

    } finally {
      psDb.close
    }

  }

  @Test
  def getPeptideWithNTermPTM() = {
    
    val psDb = newSqlDbContext( getConnector() )

    try {
      val ezDbc = ProlineEzDBC(psDb)

      val sqlPepProvider = new SQLPeptideProvider(psDb,ezDbc)

      val pep: Option[Peptide] = sqlPepProvider.getPeptide(6)
      assertThat(pep, CoreMatchers.notNullValue())
      assertNotSame(pep, None);

      assertThat(pep.get.id, CoreMatchers.equalTo(6))
      assertThat(pep.get.ptms.length, CoreMatchers.equalTo(1))
      assertThat(pep.get.ptms(0).definition.names.shortName, CoreMatchers.equalTo("Acetyl"))
      assertTrue(pep.get.ptms(0).isNTerm)

    } finally {
      psDb.close
    }

  }

  @Test
  def getPeptideOnSeqAndNoPtms() = {
    
    val psDb = newSqlDbContext( getConnector() )
    
    try {
      val ezDbc = ProlineEzDBC(psDb)

      val sqlPepProvider = new SQLPeptideProvider(psDb,ezDbc)

      val ptms = new Array[LocatedPtm](0)
      val pep: Option[Peptide] = sqlPepProvider.getPeptide(SEQ_TO_FOUND, ptms)
      assertThat(pep, CoreMatchers.notNullValue());
      assertNotSame(pep, None);
      assertTrue(pep.get.ptms == null || pep.get.ptms.length == 0);

    } finally {
      psDb.close
    }

  }

  @Test
  def getPeptideOnSeqAndPtms() = {
    
    val psDb = newSqlDbContext( getConnector() )

    try {
      val ezDbc = ProlineEzDBC(psDb)

      val sqlPepProvider = new SQLPeptideProvider(psDb,ezDbc)

      var ptmsBuilder = Array.newBuilder[LocatedPtm]

      val ptmEvi: PtmEvidence = new PtmEvidence(IonTypes.Precursor, "", Double.MaxValue, Double.MaxValue, false)
      val ptmEvidences = Array[PtmEvidence](ptmEvi)

      val ptmDef = new PtmDefinition(0, "ANYWHERE", new PtmNames("Oxidation", "Oxidation or Hydroxylation"), ptmEvidences, 'M', null, 0);
      ptmsBuilder += new LocatedPtm(ptmDef, 3, Double.MaxValue, Double.MaxValue, "O", false, false)

      /*val provTest = new fr.proline.core.om.provider.msi.impl.ORMPTMProvider( this.em )
	  val ptmDefs = provTest.getPtmDefinitions(List(1,2,30))
	  ptmDefs.foreach { ptm => println(ptm.get.names.shortName ) }*/

      val pep: Option[Peptide] = sqlPepProvider.getPeptide(SEQ_TO_FOUND, ptmsBuilder.result())
      assertThat(pep, CoreMatchers.notNullValue());
      assertNotSame(pep, None$.MODULE$);
      assertThat(pep.get.ptms.length, CoreMatchers.equalTo(1));
      assertThat(pep.get.ptms(0).seqPosition, CoreMatchers.equalTo(3));

    } finally {
      psDb.close
    }

  }

  @After
  override def tearDown() = {
    super.tearDown();
  }

}