package fr.proline.core.om.storer.msi

import com.typesafe.scalalogging.LazyLogging

import fr.profi.jdbc.easy._
import fr.profi.jdbc.PreparedStatementWrapper
import fr.profi.util.serialization.ProfiJson
import fr.proline.context.MsiDbConnectionContext
import fr.proline.core.dal.DoJDBCWork
import fr.proline.core.dal.tables.msi.{MsiDbPeptideTable,MsiDbPeptidePtmTable}
import fr.proline.core.om.model.msi.LocatedPtm
import fr.proline.core.om.model.msi.Peptide

/*@deprecated("0.6.0","Use SQLPeptideWriter instead")
class PeptideStorer extends LazyLogging {

  def storePeptides(peptides: Seq[Peptide], msiDbCtx: MsiDbConnectionContext): Unit = {

    DoJDBCWork.withEzDBC(msiDbCtx) { msiEzDBC =>
      logger.info(s"Storing ${peptides.length} peptides in MsiDb...")
  
      val peptideInsertQuery = MsiDbPeptideTable.mkInsertQuery{ (c,colsList) => 
        colsList.filter( _ != c.ID)
      }
      
      msiEzDBC.executePrepared( peptideInsertQuery, true ) { stmt => 
        peptides.foreach { this._insertPeptide( stmt, _ ) }
      }
  
      val peptidePtmInsertQuery = MsiDbPeptidePtmTable.mkInsertQuery{ (c,colsList) => 
        colsList.filter( _ != c.ID)
      }
      
      msiEzDBC.executePrepared( peptidePtmInsertQuery, false ) { stmt => 
        for( peptide <- peptides ) {
          if( peptide.ptms != null ) {
            peptide.ptms.foreach { this._insertPeptidePtm( stmt, _, peptide.id ) }
          }
        }
      }
    }
    
  }

  private def _insertPeptide(stmt: PreparedStatementWrapper, peptide: Peptide): Unit = {

    stmt.executeWith(
      peptide.sequence,
      Option(peptide.ptmString),
      peptide.calculatedMass,
      peptide.properties.map(ProfiJson.serialize(_)),
      // TODO: handle atom label id
      Option.empty[Long]
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

  }

}*/

  