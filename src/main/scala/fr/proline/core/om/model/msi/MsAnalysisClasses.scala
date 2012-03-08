package fr.proline.core.om.model.msi
  
import fr.proline.core.utils.misc.InMemoryIdGen

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
  
