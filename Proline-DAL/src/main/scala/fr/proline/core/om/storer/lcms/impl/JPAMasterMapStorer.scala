package fr.proline.core.om.storer.lcms.impl

import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.om.model.lcms.ProcessedMap
import fr.proline.core.om.model.lcms.Feature
import fr.proline.core.om.storer.lcms.IMasterMapStorer

class JPAMasterMapStorer( lcmsDbCtx: DatabaseConnectionContext ) extends IMasterMapStorer {
  
  def storeMasterMap( masterMap: ProcessedMap ): Unit = {
    throw new Exception("not yet implemented")
    
    if( ! masterMap.isProcessed ) throw new Exception( "can't store a run map" )
    
    ()
  
  }
  
}