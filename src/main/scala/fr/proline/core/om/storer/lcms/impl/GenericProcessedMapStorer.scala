package fr.proline.core.om.storer.lcms.impl

import fr.proline.core.LcmsDb
import fr.proline.core.om.storer.lcms.IProcessedMapStorer

class GenericProcessedMapStorer( lcmsDb: LcmsDb ) extends IProcessedMapStorer {
  
  import fr.proline.core.om.model.lcms.ProcessedMap
  import fr.proline.core.om.model.lcms.Feature
  
  def storeProcessedMap( processedMap: ProcessedMap, storeClusters: Boolean ): Unit = {
    throw new Exception("not yet implemented")
    
    if( ! processedMap.isProcessed ) throw new Exception( "can't store a run map" )
    
    ()
  
  }
  
  def storeFeatureClusters( features: Seq[Feature] ): Unit = {
    throw new Exception("not yet implemented")
    ()
  }
  
}