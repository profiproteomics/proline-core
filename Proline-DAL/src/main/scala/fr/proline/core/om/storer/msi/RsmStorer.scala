package fr.proline.core.om.storer.msi

import com.typesafe.scalalogging.slf4j.Logging
import fr.profi.jdbc.easy._
import fr.profi.util.serialization.ProfiJson
import fr.proline.context.DatabaseConnectionContext
import fr.proline.context.IExecutionContext
import fr.proline.core.dal._
import fr.proline.core.dal.tables.msi.{MsiDbResultSummaryTable}
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.om.storer.msi.impl.SQLRsmStorer
import fr.proline.core.dal.tables.msi.MsiDbResultSummaryObjectTreeMapTable
import fr.proline.core.dal.tables.msi.MsiDbObjectTreeTable

trait IRsmStorer extends Logging {
  
  def storeRsmPeptideInstances( rsm: ResultSummary, execCtx: IExecutionContext ): Int
  def storeRsmPeptideSets( rsm: ResultSummary, execCtx: IExecutionContext ): Int
  def storeRsmProteinSets( rsm: ResultSummary, execCtx: IExecutionContext ): Int
  
}

/** A factory object for implementations of the IRsmStorer trait */
object RsmStorer {
  
  val rsmInsertQuery = MsiDbResultSummaryTable.mkInsertQuery{ (c,colsList) => 
            colsList.filter( _ != c.ID) }
  val objectTreeInsertQuery = MsiDbObjectTreeTable.mkInsertQuery{ (ct,colsList) => 
            colsList.filter( c => (c != ct.ID) && (c != ct.BLOB_DATA)) }

  val objectTreeMapInsertQuery = MsiDbResultSummaryObjectTreeMapTable.mkInsertQuery()

            
  def apply( msiDbContext: DatabaseConnectionContext ): RsmStorer = {
    
    msiDbContext.isJPA match {
      case true => new RsmStorer( new SQLRsmStorer() )
      case false => new RsmStorer( new SQLRsmStorer() )
    }
  }
}

class RsmStorer( private val _writer: IRsmStorer ) extends Logging {
  
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
      
      msiEzDBC.executePrepared( RsmStorer.rsmInsertQuery, true ) { stmt =>
        stmt.executeWith(
          Option( rsm.description ),
          Option.empty[String],
          new java.util.Date(),          
          false,
          if (rsm.properties.isDefined) rsm.properties.get.validationProperties.map(ProfiJson.serialize(_)) else Option.empty[String],
          if( rsm.getDecoyResultSummaryId > 0 ) Some(rsm.getDecoyResultSummaryId) else Option.empty[Long],
          rsm.getResultSetId
        )
        
        rsm.id = stmt.generatedLong
      }
        
//      if (rsm.properties.isDefined && rsm.properties.get.peptideRocPoints.isDefined) {
//          
//          val objectTreeId = msiEzDBC.executePrepared( RsmStorer.objectTreeInsertQuery , true ) { stmt =>
//          	stmt.executeWith(
//          	    rsm.properties.get.peptideRocPoints.map(ProfiJson.serialize(_)),
//          	    Option.empty[String],
//          	    "result_summary.validation_properties"
//           )
//          
//           stmt.generatedLong
//          }
//
//          msiEzDBC.executePrepared( RsmStorer.objectTreeMapInsertQuery, true ) { stmt =>
//          	stmt.executeWith(rsm.id,"result_summary.validation_properties", objectTreeId) 
//          }
//          logger.info("peptideRocPoints have been saved")
//      }
    })
  }
  
}
