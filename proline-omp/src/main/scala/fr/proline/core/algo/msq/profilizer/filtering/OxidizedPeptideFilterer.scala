package fr.proline.core.algo.msq.profilizer.filtering

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import com.typesafe.scalalogging.StrictLogging
import fr.profi.util.lang.EnhancedEnum
import fr.proline.core.algo.msq.config.profilizer.OxidizedPeptideFilteringMethod
import fr.proline.core.om.model.msq.MasterQuantPeptide
import fr.proline.core.om.model.msi.Peptide
//
//object OxidizedPeptideFilteringMethod extends EnhancedEnum {
//  val DISCARD_ALL_FORMS = Value
//  val DISCARD_OXIDIZED_FORMS = Value
//  val KEEP_MOST_ABUNDANT_FORM = Value
//}
//
object OxidizedPeptideDiscardingReason extends EnhancedEnum {
  val OXIDIZED_PEPTIDE = Value("Oxidized peptide")
  val OXIDIZED_PEPTIDE_COUNTERPART = Value("Oxidized peptide counterpart")
  val LESS_ABUNDANT_THAN_OTHER_OXIDIZED_FORM = Value("Peptide less abundant than an other oxidized/unmodified form")
}

@deprecated
object OxidizedPeptideFilterer {
  
  import fr.proline.core.algo.msq.config.profilizer.OxidizedPeptideFilteringMethod._
  
  def discardPeptides( masterQuantPeptides: Seq[MasterQuantPeptide], methodName: String ): Unit = {
    val filteringMethod = OxidizedPeptideFilteringMethod.withName(methodName)
    
    filteringMethod match {
      case DISCARD_ALL_FORMS => OxidizedAndCounterpartFormsFilterer.discardPeptides(masterQuantPeptides)
      case DISCARD_OXIDIZED_FORMS => OxidizedFormFilterer.discardPeptides(masterQuantPeptides)
      case KEEP_MOST_ABUNDANT_FORM => LowestAbundantOxidizedFormFilterer.discardPeptides(masterQuantPeptides)
    }
  }
  
}

trait IOxidizedPeptideFilter extends IMasterQuantPeptideFiltering with StrictLogging {
  
  val pattern = """.*\[O\]""".r.pattern
  
  def foreachOxidizedPeptide( masterQuantPeptides: Seq[MasterQuantPeptide])(callback: (MasterQuantPeptide,Peptide) => Unit ): Unit = {
    
    this.foreachIdentifiedAndSelectedPeptide(masterQuantPeptides) { (mqPep,pepInst) =>
      
      val peptide = pepInst.peptide
      val ptmString = peptide.ptmString
      
      // Check if the ptmString contains an oxidation
      // TODO: check if this technique is error prone
      if( pattern.matcher(ptmString).matches ) {
        callback(mqPep,peptide)
      }
    }
  }
  
}

object OxidizedAndCounterpartFormsFilterer extends IOxidizedPeptideFilter {
  
  def discardPeptides( masterQuantPeptides: Seq[MasterQuantPeptide] ): Unit = {
    
    val detectedOxSeqs = new ArrayBuffer[String]()
    
    this.foreachOxidizedPeptide(masterQuantPeptides) { (mqPep,peptide) =>
      // Append detected oxidized sequences in the buffer
      detectedOxSeqs += peptide.sequence
      
      // Discard the detected oxidized peptide
      this.discardPeptide(mqPep, OxidizedPeptideDiscardingReason.OXIDIZED_PEPTIDE)
    }
    
    // Convert the detectedOxSeqs buffer into a Set
    val detectedOxSeqSet = detectedOxSeqs.toSet

    // Filter kept master quant peptides again to remove the counterpart of the oxidized ones
    this.discardPeptideSequences(
      masterQuantPeptides,
      detectedOxSeqSet,
      OxidizedPeptideDiscardingReason.OXIDIZED_PEPTIDE_COUNTERPART
    )
  }
  
}


object OxidizedFormFilterer extends IOxidizedPeptideFilter {
  
  def discardPeptides( masterQuantPeptides: Seq[MasterQuantPeptide] ): Unit = {
    this.foreachOxidizedPeptide(masterQuantPeptides) { (mqPep,peptide) =>
      // Discard the detected oxidized peptide
      this.discardPeptide(mqPep, OxidizedPeptideDiscardingReason.OXIDIZED_PEPTIDE)
    }
  }
  
}

object LowestAbundantOxidizedFormFilterer extends IOxidizedPeptideFilter {
  
  def discardPeptides( masterQuantPeptides: Seq[MasterQuantPeptide] ): Unit = {
    
    val oxMqPepsBySeq = new HashMap[String,ArrayBuffer[MasterQuantPeptide]]()
    
    this.foreachOxidizedPeptide(masterQuantPeptides) { (mqPep,peptide) =>
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
      val mostAbundantMqPepId = allDetectedForms.maxBy(_.getBestQuantPeptide.rawAbundance).id
      
      // Discard low abundance forms
      allDetectedForms.withFilter(_.id != mostAbundantMqPepId).foreach { mqPep =>
        this.discardPeptide(mqPep, OxidizedPeptideDiscardingReason.LESS_ABUNDANT_THAN_OTHER_OXIDIZED_FORM)
      }
    }
    
  }
  
}

