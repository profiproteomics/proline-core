package fr.proline.core.om.provider.msi.impl

import fr.proline.context.BasicExecutionContext
import fr.proline.context.IExecutionContext
import fr.proline.core.dal._
import fr.proline.core.om.model.msi._
import fr.proline.core.om.provider.PeptideCacheExecutionContext
import fr.proline.core.om.provider.msi.cache.IPeptideCache
import fr.proline.repository.ProlineDatabaseType
import fr.proline.repository.util.DatabaseTestCase
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.junit.After
import org.junit.Assert._
import org.junit.Before
import org.junit.Test

import scala.collection.mutable.ArrayBuffer

@Test
class SQLPeptideProviderTest extends DatabaseTestCase {

  private val SEQ_TO_FOUND: String = "LTGMAFR"
  private var peptideCache : IPeptideCache = _
  private var exeContext : IExecutionContext = _
  override def getProlineDatabaseType() = ProlineDatabaseType.MSI

  @Before
  @throws(classOf[Exception])
  def setUp() = {
    initDatabase()
    val msiDbCtx = BuildMsiDbConnectionContext(getConnector, false)
    exeContext = PeptideCacheExecutionContext(new BasicExecutionContext(1,null,msiDbCtx,null))
    peptideCache = PeptideCacheExecutionContext(exeContext).getPeptideCache()
    peptideCache.clear() // Clear peptide cache between tests

    loadCompositeDataSet(Array("/dbunit/Init/msi-db.xml", "/dbunit/datasets/msi/Peptides_Dataset.xml"))
  }

  override def getPropertiesFileName(): String = "db_settings/h2/db_msi.properties"
    
  @Test
  def getSinglePeptide() = {
      val sqlPepProvider = new SQLPeptideProvider(PeptideCacheExecutionContext(exeContext))

      val pep: Option[Peptide] = sqlPepProvider.getPeptide(4)
    MatcherAssert.assertThat(pep, CoreMatchers.notNullValue())
      assertNotSame(pep, None)
    MatcherAssert.assertThat(pep.get.calculatedMass, CoreMatchers.equalTo(810.405807))
  }

  @Test
  def getMultiplePeptides() = {
    val ids = new ArrayBuffer[Long]
    ids += 0
    ids += 1
    ids += 4


    val sqlPepProvider = new SQLPeptideProvider(PeptideCacheExecutionContext(exeContext))

    val peps: Array[Option[Peptide]] = sqlPepProvider.getPeptidesAsOptions(ids)
    MatcherAssert.assertThat(peps, CoreMatchers.notNullValue())
    MatcherAssert.assertThat(peps.length, CoreMatchers.equalTo(3))
    assertEquals(peps.apply(2).get.id, 4L)
    MatcherAssert.assertThat(peps(2).get.calculatedMass, CoreMatchers.equalTo(810.405807))

  }

  @Test
  def getPeptideWithNTermPTM() = {
     val sqlPepProvider = new SQLPeptideProvider(PeptideCacheExecutionContext(exeContext))

    val pep: Option[Peptide] = sqlPepProvider.getPeptide(6)
    MatcherAssert.assertThat(pep, CoreMatchers.notNullValue())
    assertNotSame(pep, None)

    assertEquals(pep.get.id, 6L)
    MatcherAssert.assertThat(pep.get.ptms.length, CoreMatchers.equalTo(1))
    MatcherAssert.assertThat(pep.get.ptms(0).definition.names.shortName, CoreMatchers.equalTo("Acetyl"))
    assertTrue(pep.get.ptms(0).isNTerm)

  }

  @Test
  def getPeptideOnSeqAndNoPtms() = {

    val sqlPepProvider = new SQLPeptideProvider(PeptideCacheExecutionContext(exeContext))

    val ptms = new Array[LocatedPtm](0)
    val pep: Option[Peptide] = sqlPepProvider.getPeptide(SEQ_TO_FOUND, ptms)
    MatcherAssert.assertThat(pep, CoreMatchers.notNullValue())
    assertNotSame(pep, None)
    assertTrue(pep.get.ptms == null || pep.get.ptms.length == 0)

  }

  @Test
  def getPeptideOnSeqAndPtms() = {
    val sqlPepProvider = new SQLPeptideProvider(PeptideCacheExecutionContext(exeContext))

    var ptmsBuilder = Array.newBuilder[LocatedPtm]

    val ptmEvi: PtmEvidence = new PtmEvidence(IonTypes.Precursor, "", Double.MaxValue, Double.MaxValue, false)
    val ptmEvidences = Array[PtmEvidence](ptmEvi)

    val ptmDef = new PtmDefinition(0, "ANYWHERE", new PtmNames("Oxidation", "Oxidation or Hydroxylation"), ptmEvidences, 'M', null, 0)
    ptmsBuilder += new LocatedPtm(ptmDef, 3, Double.MaxValue, Double.MaxValue, "O", false, false)

    /*val provTest = new fr.proline.core.om.provider.msi.impl.ORMPTMProvider( this.em )
	  val ptmDefs = provTest.getPtmDefinitions(List(1,2,30))
	  ptmDefs.foreach { ptm => println(ptm.get.names.shortName ) }*/

    val pep: Option[Peptide] = sqlPepProvider.getPeptide(SEQ_TO_FOUND, ptmsBuilder.result())
    MatcherAssert.assertThat(pep, CoreMatchers.notNullValue())
    assertNotSame(pep, None)
    MatcherAssert.assertThat(pep.get.ptms.length, CoreMatchers.equalTo(1))
    MatcherAssert.assertThat(pep.get.ptms(0).seqPosition, CoreMatchers.equalTo(3))

  }

  @Test
  def getPeptideWithMultiplePtmSpecificities() = {

    val sqlPepProvider = new SQLPeptideProvider(PeptideCacheExecutionContext(exeContext))

    val pep: Option[Peptide] = sqlPepProvider.getPeptide(7)
    MatcherAssert.assertThat(pep, CoreMatchers.notNullValue())
    assertNotSame(pep, None)
    MatcherAssert.assertThat(pep.get.ptms.head.definition.neutralLosses.size, CoreMatchers.equalTo(2))
    MatcherAssert.assertThat(pep.get.ptms.head.definition.ptmEvidences.size, CoreMatchers.equalTo(3))
    MatcherAssert.assertThat(pep.get.ptms.head.definition.classification, CoreMatchers.equalTo("Post-translational"))
  }

  @After
  override def tearDown() = {

    try {
      peptideCache.clear() // Clear peptide cache between tests
      exeContext.closeAll()
    } finally {
      super.tearDown()
    }

  }

}