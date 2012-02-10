package fr.proline.core.om.provider

import fr.proline.core.om.msi.PeptideClasses.Peptide
import fr.proline.core.om.msi.PtmClasses.LocatedPtm

trait IPeptideProvider {
  
  def getPeptides( peptideIds: Seq[Int] ): Array[Option[Peptide]]
  
  def getPeptide( peptideId:Int ): Option[Peptide] = { getPeptides( Array(0) )(0) }
 
  def getPeptide(peptideSeq:String, pepPtms:Array[LocatedPtm]) : Option[Peptide] 
}