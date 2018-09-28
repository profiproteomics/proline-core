package fr.proline.core.om.storer.msi.impl

import com.typesafe.scalalogging.LazyLogging
import fr.profi.jdbc.easy._
import fr.profi.jdbc.PreparedStatementWrapper
import fr.profi.util.serialization.ProfiJson
import fr.proline.context.MsiDbConnectionContext
import fr.proline.core.dal.DoJDBCWork
import fr.proline.core.dal.tables.msi.{MsiDbPeptideTable,MsiDbPeptidePtmTable}
import fr.proline.core.om.model.msi.LocatedPtm
import fr.proline.core.om.model.msi.Peptide
import fr.proline.core.om.storer.msi.IPeptideWriter

private[msi] object SQLPeptideWriter extends IPeptideWriter with LazyLogging {

  def insertPeptides(peptides: Seq[Peptide], context: StorerContext): Unit = {
    require( peptides != null, "peptides is null")
    if(peptides.isEmpty) return ()

    DoJDBCWork.withEzDBC(context.getMSIDbConnectionContext) { msiEzDBC =>
      logger.info(s"Inserting ${peptides.length} peptides in MsiDb...")
  
      val peptideInsertQuery = MsiDbPeptideTable.mkInsertQuery{ (c,colsList) => 
        colsList.filter( _ != c.ID)
      }
      
      msiEzDBC.executePrepared( peptideInsertQuery, true ) { stmt => 
        peptides.foreach { this._insertPeptide( stmt, _ ) }
      }
  
      val peptidePtmInsertQuery = MsiDbPeptidePtmTable.mkInsertQuery{ (c,colsList) => 
        colsList.filter( _ != c.ID)
      }
      
      msiEzDBC.executeInBatch( peptidePtmInsertQuery) { stmt => 
        for (peptide <- peptides; if peptide.ptms != null; ptm <- peptide.ptms) {
          this._insertPeptidePtm( stmt, ptm, peptide.id )
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
      // TODO: handle atom label
      Option.empty[Long]
    )

    peptide.id = stmt.generatedLong
  }

  private def _insertPeptidePtm(stmt: PreparedStatementWrapper, locatedPtm: LocatedPtm, peptideId: Long): Unit = {

    stmt.executeWith(
      locatedPtm.seqPosition,
      locatedPtm.monoMass,
      locatedPtm.averageMass,
      Option.empty[String],
      peptideId,
      locatedPtm.definition.id,
      // TODO: handle atom label
      Option.empty[Long]
    )

  }

}

  