package fr.proline.core.om.storer.lcms.impl

import fr.proline.core.LcmsDb
import fr.proline.core.om.storer.lcms.IRunMapStorer

class GenericRunMapStorer( lcmsDb: LcmsDb ) extends IRunMapStorer {
  
  import fr.proline.core.om.lcms.RunMap
  import fr.proline.core.om.lcms.LcmsMap
  
  def storeRunMap( runMap: RunMap, storePeaks: Boolean = false ): Unit = {
    throw new Exception("not yet implemented")
    
    ()
  
  }
  
  def insertMap( lcmsMap: LcmsMap, modificationTimestamp: java.util.Date ): Int = {
    throw new Exception("not yet implemented")
    
    0
  
  }
  
}