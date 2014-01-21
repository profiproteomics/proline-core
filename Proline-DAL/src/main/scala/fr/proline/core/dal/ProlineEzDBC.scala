package fr.proline.core.dal

import java.sql.Connection
import org.joda.time.format.DateTimeFormat
import com.typesafe.scalalogging.slf4j.Logging
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
      ezDBC.execute("PRAGMA foreign_keys=ON;")
    }
    
    ezDBC 
  }
  
  def apply( dbContext: DatabaseConnectionContext ): EasyDBC = {
    require( dbContext.isJPA == false, "database context must be created in SQL mode")
    this.apply( dbContext.getConnection, dbContext.getDriverType )
  }
  
}

