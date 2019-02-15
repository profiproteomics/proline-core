package fr.proline.core.algo.msq.profilizer.filtering

import fr.proline.core.om.model.msq.MasterQuantPeptide
import fr.proline.core.om.model.msi.PeptideInstance

trait IMasterQuantPeptideFiltering {
  
  def discardPeptides( masterQuantPeptides: Seq[MasterQuantPeptide] ): Unit
  
  def discardPeptide( masterQuantPeptide: MasterQuantPeptide, reason: String ): Unit = {
    if( masterQuantPeptide.selectionLevel == 2 ) {
      masterQuantPeptide.selectionLevel = 1
      masterQuantPeptide.properties.get.setDiscardingReason(Some(reason))
    }
  }
  
  def foreachIdentifiedAndSelectedPeptide( masterQuantPeptides: Seq[MasterQuantPeptide])(callback: (MasterQuantPeptide,PeptideInstance) => Unit ): Unit = {
    for(
      mqPep <- masterQuantPeptides;
      if mqPep.selectionLevel >= 2;
      pepInst <- mqPep.peptideInstance
    ) {
      callback(mqPep, pepInst)
    }
  }
  
  protected def discardPeptideSequences(
    masterQuantPeptides: Seq[MasterQuantPeptide],
    discardedPepSeqSet: Set[String],
    reason: String
  ) {
    
    this.foreachIdentifiedAndSelectedPeptide(masterQuantPeptides) { (mqPep,pepInst) =>
      if( discardedPepSeqSet.contains(pepInst.peptide.sequence) ) {
        this.discardPeptide(mqPep, reason)
      }
    }
    
  }
  
}