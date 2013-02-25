package fr.proline.core.algo.msi.validation

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import fr.proline.core.om.model.msi.PeptideMatch
import fr.proline.core.algo.msi.filtering._

case class TDCompetitionCounts( var better: Int = 0, var only: Int = 0, var under: Int = 0 )
case class TDCompetitionResult( winnerKey: String, winnerValue: Option[AnyVal], looserValue: Option[AnyVal] )

object TargetDecoyComputer {
  
  val TARGET_KEY = "TARGET"
  val DECOY_KEY = "DECOY"
  
  def buildPeptideMatchJointTable( peptideMatches: Seq[PeptideMatch] ): Array[Pair[PeptideMatch,PeptideMatch]] = {
    
    // Filter peptide matches to have only first rank ones
    val firstRankPSMs = peptideMatches.filter { _.rank == 1 }
    
    // Group PSMs by MS query initial id
    val pepMatchesByMsQueryInitialId = firstRankPSMs.groupBy( _.msQuery.initialId )
    
    // Build peptide match joint table
    val jointTable = new ArrayBuffer[Pair[PeptideMatch,PeptideMatch]]
    for( (msQueryInitialId, pepMatches) <- pepMatchesByMsQueryInitialId ) {
      
      // Group peptide matches by result set id
      val pepMatchesByIsDecoy = pepMatches.groupBy(_.isDecoy)
      val targetPepMatches = pepMatchesByIsDecoy.get(false)
      val decoyPepMatches = pepMatchesByIsDecoy.get(true)
      
      // Remove peptide match duplicates (same score and same rank but different peptides = see Mascot pretty rank )
      var targetPepMatch: PeptideMatch = null
      if( targetPepMatches != None ) {
        targetPepMatch = targetPepMatches.get.toList.sort( (a,b) => a.score > b.score ).head
      }
      
      // Remove peptide match duplicates (same score and same rank but different peptides = see Mascot pretty rank )
      var decoyPepMatch: PeptideMatch = null
      if( decoyPepMatches != None ) {
        decoyPepMatch = decoyPepMatches.get.toList.sort( (a,b) => a.score > b.score ).head
      }
      
      jointTable += Pair(targetPepMatch,decoyPepMatch)
      
    }
  
    jointTable.toArray
  }
  
  def buildPeptideMatchJointMap( targetPeptideMatches: Seq[PeptideMatch], decoyPeptideMatches: Option[Seq[PeptideMatch]] ): Map[Int, Seq[PeptideMatch]] = {
    
    val peptideMatches = targetPeptideMatches ++ decoyPeptideMatches.getOrElse(Seq())
    
    // Group peptide matches by MS query id and sort them by descendant score
    // TODO: do we need to sort here ???
    peptideMatches.groupBy( _.msQueryId ).map{ case(msqId,pepMatches) => 
      msqId -> pepMatches.sortWith(_.score > _.score)
    }
    
  }
    
   
  /**
   *  Compute Pair of TDCompetitionCounts based on specified scores.
   *  Pair of Score are organized as [target value, decoy value]
   *    
   */
  def computeTdCompetition(
    scoreJointTable: Seq[Pair[Double,Double]],
    scoreThreshold: Double 
  ): Pair[TDCompetitionCounts,TDCompetitionCounts] = {    

    val competitionCounts = Map( TARGET_KEY -> TDCompetitionCounts(),
                                 DECOY_KEY -> TDCompetitionCounts() )
    
    for( val scores <- scoreJointTable ) {
      
      val( targetScore, decoyScore ) = ( scores._1, scores._2 )
      var compet: TDCompetitionResult = null
      
      // If decoy value equals target value we consider that decoy wins
      if( targetScore > decoyScore ) { compet = TDCompetitionResult(TARGET_KEY, Some(targetScore), Some(decoyScore) ) }
      else { compet = TDCompetitionResult(DECOY_KEY, Some(decoyScore), Some(targetScore) ) }
      
      // Assign competition winner to a given class = better, only or under
      if( compet.winnerValue.get.asInstanceOf[Double] >= scoreThreshold ) {
        if( compet.winnerValue.get.asInstanceOf[Double] >= scoreThreshold ) { competitionCounts(compet.winnerKey).better += 1 }
        else { competitionCounts(compet.winnerKey).only += 1 }
      }
      else { competitionCounts(compet.winnerKey).under += 1 }
    }
    
    Pair(competitionCounts(TARGET_KEY), competitionCounts(DECOY_KEY))
  }
  
