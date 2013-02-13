package fr.proline.core.algo.msi.filter

import fr.proline.core.om.model.msi.PeptideMatch
import scala.collection.Seq
import scala.collection.mutable.HashMap

class PepSeqLengthPSMFilter( var minSeqLength: Int = 0 ) extends IPeptideMatchFilter {
  
  val filterParameter = PepMatchFilterParams.PEPTIDE_SEQUENCE_LENGTH.toString
  val filterDescription = "peptide sequence length filter"

  def filterPeptideMatches(pepMatches: Seq[PeptideMatch],incrementalValidation: Boolean,traceability: Boolean ): Unit = {

    for ( peptideMatch <- pepMatches ) {
      if ( !incrementalValidation ) {
        peptideMatch.isValidated = !( peptideMatch.peptide.sequence.length < minSeqLength )
      } else {
        if ( peptideMatch.peptide.sequence.length < minSeqLength )
          peptideMatch.isValidated = false
      }

    }
  }

  def getFilterProperties(): Option[Map[String, Any]] = {
     val props =new HashMap[String, Any]
    props += ( PepMatchFilterPropertyKeys.MIN_PEPTIDE_SEQUENCE_LENGTH ->  minSeqLength )
    Some( props.toMap )
  }
  
  def setThresholdValue( currentVal: AnyVal ){
    minSeqLength = currentVal.asInstanceOf[Int]
  }

}