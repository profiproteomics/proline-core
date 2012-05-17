package fr.proline.core.om.provider.ps

import fr.proline.core.om.model.msi.LocatedPtm
import fr.proline.core.om.model.msi.Peptide

trait IPeptideProvider {
  
  def getPeptides( peptideIds: Seq[Int] ): Array[Peptide]
  
  def getPeptidesForSequences( peptideSeqs: Seq[String] ): Array[Peptide]
  
  def getPeptide( peptideId:Int ): Option[Peptide] = {
    val peptides = getPeptides( Array(peptideId) )
    if( peptides.length == 1 ) Some(peptides(0)) else None
  }
 
  def getPeptide(peptideSeq:String, pepPtms:Array[LocatedPtm]) : Option[Peptide]
  
}