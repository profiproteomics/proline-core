package fr.proline.core.om.storer.msi

import com.typesafe.scalalogging.slf4j.Logging
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

trait IRsmStorer extends Logging {
  
  def storeRsmPeptideInstances( rsm: ResultSummary, execCtx: IExecutionContext ): Int
  def storeRsmPeptideSets( rsm: ResultSummary, execCtx: IExecutionContext ): Int
  def storeRsmProteinSets( rsm: ResultSummary, execCtx: IExecutionContext ): Int
  
}

/** A factory object for implementations of the IRsmStorer trait */
object RsmStorer {
  
  private val rsmInsertQuery = MsiDbResultSummaryTable.mkInsertQuery{ (c,colsList) => colsList.filter( _ != c.ID) }
  private val rocCurveInsertQuery = MsiDbObjectTreeTable.mkInsertQuery { (ct,colsList) => 
    List(ct.CLOB_DATA, ct.SCHEMA_NAME)
  }
  private val rocCurveMappingInsertQuery = MsiDbResultSummaryObjectTreeMapTable.mkInsertQuery()
  private val peptideRocCurveSchemaName = SchemaName.PEPTIDE_VALIDATION_ROC_CURVE.toString
  private val proteinRocCurveSchemaName = SchemaName.PROTEIN_VALIDATION_ROC_CURVE.toString
            
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
      
      // Store ROC curves if they are defined
      def storeRocCurve( rocCurve: MsiRocCurve, schemaName: String ) {
        val objectTreeId = msiEzDBC.executePrepared(RsmStorer.rocCurveInsertQuery, true) { stmt =>
          stmt.executeWith(
            ProfiJson.serialize(rocCurve),
            schemaName
          )

          stmt.generatedLong
        }

        msiEzDBC.executePrepared(RsmStorer.rocCurveMappingInsertQuery, true) { stmt =>
          stmt.executeWith(rsm.id, schemaName, objectTreeId)
        }
      }
      
      for (peptideValidationRocCurve <- rsm.peptideValidationRocCurve) {
        storeRocCurve(peptideValidationRocCurve, RsmStorer.peptideRocCurveSchemaName)
        logger.info("peptideValidationRocCurve have been stored")
      }
      for (proteinValidationRocCurve <- rsm.proteinValidationRocCurve) {
        storeRocCurve(proteinValidationRocCurve, RsmStorer.proteinRocCurveSchemaName)
        logger.info("proteinValidationRocCurve have been stored")
      }

    })
  }
  
}
