package fr.proline.core.om.provider.msi.impl

import scala.collection.mutable.ArrayBuffer
import org.hamcrest.CoreMatchers
import org.junit.Assert._
import org.junit.After
import org.junit.Before
import org.junit.Test
import fr.proline.core.om.model.msi.IonTypes
import fr.proline.core.om.model.msi.LocatedPtm
import fr.proline.core.om.model.msi.Peptide
import fr.proline.core.om.model.msi.PtmDefinition
import fr.proline.core.om.model.msi.PtmEvidence
import fr.proline.core.om.model.msi.PtmNames
import fr.proline.repository.util.JPAUtils
import fr.proline.repository.utils.DatabaseUtils
import fr.proline.repository.utils.DatabaseTestCase
import fr.proline.core.om.model.msi.PtmLocation
import fr.proline.repository.Database

@Test
class ORMPtmProviderTest extends DatabaseTestCase {

  override def getDatabase() = Database.PS

  @Before
  @throws(classOf[Exception])
  def setUp() = {
    initDatabase()

    loadDataSet("/fr/proline/core/om/ps/Unimod_Dataset.xml")
  }

  @Test
  def getPtmSpecificities() = {
    val ids = new ArrayBuffer[Int]
    ids += 7 //
    ids += 12
    ids += 1284

    val emf = getConnector.getEntityManagerFactory

    val psEm = emf.createEntityManager

    try {
      val ormPtmProvider = new ORMPTMProvider(psEm)

      val ptmDefs: Array[Option[PtmDefinition]] = ormPtmProvider.getPtmDefinitionsAsOptions(ids);
      assertThat(ptmDefs, CoreMatchers.notNullValue());
      assertNotSame(ptmDefs(0), None);
      assertThat(ptmDefs(0).get.location, CoreMatchers.equalTo(PtmLocation.PROT_N_TERM.toString));
      assertThat(ptmDefs(0).get.names.fullName, CoreMatchers.equalTo("Acetylation"));
      assertThat(ptmDefs(0).get.classification, CoreMatchers.equalTo("Post-translational"));

      assertThat(ptmDefs(1).get.location, CoreMatchers.equalTo(PtmLocation.ANY_N_TERM.toString));
      assertThat(ptmDefs(1).get.residue, CoreMatchers.equalTo('\0'));
      assertThat(ptmDefs(1).get.names.shortName, CoreMatchers.equalTo("Biotin"));

      assertThat(ptmDefs(2).get.location, CoreMatchers.equalTo(PtmLocation.ANYWHERE.toString));
      assertThat(ptmDefs(2).get.residue, CoreMatchers.equalTo('H'));
    } finally {

      if (psEm != null) {
        psEm.close()
      }

    }

  }

  @Test
  def getPtmSpecificitiesWithNonExistant() = {
    val ids = new ArrayBuffer[Int]
    ids += 7
    ids += 9879

    val emf = getConnector.getEntityManagerFactory

    val psEm = emf.createEntityManager

    try {
      val ormPtmProvider = new ORMPTMProvider(psEm)

      val ptmDefs: Array[Option[PtmDefinition]] = ormPtmProvider.getPtmDefinitionsAsOptions(ids);
      assertThat(ptmDefs, CoreMatchers.notNullValue());
      assertThat(ptmDefs.length, CoreMatchers.equalTo(2));
      assertNotSame(ptmDefs(0), None);
      assertThat(ptmDefs(0).get.names.fullName, CoreMatchers.equalTo("Acetylation"));
      assertSame(ptmDefs(1), None);
    } finally {

      if (psEm != null) {
        psEm.close()
      }

    }

  }

  @Test
  def getPtmSpecificitiesWithNonExistant2() = {
    val ids = new ArrayBuffer[Int]
    ids += 9879
    ids += 7

    val emf = getConnector.getEntityManagerFactory

    val psEm = emf.createEntityManager

    try {
      val ormPtmProvider = new ORMPTMProvider(psEm)

      val ptmDefs: Array[Option[PtmDefinition]] = ormPtmProvider.getPtmDefinitionsAsOptions(ids);
      assertThat(ptmDefs, CoreMatchers.notNullValue());
      assertThat(ptmDefs.length, CoreMatchers.equalTo(2));
      assertNotSame(ptmDefs(1), None);
      assertThat(ptmDefs(1).get.names.fullName, CoreMatchers.equalTo("Acetylation"));
      assertSame(ptmDefs(0), None);
    } finally {

      if (psEm != null) {
        psEm.close()
      }

    }

  }

  @Test
  def getSinglePtmSpecificities() = {
    val emf = getConnector.getEntityManagerFactory

    val psEm = emf.createEntityManager

    try {
      val ormPtmProvider = new ORMPTMProvider(psEm)

      val ptmDef: Option[PtmDefinition] = ormPtmProvider.getPtmDefinition(12);
      assertThat(ptmDef, CoreMatchers.notNullValue());
      assertNotSame(ptmDef, None);

      assertThat(ptmDef.get.location, CoreMatchers.equalTo(PtmLocation.ANY_N_TERM.toString));
      assertThat(ptmDef.get.residue, CoreMatchers.equalTo('\0'));
      assertThat(ptmDef.get.names.shortName, CoreMatchers.equalTo("Biotin"));
    } finally {

      if (psEm != null) {
        psEm.close()
      }

    }

  }

  @Test
  def getNonExistantPtmSpecificity() = {
    val emf = getConnector.getEntityManagerFactory

    val psEm = emf.createEntityManager

    try {
      val ormPtmProvider = new ORMPTMProvider(psEm)

      val ptmDef: Option[PtmDefinition] = ormPtmProvider.getPtmDefinition(9879);
      assertThat(ptmDef, CoreMatchers.notNullValue());

      assertSame(ptmDef, None);
    } finally {

      if (psEm != null) {
        psEm.close()
      }

    }

  }

  @Test
  def getPtmSpecificityByNameResiduAndLoc() = {

    val emf = getConnector.getEntityManagerFactory

    val psEm = emf.createEntityManager

    try {
      val ormPtmProvider = new ORMPTMProvider(psEm)

      //Param for PtmSpecificity ID 877 in Unimod_Dataset
      val ptmDef: Option[PtmDefinition] = ormPtmProvider.getPtmDefinition("iTRAQ8plex", 'S', PtmLocation.ANYWHERE);

      assertThat(ptmDef, CoreMatchers.notNullValue())
      assertNotSame(ptmDef, None);
      assertThat(ptmDef.get.id, CoreMatchers.equalTo(877))
    } finally {

      if (psEm != null) {
        psEm.close()
      }

    }

  }

  @Test
  def getInvalidPtmSpecificity() = {
    val emf = getConnector.getEntityManagerFactory

    val psEm = emf.createEntityManager

    try {
      val ormPtmProvider = new ORMPTMProvider(psEm)

      //Param corresponding to No PtmSpecificity in Unimod_Dataset
      val ptmDef: Option[PtmDefinition] = ormPtmProvider.getPtmDefinition("iTRAQ8plexA", '\0', PtmLocation.ANYWHERE);
      assertThat(ptmDef, CoreMatchers.notNullValue());
      assertSame(ptmDef, None);
    } finally {

      if (psEm != null) {
        psEm.close()
      }

    }

  }

  @After
  override def tearDown() = {
    super.tearDown();
  }

}