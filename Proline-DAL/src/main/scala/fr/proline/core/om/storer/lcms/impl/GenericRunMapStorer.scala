package fr.proline.core.om.storer.lcms.impl

import fr.proline.core.dal.SQLQueryHelper
import fr.proline.core.om.storer.lcms.IRunMapStorer

class GenericRunMapStorer( lcmsDb: SQLQueryHelper ) extends IRunMapStorer {
  
  import fr.proline.core.om.model.lcms.RunMap
  import fr.proline.core.om.model.lcms.LcmsMap
  
  def storeRunMap( runMap: RunMap, storePeaks: Boolean = false ): Unit = {
    throw new Exception("not yet implemented")
    
    ()
  
  }
  
  def insertMap( lcmsMap: LcmsMap, modificationTimestamp: java.util.Date ): Int = {
    throw new Exception("not yet implemented")
    
    0
  
  }
  
}