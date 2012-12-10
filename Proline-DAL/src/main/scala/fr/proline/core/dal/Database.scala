package fr.proline.core.dal

import org.joda.time.format.DateTimeFormat

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

class SQLQueryHelper( val dbConnector: IDatabaseConnector ) {
  
  val driverType = dbConnector.getDriverType()
  val dialect = driverType match {
    case DriverType.SQLITE => ProlineSQLiteSQLDialect
    case _ => DefaultSQLDialect
  }
  val txIsolationLevel = driverType match {
    case DriverType.SQLITE => TxIsolationLevels.SERIALIZABLE
    case _ => TxIsolationLevels.READ_COMMITTED
  }
  
  lazy val ezDBC = EasyDBC( dbConnector.getDataSource().getConnection(),
                           dialect,
                           txIsolationLevel )

}

object SQLQueryHelper {
  def apply(dbConnector: IDatabaseConnector): EasyDBC = {
    new SQLQueryHelper(dbConnector).ezDBC
  }
}

/*
import java.sql.Connection
import java.sql.DriverManager
import net.noerd.prequel._
import net.noerd.prequel.Nullable
import net.noerd.prequel.IntFormattable
import net.noerd.prequel.DoubleFormattable
import net.noerd.prequel.StringFormattable
import net.noerd.prequel.SQLFormatter
import fr.proline.util.StringUtils.isEmpty
import fr.proline.repository.IDatabaseConnector
import fr.proline.repository.DriverType

trait SQLQueryHelper {
  
  // Required fields
  val dbConnector: IDatabaseConnector  
  val maxVariableNumber: Int = 999 // TODO: retrieve from elsewhere
  
  // Non required fields
  val driverType = dbConnector.getDriverType  
  lazy val txIsolationLevel: TransactionIsolation = this.getTxIsolationLevel
  var sqlFormatter = SQLFormatter.HSQLDBSQLFormatter
  
  private var _transaction: Transaction = null
  
  // TODO: use connection pooling feature ?  
  lazy val connection: java.sql.Connection = dbConnector.getDataSource.getConnection
  
  protected def getTxIsolationLevel(): TransactionIsolation = {   
    this.driverType match {
      case DriverType.SQLITE => IsolationLevels.Serializable
      case _ => IsolationLevels.ReadCommitted
    }
  }
  
  def closeConnection(): Unit = {
    this.connection.close()
  }
  
  def newTransaction(): Unit = {
    
    /*
     val transaction = TransactionFactory.newTransaction( config )

     val tx = Transaction(
            ConnectionPools.getOrCreatePool( config ).getConnection(),
            config.sqlFormatter
        )
     */
    
    // Create or retrieve the database connection
    val conn = this.connection
    
    // Change the connection config to be ready for a new transaction
    conn.setAutoCommit(false)
    conn.setTransactionIsolation( this.txIsolationLevel.id )
    
    // Create new transaction
    this._transaction = Transaction( conn, this.sqlFormatter )
    
    ()
  }
  
  def getOrCreateTransaction(): Transaction = {
    if( this._transaction == null ) newTransaction()
    this._transaction
  }
  
  def rollbackTransaction(): Unit = { 
    this._transaction.rollback()
    this._transaction = null
  }
  
  def commitTransaction(): Unit = {
    _transaction.commit()
    _transaction = null
  }
  
  def isInTransaction(): Boolean = _transaction != null
  
  /** A workaround for date to string conversion (will be removed obsolete when prequel is fixed) */
  def stringifyDate( date: java.util.Date ): String = {    
    val dt = new org.joda.time.DateTime( date )
    this.sqlFormatter.timeStampFormatter.print( dt )    
  }
  
  // TODO: move to SQL utils ?
  def extractGeneratedInt( statement: java.sql.Statement ): Int = {
    
    val rsWithGenKeys = statement.getGeneratedKeys()
    
    this.driverType match {
      case DriverType.SQLITE => rsWithGenKeys.getInt("last_insert_rowid()")
      case _ => if( rsWithGenKeys.next() ) rsWithGenKeys.getInt(1) else 0
    }
    
  }
  
  // TODO: move to SQL utils ?
  def selectRecordsAsMaps( queryString: String ): Array[Map[String,Any]] = {
    
    var colNames: Seq[String] = null
    
    // Execute SQL query to load records
    this.getOrCreateTransaction.select( queryString ) { r => 
      
      if( colNames == null ) { colNames = r.columnNames }
      
      // Build the record
      colNames.map( colName => ( colName -> r.nextObject.getOrElse(null) ) ).toMap
      
    } toArray
    
  }
  
}




 /*
class NothingFormattable( override val value: Option[Nothing] ) extends Nullable( value ) {
    override def escaped( formatter: SQLFormatter ): String = "null"
}
object NothingFormattable{
    def apply( value: Option[Nothing] ) = new NothingFormattable( value )
}*/

class NullFormattable( override val value: Option[Null] ) extends Nullable( value ) {
    override def escaped( formatter: SQLFormatter ): String = "null"
}
object NullFormattable{
    def apply( value: Option[Null] ) = new NullFormattable( value )
}

object SQLFormatterImplicits {
  implicit def someNull2Formattable( wrapped: Option[Null] ) = NullFormattable( wrapped )
  implicit def someInt2Formattable( wrapped: Option[Int] ) = wrapped match {
                                                              case None => NullFormattable(Some(null))
                                                              case Some(value) => IntFormattable(value)
                                                            }
  implicit def someBoolean2Formattable( wrapped: Option[Boolean] ) = wrapped match {
                                                              case None => NullFormattable(Some(null))
                                                              case Some(value) => BooleanFormattable(value)
                                                            }
  implicit def someFloat2Formattable( wrapped: Option[Float] ) = wrapped match {
                                                              case None => NullFormattable(Some(null))
                                                              case Some(value) => FloatFormattable(value)
                                                            }
  implicit def someDouble2Formattable( wrapped: Option[Double] ) = wrapped match {
                                                              case None => NullFormattable(Some(null))
                                                              case Some(value) => DoubleFormattable(value)
                                                            }
  implicit def someString2Formattable( wrapped: Option[String] ) = wrapped match {
                                                              case None => NullFormattable(Some(null))
                                                              case Some(value) => StringFormattable(value)
                                                            }
}
*/