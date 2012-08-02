package fr.proline.core.dal

import net.noerd.prequel._
import javax.persistence.Persistence
import fr.proline.repository.DatabaseConnector
import fr.proline.core.orm.utils.JPAUtil
import fr.proline.repository.ProlineRepository

object MsiDb extends DatabaseConfigBuilder {
  
  def apply( projectId: Int ): MsiDb = {
    
    // TODO: change the configuration according to the project id instead of using a default config
    
    val msiDbConfig = this.getDefaultConfig
    new MsiDb( msiDbConfig )
  }
  
  def apply( dbManager: DatabaseManagement, projectId: Int ): MsiDb = {    
    new MsiDb( dbManager.getMSIDatabaseConnector( projectId ) )
  }
    
  /*def getDefaultConfig = DatabaseConfig(
    driver = "org.postgresql.Driver",
    jdbcURL = "jdbc:postgresql://10.1.31.10:5432/msi_db",
    username = "proline_db_user", 
    password = "proline",
    //isolationLevel = IsolationLevels.Serializable,
    sqlFormatter = SQLFormatter.HSQLDBSQLFormatter    
    )*/
  
  def getDefaultConfig = DatabaseConfig(
    driver = "org.sqlite.JDBC",
    jdbcURL = "jdbc:sqlite:D:/proline/data/msi-db.sqlite",
    isolationLevel = IsolationLevels.Serializable,
    sqlFormatter = SQLFormatter.HSQLDBSQLFormatter
    )
}

/*class MsiDb( val config: DatabaseConfig,
             val boolStrAsInt: Boolean = false, // TODO: set to false when DB model is updated
             val maxVariableNumber: Int = 999 ) extends Database {
}*/

class MsiDb( val config: DatabaseConfig,
             val dbConnector: DatabaseConnector = null,
             val boolStrAsInt: Boolean = false,
             val maxVariableNumber: Int = 999              
              ) extends Database {
  
  def this( dbConnector: DatabaseConnector, boolStrAsInt: Boolean = false, maxVariableNumber: Int = 999 ) = {
    this( MsiDb.buildConfigFromDatabaseConnector( dbConnector ), dbConnector, boolStrAsInt, maxVariableNumber )
  }
  
  lazy val entityManagerFactory = {
    if( dbConnector == null ) {
      throw new Exception("a DB connector must be first provided")
    }
  
    Persistence.createEntityManagerFactory(
      JPAUtil.PersistenceUnitNames.getPersistenceUnitNameForDB(ProlineRepository.Databases.MSI),
      this.dbConnector.getEntityManagerSettings
    )
  }
  
  lazy val entityManager = this.entityManagerFactory.createEntityManager()
  
}