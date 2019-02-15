package fr.proline.core.algo.msq.profilizer.filtering

import com.typesafe.scalalogging.LazyLogging
import fr.profi.util.lang.EnhancedEnum
import fr.proline.core.algo.msq.config.profilizer.ModifiedPeptideFilteringMethod
import fr.proline.core.om.model.msi.Peptide
import fr.proline.core.om.model.msq.MasterQuantPeptide

import scala.collection.mutable.ArrayBuffer

object ModifiedPeptideDiscardingReason extends EnhancedEnum{
  val MODIFIED_PEPTIDE = Value("Modified peptide")
  val MODIFIED_PEPTIDE_COUNTERPART = Value("Modified peptide counterpart")
}

object ModifiedPeptideFilter {
  import fr.proline.core.algo.msq.config.profilizer.ModifiedPeptideFilteringMethod._

  def discardPeptides( masterQuantPeptides: Seq[MasterQuantPeptide], methodName: String, ptmsToDiscard: Array[Long] ): Unit = {
    val filteringMethod = ModifiedPeptideFilteringMethod.withName(methodName)

    filteringMethod match {
      case DISCARD_ALL_FORMS =>  ModifiedAndCounterpartFormsFilterer(ptmsToDiscard).discardPeptides(masterQuantPeptides)
      case DISCARD_MODIFIED_FORMS => ModifiedFormFilterer(ptmsToDiscard).discardPeptides(masterQuantPeptides)
      // case KEEP_MOST_ABUNDANT_FORM => LowestAbundantOxidizedFormFilterer.discardPeptides(masterQuantPeptides)
    }
  }
}

trait IModifiedPeptideFilter extends IMasterQuantPeptideFiltering with LazyLogging{
  val ptmsToConsider: Array[Long]

  def foreachModifiedPeptides(masterQuantPeptides: Seq[MasterQuantPeptide])(callback: (MasterQuantPeptide,Peptide) => Unit ): Unit = {
    this.foreachIdentifiedAndSelectedPeptide(masterQuantPeptides) { (mqPep, pepInst) =>
      val peptide = pepInst.peptide
      var containsPtmToDiscard = false
      peptide.ptms.foreach(locPtm => {
        if(ptmsToConsider.contains(locPtm.definition.id))
          containsPtmToDiscard=true
      })
      if(containsPtmToDiscard)
        callback(mqPep, peptide)
    }
  }
}

case class ModifiedAndCounterpartFormsFilterer(ptmsToConsider: Array[Long]) extends IModifiedPeptideFilter {


  override def discardPeptides(masterQuantPeptides: Seq[MasterQuantPeptide]): Unit = {
    val detectedModifiedSeqs = new ArrayBuffer[String]()

    this.foreachModifiedPeptides(masterQuantPeptides) { (mqPep,peptide) =>
      // Append detected modified sequences in the buffer
      detectedModifiedSeqs += peptide.sequence

      // Discard the detected oxidized peptide
      this.discardPeptide(mqPep, ModifiedPeptideDiscardingReason.MODIFIED_PEPTIDE)
    }
    // Convert the detectedOxSeqs buffer into a Set
    val detectedModifiedSeqsSet = detectedModifiedSeqs.toSet

    // Filter kept master quant peptides again to remove the counterpart of the oxidized ones
    this.discardPeptideSequences(
      masterQuantPeptides,
      detectedModifiedSeqsSet,
      ModifiedPeptideDiscardingReason.MODIFIED_PEPTIDE_COUNTERPART
    )
  }


}

case class ModifiedFormFilterer(ptmsToConsider: Array[Long])  extends IModifiedPeptideFilter {
  override def discardPeptides(masterQuantPeptides: Seq[MasterQuantPeptide]): Unit = {
    this.foreachModifiedPeptides(masterQuantPeptides) { (mqPep,peptide) =>
      // Discard the detected oxidized peptide
      this.discardPeptide(mqPep, ModifiedPeptideDiscardingReason.MODIFIED_PEPTIDE)
    }
  }
}