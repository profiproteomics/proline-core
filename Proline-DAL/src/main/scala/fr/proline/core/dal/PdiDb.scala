package fr.proline.core.dal

import net.noerd.prequel._

object PdiDb extends DatabaseConfigBuilder {
  
  def apply( projectId: Int ): PdiDb = {    
    val psDbConfig = this.getDefaultConfig
    new PdiDb( psDbConfig )
  }
  
  def getDefaultConfig = DatabaseConfig(
    driver = "org.sqlite.JDBC",
    jdbcURL = "jdbc:sqlite:D:/prosper/data/pdi-db.sqlite",
    isolationLevel = IsolationLevels.Serializable,
    sqlFormatter = SQLFormatter.HSQLDBSQLFormatter
    )
}

class PdiDb( val config: DatabaseConfig,
             val boolStrAsInt: Boolean = true, // TODO: set to false when DB model is updated
             val maxVariableNumber: Int = 998 ) extends Database {
  
}