package fr.proline.core.om.storer.ps

import com.weiglewilczek.slf4s.Logging

import net.noerd.prequel.ReusableStatement
import net.noerd.prequel.SQLFormatterImplicits._

import fr.proline.core.dal.SQLFormatterImplicits._
import fr.proline.core.dal.PsDb
import fr.proline.core.dal.{PsDbPeptideTable,PsDbPeptidePtmTable}
import fr.proline.core.om.model.msi.{Peptide,LocatedPtm}
import fr.proline.core.utils.sql._

/** A factory object for implementations of the IRsStorer trait */
object PeptideStorer {
  def apply( psDb: PsDb ) = new PeptideStorer( psDb )
}

class PeptideStorer( psDb: PsDb ) extends Logging {
  
  def storePeptides( peptides: Seq[Peptide] ): Map[String,Int] = {
    
    logger.info( "storing peptides in PsDb..." )
    
    val psDbTx = this.psDb.getOrCreateTransaction()
    
    val peptideColsList = PsDbPeptideTable.getColumnsAsStrList().filter { _ != "id" }
    val peptideInsertQuery = PsDbPeptideTable.makeInsertQuery( peptideColsList )
    
    psDbTx.executeBatch( peptideInsertQuery ) { stmt => 
      peptides.foreach { this._insertPeptide( stmt, _ ) }
    }
    
    val peptidePtmColsList = PsDbPeptidePtmTable.getColumnsAsStrList().filter { _ != "id" }
    val peptidePtmInsertQuery = PsDbPeptidePtmTable.makeInsertQuery( peptidePtmColsList )
    
    psDbTx.executeBatch( peptidePtmInsertQuery ) { stmt => 
      for( peptide <- peptides ) {
        if( peptide.ptms != null ) {
          peptide.ptms.foreach { this._insertPeptidePtm( stmt, _, peptide.id ) }
        }
      }
    }
    
    null
  }
  
  private def _insertPeptide( stmt: ReusableStatement, peptide: Peptide ): Unit = {
    
    stmt.executeWith(
          peptide.sequence,
          Option(peptide.ptmString),
          peptide.calculatedMass,
          Option(null),
          Option(null)
         )
        
    peptide.id = this.psDb.extractGeneratedInt( stmt.wrapped )
    
  }
  
  private def _insertPeptidePtm( stmt: ReusableStatement, locatedPtm: LocatedPtm, peptideId: Int ): Unit = {
                                        
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

  