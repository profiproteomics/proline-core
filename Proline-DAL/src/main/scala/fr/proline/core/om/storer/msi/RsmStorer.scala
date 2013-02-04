package fr.proline.core.om.storer.msi

import com.weiglewilczek.slf4s.Logging
import com.codahale.jerkson.Json.generate
import fr.profi.jdbc.easy._
import fr.proline.core.dal._
import fr.proline.core.dal.tables.msi.{MsiDbResultSummaryTable}
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.om.storer.msi.impl.SQLiteRsmStorer
import fr.proline.context.DatabaseConnectionContext
import fr.proline.context.IExecutionContext

trait IRsmStorer extends Logging {
  
  def storeRsmPeptideInstances( rsm: ResultSummary, execCtx: IExecutionContext ): Int
  def storeRsmPeptideSets( rsm: ResultSummary, execCtx: IExecutionContext ): Int
  def storeRsmProteinSets( rsm: ResultSummary, execCtx: IExecutionContext ): Int
  
}

/** A factory object for implementations of the IRsmStorer trait */
object RsmStorer {
  
  val rsmInsertQuery = MsiDbResultSummaryTable.mkInsertQuery{ (c,colsList) => 
                         colsList.filter( _ != c.ID)
                       }
  
  def apply( msiDbContext: DatabaseConnectionContext ): RsmStorer = {
    
    msiDbContext.isJPA match {
      case true => new RsmStorer( new SQLiteRsmStorer() )
      case false => new RsmStorer( new SQLiteRsmStorer() )
    }
  }
}

class RsmStorer( private val _writer: IRsmStorer ) extends Logging {
  
  val rsmInsertQuery = RsmStorer.rsmInsertQuery
  
  def storeResultSummary( rsm: ResultSummary, execCtx: IExecutionContext ): Unit = {
    
    this._insertResultSummary( rsm, execCtx )
    
    // Store peptide instances
    this._writer.storeRsmPeptideInstances( rsm, execCtx )
    logger.info( "peptide instances have been stored !" )
    
    // Store protein sets
    this._writer.storeRsmProteinSets( rsm, execCtx )
    logger.info( "protein sets have been stored" )
    
    // Store peptides sets
    this._writer.storeRsmPeptideSets( rsm, execCtx )
    logger.info( "peptides sets have been stored" )
    
  }
  
  private def _insertResultSummary( rsm: ResultSummary, execCtx: IExecutionContext ): Unit = {
    
    // Store RDB result summary
    // TODO: use JPA instead    
    DoJDBCWork.withEzDBC( execCtx.getMSIDbConnectionContext, { msiEzDBC =>
      msiEzDBC.executePrepared( this.rsmInsertQuery, true ) { stmt =>
        stmt.executeWith(
          Option( rsm.description ),
          new java.util.Date(),
          false,
          rsm.properties.map(generate(_)),
          if( rsm.getDecoyResultSummaryId > 0 ) Some(rsm.getDecoyResultSummaryId) else Option.empty[Int],
          rsm.getResultSetId
        )
        
        rsm.id = stmt.generatedInt
      }
    })
  }
  
}
