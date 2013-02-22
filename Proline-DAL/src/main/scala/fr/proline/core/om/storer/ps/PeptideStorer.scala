package fr.proline.core.om.storer.ps

import com.weiglewilczek.slf4s.Logging
import com.codahale.jerkson.Json.generate
import fr.profi.jdbc.easy._
import fr.profi.jdbc.PreparedStatementWrapper
import fr.proline.core.dal.DoJDBCWork
import fr.proline.core.dal.tables.ps.{PsDbPeptideTable,PsDbPeptidePtmTable}
import fr.proline.core.om.model.msi.LocatedPtm
import fr.proline.core.om.model.msi.Peptide
import fr.proline.context.DatabaseConnectionContext

class PeptideStorer extends Logging {

  def storePeptides(peptides: Seq[Peptide], psDbCtx: DatabaseConnectionContext): Unit = {

    DoJDBCWork.withEzDBC(psDbCtx, { psEzDBC =>
      logger.info("storing peptides in PsDb...")
  
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
      peptide.properties.map(generate(_)),
      Option(null)
    )

    peptide.id = stmt.generatedInt

  }

  private def _insertPeptidePtm(stmt: PreparedStatementWrapper, locatedPtm: LocatedPtm, peptideId: Int): Unit = {

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

  