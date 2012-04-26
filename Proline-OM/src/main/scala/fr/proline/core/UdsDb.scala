package fr.proline.core

import net.noerd.prequel._

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
}

class UdsDb( val config: DatabaseConfig,
             val boolStrAsInt: Boolean = false,
             val maxVariableNumber: Int = 999 ) extends Database {
  
}