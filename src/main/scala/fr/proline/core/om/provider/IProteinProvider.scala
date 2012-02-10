package fr.proline.core.om.provider

import fr.proline.core.om.msi.ProteinClasses.Protein

trait IProteinProvider {
  
  def getProteins( protIds: Seq[Int] ): Array[Option[Protein]]
  
  def getProtein( protId:Int ): Option[Protein] = { getProteins( Array(0) )(0) }
 
}