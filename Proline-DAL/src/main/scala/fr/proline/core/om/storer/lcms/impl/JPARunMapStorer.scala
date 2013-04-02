package fr.proline.core.om.storer.lcms.impl

import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.om.model.lcms.RunMap
import fr.proline.core.om.model.lcms.ILcMsMap
import fr.proline.core.om.storer.lcms.IRunMapStorer

class JPARunMapStorer( lcmsDbCtx: DatabaseConnectionContext ) extends IRunMapStorer {
  
  def storeRunMap( runMap: RunMap, storePeaks: Boolean = false ): Unit = {
    throw new Exception("not yet implemented")
    
    ()
  
  }
  
  def insertMap( lcmsMap: ILcMsMap, modificationTimestamp: java.util.Date ): Int = {
    throw new Exception("not yet implemented")
    
    0
  
  }
  
}