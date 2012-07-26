package fr.proline.core.dal

import net.noerd.prequel._
import fr.proline.repository.DatabaseConnector

object PsDb {

  def getConfigFromDatabaseConnector(dbConnector: DatabaseConnector) : DatabaseConfig =   { 
	 DatabaseConfig (    
			 driver = dbConnector.getProperty(DatabaseConnector.PROPERTY_DRIVERCLASSNAME),
			 jdbcURL = dbConnector.getProperty(DatabaseConnector.PROPERTY_URL),
			 username =  dbConnector.getProperty(DatabaseConnector.PROPERTY_USERNAME), 
			 password =  dbConnector.getProperty(DatabaseConnector.PROPERTY_PASSWORD), 				
			 sqlFormatter = SQLFormatter.HSQLDBSQLFormatter   )
  }
    
    
  def getDefaultConfig = DatabaseConfig(
    driver = "org.postgresql.Driver",
    jdbcURL = "jdbc:postgresql://10.1.31.10:5432/ps_db",
    username = "proline_db_user", 
    password = "proline",
    //isolationLevel = IsolationLevels.Serializable,
    sqlFormatter = SQLFormatter.HSQLDBSQLFormatter    
    )
}

class PsDb( val config: DatabaseConfig,
            val boolStrAsInt: Boolean = false,
            val maxVariableNumber: Int = 10000 ) extends Database {
  
}