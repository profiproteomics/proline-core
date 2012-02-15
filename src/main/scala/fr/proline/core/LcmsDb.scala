package fr.proline.core

import net.noerd.prequel._

object LcmsDb {
  
  def apply( projectId: Int ): LcmsDb = {
    
    // TODO: change the configuration according to the project id
    
    val lcmsDbConfig = this.getDefaultConfig
    new LcmsDb( lcmsDbConfig )
  }
  
  def getDefaultConfig = DatabaseConfig(
    driver = "org.sqlite.JDBC",
    jdbcURL = "jdbc:sqlite:D:/prosper/data/lcms-db.sqlite",
    isolationLevel = IsolationLevels.Serializable,
    sqlFormatter = SQLFormatter.HSQLDBSQLFormatter
    )
}

class LcmsDb( val config: DatabaseConfig,
              private var transaction: Transaction = null,
              val boolStrAsInt: Boolean = true, // TODO: set to false when DB model is updated
              val maxVariableNumber: Int = 999
              ) {
  
  // TODO: use connection pooling feature
  import java.sql.Connection
  import java.sql.DriverManager
  
  var connection: java.sql.Connection = null
  if( transaction != null ) connection = transaction.connection
  
  def newConnection(): Unit = {
    connection = DriverManager.getConnection( config.jdbcURL )
  }
  
  def getOrCreateConnection(): Connection = {
    if( connection == null ) newConnection()
    connection
  }
  
  def closeConnection(): Unit = {
    connection.close()
    connection = null
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
    val connection = getOrCreateConnection
    
    // Change the connection config to be ready for a new transaction
    connection.setAutoCommit(false)
    connection.setTransactionIsolation( config.isolationLevel.id )
    
    // Create new transaction
    transaction = Transaction( connection, config.sqlFormatter )
    
    ()
  }
  
  def getOrCreateTransaction(): Transaction = {
    if( transaction == null ) newTransaction()
    transaction
  }
  
  def rollbackTransaction(): Unit = { 
    transaction.rollback()
    transaction = null
  }
  
  def commitTransaction(): Unit = {
    transaction.commit()
    transaction = null
  }
  
  def isInTransaction(): Boolean = transaction != null
  
  /** A workaround for date to string conversion (will be removed obsolete when prequel is fixed) */
  def stringifyDate( date: java.util.Date ): String = {
    
    val dt = new org.joda.time.DateTime( date )
    this.config.sqlFormatter.timeStampFormatter.print( dt )
    
  }
  
}

import net.noerd.prequel.Nullable
import net.noerd.prequel.IntFormattable
import net.noerd.prequel.DoubleFormattable
import net.noerd.prequel.StringFormattable
import net.noerd.prequel.SQLFormatter

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
  implicit def someDouble2Formattable( wrapped: Option[Double] ) = wrapped match {
                                                              case None => NullFormattable(Some(null))
                                                              case Some(value) => DoubleFormattable(value)
                                                            }
  implicit def someString2Formattable( wrapped: Option[String] ) = wrapped match {
                                                              case None => NullFormattable(Some(null))
                                                              case Some(value) => StringFormattable(value)
                                                            }
}
