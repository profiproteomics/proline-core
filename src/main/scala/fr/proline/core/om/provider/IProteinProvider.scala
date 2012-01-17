package fr.proline.core.om.provider

import fr.proline.core.om.msi.ProteinClasses.Protein

trait IProteinProvider {
  
  def getProteins( protIds: Seq[Int] ): Array[Protein]
  
  def getProtein( protId:Int ): Protein = { getProteins( Array(0) )(0) }
 
}