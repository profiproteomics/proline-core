package fr.proline.core.om.storer.lcms.impl

import fr.proline.core.dal.SQLQueryHelper
import fr.proline.core.om.storer.lcms.IMasterMapStorer

class GenericMasterMapStorer( lcmsDb: SQLQueryHelper ) extends IMasterMapStorer {
  
  import fr.proline.core.om.model.lcms.ProcessedMap
  import fr.proline.core.om.model.lcms.Feature
  
  def storeMasterMap( masterMap: ProcessedMap ): Unit = {
    throw new Exception("not yet implemented")
    
    if( ! masterMap.isProcessed ) throw new Exception( "can't store a run map" )
    
    ()
  
  }
  
}