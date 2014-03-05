package fr.proline.core.om.util

import com.typesafe.scalalogging.slf4j.Logging
import fr.proline.core.dal.DataStoreConnectorFactoryForTest
import fr.proline.repository.DriverType
import fr.proline.repository.ProlineDatabaseType
import fr.proline.repository.util.DatabaseTestCase
import fr.proline.core.om.provider.msi.impl.SQLPeptideProvider

class AbstractMultipleDBTestCase extends Logging {

  private val m_fakeException = new RuntimeException("_FakeException_ AbstractMultipleDBTestCase instance creation")

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
        throw new IllegalStateException("TestCase ALREADY torn down")
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
      
      SQLPeptideProvider.clear() // Clear peptide cache between tests
    } // End of synchronized block on m_testCaseLock

  }

  def tearDown() = {
    doTearDown(false)
  }

  override def finalize() {

    try {
      doTearDown(true)
    } finally {
      super.finalize()
    }

  }

  /* Private methods */
  def doTearDown(finalizing: Boolean) = {

    m_testCaseLock.synchronized {

      if (!m_toreDown) {
        m_toreDown = true

        if (finalizing) {
          logger.warn("Tearing down MultipleDBTestCase in finalize !", m_fakeException)
        }

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
        
        SQLPeptideProvider.clear() // Clear peptide cache between tests

        logger.info("All Database TestCases closed successfully")
      }

    } // End of synchronized block on m_testCaseLock

  }

}

abstract class DatabaseAndDriverTestCase extends DatabaseTestCase {

  val driverType: DriverType

  protected val propertiesFileDirectory: String = {

    driverType match {
      case DriverType.H2         => "db_settings/h2"
      case DriverType.SQLITE     => "db_settings/sqlite"
      case DriverType.POSTGRESQL => "db_settings/postgresql"
      case _                     => throw new IllegalArgumentException("Unsupported database driver for testing")
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
