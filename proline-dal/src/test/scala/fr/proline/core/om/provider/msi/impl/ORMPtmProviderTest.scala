package fr.proline.core.om.provider.msi.impl

import scala.collection.mutable.ArrayBuffer
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

      assertNotNull(ptmDefs)
      assertEquals(ptmDefs.length, ids.length)

      assertEquals(ptmDefs(0).location, PtmLocation.PROT_N_TERM.toString)
      assertEquals(ptmDefs(0).names.fullName, "Acetylation")
      assertEquals(ptmDefs(0).classification, "Post-translational")

      assertEquals(ptmDefs(1).location, PtmLocation.ANY_N_TERM.toString)
      assertEquals(ptmDefs(1).residue,'\u0000')
      assertEquals(ptmDefs(1).names.shortName,"Biotin")

      assertEquals(ptmDefs(2).location, PtmLocation.ANYWHERE.toString)
      assertEquals(ptmDefs(2).residue, 'H')
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

      assertNotNull(ptmDefs)
      assertEquals(ptmDefs.length, (ids.length - 1))

      assertEquals(ptmDefs(0).names.fullName,"Acetylation")
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
      assertNotNull(ptmDefs)
      assertEquals(ptmDefs.length, (ids.length - 1))

      assertEquals(ptmDefs(0).names.fullName, "Acetylation")
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
      assertNotNull(ptmDef)
      assertNotSame(ptmDef, None)

      assertEquals(ptmDef.get.location, PtmLocation.ANY_N_TERM.toString)
      assertEquals(ptmDef.get.residue, '\u0000')
      assertEquals(ptmDef.get.names.shortName, "Biotin")
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
      assertNotNull(ptmDef)

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

      assertNotNull(ptmDef)
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
      assertNotNull(ptmDef)
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