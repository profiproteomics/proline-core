package fr.proline.core.algo.msq.profilizer.filtering

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import com.typesafe.scalalogging.StrictLogging
import fr.profi.util.lang.EnhancedEnum
import fr.proline.core.algo.msq.config.profilizer.MissCleavedPeptideFilteringMethod
import fr.proline.core.om.model.msq.MasterQuantPeptide

//object MissCleavedPeptideFilteringMethod extends EnhancedEnum {
//  val DISCARD_ALL_FORMS = Value
//  val DISCARD_MISS_CLEAVED_FORMS = Value
//  val KEEP_MOST_ABUNDANT_FORM = Value
//}

object MissCleavedPeptideDiscardingReason extends EnhancedEnum {
  val INCLUDED_IN_MC_SEQUENCE = Value("Sequence included in a miss-cleaved one")
  val MC_SEQUENCE = Value("Miss-cleaved sequence")
  val LESS_ABUNDANT_THAN_OTHER_MC_FORM = Value("Peptide less abundant than an other MC form")
}

object MissCleavedPeptideFilterer {
  
  import MissCleavedPeptideFilteringMethod._

  def discardPeptides( masterQuantPeptides: Seq[MasterQuantPeptide], methodName: String ): Unit = {
    discardPeptides(masterQuantPeptides, MissCleavedPeptideFilteringMethod.withName(methodName))
  }

  def discardPeptides( masterQuantPeptides: Seq[MasterQuantPeptide], filteringMethod: MissCleavedPeptideFilteringMethod.Value ): Unit = {
    filteringMethod match {
      case DISCARD_ALL_FORMS => AllCleavageFormsFilterer.discardPeptides(masterQuantPeptides)
      case DISCARD_MISS_CLEAVED_FORMS => MissCleavedFormFilterer.discardPeptides(masterQuantPeptides)
      case KEEP_MOST_ABUNDANT_FORM => LowestAbundantCleavageFormFilterer.discardPeptides(masterQuantPeptides)
    }
  }
  
}

trait IMissCleavedPeptideFilterer extends IMasterQuantPeptideFiltering with StrictLogging {
  
  // FIXME: we assume here that Trypsin has been used => retrieve the right enzyme to apply this filter correctly
  val regex = ".*?[R|K]".r
  
  def foreachMissCleavedPeptide( masterQuantPeptides: Seq[MasterQuantPeptide])(callback: (MasterQuantPeptide,Array[String]) => Unit ): Unit = {
    
    this.foreachIdentifiedAndSelectedPeptide(masterQuantPeptides) { (mqPep,pepInst) =>
      
      val pepSeq = pepInst.peptide.sequence
      val seqParts = regex.findAllIn(pepSeq).toArray
      
      // If we have found R|K multiple times
      if( seqParts.length > 1 ) {
        // Execute callback with mqPep and seqParts longer than 1 (to exclude K|R chars from seqParts)
        callback(mqPep, seqParts.filter(_.length > 1))
      }
    }
  }
  
}

object AllCleavageFormsFilterer extends IMissCleavedPeptideFilterer {
  
  def discardPeptides( masterQuantPeptides: Seq[MasterQuantPeptide] ): Unit = {
    
    val detectedMCSeqParts = new ArrayBuffer[String]()
    
    this.foreachMissCleavedPeptide(masterQuantPeptides) { (mqPep,seqParts) =>
      // Append detected miss-cleaved sequences in the buffer
      detectedMCSeqParts ++= seqParts
      
      // Discard detected miss-cleaved peptide
      this.discardPeptide(mqPep, MissCleavedPeptideDiscardingReason.MC_SEQUENCE)
    }
    
    // Convert the detectedSeqsWithMC buffer into a Set
    val detectedMCSeqSet = detectedMCSeqParts.toSet
    
    // Filter master quant peptides again to remove the counterpart of the MC ones
    this.discardPeptideSequences(
      masterQuantPeptides,
      detectedMCSeqSet,
      MissCleavedPeptideDiscardingReason.INCLUDED_IN_MC_SEQUENCE
    )
  }
  
}


object MissCleavedFormFilterer extends IMissCleavedPeptideFilterer {
  
  def discardPeptides( masterQuantPeptides: Seq[MasterQuantPeptide] ): Unit = {
    this.foreachMissCleavedPeptide(masterQuantPeptides) { (mqPep,seqParts) =>
      // Discard detected miss-cleaved peptide
      this.discardPeptide(mqPep, MissCleavedPeptideDiscardingReason.MC_SEQUENCE)
    }
  }
  
}

object LowestAbundantCleavageFormFilterer extends IMissCleavedPeptideFilterer {
  
  def discardPeptides( masterQuantPeptides: Seq[MasterQuantPeptide] ): Unit = {
    
    val mcMqPepsByMcSeqPart = new HashMap[String,ArrayBuffer[MasterQuantPeptide]]()
    
    this.foreachMissCleavedPeptide(masterQuantPeptides) { (mqPep,seqParts) =>
      for( seqPart <- seqParts ) {
    	  mcMqPepsByMcSeqPart.getOrElseUpdate(seqPart, new ArrayBuffer[MasterQuantPeptide]) += mqPep
      }
    }
    
    val mqPepBySeq = masterQuantPeptides
      .withFilter(_.peptideInstance.isDefined)
      .map( mqPep => mqPep.peptideInstance.get.peptide.sequence -> mqPep)
      .toMap
      
    for( (mcSeqPart,mcMqPeps) <- mcMqPepsByMcSeqPart ) {
      val mqPepOpt = mqPepBySeq.get(mcSeqPart)
      val allDetectedForms = if( mqPepOpt.isEmpty ) mcMqPeps else mcMqPeps ++ List(mqPepOpt.get)
      
      // We compare the abundances of the different forms
      val mostAbundantMqPepId = allDetectedForms.maxBy(_.getBestQuantPeptide.rawAbundance).id
      
      // Discard low abundance forms
      allDetectedForms.withFilter(_.id != mostAbundantMqPepId).foreach { mqPep =>
        this.discardPeptide(mqPep, MissCleavedPeptideDiscardingReason.LESS_ABUNDANT_THAN_OTHER_MC_FORM)
      }
    }
    
  }
  
}

