package fr.proline.core.algo.msi.filtering.pepmatch

import scala.collection.mutable.HashMap
import scala.collection.Seq
import com.typesafe.scalalogging.LazyLogging
import fr.proline.core.om.model.msi.PeptideMatch
import fr.proline.core.algo.msi.filtering._
import fr.profi.util.primitives._

object PrettyRankPSMFilter {
  
  val scoreTol = 0.1f
  
  def getPeptideMatchesRankMap( pepMatches: Seq[PeptideMatch] ): Map[Long,Int] = {
    pepMatches.map( pm => pm.id -> pm.rank ).toMap
  }
  
  def restorePeptideMatchesRank( pepMatches: Seq[PeptideMatch], pepMatchRankMap: Map[Long,Int] ): Unit = {
    pepMatches.foreach( pm => pm.rank = pepMatchRankMap(pm.id) )
  }
  
  def rerankPeptideMatches(pepMatches: Seq[PeptideMatch] ) {
    
    // Group peptide matches by MS query
    val pepMatchesByMsqId = pepMatches.groupBy(_.msQueryId)
    
    // Iterate over peptide matches of each MS query
    for( (msqId,pepMatches) <- pepMatchesByMsqId ) {
      
      val sortedPepmatches = pepMatches.sortWith( _.score > _.score )
      
      var rank = 1
      var refScore = sortedPepmatches.head.score
      
      sortedPepmatches.foreach { pm =>
        // Increase rank if score is too far from reference
        if( (refScore - pm.score) > scoreTol ) {
          rank += 1
          refScore = pm.score // update reference score        
        }
        pm.rank = rank
      }

    }
  }
  
}

/**
 * Peptide Match Filter using rank. Specified PeptideMatch are ordered by score and a "pretty" rank is defined for each. PSM
 * with difference of scores < 0.1 are assigned the same rank. 
 * 
 * Constructor pepMatchRank specifies rank to consider : all PSM of rank higher than specified one will be excluded 
 * 
 */
class PrettyRankPSMFilter( var maxPrettyRank: Int = 1 ) extends IPeptideMatchFilter with IPeptideMatchSorter with LazyLogging {
  
  val filterParameter = PepMatchFilterParams.PRETTY_RANK.toString
  val filterDescription = "peptide match rank filter"
    
  def getPeptideMatchValueForFiltering(pepMatch: PeptideMatch): Any = pepMatch.rank

  // TODO: rerank outside the filter ???
  def filterPeptideMatches( pepMatches: Seq[PeptideMatch], incrementalValidation: Boolean, traceability: Boolean ): Unit = {
    
    // Reset validation status if validation is not incremental
    if( !incrementalValidation ) PeptideMatchFiltering.resetPepMatchValidationStatus(pepMatches)
    
    // Memorize peptide matches rank
    val pepMatchRankMap = PrettyRankPSMFilter.getPeptideMatchesRankMap(pepMatches)
    
    // Rerank peptide matches
    PrettyRankPSMFilter.rerankPeptideMatches(pepMatches)
    
    // Filter peptide matches against the new rank
    pepMatches.filter( _.rank > maxPrettyRank ).foreach( _.isValidated = false )
    
    // Restore the previous peptide match rank
    PrettyRankPSMFilter.restorePeptideMatchesRank(pepMatches, pepMatchRankMap)
    
  }
  
  def sortPeptideMatches( pepMatches: Seq[PeptideMatch] ): Seq[PeptideMatch] = {
        
    // Memorize peptide matches rank
    val pepMatchRankMap = PrettyRankPSMFilter.getPeptideMatchesRankMap(pepMatches)
    
    // Rerank peptide matches
    // TODO: rerank outside the filter ???
    PrettyRankPSMFilter.rerankPeptideMatches(pepMatches)
    
    // Sort peptide matches by rank value
    val sortedPepMatches = pepMatches.sortBy( _.rank )
    
    // Restore the previous peptide match rank
    PrettyRankPSMFilter.restorePeptideMatchesRank(pepMatches, pepMatchRankMap)
    
    sortedPepMatches
  }

  def getFilterProperties(): Map[String, Any] = {
    val props = new HashMap[String, Any]
    props += (FilterPropertyKeys.THRESHOLD_VALUE -> maxPrettyRank )
    props.toMap
  }
  
  def getThresholdValue(): Any = maxPrettyRank
  
  def setThresholdValue( currentVal : Any ): Unit= {
    maxPrettyRank = toInt(currentVal)
  }

}