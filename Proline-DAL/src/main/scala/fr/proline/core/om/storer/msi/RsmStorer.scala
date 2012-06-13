package fr.proline.core.om.storer.msi

import com.weiglewilczek.slf4s.Logging
import com.codahale.jerkson.Json.generate
import net.noerd.prequel.ReusableStatement
import net.noerd.prequel.SQLFormatterImplicits._

import fr.proline.core.dal.SQLFormatterImplicits._
import fr.proline.core.utils.sql.BoolToSQLStr
import fr.proline.core.dal.{MsiDb,MsiDbResultSummaryTable}
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.om.storer.msi.impl.SQLiteRsmStorer

trait IRsmStorer extends Logging {
  
  val msiDb: MsiDb // Main MSI db connection
  val scoringIdByType = new fr.proline.core.dal.helper.MsiDbHelper( msiDb ).getScoringIdByType
  
  def storeRsmPeptideInstances( rsm: ResultSummary ): Int
  def storeRsmPeptideSets( rsm: ResultSummary ): Int
  def storeRsmProteinSets( rsm: ResultSummary ): Int
  
}

/** A factory object for implementations of the IRsmStorer trait */
object RsmStorer {
  
  val rsmInsertQuery = MsiDbResultSummaryTable.makeInsertQuery(
                         MsiDbResultSummaryTable.getColumnsAsStrList().filter( _ != "id" )
                       )
  
  /*import fr.proline.core.om.storer.msi.impl.GenericRsStorer
  import fr.proline.core.om.storer.msi.impl.PgRsStorer
  import fr.proline.core.om.storer.msi.impl.SQLiteRsStorer*/
  
  def apply( msiDb: MsiDb ): RsmStorer = { msiDb.config.driver match {
    //case "org.postgresql.Driver" => new RsStorer( new PgRsStorer( msiDb ) )
    case "org.sqlite.JDBC" => new RsmStorer( new SQLiteRsmStorer( msiDb ) )
    //case _ => new RsStorer( new GenericRsStorer( msiDb ) )
    }
  }
}

class RsmStorer( private val _storer: IRsmStorer ) extends Logging {
  
  val msiDb = _storer.msiDb
  val rsmInsertQuery = RsmStorer.rsmInsertQuery
  
  def storeResultSummary( rsm: ResultSummary ): Unit = {
    
    this._insertResultSummary( rsm )
    
    // Store peptide instances
    this._storer.storeRsmPeptideInstances( rsm )
    logger.info( "peptide instances have been stored !" )
    
    // Store protein sets
    this._storer.storeRsmProteinSets( rsm )
    logger.info( "protein sets have been stored" )
    
    // Store peptides sets
    this._storer.storeRsmPeptideSets( rsm )
    logger.info( "peptides sets have been stored" )
    

    
  }
  
  private def _insertResultSummary( rsm: ResultSummary ): Unit = {
    
    val msiDbConn = this.msiDb.getOrCreateConnection()
    
    /// Define some vars
    val rsmDesc = if( rsm.name == null ) None else Some( rsm.description )
    val modificationTimestamp = new java.util.Date() // msiDb.stringifyDate( new java.util.Date )        
    var decoyRsmId = if( rsm.getDecoyResultSummaryId > 0 ) Some(rsm.getDecoyResultSummaryId) else None
    val rsId = rsm.getResultSetId
    val rsmPropsAsJSON = if( rsm.properties != None ) Some(generate( rsm.properties )) else None
    
    // Store RDB result summary
    // TODO: use JPA instead
    
    val stmt = msiDbConn.prepareStatement( this.rsmInsertQuery, java.sql.Statement.RETURN_GENERATED_KEYS )     
    new ReusableStatement( stmt, msiDb.config.sqlFormatter ) <<
      rsmDesc <<
      modificationTimestamp <<
      BoolToSQLStr(false) <<
      rsmPropsAsJSON <<
      decoyRsmId <<
      rsId

    stmt.execute()
    rsm.id = this.msiDb.extractGeneratedInt( stmt )
    
  }
  
}
