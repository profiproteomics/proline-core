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
import scala.collection.mutable.ArrayBuilder
import fr.proline.repository.Database

class OMConverterUtilTest extends DatabaseTestCase {

  var converter: PeptidesOMConverterUtil = null

  override def getDatabase() = Database.PS

  @Before
  @throws(classOf[Exception])
  def initialize() = {
    initDatabase();

    loadDataSet("/fr/proline/core/om/ps/Unimod_Dataset.xml");

    converter = new PeptidesOMConverterUtil(true);
  }

  @Test
  def testConvertPeptides() = {
    val ormPep: fr.proline.core.orm.ps.Peptide = getEntityManager.find(classOf[fr.proline.core.orm.ps.Peptide], 4)
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

  }

  @After
  override def tearDown() = {
    super.tearDown();
  }
  
}