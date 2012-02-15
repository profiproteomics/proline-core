package fr.proline.core.om.storer.lcms

trait IProcessedMapStorer {
  
  import fr.proline.core.om.lcms.ProcessedMap
  import fr.proline.core.om.lcms.Feature
  
  def storeProcessedMap( processedMap: ProcessedMap, storeClusters: Boolean = false ): Unit
  def storeFeatureClusters( features: Seq[Feature] ): Unit
  
 }