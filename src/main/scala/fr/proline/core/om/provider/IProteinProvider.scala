package fr.proline.core.om.provider

import fr.proline.core.om.msi.ProteinClasses.Protein
import fr.proline.core.om.msi.MsiSearchClasses.SeqDatabase

trait IProteinProvider {
  
  def getProteins( protIds: Seq[Int] ): Array[Option[Protein]]
  
  def getProtein( protId:Int ): Option[Protein] = { getProteins( Array(0) )(0) }
 
  def getProtein( seq:String): Option[Protein]
  
  def getProtein(accession:String, seqDb: SeqDatabase): Option[Protein]
}