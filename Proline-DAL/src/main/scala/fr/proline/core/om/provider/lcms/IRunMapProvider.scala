package fr.proline.core.om.provider.lcms

import fr.proline.core.om.model.lcms.RunMap

trait IRunMapProvider extends ILcMsMapProvider {
  
  def getRunMaps(runMapIds: Seq[Int]): Array[RunMap]

}