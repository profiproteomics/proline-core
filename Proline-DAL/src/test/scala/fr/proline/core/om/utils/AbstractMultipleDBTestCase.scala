package fr.proline.core.om.utils

import com.weiglewilczek.slf4s.Logging

import fr.proline.core.dal.DatabaseManagementTestCase
import fr.proline.core.orm.utils.JPAUtil
import fr.proline.repository.ProlineRepository.DriverType
import fr.proline.repository.utils.DatabaseUtils
import fr.proline.repository.utils.DatabaseTestCase

class AbstractMultipleDBTestCase extends Logging  {
  
  var pdiDBTestCase: PDIDatabaseTestCase = null
  var msiDBTestCase: MSIDatabaseTestCase = null
  var psDBTestCase: PSDatabaseTestCase = null
  var udsDBTestCase: UDSDatabaseTestCase = null
  
  var dbMgntTest: DatabaseManagementTestCase = null
	  
  def initDBsDBManagement( driverType: DriverType ) {
    
    logger.info( " ---- initDBsDBManagement Dbs" )
    pdiDBTestCase = new PDIDatabaseTestCase( driverType )
    msiDBTestCase = new MSIDatabaseTestCase( driverType )
    psDBTestCase = new PSDatabaseTestCase( driverType )
    udsDBTestCase = new UDSDatabaseTestCase( driverType )
  
    msiDBTestCase.initDatabase()
    msiDBTestCase.initEntityManager(JPAUtil.PersistenceUnitNames.MSI_Key.getPersistenceUnitName())
    
    psDBTestCase.initDatabase()
    psDBTestCase.initEntityManager(JPAUtil.PersistenceUnitNames.PS_Key.getPersistenceUnitName())
    
    udsDBTestCase.initDatabase()
    udsDBTestCase.initEntityManager(JPAUtil.PersistenceUnitNames.UDS_Key.getPersistenceUnitName())

    pdiDBTestCase.initDatabase()
    pdiDBTestCase.initEntityManager(JPAUtil.PersistenceUnitNames.PDI_Key.getPersistenceUnitName())
    
    dbMgntTest = new DatabaseManagementTestCase(udsDBTestCase.getConnector, pdiDBTestCase.getConnector, psDBTestCase.getConnector, msiDBTestCase.getConnector)
  }
	
	def closeDbs() = {
	  msiDBTestCase.tearDown
	  psDBTestCase.tearDown
	  udsDBTestCase.tearDown
	  pdiDBTestCase.tearDown
	}
}

abstract class DatabaseAndDriverTestCase extends DatabaseTestCase {
  val driverType: DriverType
  
  lazy val driverException = new Exception("unsupported database driver for testing")
  
  protected val propertiesFileDirectory: String = {
    driverType match {
      case DriverType.H2 => "/db_settings/h2"
      case DriverType.SQLITE => "/db_settings/sqlite"
      case _ => throw driverException
    }
  }
  
}

class MSIDatabaseTestCase( val driverType: DriverType ) extends DatabaseAndDriverTestCase {

  override def getSQLScriptLocation(): String = {
    driverType match {
      case DriverType.H2 => DatabaseUtils.H2_DATABASE_MSI_SCRIPT_LOCATION
      case DriverType.SQLITE => DatabaseUtils.SQLITE_DATABASE_MSI_SCRIPT_LOCATION
      case _ => throw driverException
    }
  }

  override def getPropertiesFilename(): String = {
    propertiesFileDirectory + "/db_msi.properties"
  }

}

class PSDatabaseTestCase( val driverType: DriverType ) extends DatabaseAndDriverTestCase {

  override def getSQLScriptLocation(): String = {
    driverType match {
      case DriverType.H2 => DatabaseUtils.H2_DATABASE_PS_SCRIPT_LOCATION
      case DriverType.SQLITE => DatabaseUtils.SQLITE_DATABASE_PS_SCRIPT_LOCATION
      case _ => throw driverException
    }
  }

  override def getPropertiesFilename(): String = {
    propertiesFileDirectory + "/db_ps.properties"
  }

}

class UDSDatabaseTestCase( val driverType: DriverType ) extends DatabaseAndDriverTestCase {

  override def getSQLScriptLocation(): String = {
    driverType match {
      case DriverType.H2 => DatabaseUtils.H2_DATABASE_UDS_SCRIPT_LOCATION
      case DriverType.SQLITE => DatabaseUtils.SQLITE_DATABASE_UDS_SCRIPT_LOCATION
      case _ => throw driverException
    }
  }

  override def getPropertiesFilename(): String = {
    propertiesFileDirectory + "/db_uds.properties"
  }

}

class PDIDatabaseTestCase( val driverType: DriverType ) extends DatabaseAndDriverTestCase {

  override def getSQLScriptLocation(): String = {
    driverType match {
      case DriverType.H2 => DatabaseUtils.H2_DATABASE_PDI_SCRIPT_LOCATION
      case DriverType.SQLITE => DatabaseUtils.SQLITE_DATABASE_PDI_SCRIPT_LOCATION
      case _ => throw driverException
    }
  }
  
  override def getPropertiesFilename(): String = {
    propertiesFileDirectory + "/db_pdi.properties"
  }
  
}
