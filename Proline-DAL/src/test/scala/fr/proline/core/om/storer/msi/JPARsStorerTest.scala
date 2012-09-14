package fr.proline.core.om.storer.msi

import org.junit.Test

import com.weiglewilczek.slf4s.Logging

import fr.proline.core.orm.utils.JPAUtil
import fr.proline.core.utils.generator.ResultSetFakeBuilder
import fr.proline.repository.utils.DatabaseTestCase
import fr.proline.repository.utils.DatabaseUtils

class JPARsStorerTest extends Logging {

  val milliToNanos = 1000000L

  /**
   * Creates some ResultSets with {{{ResultSetFakeBuilder}}} from Proline-OM ''test'' project
   * and persists them into Msi Db using a {{{JPARsStorer}}} instance.
   */
  @Test
  def testRsStorer() {
    logger.info("Initializing Dbs")
    /* Init Msi Db connection */
    val msiDBTestCase = new MSIDatabaseTestCase()
    msiDBTestCase.initDatabase()
    msiDBTestCase.initEntityManager(JPAUtil.PersistenceUnitNames.MSI_Key.getPersistenceUnitName())
    msiDBTestCase.loadDataSet("/fr/proline/core/om/msi/Init_Dataset.xml")

    /* Init Ps Db connection */
    val psDBTestCase = new PSDatabaseTestCase()

    psDBTestCase.initDatabase()
    psDBTestCase.initEntityManager(JPAUtil.PersistenceUnitNames.PS_Key.getPersistenceUnitName())
    psDBTestCase.loadDataSet("/fr/proline/core/om/ps/Unimod_Dataset.xml")

    /* Init Uds Db connection */
    val udsDBTestCase = new UDSDatabaseTestCase()
    udsDBTestCase.initDatabase()
    udsDBTestCase.initEntityManager(JPAUtil.PersistenceUnitNames.UDS_Key.getPersistenceUnitName())
    udsDBTestCase.loadDataSet("/fr/proline/core/om/uds/UDS_Simple_Dataset.xml")

    /* Init Pdi Db connection */
    val pdiDBTestCase = new PDIDatabaseTestCase()
    pdiDBTestCase.initDatabase()
    pdiDBTestCase.initEntityManager(JPAUtil.PersistenceUnitNames.PDI_Key.getPersistenceUnitName())
    pdiDBTestCase.loadDataSet("/fr/proline/core/om/pdi/Proteins_Dataset.xml")

    logger.info("Dbs succesfully initialized")

    val storer = new JPARsStorer(null, null, null, null)

    for (i <- 1 to 3) {
      logger.info("Creating a new fake Result Set")

      var start = System.nanoTime
      val rsb = new ResultSetFakeBuilder(10, 2)

      val resultSet = rsb.toResultSet()
      var stop = System.nanoTime

      logger.info("ResultSet creation time: " + ((stop - start) / milliToNanos))

      /* Used to add some existing Peptides (#1 .. #6) */
      //      var peptideId = 1
      //      for (peptideMatch <- resultSet.peptideMatches if (peptideId < 7)) {
      //        val peptide = peptideMatch.peptide
      //
      //        if (peptide != null) {
      //          peptide.id = peptideId
      //
      //          peptideId += 1
      //        }
      //
      //      }

      start = System.nanoTime
      storer.storeResultSet(msiDBTestCase.getEntityManager(), psDBTestCase.getEntityManager(), udsDBTestCase.getEntityManager(), pdiDBTestCase.getEntityManager(), resultSet, null)
      stop = System.nanoTime

      logger.info("ResultSet persisted time: " + ((stop - start) / milliToNanos))
    }

    pdiDBTestCase.tearDown()
    udsDBTestCase.tearDown()
    psDBTestCase.tearDown()
    msiDBTestCase.tearDown()

    logger.info("Dbs succesfully closed")
  }

}

class MSIDatabaseTestCase extends DatabaseTestCase {

  override def getSQLScriptLocation(): String = {
    DatabaseUtils.H2_DATABASE_MSI_SCRIPT_LOCATION
  }

  override def getPropertiesFilename(): String = {
    return "/db_msi.properties";
  }

}

class PSDatabaseTestCase extends DatabaseTestCase {

  override def getSQLScriptLocation(): String = {
    DatabaseUtils.H2_DATABASE_PS_SCRIPT_LOCATION
  }

  override def getPropertiesFilename(): String = {
    return "/db_ps.properties";
  }

}

class UDSDatabaseTestCase extends DatabaseTestCase {

  override def getSQLScriptLocation(): String = {
    DatabaseUtils.H2_DATABASE_UDS_SCRIPT_LOCATION
  }

  override def getPropertiesFilename(): String = {
    return "/db_uds.properties";
  }

}

class PDIDatabaseTestCase extends DatabaseTestCase {

  override def getSQLScriptLocation(): String = {
    DatabaseUtils.H2_DATABASE_PDI_SCRIPT_LOCATION
  }

  override def getPropertiesFilename(): String = {
    return "/db_pdi.properties";
  }

}
