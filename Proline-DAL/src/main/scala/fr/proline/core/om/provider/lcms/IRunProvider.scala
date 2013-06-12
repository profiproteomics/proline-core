package fr.proline.core.om.provider.lcms

import fr.proline.core.om.model.lcms.LcMsRun

trait IRunProvider {
  
  def getRuns( runIds: Seq[Long] ): Array[LcMsRun]
  
  def getRun( runId: Long ): LcMsRun = this.getRuns( Seq(runId) )(0)

}