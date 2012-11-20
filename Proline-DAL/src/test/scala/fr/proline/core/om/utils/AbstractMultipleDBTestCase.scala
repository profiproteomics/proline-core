package fr.proline.core.om.utils

import fr.proline.repository.utils.DatabaseUtils
import fr.proline.repository.utils.DatabaseTestCase
import fr.proline.core.orm.utils.JPAUtil
import fr.proline.core.dal.DatabaseManagementTestCase
import com.weiglewilczek.slf4s.Logging

class AbstractMultipleDBTestCase extends Logging  {
  
	var pdiDBTestCase : PDIDatabaseTestCase = null
  var msiDBTestCase : MSIDatabaseTestCase = null
  var psDBTestCase: PSDatabaseTestCase = null
  var udsDBTestCase : UDSDatabaseTestCase = null
  
	var dbMgntTest: DatabaseManagementTestCase = null
	  
	def initDBsDBManagement(){
	  logger.info( " ---- initDBsDBManagement Dbs" )
		pdiDBTestCase = new PDIDatabaseTestCase()
		msiDBTestCase = new MSIDatabaseTestCase()
		psDBTestCase = new PSDatabaseTestCase()
		udsDBTestCase = new UDSDatabaseTestCase()
  
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
