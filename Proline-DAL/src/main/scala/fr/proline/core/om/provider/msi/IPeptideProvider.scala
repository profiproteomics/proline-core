package fr.proline.core.om.provider.msi
import fr.proline.core.om.model.msi.LocatedPtm
import fr.proline.core.om.model.msi.Peptide


trait IPeptideProvider {
  
  def getPeptides( peptideIds: Seq[Int] ): Array[Option[Peptide]]
  
  def getPeptide( peptideId:Int ): Option[Peptide] = { getPeptides( Array(peptideId) )(0) }
 
  def getPeptide(peptideSeq:String, pepPtms:Array[LocatedPtm]) : Option[Peptide] 
}