package fr.proline.core.om.storer.lcms

import fr.proline.core.LcmsDb

class GenericMasterMapStorer( lcmsDb: LcmsDb ) extends IMasterMapStorer {
  
  import fr.proline.core.om.lcms.ProcessedMap
  import fr.proline.core.om.lcms.Feature
  
  def storeMasterMap( masterMap: ProcessedMap ): Unit = {
    throw new Exception("not yet implemented")
    
    if( ! masterMap.isProcessed ) throw new Exception( "can't store a run map" )
    
    ()
  
  }
  
}