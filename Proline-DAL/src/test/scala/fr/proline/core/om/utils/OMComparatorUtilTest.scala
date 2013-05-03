package fr.proline.core.om.utils

import org.junit.Before
import fr.proline.repository.utils.DatabaseTestCase
import fr.proline.repository.util.JPAUtils
import org.junit.After
import org.junit.Assert._
import fr.proline.repository.utils.DatabaseUtils
import fr.proline.core.orm.ps.PeptidePtm
import fr.proline.core.om.model.msi.PtmDefinition
import fr.proline.core.om.model.msi.LocatedPtm
import org.junit.Test
import fr.proline.core.om.model.msi.PtmEvidence
import fr.proline.core.om.model.msi.IonTypes
import fr.proline.core.om.model.msi.PtmNames
import fr.proline.repository.ProlineDatabaseType

class OMComparatorUtilTest extends DatabaseTestCase {

  override def getProlineDatabaseType() = ProlineDatabaseType.PS

  @Before
  @throws(classOf[Exception])
  def initialize() = {
    initDatabase()

    //loadDataSet("/fr/proline/core/om/ps/Unimod_Dataset.xml");
    loadCompositeDataSet(Array("/dbunit/datasets/ps-db_init_dataset.xml","/dbunit/datasets/ps/Peptides_Dataset.xml"))
  }

  @Test
  def comparePepPtm() = {
    val emf = getConnector.getEntityManagerFactory

    val psEm = emf.createEntityManager

    try {
      val pepPtm: PeptidePtm = psEm.find(classOf[PeptidePtm], 1)

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