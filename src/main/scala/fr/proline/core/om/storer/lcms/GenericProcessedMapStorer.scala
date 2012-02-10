package fr.proline.core.om.storer.lcms

import fr.proline.core.LcmsDb

class GenericProcessedMapStorer( lcmsDb: LcmsDb ) extends IProcessedMapStorer {
  
  import fr.proline.core.om.lcms.MapClasses.ProcessedMap
  import fr.proline.core.om.lcms.FeatureClasses.Feature
  
  def storeProcessedMap( processedMap: ProcessedMap ): Unit = {
    throw new Exception("not yet implemented")
    
    if( ! processedMap.isProcessed ) throw new Exception( "can't store a run map" )
    
    ()
  
  }
  
  def storeFeatureClusters( features: Seq[Feature] ): Unit = {
    throw new Exception("not yet implemented")
    ()
  }
  
}