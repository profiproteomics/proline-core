package fr.proline.core.om.provider.lcms

import fr.proline.core.om.model.lcms.LcMsRun

trait IRunProvider {
  
  def getRuns( runIds: Seq[Long], loadScanSequence: Boolean ): Array[LcMsRun]
  
  def getRun( runId: Long, loadScanSequence: Boolean ): LcMsRun = this.getRuns( Seq(runId), loadScanSequence )(0)

}