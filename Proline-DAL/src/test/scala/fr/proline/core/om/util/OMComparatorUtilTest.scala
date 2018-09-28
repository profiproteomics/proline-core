package fr.proline.core.om.util

import org.junit.Before

import org.junit.After
import org.junit.Assert._
import org.junit.Test
import fr.proline.repository.util.DatabaseUtils
import fr.proline.core.om.model.msi._
import fr.proline.core.orm.msi.PeptidePtm
import fr.proline.repository.ProlineDatabaseType
import fr.proline.repository.util.DatabaseTestCase
import fr.proline.repository.util.JPAUtils

class OMComparatorUtilTest extends DatabaseTestCase {

  override def getProlineDatabaseType() = ProlineDatabaseType.MSI

  @Before
  @throws(classOf[Exception])
  def initialize() = {
    initDatabase()
    loadCompositeDataSet(Array("/dbunit/Init/msi-db.xml","/dbunit/datasets/msi/Peptides_Dataset.xml"))
  }

  def getPropertiesFileName(): String = "db_settings/h2/db_msi.properties"
  
  @Test
  def comparePepPtm() = {
    val msiEm = getConnector.createEntityManager

    try {
      val pepPtm: PeptidePtm = msiEm.find(classOf[PeptidePtm], java.lang.Long.valueOf(1L))

      val ptmEvi = new PtmEvidence(
        ionType = IonTypes.Precursor,
        composition = "Post-translational",
        monoMass = 15.994915,
        averageMass = 15.9994,
        isRequired = true
      )

      var ptmEvidences = new Array[PtmEvidence](1)
      ptmEvidences.update(0, ptmEvi)

      val ptmDef = new PtmDefinition(id = -100,
        location = "ANYWHERE",
        names = new PtmNames(shortName = "Oxidation", fullName = "Oxidation or Hydroxylation"),
        ptmEvidences = ptmEvidences,
        residue = 'M',
        classification = "Post-translational", ptmId = 0)

      val lPtm: LocatedPtm = new LocatedPtm(
        definition = ptmDef,
        seqPosition = 3,
        monoMass = Double.MaxValue,
        averageMass = Double.MaxValue,
        composition = "O",
        isNTerm = false,
        isCTerm = false
      )

      assertTrue(OMComparatorUtil.comparePeptidePtm(lPtm, pepPtm))
    } finally {

      if (msiEm != null) {
        msiEm.close()
      }

    }

  }

  @After
  override def tearDown() = {
    super.tearDown()
  }

}