package fr.proline.core.om.storer.msi

import com.weiglewilczek.slf4s.Logging
import com.codahale.jerkson.Json.generate
import fr.profi.jdbc.easy._
import fr.proline.core.dal._
import fr.proline.core.dal.tables.msi.{MsiDbResultSummaryTable}
import fr.proline.core.dal.helper.MsiDbHelper
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.om.storer.msi.impl.SQLiteRsmStorer
import fr.proline.repository.DatabaseContext

trait IRsmStorer extends Logging {
  
  val msiEzDBC: EasyDBC // Main MSI db connection
  val scoringIdByType = new MsiDbHelper( msiEzDBC ).getScoringIdByType
  
  def storeRsmPeptideInstances( rsm: ResultSummary ): Int
  def storeRsmPeptideSets( rsm: ResultSummary ): Int
  def storeRsmProteinSets( rsm: ResultSummary ): Int
  
}

/** A factory object for implementations of the IRsmStorer trait */
object RsmStorer {
  
  val rsmInsertQuery = MsiDbResultSummaryTable.mkInsertQuery{ (c,colsList) => 
                         colsList.filter( _ != c.ID)
                       }
  
  /*import fr.proline.core.om.storer.msi.impl.GenericRsStorer
  import fr.proline.core.om.storer.msi.impl.PgRsStorer
  import fr.proline.core.om.storer.msi.impl.SQLiteRsStorer*/
  
  def apply( msiDbContext: DatabaseContext ): RsmStorer = {
    this.apply( msiDbContext, ProlineEzDBC(msiDbContext) )
  }
  
  def apply( msiDbContext: DatabaseContext, msiEzDBC: EasyDBC ): RsmStorer = {
    //if( msiDbContext.isJPA() ) return new JPARsmStorer
    
    msiEzDBC.dialect match {
      //case "org.postgresql.Driver" => new RsStorer( new PgRsStorer( msiDb ) )
      case ProlineSQLiteSQLDialect => new RsmStorer( new SQLiteRsmStorer( msiEzDBC ) )
      case _ => new RsmStorer( new SQLiteRsmStorer( msiEzDBC ) )
    }
  }
}

class RsmStorer( private val _storer: IRsmStorer ) extends Logging {
  
  val rsmInsertQuery = RsmStorer.rsmInsertQuery
  val msiEzDBC = _storer.msiEzDBC
  
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
    
    // Define some vars
    val rsmDesc = Option( rsm.description )
    val modificationTimestamp = new java.util.Date() // msiDb.stringifyDate( new java.util.Date )        
    var decoyRsmId = if( rsm.getDecoyResultSummaryId > 0 ) Some(rsm.getDecoyResultSummaryId) else None
    val rsId = rsm.getResultSetId
    val rsmPropsAsJSON = if( rsm.properties != None ) Some(generate( rsm.properties )) else None
    
    // Store RDB result summary
    // TODO: use JPA instead
    
    msiEzDBC.executePrepared( this.rsmInsertQuery, true ) { stmt =>
      stmt.executeWith(
        rsmDesc,
        modificationTimestamp,
        false,
        rsmPropsAsJSON,
        decoyRsmId,
        rsId
      )
      
      rsm.id = stmt.generatedInt
    }
  }
  
}
