package fr.proline.core.om.provider.msi.impl

import scala.collection.mutable.ArrayBuffer
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.junit.Assert._
import org.junit.After
import org.junit.Before
import org.junit.Test
import fr.proline.context.MsiDbConnectionContext
import fr.proline.core.om.model.msi.PtmDefinition
import fr.proline.core.om.model.msi.PtmLocation
import fr.proline.repository.util.DatabaseTestCase
import fr.proline.repository.ProlineDatabaseType

@Test
class ORMPtmProviderTest extends DatabaseTestCase {

  override def getProlineDatabaseType() = ProlineDatabaseType.MSI

  @Before
  @throws(classOf[Exception])
  def setUp() = {
    initDatabase()
    loadCompositeDataSet(Array("/dbunit/Init/msi-db.xml","/dbunit/datasets/msi/Peptides_Dataset.xml"))
  }

  override def getPropertiesFileName(): String = "db_settings/h2/db_msi.properties"
    
  @Test
  def getPtmSpecificities() = {
    val ids = new ArrayBuffer[Long]
    ids += 2
    ids += 11
    ids += 48

    val msiDb = new MsiDbConnectionContext(getConnector)

    try {
      val ormPtmProvider = new ORMPTMProvider(msiDb)

      val ptmDefs = ormPtmProvider.getPtmDefinitions(ids)

      MatcherAssert.assertThat(ptmDefs, CoreMatchers.notNullValue())
      MatcherAssert.assertThat(ptmDefs.length, CoreMatchers.equalTo(ids.length))

      MatcherAssert.assertThat(ptmDefs(0).location, CoreMatchers.equalTo(PtmLocation.PROT_N_TERM.toString))
      MatcherAssert.assertThat(ptmDefs(0).names.fullName, CoreMatchers.equalTo("Acetylation"))
      MatcherAssert.assertThat(ptmDefs(0).classification, CoreMatchers.equalTo("Post-translational"))

      MatcherAssert.assertThat(ptmDefs(1).location, CoreMatchers.equalTo(PtmLocation.ANY_N_TERM.toString))
      MatcherAssert.assertThat(ptmDefs(1).residue, CoreMatchers.equalTo('\u0000'))
      MatcherAssert.assertThat(ptmDefs(1).names.shortName, CoreMatchers.equalTo("Biotin"))

      MatcherAssert.assertThat(ptmDefs(2).location, CoreMatchers.equalTo(PtmLocation.ANYWHERE.toString))
      MatcherAssert.assertThat(ptmDefs(2).residue, CoreMatchers.equalTo('H'))
    } finally {
      msiDb.close()
    }

  }

  @Test
  def getPtmSpecificitiesWithNonExistant() = {
    val ids = new ArrayBuffer[Long]
    ids += 2
    ids += 9879

    val msiDb = new MsiDbConnectionContext(getConnector)
    try {
      val ormPtmProvider = new ORMPTMProvider(msiDb)

      val ptmDefs = ormPtmProvider.getPtmDefinitions(ids)

      MatcherAssert.assertThat(ptmDefs, CoreMatchers.notNullValue())
      MatcherAssert.assertThat(ptmDefs.length, CoreMatchers.equalTo(ids.length - 1))

      MatcherAssert.assertThat(ptmDefs(0).names.fullName, CoreMatchers.equalTo("Acetylation"))
    } finally {
      msiDb.close()
    }

  }

  @Test
  def getPtmSpecificitiesWithNonExistant2() = {
    val ids = new ArrayBuffer[Long]
    ids += 9879
    ids += 2

    val msiDb = new MsiDbConnectionContext(getConnector)

    try {
      val ormPtmProvider = new ORMPTMProvider(msiDb)

      val ptmDefs = ormPtmProvider.getPtmDefinitions(ids)
      MatcherAssert.assertThat(ptmDefs, CoreMatchers.notNullValue())
      MatcherAssert.assertThat(ptmDefs.length, CoreMatchers.equalTo(ids.length - 1))

      MatcherAssert.assertThat(ptmDefs(0).names.fullName, CoreMatchers.equalTo("Acetylation"))
    } finally {
      msiDb.close()
    }

  }

  @Test
  def getSinglePtmSpecificities() = {
    val msiDb = new MsiDbConnectionContext(getConnector)

    try {
      val ormPtmProvider = new ORMPTMProvider(msiDb)

      val ptmDef: Option[PtmDefinition] = ormPtmProvider.getPtmDefinition(11)
      MatcherAssert.assertThat(ptmDef, CoreMatchers.notNullValue())
      assertNotSame(ptmDef, None)

      MatcherAssert.assertThat(ptmDef.get.location, CoreMatchers.equalTo(PtmLocation.ANY_N_TERM.toString))
      MatcherAssert.assertThat(ptmDef.get.residue, CoreMatchers.equalTo('\u0000'))
      MatcherAssert.assertThat(ptmDef.get.names.shortName, CoreMatchers.equalTo("Biotin"))
    } finally {
      msiDb.close()
    }

  }

  @Test
  def getNonExistantPtmSpecificity() = {
    val msiDb = new MsiDbConnectionContext(getConnector)

    try {
      val ormPtmProvider = new ORMPTMProvider(msiDb)

      val ptmDef: Option[PtmDefinition] = ormPtmProvider.getPtmDefinition(9879)
      MatcherAssert.assertThat(ptmDef, CoreMatchers.notNullValue())

      assertSame(ptmDef, None)
    } finally {
      msiDb.close()
    }

  }

  @Test
  def getPtmSpecificityByNameResiduAndLoc() = {
    val msiDb = new MsiDbConnectionContext(getConnector)

    try {
      val ormPtmProvider = new ORMPTMProvider(msiDb)

      //Param for PtmSpecificity ID 877 in Unimod_Dataset
      val ptmDef: Option[PtmDefinition] = ormPtmProvider.getPtmDefinition("Phospho", 'S', PtmLocation.ANYWHERE)

      MatcherAssert.assertThat(ptmDef, CoreMatchers.notNullValue())
      assertNotSame(ptmDef, None)
      assertEquals(ptmDef.get.id,52L)
    } finally {
      msiDb.close()
    }

  }

  @Test
  def getInvalidPtmSpecificity() = {
    val msiDb = new MsiDbConnectionContext(getConnector)

    try {
      val ormPtmProvider = new ORMPTMProvider(msiDb)

      //Param corresponding to No PtmSpecificity in Unimod_Dataset
      val ptmDef: Option[PtmDefinition] = ormPtmProvider.getPtmDefinition("iTRAQ8plexA", '\u0000', PtmLocation.ANYWHERE)
      MatcherAssert.assertThat(ptmDef, CoreMatchers.notNullValue())
      assertSame(ptmDef, None)
    } finally {
      msiDb.close()
    }

  }

  @After
  override def tearDown() = {
    super.tearDown()
  }

}