package fr.proline.core.om.util

import scala.collection.mutable.ArrayBuilder
import org.junit.Before
import org.junit.After
import org.junit.Assert._
import org.junit.Test
import fr.proline.core.orm.msi.PeptidePtm
import fr.proline.core.om.model.msi._
import fr.proline.repository.ProlineDatabaseType
import fr.proline.repository.util.DatabaseTestCase
import fr.proline.repository.util.DatabaseUtils
import fr.proline.repository.util.JPAUtils

class OMConverterUtilTest extends DatabaseTestCase {

  var converter: PeptidesOMConverterUtil = null

  override def getProlineDatabaseType() = ProlineDatabaseType.MSI

  @Before
  @throws(classOf[Exception])
  def initialize() = {
    initDatabase();
    loadCompositeDataSet(Array("/dbunit/Init/msi-db.xml","/dbunit/datasets/msi/Peptides_Dataset.xml"))
    converter = new PeptidesOMConverterUtil(true)
  }

  def getPropertiesFileName(): String =  "db_settings/h2/db_msi.properties"
  
  @Test
  def testConvertPeptides() = {
    val msiEm = getConnector.createEntityManager

    try {
      val ormPep = msiEm.find(classOf[fr.proline.core.orm.msi.Peptide], java.lang.Long.valueOf(4L))
      val omPep = converter.convertPeptideORM2OM(ormPep)
      assertNotNull(omPep)
      assertEquals(omPep.calculatedMass, ormPep.getCalculatedMass(), 0.01d)
      assertEquals(omPep.sequence, ormPep.getSequence())

      //Test PTMs
      val pepPTMsIT: java.util.Iterator[PeptidePtm] = ormPep.getPtms().iterator()
      var pepPtmsNamesBuilder = Array.newBuilder[String]
      var pepPtmsLocationsBuilder = Array.newBuilder[Int]
      while (pepPTMsIT.hasNext()) {
        val nextORMPtm: PeptidePtm = pepPTMsIT.next()
        pepPtmsNamesBuilder += nextORMPtm.getSpecificity().getPtm().getFullName()
        pepPtmsLocationsBuilder += nextORMPtm.getSeqPosition()
      }
      val pepPtmsNames: Array[String] = pepPtmsNamesBuilder.result
      val pepPtmsLocations: Array[Int] = pepPtmsLocationsBuilder.result

      val omPtms = omPep.ptms

      omPtms foreach (nextOMPtm => {
        val indexPtm = pepPtmsNames.indexOf(nextOMPtm.definition.names.fullName)
        if (indexPtm == -1)
          fail("Ptm not found in OM Peptide")
        else
          assertEquals(Integer.valueOf(nextOMPtm.seqPosition), pepPtmsLocations.apply(indexPtm))
      })

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