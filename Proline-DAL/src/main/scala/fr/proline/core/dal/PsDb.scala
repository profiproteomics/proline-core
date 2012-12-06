package fr.proline.core.dal

import net.noerd.prequel._
import fr.proline.repository.IDatabaseConnector

class PsDbSQLHelper( val dbConnector: IDatabaseConnector ) extends SQLQueryHelper

/*
object PsDb extends DatabaseConfigBuilder {

  def apply( projectId: Int ): PsDb = {
    
    // TODO: change the configuration according to the project id instead of using a default config
    
    val psDbConfig = this.getDefaultConfig
    new PsDb( psDbConfig )
  }
  
  def apply( dbManager: DatabaseManagement): PsDb = {    
    new PsDb( PsDb.buildConfigFromDatabaseConnector( dbManager.psDBConnector ))
  }
  
  def apply( dbConn: DatabaseConnector): PsDb = {    
    new PsDb( PsDb.buildConfigFromDatabaseConnector( dbConn ))
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
    jdbcURL = "jdbc:sqlite:D:/proline/data/ps-db.sqlite",
    isolationLevel = IsolationLevels.Serializable,
    sqlFormatter = SQLFormatter.HSQLDBSQLFormatter
    )
}

class PsDb( val config: DatabaseConfig,
            val boolStrAsInt: Boolean = false,
            val maxVariableNumber: Int = 999 ) extends Database {
  
  override def onNewConnection() {
    
    if( this.config.driver == "org.sqlite.JDBC" ) {
      val conn = this.connection    
      val tx = Transaction( conn, config.sqlFormatter )  
      
      tx.execute("PRAGMA cache_size=1000000;")
      //tx.execute("PRAGMA journal_mode=WAL;")
      tx.execute("PRAGMA temp_store=MEMORY;")
    }
    
  }
  
}*/