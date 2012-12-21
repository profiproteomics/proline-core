package fr.proline.core.dal

import com.weiglewilczek.slf4s.Logging
import java.sql.Connection
import org.joda.time.format.DateTimeFormat
import fr.profi.jdbc.easy.EasyDBC
import fr.profi.jdbc.AbstractSQLDialect
import fr.profi.jdbc.AsShortStringBooleanFormatter
import fr.profi.jdbc.DefaultSQLDialect
import fr.profi.jdbc.SQLiteTypeMapper
import fr.profi.jdbc.TxIsolationLevels
import fr.proline.repository.DatabaseContext
import fr.proline.repository.DriverType
import fr.proline.repository.util.JDBCWork

object ProlineSQLiteSQLDialect extends AbstractSQLDialect(
  DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss.SSSS"),
  AsShortStringBooleanFormatter,
  SQLiteTypeMapper,
  "last_insert_rowid()",
  999
)

object ProlineEzDBC {
  
  def getDriverDialect( driverType: DriverType ) = {
    driverType match {
      case DriverType.SQLITE => ProlineSQLiteSQLDialect
      case _ => DefaultSQLDialect
    }
  }
  
  def getDriverTxIsolationLevel( driverType: DriverType  ) = {
    driverType match {
      case DriverType.SQLITE => TxIsolationLevels.SERIALIZABLE
      case _ => TxIsolationLevels.READ_COMMITTED
    }
  }
  
  def apply( connection: Connection, driverType: DriverType ): EasyDBC = {
    EasyDBC( connection, getDriverDialect(driverType), getDriverTxIsolationLevel(driverType) )    
  }
  
  def apply( dbContext: DatabaseContext ): EasyDBC = {
    this.apply( dbContext.getConnection(), dbContext.getDriverType() )
  }
  
}

import fr.proline.repository.IDatabaseConnector
  
@deprecated( message = "Use ProlineEzDBC instead", since = "0.0.3.2" )
class SQLQueryHelper( val connection: Connection, val driverType: DriverType ) extends Logging {
  
  def this( dbConnector: IDatabaseConnector ) {
    this( dbConnector.getDataSource.getConnection, dbConnector.getDriverType )
    this.logger.debug("opening database connection (database type =" + dbConnector.getDatabase().name() +")" )
  }
  
  val dialect = ProlineEzDBC.getDriverDialect(driverType)
  val txIsolationLevel = ProlineEzDBC.getDriverTxIsolationLevel(driverType)
  
  lazy val ezDBC = EasyDBC( connection, dialect, txIsolationLevel )
  
  def closeConnection() = {
    this.connection.close()
  }

}

