package fr.proline.core.om.provider.lcms

import fr.proline.core.om.model.lcms.RawMap

trait IRawMapProvider extends ILcMsMapProvider {
  
  def getRawMaps(rawMapIds: Seq[Long], loadPeakels: Boolean = false): Array[RawMap]

}