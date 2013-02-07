package fr.proline.core.om.utils

import com.weiglewilczek.slf4s.Logging

import fr.proline.core.dal.DataStoreConnectorFactoryForTest
import fr.proline.repository.{ DriverType, ProlineDatabaseType }
import fr.proline.repository.utils.DatabaseTestCase

class AbstractMultipleDBTestCase extends Logging {

  private val m_testCaseLock = new AnyRef()

  /* All mutable fields are @GuardedBy("m_testCaseLock") */

  var pdiDBTestCase: PDIDatabaseTestCase = null
  var msiDBTestCase: MSIDatabaseTestCase = null
  var psDBTestCase: PSDatabaseTestCase = null
  var udsDBTestCase: UDSDatabaseTestCase = null

  var dsConnectorFactoryForTest: DataStoreConnectorFactoryForTest = null

  private var m_toreDown = false

  def initDBsDBManagement(driverType: DriverType) {

    m_testCaseLock.synchronized {

      if (m_toreDown) {
        throw new IllegalStateException("TestCase ALREADY torn down");
      }

      logger.info("Creating UDS, PDI, PS, MSI Database TestCases")

      udsDBTestCase = new UDSDatabaseTestCase(driverType)
      udsDBTestCase.initDatabase()

      pdiDBTestCase = new PDIDatabaseTestCase(driverType)
      pdiDBTestCase.initDatabase()

      psDBTestCase = new PSDatabaseTestCase(driverType)
      psDBTestCase.initDatabase()

      msiDBTestCase = new MSIDatabaseTestCase(driverType)
      msiDBTestCase.initDatabase()

      dsConnectorFactoryForTest = new DataStoreConnectorFactoryForTest(udsDBTestCase.getConnector, pdiDBTestCase.getConnector, psDBTestCase.getConnector, msiDBTestCase.getConnector, null, false)
    } // End of synchronized block on m_testCaseLock

  }

  def tearDown() = {

    m_testCaseLock.synchronized {

      if (!m_toreDown) {
        m_toreDown = true

        if (msiDBTestCase != null) {
          logger.debug("Closing MSI Db TestCase")
          msiDBTestCase.tearDown()
        }

        if (psDBTestCase != null) {
          logger.debug("Closing PS Db TestCase")
          psDBTestCase.tearDown()
        }

        if (pdiDBTestCase != null) {
          logger.debug("Closing PDI Db TestCase")
          pdiDBTestCase.tearDown()
        }

        if (udsDBTestCase != null) {
          logger.debug("Closing UDS Db TestCase")
          udsDBTestCase.tearDown()
        }

        logger.info("All Database TestCases closed successfully")
      }

    } // End of synchronized block on m_testCaseLock

  }

  override def finalize() {

    try {
      tearDown();
    } finally {
      super.finalize();
    }

  }

}

abstract class DatabaseAndDriverTestCase extends DatabaseTestCase {

  val driverType: DriverType

  protected val propertiesFileDirectory: String = {

    driverType match {
      case DriverType.H2 => "db_settings/h2"
      case DriverType.SQLITE => "db_settings/sqlite"
      case _ => throw new Exception("Unsupported database driver for testing")
    }

  }

  val propertiesFile: String

  override def getPropertiesFileName(): String = {
    propertiesFileDirectory + '/' + propertiesFile
  }

}

class UDSDatabaseTestCase(val driverType: DriverType) extends DatabaseAndDriverTestCase {

  override def getProlineDatabaseType() = {
    ProlineDatabaseType.UDS
  }

  val propertiesFile = "db_uds.properties"

}

class PDIDatabaseTestCase(val driverType: DriverType) extends DatabaseAndDriverTestCase {

  override def getProlineDatabaseType() = {
    ProlineDatabaseType.PDI
  }

  val propertiesFile = "db_pdi.properties"

}

class PSDatabaseTestCase(val driverType: DriverType) extends DatabaseAndDriverTestCase {

  override def getProlineDatabaseType() = {
    ProlineDatabaseType.PS
  }

  val propertiesFile = "db_ps.properties"

}

class MSIDatabaseTestCase(val driverType: DriverType) extends DatabaseAndDriverTestCase {

  override def getProlineDatabaseType() = {
    ProlineDatabaseType.MSI
  }

  val propertiesFile = "db_msi.properties"

}
