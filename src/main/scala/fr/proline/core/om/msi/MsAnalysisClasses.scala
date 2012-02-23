package fr.proline.core.om.msi

package MsAnalysisClasses {
  
import fr.proline.core.om.helper.MiscUtils.InMemoryIdGen

  object Peaklist extends InMemoryIdGen(){ 
  
 }
  class Peaklist(
                   // Required fields
                   val id: Int,
                   val fileType: String,
                   val path: String,
                   val msLevel: Int
                   ) {
      
  }
  
}
