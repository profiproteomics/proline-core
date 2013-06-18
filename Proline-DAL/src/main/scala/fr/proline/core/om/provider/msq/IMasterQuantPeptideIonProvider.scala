package fr.proline.core.om.provider.msq

import fr.proline.core.om.model.msq.MasterQuantPeptideIon

trait IMasterQuantPeptideIonProvider {
  
  def getMasterQuantPeptideIonsAsOptions( mqPepIonIds: Seq[Long] ): Array[Option[MasterQuantPeptideIon]]
  
  def getMasterQuantPeptideIons( mqPepIonIds: Seq[Long] ): Array[MasterQuantPeptideIon]
  
  def getQuantResultSummariesMQPeptideIons( quantRsmIds: Seq[Long] ): Array[MasterQuantPeptideIon]
  
  
  def getMasterQuantPeptideIon( mqPepIonId: Long ): Option[MasterQuantPeptideIon] = {
    getMasterQuantPeptideIonsAsOptions( Array(mqPepIonId) )(0)
  }
  
  def getQuantResultSummaryMQPeptideIons( quantRsmId: Long ): Array[MasterQuantPeptideIon] = {
    getQuantResultSummariesMQPeptideIons( Array(quantRsmId) )
  }
}