package fr.proline.core.om.storer.ps

import com.weiglewilczek.slf4s.Logging

import fr.profi.jdbc.easy._
import fr.profi.jdbc.PreparedStatementWrapper
import fr.proline.core.dal.tables.ps.{PsDbPeptideTable,PsDbPeptidePtmTable}
import fr.proline.core.om.model.msi.LocatedPtm
import fr.proline.core.om.model.msi.Peptide

/** A factory object for implementations of the IRsStorer trait */
object PeptideStorer {
  def apply(psEzDBC: EasyDBC) = new PeptideStorer(psEzDBC)
}

class PeptideStorer(psEzDBC: EasyDBC) extends Logging {

  def storePeptides(peptides: Seq[Peptide]): Map[String, Int] = {

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

    null
  }

  private def _insertPeptide(stmt: PreparedStatementWrapper, peptide: Peptide): Unit = {

    stmt.executeWith(
      peptide.sequence,
      Option(peptide.ptmString),
      peptide.calculatedMass,
      Option(null),
      Option(null))

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
      Option(null))

    //locatedPtm.id = this.psDb.extractGeneratedInt( stmt.wrapped )

  }

}

  