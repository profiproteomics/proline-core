package fr.proline.core.om.provider.lcms

import fr.proline.core.om.model.lcms.LcMsScanSequence

trait IScanSequenceProvider {
  
  def getScanSequences( runIds: Seq[Int] ): Array[LcMsScanSequence]
  
  def getScanSequence( runId: Int ): LcMsScanSequence = this.getScanSequences( Seq(runId) )(0)

}