package fr.proline.core.om.storer.lcms.impl

import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.om.model.lcms.RawMap
import fr.proline.core.om.model.lcms.ILcMsMap
import fr.proline.core.om.storer.lcms.IRawMapStorer

class JPARawMapStorer( lcmsDbCtx: DatabaseConnectionContext ) extends IRawMapStorer {
  
  def storeRawMap( rawMap: RawMap, storePeaks: Boolean = false ): Unit = {
    throw new Exception("not yet implemented")
    
    ()
  
  }
  
  def insertMap( lcmsMap: ILcMsMap, modificationTimestamp: java.util.Date ): Int = {
    throw new Exception("not yet implemented")
    
    0
  
  }
  
}