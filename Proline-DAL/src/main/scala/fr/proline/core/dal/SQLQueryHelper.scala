package fr.proline.core.dal

import org.joda.time.format.DateTimeFormat

import com.weiglewilczek.slf4s.Logging

import fr.profi.jdbc.easy.EasyDBC
import fr.profi.jdbc.AbstractSQLDialect
import fr.profi.jdbc.AsShortStringBooleanFormatter
import fr.profi.jdbc.DefaultSQLDialect
import fr.profi.jdbc.SQLiteTypeMapper
import fr.profi.jdbc.TxIsolationLevels
import fr.proline.repository.DriverType
import fr.proline.repository.IDatabaseConnector

object ProlineSQLiteSQLDialect extends AbstractSQLDialect(
  DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss.SSSS"),
  AsShortStringBooleanFormatter,
  SQLiteTypeMapper,
  "last_insert_rowid()",
  999
)

class SQLQueryHelper( val connection: java.sql.Connection, val driverType: DriverType ) extends Logging {
  
  def this( dbConnector: IDatabaseConnector ) {    
    this( dbConnector.getDataSource.getConnection, dbConnector.getDriverType )
    this.logger.debug("opening database connection (database type =" + dbConnector.getDatabase().name() +")" )
  }
  
  val dialect = driverType match {
    case DriverType.SQLITE => ProlineSQLiteSQLDialect
    case _ => DefaultSQLDialect
  }
  val txIsolationLevel = driverType match {
    case DriverType.SQLITE => TxIsolationLevels.SERIALIZABLE
    case _ => TxIsolationLevels.READ_COMMITTED
  }
  
  lazy val ezDBC = {
    EasyDBC( connection, dialect, txIsolationLevel )
  }
  
  def closeConnection() = {
    this.connection.close()
  }

}
