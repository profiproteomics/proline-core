package fr.proline.core.dal

import com.typesafe.scalalogging.StrictLogging
import fr.proline.repository.DriverType
import fr.proline.repository.ProlineDatabaseType
import fr.proline.repository.util.DatabaseTestCase
import fr.proline.core.om.provider.msi.cache.PeptideCache

class AbstractMultipleDBTestCase extends StrictLogging {

  protected val m_fakeException = new RuntimeException("_FakeException_ AbstractMultipleDBTestCase instance creation")
  protected val m_testCaseLock = new AnyRef()
  protected var m_toreDown = false

  /* All mutable fields are @GuardedBy("m_testCaseLock") */
  var msiDBTestCase: MSIDatabaseTestCase = null

  var udsDBTestCase: UDSDatabaseTestCase = null

  var dsConnectorFactoryForTest: DataStoreConnectorFactoryForTest = null

  def initDBsDBManagement(driverType: DriverType) {

    m_testCaseLock.synchronized {

      if (m_toreDown) {
        throw new IllegalStateException("TestCase ALREADY torn down")
      }

      logger.info("Creating UDS and MSI Database TestCases")

      udsDBTestCase = new UDSDatabaseTestCase(driverType)
      udsDBTestCase.initDatabase()

      msiDBTestCase = new MSIDatabaseTestCase(driverType)
      msiDBTestCase.initDatabase()

      dsConnectorFactoryForTest = new DataStoreConnectorFactoryForTest(
        udsDBTestCase.getConnector,
        msiDBTestCase.getConnector,
        lcMsDb = null,
        initialize = false
      )
      
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
  def doTearDown(fromFinalize: Boolean) = {

    m_testCaseLock.synchronized {

      if (!m_toreDown) {
        m_toreDown = true

        if (fromFinalize) {
          logger.warn("Tearing down MultipleDBTestCase from finalize !", m_fakeException)
        }

        if (msiDBTestCase != null) {
          logger.debug("Closing MSI Db TestCase")
          // FIXME: this a workaround for the Multiple DatabaseConnectors issue (H2 databases are kept between different tests)
          // Sources:
          // - https://ahmadatwi.me/2016/05/23/using-h2-in-memory-to-test-your-dal/
          // - http://stackoverflow.com/questions/8523423/reset-embedded-h2-database-periodically
          if (msiDBTestCase.getConnector.isMemory) {
            val connection = msiDBTestCase.getConnector.getDataSource.getConnection
            logger.warn("Removing all objects from in-memory MSI Db")
            connection.createStatement().execute("DROP ALL OBJECTS")
          }
          msiDBTestCase.tearDown()
        }

        if (udsDBTestCase != null) {
          logger.debug("Closing UDS Db TestCase")
          // FIXME: same workaround as above
          if (udsDBTestCase.getConnector.isMemory) {
            val connection = udsDBTestCase.getConnector.getDataSource.getConnection
            logger.warn("Removing all objects from in-memory UDS Db")
            connection.createStatement().execute("DROP ALL OBJECTS")
          }
          udsDBTestCase.tearDown()
        }


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

class MSIDatabaseTestCase(val driverType: DriverType) extends DatabaseAndDriverTestCase {

  override def getProlineDatabaseType() = {
    ProlineDatabaseType.MSI
  }

  val propertiesFile = "db_msi.properties"

}

class LCMSDatabaseTestCase(val driverType: DriverType) extends DatabaseAndDriverTestCase {

  override def getProlineDatabaseType() = {
    ProlineDatabaseType.LCMS
  }

  val propertiesFile = "db_lcms.properties"

}
