package fr.proline.core.om.provider.msq

import fr.proline.core.om.model.msq.MasterQuantPeptideIon

trait IMasterQuantPeptideIonProvider {
  
  def getMasterQuantPeptideIons( mqPepIonIds: Seq[Long] ): Array[MasterQuantPeptideIon]
  
  def getQuantResultSummariesMQPeptideIons( quantRsmIds: Seq[Long] ): Array[MasterQuantPeptideIon]
  
  def getMasterQuantPeptideIon( mqPepIonId: Long ): Option[MasterQuantPeptideIon] = {
    getMasterQuantPeptideIons( Array(mqPepIonId) ).headOption
  }
  
  def getQuantResultSummaryMQPeptideIons( quantRsmId: Long ): Array[MasterQuantPeptideIon] = {
    getQuantResultSummariesMQPeptideIons( Array(quantRsmId) )
  }
}