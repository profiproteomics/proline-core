package fr.proline.core.om.provider
import fr.proline.core.om.model.msi.Protein
import fr.proline.core.om.model.msi.SeqDatabase



trait IProteinProvider {
  
  def getProteins( protIds: Seq[Int] ): Array[Option[Protein]]
  
  def getProtein( protId:Int ): Option[Protein] = { getProteins( Array(0) )(0) }
 
  def getProtein( seq:String): Option[Protein]
  
  def getProtein(accession:String, seqDb: SeqDatabase): Option[Protein]
}