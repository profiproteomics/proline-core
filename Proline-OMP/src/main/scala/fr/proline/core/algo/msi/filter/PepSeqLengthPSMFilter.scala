package fr.proline.core.algo.msi.filter


import fr.proline.core.om.model.msi.PeptideMatch
import scala.collection.Seq
import scala.collection.mutable.HashMap

class PepSeqLengthPSMFilter( minSeqLength: Int = 0 ) extends IPeptideMatchFilter {

  val filterName = "peptide sequence lenght filter"

  def filterPSM( pepMatches: Seq[PeptideMatch], incrementalValidation: Boolean,
                 traceability: Boolean ): Unit = {

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
    props += ("min sequence length" ->  minSeqLength )
    Some( props.toMap )
  }

}