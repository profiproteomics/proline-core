package fr.proline.core.dal

import net.noerd.prequel._
import net.noerd.prequel.Nullable
import net.noerd.prequel.IntFormattable
import net.noerd.prequel.DoubleFormattable
import net.noerd.prequel.StringFormattable
import net.noerd.prequel.SQLFormatter
import org.apache.commons.lang3.StringUtils.isEmpty

trait Database {
  
  val config: DatabaseConfig
  val boolStrAsInt: Boolean // TODO: set to false when DB model is updated
  val maxVariableNumber: Int
  //val hibernateDialect: String = ""
  
  private var transaction: Transaction = null
  
  // TODO: use connection pooling feature
  import java.sql.Connection
  import java.sql.DriverManager
  
  var connection: java.sql.Connection = null
  if( transaction != null ) connection = transaction.connection
  
  def newConnection(): Unit = {
    
    val driver = config.driver
    
    // Load the driver
    Class.forName( config.driver )
    
    // Open connection
    if( isEmpty(config.username) ) { this.connection = DriverManager.getConnection( config.jdbcURL ) }
    else { this.connection = DriverManager.getConnection( config.jdbcURL, config.username, config.password ) }
      
    this.onNewConnection()
    
    ()
  }
  
    
  def onNewConnection() {}
  
  def getOrCreateConnection(): Connection = {
    if( this.connection == null ) newConnection()
    this.connection
  }
  
  def closeConnection(): Unit = {
    if( this.connection != null ) {
      this.connection.close()
      this.connection = null
    }
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
    val conn = getOrCreateConnection
    
    // Change the connection config to be ready for a new transaction
    conn.setAutoCommit(false)
    conn.setTransactionIsolation( config.isolationLevel.id )
    
    // Create new transaction
    this.transaction = Transaction( conn, config.sqlFormatter )
    
    ()
  }
  
  def getOrCreateTransaction(): Transaction = {
    if( this.transaction == null ) newTransaction()
    this.transaction
  }
  
  def rollbackTransaction(): Unit = { 
    this.transaction.rollback()
    this.transaction = null
  }
  
  def commitTransaction(): Unit = {
    transaction.commit()
    transaction = null
  }
  
  def isInTransaction(): Boolean = transaction != null
  
  /*def getConnector() = {
    import fr.proline.repository.DatabaseConnector
    val connProperties = new java.util.HashMap[String,String]
    connProperties.put(DatabaseConnector.PROPERTY_DRIVERCLASSNAME, this.config.driver )
    connProperties.put(DatabaseConnector.PROPERTY_URL, this.config.jdbcURL )
    connProperties.put(DatabaseConnector.PROPERTY_USERNAME, this.config.username )
    connProperties.put(DatabaseConnector.PROPERTY_PASSWORD, this.config.password )
    connProperties.put(DatabaseConnector.PROPERTY_DIALECT, this.hibernateDialect )
    
    new DatabaseConnector( connProperties )
  }*/
  
  /** A workaround for date to string conversion (will be removed obsolete when prequel is fixed) */
  def stringifyDate( date: java.util.Date ): String = {
    
    val dt = new org.joda.time.DateTime( date )
    this.config.sqlFormatter.timeStampFormatter.print( dt )
    
  }
  
  // TODO: move to SQL utils
  def extractGeneratedInt( statement: java.sql.Statement ): Int = {
    
    val rsWithGenKeys = statement.getGeneratedKeys()
    
    this.config.driver match {
      case "org.sqlite.JDBC" => rsWithGenKeys.getInt("last_insert_rowid()")
      case _ => if( rsWithGenKeys.next() ) rsWithGenKeys.getInt(1) else 0
    }
    
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
