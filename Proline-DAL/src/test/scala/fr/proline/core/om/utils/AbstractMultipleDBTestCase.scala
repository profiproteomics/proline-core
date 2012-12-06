package fr.proline.core.om.utils

import com.weiglewilczek.slf4s.Logging

import fr.proline.core.dal.DatabaseManagerForTest
import fr.proline.repository.utils.DatabaseTestCase
import fr.proline.repository.Database
import fr.proline.repository.DriverType

class AbstractMultipleDBTestCase extends Logging {

  var pdiDBTestCase: PDIDatabaseTestCase = null
  var msiDBTestCase: MSIDatabaseTestCase = null
  var psDBTestCase: PSDatabaseTestCase = null
  var udsDBTestCase: UDSDatabaseTestCase = null

  var dbManagerForTest: DatabaseManagerForTest = null

  def initDBsDBManagement(driverType: DriverType) {
    logger.info("Creating UDS, PDI, PS, MSI test databases")

    udsDBTestCase = new UDSDatabaseTestCase(driverType)
    udsDBTestCase.initDatabase()

    pdiDBTestCase = new PDIDatabaseTestCase(driverType)
    pdiDBTestCase.initDatabase()

    psDBTestCase = new PSDatabaseTestCase(driverType)
    psDBTestCase.initDatabase()

    msiDBTestCase = new MSIDatabaseTestCase(driverType)
    msiDBTestCase.initDatabase()

    dbManagerForTest = new DatabaseManagerForTest(udsDBTestCase.getConnector, pdiDBTestCase.getConnector, psDBTestCase.getConnector, msiDBTestCase.getConnector, null, false)
  }

  def closeDbs() = {
    logger.debug("Closing MSI Db TestCase")
    msiDBTestCase.tearDown

    logger.debug("Closing PS Db TestCase")
    psDBTestCase.tearDown

    logger.debug("Closing PDI Db TestCase")
    pdiDBTestCase.tearDown

    logger.debug("Closing UDS Db TestCase")
    udsDBTestCase.tearDown
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

  override def getDatabase() = {
    Database.UDS
  }

  val propertiesFile = "db_uds.properties"

}

class PDIDatabaseTestCase(val driverType: DriverType) extends DatabaseAndDriverTestCase {

  override def getDatabase() = {
    Database.PDI
  }

  val propertiesFile = "db_pdi.properties"

}

class PSDatabaseTestCase(val driverType: DriverType) extends DatabaseAndDriverTestCase {

  override def getDatabase() = {
    Database.PS
  }

  val propertiesFile = "db_ps.properties"

}

class MSIDatabaseTestCase(val driverType: DriverType) extends DatabaseAndDriverTestCase {

  override def getDatabase() = {
    Database.MSI
  }

  val propertiesFile = "db_msi.properties"

}



