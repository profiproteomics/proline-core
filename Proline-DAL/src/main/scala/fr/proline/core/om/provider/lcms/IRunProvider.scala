package fr.proline.core.om.provider.lcms

import fr.proline.core.om.model.lcms.LcMsRun

trait IRunProvider {
  
  def getRuns( runIds: Seq[Int] ): Array[LcMsRun]

}