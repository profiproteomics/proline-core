package fr.proline.core.dal

import java.sql.Connection
import org.joda.time.format.DateTimeFormat
import com.weiglewilczek.slf4s.Logging
import fr.profi.jdbc.AbstractSQLDialect
import fr.profi.jdbc.AsShortStringBooleanFormatter
import fr.profi.jdbc.DefaultSQLDialect
import fr.profi.jdbc.SQLiteTypeMapper
import fr.profi.jdbc.TxIsolationLevels
import fr.profi.jdbc.easy.EasyDBC
import fr.proline.context.DatabaseConnectionContext
import fr.proline.repository.DriverType
import fr.proline.repository.IDatabaseConnector
import fr.profi.jdbc.AbstractSQLDialect

object ProlineSQLiteSQLDialect extends AbstractSQLDialect(
  DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss.SSSS"),
  AsShortStringBooleanFormatter,
  SQLiteTypeMapper,
  "last_insert_rowid()",
  999
)

object ProlinePgSQLDialect extends AbstractSQLDialect(inExpressionCountLimit = 1000)

object ProlineEzDBC extends Logging {
  
  def getDriverDialect( driverType: DriverType ) = {
    driverType match {
      case DriverType.SQLITE => ProlineSQLiteSQLDialect
      case DriverType.POSTGRESQL => ProlinePgSQLDialect
      case _ =>  DefaultSQLDialect
    }
  }
  
  def getDriverTxIsolationLevel( driverType: DriverType  ) = {
    driverType match {
      case DriverType.SQLITE => TxIsolationLevels.SERIALIZABLE
      case _ => TxIsolationLevels.READ_COMMITTED
    }
  }
  
  def apply( connection: Connection, driverType: DriverType ): EasyDBC = {
    val ezDBC = EasyDBC( connection, getDriverDialect(driverType), getDriverTxIsolationLevel(driverType) )
    
    // Make some driver based optimizations
    if( (driverType == DriverType.SQLITE) && connection.getAutoCommit) {
      /* Only change PRAGMA outside transaction */
      // TODO LMN : move this code outside any JPA / EasyDBC transactions
      this.logger.debug("Setting SQLite DB cache to 100Mo and temp_store to MEMORY" )
      ezDBC.execute("PRAGMA cache_size=100000;")
      ezDBC.execute("PRAGMA temp_store=MEMORY;")
    }
    
    ezDBC 
  }
  
  def apply( dbContext: DatabaseConnectionContext ): EasyDBC = {
    require( dbContext.isJPA == false, "database context must be created in SQL mode")
    this.apply( dbContext.getConnection, dbContext.getDriverType )
  }
  
}
  
@deprecated( "Use ProlineEzDBC instead", "0.0.3.2" )
class SQLQueryHelper( val connection: Connection, val driverType: DriverType ) extends Logging {
  
  def this( dbConnector: IDatabaseConnector ) {
    this( dbConnector.getDataSource.getConnection, dbConnector.getDriverType )
    this.logger.debug("opening database connection (database type =" + dbConnector.getProlineDatabaseType.name() +")" )
  }
  
  val dialect = ProlineEzDBC.getDriverDialect(driverType)
  val txIsolationLevel = ProlineEzDBC.getDriverTxIsolationLevel(driverType)
  
  lazy val ezDBC = EasyDBC( connection, dialect, txIsolationLevel )
  
  def closeConnection() = {
    this.connection.close()
  }

}

