package fr.proline.core.dal

import net.noerd.prequel._

object MsiDb {
  
  def apply( projectId: Int ): MsiDb = {
    
    // TODO: change the configuration according to the project id instead of using a default config
    
    val msiDbConfig = this.getDefaultConfig
    new MsiDb( msiDbConfig )
  }
  
  def getDefaultConfig = DatabaseConfig(
    driver = "org.postgresql.Driver",
    jdbcURL = "jdbc:postgresql://10.1.31.10:5432/msi_db",
    username = "proline_db_user", 
    password = "proline",
    //isolationLevel = IsolationLevels.Serializable,
    sqlFormatter = SQLFormatter.HSQLDBSQLFormatter    
    )
}

class MsiDb( val config: DatabaseConfig,
             val boolStrAsInt: Boolean = false, // TODO: set to false when DB model is updated
             val maxVariableNumber: Int = 10000 ) extends Database {
  
}