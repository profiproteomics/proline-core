package fr.proline.core.algo.msq.profilizer.filtering

import java.util.regex.Pattern

import com.typesafe.scalalogging.LazyLogging
import fr.profi.util.lang.EnhancedEnum
import fr.proline.core.algo.msq.config.profilizer.{ModifiedPeptideFilterConfig, ModifiedPeptideFilteringMethod}
import fr.proline.core.om.model.msi.Peptide
import fr.proline.core.om.model.msq.MasterQuantPeptide

import scala.collection.mutable
import scala.collection.mutable.{ArrayBuffer}

object ModifiedPeptideDiscardingReason extends EnhancedEnum{
  val MODIFIED_PEPTIDE = Value("Modified peptide")
  val MODIFIED_PEPTIDE_COUNTERPART = Value("Modified peptide counterpart")
  val LESS_ABUNDANT_THAN_OTHER_MODIFIED_FORM = Value("Peptide less abundant than an other modified/unmodified form")
}

object ModifiedPeptideFilter {
  import fr.proline.core.algo.msq.config.profilizer.ModifiedPeptideFilteringMethod._

  def discardPeptides( masterQuantPeptides: Seq[MasterQuantPeptide], filteringMethod: ModifiedPeptideFilteringMethod.Value, filteringConfig: ModifiedPeptideFilterConfig): Unit = {

    val ptmPattern = { if (filteringConfig.ptmPattern.isDefined) {
      Some(filteringConfig.ptmPattern.get.r.pattern)
    } else {
      None
    }

    }
    filteringMethod match {
      case DISCARD_ALL_FORMS =>  ModifiedAndCounterpartFormsFilterer(filteringConfig.ptmDefinitionIdsToDiscard, ptmPattern).discardPeptides(masterQuantPeptides)
      case DISCARD_MODIFIED_FORMS => ModifiedFormFilterer(filteringConfig.ptmDefinitionIdsToDiscard, ptmPattern).discardPeptides(masterQuantPeptides)
      case KEEP_MOST_ABUNDANT_FORM => LowestAbundantModifiedFormFilterer(filteringConfig.ptmDefinitionIdsToDiscard, ptmPattern).discardPeptides(masterQuantPeptides)
    }
  }
}

trait IModifiedPeptideFilter extends IMasterQuantPeptideFiltering with LazyLogging{
  val ptmsToConsider: Array[Long]
  val ptmPattern: Option[Pattern]

  def foreachModifiedPeptides(masterQuantPeptides: Seq[MasterQuantPeptide])(callback: (MasterQuantPeptide,Peptide) => Unit): Unit = {
    this.foreachIdentifiedAndSelectedPeptide(masterQuantPeptides) { (mqPep, pepInst) =>
      val peptide = pepInst.peptide
      var containsPtmToDiscard = false

      if (!ptmsToConsider.isEmpty) {
        peptide.ptms.foreach(locPtm => {
          if (ptmsToConsider.contains(locPtm.definition.id))
            containsPtmToDiscard = true
        })
      } else {
        // TODO: check if this technique is error prone
        val ptmString = peptide.ptmString
        if( ptmPattern.get.matcher(ptmString).matches ) {
          containsPtmToDiscard = true
        }
      }

      if(containsPtmToDiscard)
        callback(mqPep, peptide)

    }
  }
}

case class ModifiedAndCounterpartFormsFilterer(ptmsToConsider: Array[Long], ptmPattern: Option[Pattern]) extends IModifiedPeptideFilter {

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

case class ModifiedFormFilterer(ptmsToConsider: Array[Long], ptmPattern: Option[Pattern])  extends IModifiedPeptideFilter {
  override def discardPeptides(masterQuantPeptides: Seq[MasterQuantPeptide]): Unit = {
    this.foreachModifiedPeptides(masterQuantPeptides) { (mqPep,peptide) =>
      // Discard the detected oxidized peptide
      this.discardPeptide(mqPep, ModifiedPeptideDiscardingReason.MODIFIED_PEPTIDE)
    }
  }
}

case class LowestAbundantModifiedFormFilterer(ptmsToConsider: Array[Long], ptmPattern: Option[Pattern]) extends IModifiedPeptideFilter {

  def discardPeptides( masterQuantPeptides: Seq[MasterQuantPeptide] ): Unit = {

    val oxMqPepsBySeq = new mutable.HashMap[String,ArrayBuffer[MasterQuantPeptide]]()

    this.foreachModifiedPeptides(masterQuantPeptides) { (mqPep,peptide) =>
      oxMqPepsBySeq.getOrElseUpdate(peptide.sequence, new ArrayBuffer[MasterQuantPeptide]) += mqPep
    }

    val mqPepBySeq = masterQuantPeptides
      .withFilter(_.peptideInstance.isDefined)
      .map( mqPep => mqPep.peptideInstance.get.peptide.sequence -> mqPep)
      .toMap

    for( (pepSeq,oxMqPeps) <- oxMqPepsBySeq ) {
      val mqPepOpt = mqPepBySeq.get(pepSeq)
      val allDetectedForms = if( mqPepOpt.isEmpty ) oxMqPeps else oxMqPeps ++ List(mqPepOpt.get)

      // We compare the abundances of the different forms
      //VDS Warning maxBy may return wrong value if NaN
      val mostAbundantMqPepId = allDetectedForms.maxBy(_.getBestQuantPeptide.rawAbundance).id

      // Discard low abundance forms
      allDetectedForms.withFilter(_.id != mostAbundantMqPepId).foreach { mqPep =>
        this.discardPeptide(mqPep, ModifiedPeptideDiscardingReason.LESS_ABUNDANT_THAN_OTHER_MODIFIED_FORM)
      }
    }

  }

}