  /**
   *  Compute Pair of TDCompetitionCounts based on specified scores.
   *  Pair of Score are organized as [target value, decoy value]
   *    
   */
  def computeTdCompetition(
    pmJointMap: Map[Int, Seq[PeptideMatch]],
    filter: IOptimizablePeptideMatchFilter
  ): Pair[TDCompetitionCounts,TDCompetitionCounts] = {
    
    val competitionCounts = Map( TARGET_KEY -> TDCompetitionCounts(),
                                 DECOY_KEY -> TDCompetitionCounts() )
    
    var (tB, tO, tU, dB, dO, dU) = (0, 0, 0, 0, 0, 0) // Counts to target / decoy only or better 

    // Iterate over peptide matches grouped by MS query id and sorted by rank 
    pmJointMap.foreach { case (msqId,pepMatches) =>
      
      // Filter peptide matches incrementally without traceability
      filter.filterPeptideMatches(pepMatches, true, false)
      
      // Sort peptide matches by the filtering parameter
      val sortedPepMatches = filter.sortPeptideMatches(pepMatches)
      
      // Retrieve best peptide match
      val bestPM = sortedPepMatches.head
      
      // Retrieve best target peptide match and related values
      val bestTargetPM = sortedPepMatches.find( !_.isDecoy )
      
      // Retrieve best decoy peptide match and related values
      val bestDecoyPM = sortedPepMatches.find( _.isDecoy )
      
      // If decoy value equals target value we consider that decoy wins
      val( winnerKey, winner, looser ) = if( bestPM.isDecoy == false ) (TARGET_KEY, bestTargetPM, bestDecoyPM )
      else (DECOY_KEY, bestDecoyPM, bestTargetPM )
      
      // Assign competition winner to a given class = better, only or under
      if( bestPM.isValidated ) {
        if( looser.isDefined &&  looser.get.isValidated ) { competitionCounts(winnerKey).better += 1 }
        else { competitionCounts(winnerKey).only += 1 }
      }
      else { competitionCounts(winnerKey).under += 1 }
      
      /*
      // Retrieve
      val( targetPepMatches, decoyPepMatches ) = pepMatches.partition( ! _.isDecoy )
      
      val targetPMCount = targetPepMatches.length
      val validTargetPMCount = targetPepMatches.count( _.isValidated )
      val invalidTargetPMCount = targetPMCount - validTargetPMCount
      val decoyPMCount = decoyPepMatches.length
      val validDecoyPMCount = decoyPepMatches.count( _.isValidated )
      val invalidDecoyPMCount = decoyPMCount - validDecoyPMCount
      
      // If no decoy PM above threshold
      if(validDecoyPMCount == 0) {
        tO += validTargetPMCount
      // Else if some decoy PMs above threshold
      } else {
        tB += validTargetPMCount
        // If no valid target PM => only decoy
        if( validTargetPMCount == 0 ) dU += invalidDecoyPMCount        
      }
      
      // If no target PM above threshold
      if(validTargetPMCount == 0) {
        dO += validDecoyPMCount
      // Else if some target PMs above threshold
      } else {
        dB += validDecoyPMCount
        if( validDecoyPMCount == 0 ) tU += invalidTargetPMCount
      }*/

    } // End go through pmJointMap entries
 
    
    Pair(competitionCounts(TARGET_KEY), competitionCounts(DECOY_KEY))
  }
  
  def validatePepMatchesWithCompetition(
    pmJointMap: Map[Int, Seq[PeptideMatch]],
    validationFilter: IOptimizablePeptideMatchFilter
  ): ValidationResult = {
    
    // Compute target/decoy competition
    val competitionCount = this.computeTdCompetition(pmJointMap, validationFilter)
    
    // Retrieve competition counts
    val (targetCount, decoyCount) = (competitionCount._1, competitionCount._2)
    val (tB, tO, dB, dO) = (targetCount.better, targetCount.only, decoyCount.better, decoyCount.only)
    
    // Compute FDR (note that FDR may be greater than 100%) for current filterThreshold
    val fdr = TargetDecoyComputer.calcCompetitionFDR(tB, tO, dB, dO)
    
    // Add ROC point to the list
    ValidationResult(
      targetMatchesCount = tB + tO + dB,
      decoyMatchesCount = Some(dB + dO + tB),
      fdr = Some(fdr),
      properties = Some(HashMap(FilterPropertyKeys.THRESHOLD_VALUE -> validationFilter.getThresholdValue) )
    )
  }

  /** Classic method for FPR calculation. */
  def calcFPR( tp: Int, fp: Int ): Float = { 
    require( tp > 0 && fp >= 0 )
    
    (100 * fp).toFloat / (tp + fp )
  }

  /** Calculates FDR for separate target/decoy databases (Matrix Science).
  * tp = target positive  dp = decoy positive
  */
  def calcSdFDR( tp: Int, dp: Int ): Float = {
    require( tp > 0 && dp >= 0 )
    
    (100 * dp).toFloat / tp
  }
  
  /** Calculates FDR for concatenated target/decoy databases (Elias and Gygi, Nature Methods, 2007)
  * tp = target positive  dp = decoy positive
  */
  def calcCdFDR( tp: Int, dp: Int ): Float = {
    require( tp + dp > 0 && dp >= 0 )
    
    (100 * 2 * dp).toFloat / (tp + dp )
  }

  /** Calculates FDR using the refined method described by Navarro et al. (JPR, 2009)
  * tB = target better ; tO = target only ; dB = decoy better ; dO = decoy only
  */
  def calcCompetitionFDR( tB: Int, tO: Int, dB: Int, dO: Int ): Float = { 
    require( tB + tO + dB > 0 )
    
    (100 * (2 * dB + dO)).toFloat / (tB + tO + dB)
  }

}