package fr.proline.core.om.provider.msq

import fr.proline.core.om.model.msq.MasterQuantPeptideIon

trait IMasterQuantPeptideIonProvider {
  
  def getMasterQuantPeptideIonsAsOptions( mqPepIonIds: Seq[Int] ): Array[Option[MasterQuantPeptideIon]]
  
  def getMasterQuantPeptideIons( mqPepIonIds: Seq[Int] ): Array[MasterQuantPeptideIon]
  
  def getQuantResultSummariesMQPeptideIons( quantRsmIds: Seq[Int] ): Array[MasterQuantPeptideIon]
  
  
  def getMasterQuantPeptideIon( mqPepIonId: Int ): Option[MasterQuantPeptideIon] = {
    getMasterQuantPeptideIonsAsOptions( Array(mqPepIonId) )(0)
  }
  
  def getQuantResultSummaryMQPeptideIons( quantRsmId: Int ): Array[MasterQuantPeptideIon] = {
    getQuantResultSummariesMQPeptideIons( Array(quantRsmId) )
  }
}