package fr.proline.core.om.provider.lcms

import fr.proline.core.om.model.lcms.ProcessedMap

trait IProcessedMapProvider extends ILcMsMapProvider {
  
  def getProcessedMaps( processedMapIds: Seq[Long], loadPeakels: Boolean = false ): Array[ProcessedMap]

}