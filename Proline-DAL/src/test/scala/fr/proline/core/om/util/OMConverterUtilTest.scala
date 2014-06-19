package fr.proline.core.om.util

import org.junit.Before
import fr.proline.repository.util.DatabaseTestCase
import fr.proline.repository.util.JPAUtils
import org.junit.After
import org.junit.Assert._
import fr.proline.repository.util.DatabaseUtils
import fr.proline.core.orm.ps.PeptidePtm
import fr.proline.core.om.model.msi.PtmDefinition
import fr.proline.core.om.model.msi.LocatedPtm
import org.junit.Test
import fr.proline.core.om.model.msi.PtmEvidence
import fr.proline.core.om.model.msi.IonTypes
import fr.proline.core.om.model.msi.PtmNames
import scala.collection.mutable.ArrayBuilder
import fr.proline.repository.ProlineDatabaseType

class OMConverterUtilTest extends DatabaseTestCase {

  var converter: PeptidesOMConverterUtil = null

  override def getProlineDatabaseType() = ProlineDatabaseType.PS

  @Before
  @throws(classOf[Exception])
  def initialize() = {
    initDatabase();

    //loadDataSet("/fr/proline/core/om/ps/Unimod_Dataset.xml");
    loadCompositeDataSet(Array("/dbunit/datasets/ps-db_init_dataset.xml","/dbunit/datasets/ps/Peptides_Dataset.xml"))

    converter = new PeptidesOMConverterUtil(true);
  }

  @Test
  def testConvertPeptides() = {
    val emf = getConnector.getEntityManagerFactory

    val psEm = emf.createEntityManager

    try {
      val ormPep: fr.proline.core.orm.ps.Peptide = psEm.find(classOf[fr.proline.core.orm.ps.Peptide], java.lang.Long.valueOf(4L))
      val omPep: fr.proline.core.om.model.msi.Peptide = converter.convertPeptidePsORM2OM(ormPep)
      assertNotNull(omPep);
      assertEquals(omPep.calculatedMass, ormPep.getCalculatedMass(), 0.01d)
      assertEquals(omPep.sequence, ormPep.getSequence())

      //Test PTMs
      val pepPTMsIT: java.util.Iterator[PeptidePtm] = ormPep.getPtms().iterator()
      var pepPtmsNamesBuilder = Array.newBuilder[String]
      var pepPtmsLocationsBuilder = Array.newBuilder[Int]
      while (pepPTMsIT.hasNext()) {
        val nextORMPtm: PeptidePtm = pepPTMsIT.next();
        pepPtmsNamesBuilder += nextORMPtm.getSpecificity().getPtm().getFullName()
        pepPtmsLocationsBuilder += nextORMPtm.getSeqPosition()
      }
      val pepPtmsNames: Array[String] = pepPtmsNamesBuilder.result
      val pepPtmsLocations: Array[Int] = pepPtmsLocationsBuilder.result

      val omPtms = omPep.ptms

      omPtms foreach (nextOMPtm => {
        val indexPtm = pepPtmsNames.indexOf(nextOMPtm.definition.names.fullName);
        if (indexPtm == -1)
          fail("Ptm not found in OM Peptide");
        else
          assertEquals(Integer.valueOf(nextOMPtm.seqPosition), pepPtmsLocations.apply(indexPtm));
      })

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