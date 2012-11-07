package fr.proline.core.dal

import net.noerd.prequel._
import fr.proline.repository.DatabaseConnector

object UdsDb extends DatabaseConfigBuilder {
  
  def apply( dbConnector: DatabaseConnector ): UdsDb = {
    new UdsDb( this.buildConfigFromDatabaseConnector( dbConnector ) )
  }
  
  def apply( dbManager: DatabaseManagement ): UdsDb = {
    new UdsDb( this.buildConfigFromDatabaseManagement( dbManager ) )
  }
  
  def buildConfigFromDatabaseManagement(dbMgnt: DatabaseManagement): DatabaseConfig = {
    this.buildConfigFromDatabaseConnector( dbMgnt.udsDBConnector )
  }
  
  /*def getDefaultConfig = DatabaseConfig(
    driver = "org.postgresql.Driver",
    jdbcURL = "jdbc:postgresql://10.1.31.10:5432/ps_db",
    username = "proline_db_user", 
    password = "proline",
    //isolationLevel = IsolationLevels.Serializable,
    sqlFormatter = SQLFormatter.HSQLDBSQLFormatter    
    )*/
  
}

class UdsDb( val config: DatabaseConfig,
             val boolStrAsInt: Boolean = false,
             val maxVariableNumber: Int = 999 ) extends Database {
  
}