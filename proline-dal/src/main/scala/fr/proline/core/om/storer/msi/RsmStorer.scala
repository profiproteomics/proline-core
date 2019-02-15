package fr.proline.core.om.storer.msi

import com.typesafe.scalalogging.LazyLogging
import fr.profi.jdbc.easy._
import fr.profi.util.serialization.ProfiJson
import fr.proline.context.DatabaseConnectionContext
import fr.proline.context.IExecutionContext
import fr.proline.core.dal._
import fr.proline.core.dal.tables.msi.MsiDbObjectTreeTable
import fr.proline.core.dal.tables.msi.MsiDbResultSummaryObjectTreeMapTable
import fr.proline.core.dal.tables.msi.MsiDbResultSummaryTable
import fr.proline.core.om.model.msi.MsiRocCurve
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.om.storer.msi.impl.SQLRsmStorer
import fr.proline.core.orm.msi.ObjectTreeSchema.SchemaName
import fr.proline.core.om.model.msi.PtmSite

trait IRsmStorer extends LazyLogging {
  
  def storeRsmPeptideInstances( rsm: ResultSummary, execCtx: IExecutionContext ): Int
  def storeRsmPeptideSets( rsm: ResultSummary, execCtx: IExecutionContext ): Int
  def storeRsmProteinSets( rsm: ResultSummary, execCtx: IExecutionContext ): Int
  
}

/** A factory object for implementations of the IRsmStorer trait */
object RsmStorer {
  
  private val rsmInsertQuery = MsiDbResultSummaryTable.mkInsertQuery{ (c,colsList) => colsList.filter( _ != c.ID) }
  private val objectTreeInsertQuery = MsiDbObjectTreeTable.mkInsertQuery { (ct,colsList) => List(ct.CLOB_DATA, ct.SCHEMA_NAME) }
  private val rsmObjectTreeMapInsertQuery = MsiDbResultSummaryObjectTreeMapTable.mkInsertQuery()

  def apply( msiDbContext: DatabaseConnectionContext ): RsmStorer = {
      new RsmStorer( new SQLRsmStorer() )
  }
}

class RsmStorer( private val _writer: IRsmStorer ) extends LazyLogging {
  
  //TODO : temporarily disable Roc curve storage
  def storeRocCurves = false 
  
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
    
    DoJDBCWork.withEzDBC(execCtx.getMSIDbConnectionContext) { msiEzDBC =>
      
      msiEzDBC.executePrepared( RsmStorer.rsmInsertQuery, true ) { stmt =>
        stmt.executeWith(
          Option( rsm.description ),
          Option.empty[String],
          new java.util.Date(),
          false,
          if (rsm.properties.isDefined) rsm.properties.map(ProfiJson.serialize(_)) else Option.empty[String],
          if( rsm.getDecoyResultSummaryId > 0 ) Some(rsm.getDecoyResultSummaryId) else Option.empty[Long],
          rsm.getResultSetId
        )
        
        rsm.id = stmt.generatedLong
      }

      // Store ROC curves if they are defined
      if (storeRocCurves) {
        for (peptideValidationRocCurve <- rsm.peptideValidationRocCurve) {
          _storeObjectTree(msiEzDBC, rsm.id, peptideValidationRocCurve, SchemaName.PEPTIDE_VALIDATION_ROC_CURVE.toString)
          logger.info("peptideValidationRocCurve have been stored")
        }
        for (proteinValidationRocCurve <- rsm.proteinValidationRocCurve) {
          _storeObjectTree(msiEzDBC, rsm.id, proteinValidationRocCurve, SchemaName.PROTEIN_VALIDATION_ROC_CURVE.toString)
          logger.info("proteinValidationRocCurve have been stored")
        }
      }
    }
  }
  
  
  def storePtmSites(rsmId: Long, ptmSites: Iterable[PtmSite], execCtx: IExecutionContext ): Unit = {
    DoJDBCWork.withEzDBC(execCtx.getMSIDbConnectionContext) { msiEzDBC =>      
          _storeObjectTree(msiEzDBC, rsmId, ptmSites, SchemaName.PTM_SITES.toString)
          logger.info("ptmSites have been stored")
    }
  }

  private def _storeObjectTree(msiEzDBC: EasyDBC, rsmId: Long, objectTree: Any, schemaName: String ) {
        val objectTreeId = msiEzDBC.executePrepared(RsmStorer.objectTreeInsertQuery, true) { stmt =>
          stmt.executeWith(
            ProfiJson.serialize(objectTree),
            schemaName
          )
          stmt.generatedLong
        }
        msiEzDBC.executePrepared(RsmStorer.rsmObjectTreeMapInsertQuery, true) { stmt =>
          stmt.executeWith(rsmId, schemaName, objectTreeId)
        }
  }

}
