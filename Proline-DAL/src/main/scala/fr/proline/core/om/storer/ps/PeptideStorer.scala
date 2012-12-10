package fr.proline.core.om.storer.ps

import com.weiglewilczek.slf4s.Logging
import fr.profi.jdbc.easy._
import fr.profi.jdbc.PreparedStatementWrapper
import fr.proline.core.dal.SQLQueryHelper
import fr.proline.core.dal.{PsDbPeptideTable,PsDbPeptidePtmTable}
import fr.proline.core.om.model.msi.{Peptide,LocatedPtm}
import fr.proline.util.sql._

/** A factory object for implementations of the IRsStorer trait */
object PeptideStorer {
  def apply( psDb: SQLQueryHelper ) = new PeptideStorer( psDb )
}

class PeptideStorer( psDb: SQLQueryHelper ) extends Logging {
  
  def storePeptides( peptides: Seq[Peptide] ): Map[String,Int] = {
    
    logger.info( "storing peptides in PsDb..." )
    
    val peptideColsList = PsDbPeptideTable.getColumnsAsStrList().filter { _ != "id" }
    val peptideInsertQuery = PsDbPeptideTable.makeInsertQuery( peptideColsList )
    
    psDb.ezDBC.executePrepared( peptideInsertQuery, true ) { stmt => 
      peptides.foreach { this._insertPeptide( stmt, _ ) }
    }
    
    val peptidePtmColsList = PsDbPeptidePtmTable.getColumnsAsStrList().filter { _ != "id" }
    val peptidePtmInsertQuery = PsDbPeptidePtmTable.makeInsertQuery( peptidePtmColsList )
    
    psDb.ezDBC.executePrepared( peptidePtmInsertQuery, false ) { stmt => 
      for( peptide <- peptides ) {
        if( peptide.ptms != null ) {
          peptide.ptms.foreach { this._insertPeptidePtm( stmt, _, peptide.id ) }
        }
      }
    }
    
    null
  }
  
  private def _insertPeptide( stmt: PreparedStatementWrapper, peptide: Peptide ): Unit = {
    
    stmt.executeWith(
          peptide.sequence,
          Option(peptide.ptmString),
          peptide.calculatedMass,
          Option(null),
          Option(null)
         )
        
    peptide.id = stmt.generatedInt
    
  }
  
  private def _insertPeptidePtm( stmt: PreparedStatementWrapper, locatedPtm: LocatedPtm, peptideId: Int ): Unit = {
                                        
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

  