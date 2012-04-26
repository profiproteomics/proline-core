package fr.proline.core

import net.noerd.prequel._

object PsDb {
  
  def apply( projectId: Int ): PsDb = {
    
    // TODO: change the configuration according to the project id instead of using a default config
    
    val psDbConfig = this.getDefaultConfig
    new PsDb( psDbConfig )
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