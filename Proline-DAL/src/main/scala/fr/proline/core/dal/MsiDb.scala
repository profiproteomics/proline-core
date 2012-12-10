package fr.proline.core.dal

/*
import net.noerd.prequel._
import java.sql.Connection
import javax.persistence.Persistence
import fr.proline.repository.IDatabaseConnector
import fr.proline.repository.util.JPAUtils

class MsiDbSQLHelper( val dbConnector: IDatabaseConnector ) extends SQLQueryHelper


class MsiDb( val config: DatabaseConfig,
             val dbConnector: DatabaseConnector = null,
             val boolStrAsInt: Boolean = false,
             val maxVariableNumber: Int = 999              
              ) extends Database {
  
  def this( dbConnector: DatabaseConnector, boolStrAsInt: Boolean = false, maxVariableNumber: Int = 999 ) = {
    this( MsiDb.buildConfigFromDatabaseConnector( dbConnector ), dbConnector, boolStrAsInt, maxVariableNumber )
  }
  
  override def getOrCreateConnection(): Connection = {
    if( this.connection == null ) {
      if( dbConnector != null && dbConnector.hasConnection && dbConnector.getConnection.isClosed == false ) {
        this.connection = dbConnector.getConnection()
      }
      else { super.getOrCreateConnection() }
    }
    this.connection
  }
  
  lazy val entityManagerFactory = {
    require( dbConnector != null, "a DB connector must be first provided" )
    
    Persistence.createEntityManagerFactory(
      JPAUtil.PersistenceUnitNames.getPersistenceUnitNameForDB(ProlineRepository.Databases.MSI),
      this.dbConnector.getEntityManagerSettings
    )
  }
  
  lazy val entityManager = this.entityManagerFactory.createEntityManager()
  
}*/