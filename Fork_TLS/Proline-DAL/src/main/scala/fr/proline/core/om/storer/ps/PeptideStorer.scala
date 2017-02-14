package fr.proline.core.om.storer.ps

import com.typesafe.scalalogging.LazyLogging

import fr.profi.jdbc.easy._
import fr.profi.jdbc.PreparedStatementWrapper
import fr.profi.util.serialization.ProfiJson
import fr.proline.core.dal.DoJDBCWork
import fr.proline.core.dal.tables.ps.{PsDbPeptideTable,PsDbPeptidePtmTable}
import fr.proline.core.om.model.msi.LocatedPtm
import fr.proline.core.om.model.msi.Peptide
import fr.proline.context.DatabaseConnectionContext

class PeptideStorer extends LazyLogging {

  def storePeptides(peptides: Seq[Peptide], psDbCtx: DatabaseConnectionContext): Unit = {

    DoJDBCWork.withEzDBC(psDbCtx, { psEzDBC =>
      logger.info("Storing "+peptides.length+" Peptides in PsDb...")
  
      val peptideInsertQuery = PsDbPeptideTable.mkInsertQuery{ (c,colsList) => 
                                 colsList.filter( _ != c.ID)
                               }
      
      psEzDBC.executePrepared( peptideInsertQuery, true ) { stmt => 
        peptides.foreach { this._insertPeptide( stmt, _ ) }
      }
  
      val peptidePtmInsertQuery = PsDbPeptidePtmTable.mkInsertQuery{ (c,colsList) => 
                                    colsList.filter( _ != c.ID)
                                  }
      
      psEzDBC.executePrepared( peptidePtmInsertQuery, false ) { stmt => 
        for( peptide <- peptides ) {
          if( peptide.ptms != null ) {
            peptide.ptms.foreach { this._insertPeptidePtm( stmt, _, peptide.id ) }
          }
        }
      }
      
    },true)
    
  }

  private def _insertPeptide(stmt: PreparedStatementWrapper, peptide: Peptide): Unit = {

    stmt.executeWith(
      peptide.sequence,
      Option(peptide.ptmString),
      peptide.calculatedMass,
      peptide.properties.map(ProfiJson.serialize(_)),
      Option(null)
    )

    peptide.id = stmt.generatedLong

  }

  private def _insertPeptidePtm(stmt: PreparedStatementWrapper, locatedPtm: LocatedPtm, peptideId: Long): Unit = {

    stmt.executeWith(
      locatedPtm.seqPosition,
      locatedPtm.monoMass,
      locatedPtm.averageMass,
      Option(null),
      peptideId,
      locatedPtm.definition.id,
      Option(null)
    )

    //locatedPtm.id = this.psDb.extractGeneratedInt( stmt.wrapped )

  }

}

  