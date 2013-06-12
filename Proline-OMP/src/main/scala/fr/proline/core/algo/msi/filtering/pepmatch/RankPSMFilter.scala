package fr.proline.core.algo.msi.filtering.pepmatch

import scala.collection.mutable.HashMap
import scala.collection.Seq
import com.weiglewilczek.slf4s.Logging
import fr.proline.core.om.model.msi.PeptideMatch
import fr.proline.core.algo.msi.filtering._
import fr.proline.util.primitives._
object RankPSMFilter {
  
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
      var refScore = sortedPepmatches(0).score
      
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
class RankPSMFilter( var pepMatchMaxRank: Int = 1 ) extends IPeptideMatchFilter with Logging {
  
  val filterParameter = PepMatchFilterParams.RANK.toString
  val filterDescription = "peptide match rank filter"
    
  def getPeptideMatchValueForFiltering(pepMatch: PeptideMatch): AnyVal = pepMatch.rank

  def filterPeptideMatches( pepMatches: Seq[PeptideMatch], incrementalValidation: Boolean, traceability: Boolean ): Unit = {
    filterPeptideMatchesDBO(pepMatches,incrementalValidation,traceability)
  }
  
  // TODO: rerank outside the filter ???
  def filterPeptideMatchesDBO( pepMatches: Seq[PeptideMatch], incrementalValidation: Boolean, traceability: Boolean ): Unit = {
    
    // Reset validation status if validation is not incremental
    if( !incrementalValidation ) PeptideMatchFiltering.resetPepMatchValidationStatus(pepMatches)
    
    // Memorize peptide matches rank
    val pepMatchRankMap = RankPSMFilter.getPeptideMatchesRankMap(pepMatches)
    
    // Rerank peptide matches
    RankPSMFilter.rerankPeptideMatches(pepMatches)
    
    // Filter peptide matches against the new rank
    pepMatches.filter( _.rank > pepMatchMaxRank ).foreach( _.isValidated = false )
    
    // Restore the previous peptide match rank
    RankPSMFilter.restorePeptideMatchesRank(pepMatches, pepMatchRankMap)
    
    /*
     * Alternative solution
    
    // Group peptide matches by MS query
    val pepMatchesByMsqId = pepMatches.groupBy(_.msQueryId)
    
    // Iterate over peptide matches of each MS query
    for( (msqId,pepMatches) <- pepMatchesByMsqId ) {
      
      pepMatches
        .groupBy( p => math.round(p.score/scoreTol) ) // group by score diff < scoreTol
        .toSeq.sortWith( (a,b) => a._1 > b._1 )       // sort by score aggregation
        .drop(pepMatchMaxRank)                        // drop peptide matches having the wanted rank
        .flatten( _._2 )                              // flatten the invalid peptide matches
        .foreach( _.isValidated = false )             // change validation status of invalid peptide matches
    }*/
    
  }
  
  def sortPeptideMatches( pepMatches: Seq[PeptideMatch] ): Seq[PeptideMatch] = {
        
    // Memorize peptide matches rank
    val pepMatchRankMap = RankPSMFilter.getPeptideMatchesRankMap(pepMatches)
    
    // Rerank peptide matches
    // TODO: rerank outside the filter ???
    RankPSMFilter.rerankPeptideMatches(pepMatches)
    
    // Sort peptide matches by rank value
    val sortedPepMatches = pepMatches.sortBy( _.rank )
    
    // Restore the previous peptide match rank
    RankPSMFilter.restorePeptideMatchesRank(pepMatches, pepMatchRankMap)
    
    sortedPepMatches
  }
    
  def filterPeptideMatchesVDS( pepMatches: Seq[PeptideMatch], incrementalValidation: Boolean, traceability: Boolean ): Unit = {

//    logger.debug(" Filter on rank "+pepMatchMaxRank+" BEFORE "+pepMatches.filter(_.isValidated).size+" ON "+pepMatches.size)
    val orderedPepMatch = pepMatches.sortWith( _.score > _.score )

    var currentpepMatchRank = 1
    var pepMatchesIndex = 0
    var currentRankScore = orderedPepMatch( pepMatchesIndex ).score
    var reachEndArray = false
    
    do {
      if ( !incrementalValidation ) { 
        orderedPepMatch( pepMatchesIndex ).isValidated = true //save information if not incremental mode
      }
      pepMatchesIndex += 1
      
      if(pepMatchesIndex >= orderedPepMatch.length-1)
        reachEndArray = true
      
      //Calculate next peptide match rank : same as current if scores difference is less than 0.1      
      if (!reachEndArray &&  (currentRankScore - orderedPepMatch( pepMatchesIndex ).score )>= 0.1 ) {
        currentpepMatchRank += 1 // increment pretty rank
        currentRankScore = orderedPepMatch( pepMatchesIndex ).score // update new pretty rank score reference
      }
      
    } while ( currentpepMatchRank <= pepMatchMaxRank && !reachEndArray)  //Current PeptideMatch has a valid rank
         

    //Set remaining PeptideMatch (does > than validation rank) to isValidated = false. 
    while ( pepMatchesIndex < orderedPepMatch.size ) {
      orderedPepMatch( pepMatchesIndex ).isValidated = false
      pepMatchesIndex += 1
    }
//     logger.debug(" Filter on rank "+pepMatchMaxRank+" AFTER "+pepMatches.filter(_.isValidated).size+" ON "+pepMatches.size)
  }

  def getFilterProperties(): Map[String, Any] = {
    val props = new HashMap[String, Any]
    props += (FilterPropertyKeys.THRESHOLD_VALUE -> pepMatchMaxRank )
    props.toMap
  }
  
  def getThresholdValue(): AnyVal = pepMatchMaxRank
  
  def setThresholdValue( currentVal : AnyVal ) {
    pepMatchMaxRank = toInt(currentVal)
  }

}