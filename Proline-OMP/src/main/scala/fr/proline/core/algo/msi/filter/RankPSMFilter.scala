package fr.proline.core.algo.msi.filter

import scala.collection.mutable.HashMap
import scala.collection.Seq

import fr.proline.core.om.model.msi.PeptideMatch

class RankPSMFilter( pepMatchRank: Int ) extends IPeptideMatchFilter {

  val filterName = "peptide match rank filter"

  def filterPSM( pepMatches: Seq[PeptideMatch], incrementalValidation: Boolean, traceability: Boolean ): Unit = {

    val orderedPepMatch = pepMatches.sortWith( _.score > _.score )

    var currentpepMatchRank = 1
    var pepMatchesIndex = 0
    while ( currentpepMatchRank <= pepMatchRank ) {
      if ( !incrementalValidation ) { //Current PeptideMatch has a valid rank 
        orderedPepMatch( pepMatchesIndex ).isValidated = true //save information if not incremental mode
      }

      //Calculate next peptide match rank : same as current if scores difference is less than 0.1
      if ( orderedPepMatch( pepMatchesIndex ).score - orderedPepMatch( pepMatchesIndex + 1 ).score >= 0.1 ) {
        currentpepMatchRank += 1
      }

      pepMatchesIndex += 1
    }

    //Set remaining PeptideMatch (does > than validation rank) to isValidated = false. 
    while ( pepMatchesIndex < orderedPepMatch.size ) {
      orderedPepMatch( pepMatchesIndex ).isValidated = false
      pepMatchesIndex += 1
    }
  }

  def getFilterProperties(): Option[Map[String, Any]] = {
    val props = new HashMap[String, Any]
    props += ("rank" -> pepMatchRank )
    Some( props.toMap )
  }

}