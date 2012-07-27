package fr.proline.core.dal

import net.noerd.prequel._
import fr.proline.repository.DatabaseConnector

object UdsDb {
  
  def apply( projectId: Int ): UdsDb = {
    val psDbConfig = this.getDefaultConfig
    new UdsDb( psDbConfig )
  }
  
  /*def getDefaultConfig = DatabaseConfig(
    driver = "org.postgresql.Driver",
    jdbcURL = "jdbc:postgresql://10.1.31.10:5432/ps_db",
    username = "proline_db_user", 
    password = "proline",
    //isolationLevel = IsolationLevels.Serializable,
    sqlFormatter = SQLFormatter.HSQLDBSQLFormatter    
    )*/
    
  def getDefaultConfig = DatabaseConfig(
    driver = "org.sqlite.JDBC",
    jdbcURL = "jdbc:sqlite:E:/eclipse/workspace/proline-core/src/main/resources/uds-db.sqlite",
    isolationLevel = IsolationLevels.Serializable,
    sqlFormatter = SQLFormatter.HSQLDBSQLFormatter
    )
    
  def getConfigFromDatabaseManagement(dbMgnt: DatabaseManagement) : DatabaseConfig =   { 
	 DatabaseConfig (    
			 driver = dbMgnt.udsDBConnector.getProperty(DatabaseConnector.PROPERTY_DRIVERCLASSNAME),
			 jdbcURL = dbMgnt.udsDBConnector.getProperty(DatabaseConnector.PROPERTY_URL),
			 username =  dbMgnt.udsDBConnector.getProperty(DatabaseConnector.PROPERTY_USERNAME), 
			 password =  dbMgnt.udsDBConnector.getProperty(DatabaseConnector.PROPERTY_PASSWORD), 				
			 sqlFormatter = SQLFormatter.HSQLDBSQLFormatter   )
  }
}

class UdsDb( val config: DatabaseConfig,
             val boolStrAsInt: Boolean = false,
             val maxVariableNumber: Int = 999 ) extends Database {
  
}