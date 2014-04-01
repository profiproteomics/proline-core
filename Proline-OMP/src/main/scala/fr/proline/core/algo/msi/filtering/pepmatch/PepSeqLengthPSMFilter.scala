package fr.proline.core.algo.msi.filtering.pepmatch

import fr.proline.core.om.model.msi.PeptideMatch
import scala.collection.Seq
import scala.collection.mutable.HashMap
import fr.proline.core.algo.msi.filtering._
import fr.proline.util.primitives._

class PepSeqLengthPSMFilter( var minSeqLength: Int = 0 ) extends IPeptideMatchFilter with IPeptideMatchSorter {
  
  val filterParameter = PepMatchFilterParams.PEPTIDE_SEQUENCE_LENGTH.toString
  val filterDescription = "peptide sequence length filter"

  def getPeptideMatchSequenceLength(pepMatch: PeptideMatch): Int = pepMatch.peptide.sequence.length
  
  def getPeptideMatchValueForFiltering(pepMatch: PeptideMatch): AnyVal = getPeptideMatchSequenceLength(pepMatch)
    
  def filterPeptideMatches(pepMatches: Seq[PeptideMatch],incrementalValidation: Boolean,traceability: Boolean ): Unit = {

    for ( peptideMatch <- pepMatches ) {
      
      // Reset validation status if validation is not incremental
      if ( !incrementalValidation ) peptideMatch.isValidated = true

      // Update validation status
      if ( peptideMatch.peptide.sequence.length < minSeqLength ) peptideMatch.isValidated = false

    }
  }
  
  /*def sortPeptideMatches( pepMatches: Seq[PeptideMatch] ): Seq[PeptideMatch] = {
    pepMatches.sortWith( getPeptideMatchSequenceLength(_) > getPeptideMatchSequenceLength(_) )
  }*/

  def getFilterProperties(): Map[String, Any] = {
    val props =new HashMap[String, Any]    
    props += (FilterPropertyKeys.THRESHOLD_VALUE -> minSeqLength)
    props.toMap
  }
  
  def getThresholdValue(): AnyVal = minSeqLength
  
  def setThresholdValue( currentVal: AnyVal ){
    minSeqLength = toInt(currentVal)
  }
   
  def sortPeptideMatches( pepMatches: Seq[PeptideMatch] ): Seq[PeptideMatch] = {
    pepMatches.sortWith( _.peptide.sequence.length > _.peptide.sequence.length )
  }
}