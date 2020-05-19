package fr.proline.core.algo.msq.profilizer.filtering

import fr.proline.core.om.model.msq.MasterQuantPeptide

object UnspecificPeptideFilterer extends IMasterQuantPeptideFiltering {
  
  def discardPeptides( masterQuantPeptides: Seq[MasterQuantPeptide] ): Unit = {
    this.foreachIdentifiedAndSelectedPeptide(masterQuantPeptides) { (mqPep,pepInst) =>
      if( !pepInst.isValidProteinSetSpecific )
        this.discardPeptide(mqPep, "Peptide sequence is not specific to this protein set")
    }
  }
  
}