package fr.proline.core.om.storer.lcms

trait IMasterMapStorer {
  
  import fr.proline.core.om.lcms.ProcessedMap
  
  def storeMasterMap( processedMap: ProcessedMap ): Unit
  
 }