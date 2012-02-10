package fr.proline.core.om.storer.lcms

trait IProcessedMapStorer {
  
  import fr.proline.core.om.lcms.MapClasses.ProcessedMap
  import fr.proline.core.om.lcms.FeatureClasses.Feature
  
  def storeProcessedMap( processedMap: ProcessedMap ): Unit
  def storeFeatureClusters( features: Seq[Feature] ): Unit
  
 }