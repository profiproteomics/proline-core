package fr.proline.core.om.provider.lcms

import fr.proline.core.om.model.lcms.LcMsScanSequence

trait IScanSequenceProvider {
  
  def getScanSequences( runIds: Seq[Long] ): Array[LcMsScanSequence]
  
  def getScanSequence( runId: Long ): LcMsScanSequence = this.getScanSequences( Seq(runId) )(0)

}