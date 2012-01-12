package fr.proline.core.om.provider

import fr.proline.core.om.msi.PeptideClasses.Peptide

trait PeptideProvider {
  
  def getPeptides( peptideIds: Seq[Int] ): Array[Peptide]
  
  def getPeptide( peptideId:Int ): Peptide = { getPeptides( Array(0) )(0) }
 
}