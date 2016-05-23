package fr.proline.core.om.storer.lcms.impl

import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.om.model.lcms.RawMap
import fr.proline.core.om.model.lcms.ILcMsMap
import fr.proline.core.om.storer.lcms._

class JPARawMapStorer( val lcmsDbCtx: DatabaseConnectionContext, val featureWriter: IFeatureWriter ) extends IRawMapStorer {
  
  def storeRawMap( rawMap: RawMap, storePeaks: Boolean = false ): Unit = {
    throw new Exception("not yet implemented")
    
    ()
  
  }
  
  def insertMap( lcmsMap: ILcMsMap, modificationTimestamp: java.util.Date ): Int = {
    throw new Exception("not yet implemented")
    
    0
  
  }
  
}