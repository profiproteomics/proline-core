package fr.proline.core.algo.msi

import scala.collection.mutable.ArrayBuffer
import fr.proline.core.om.model.msi.PeptideMatch
import fr.proline.core.algo.msi.validation.ValidationResult
import fr.proline.core.algo.msi.filter.IOptimizablePeptideMatchFilter
import scala.collection.mutable.HashMap
import fr.proline.core.algo.msi.filter.PepMatchFilterPropertyKeys

case class TDCompetitionCounts( var better: Int = 0, var only: Int = 0, var under: Int = 0 )

// TODO: move to validation package ?
object TargetDecoyComputer {
  
  import scala.collection.mutable.ArrayBuffer
  
  def buildPeptideMatchJointMap( targetPeptideMatches: Seq[PeptideMatch], decoyPeptideMatches: Option[Seq[PeptideMatch]] ): Map[Int, Seq[PeptideMatch]]= {
    
    val peptideMatches = targetPeptideMatches ++ decoyPeptideMatches.getOrElse(scala.collection.mutable.Seq[PeptideMatch]())
    
    // Group PSMs by MS query initial id
    var pepMatchesByMsQueryInitialId = peptideMatches.groupBy( _.msQuery.initialId )
    
    // Build peptide match joint table
    for( (msQueryInitialId, pepMatches) <- pepMatchesByMsQueryInitialId ) {
      
      // Group peptide matches by result set id
	val sortedPepMatches : Seq[PeptideMatch]= pepMatches.sortBy(_.rank)
	pepMatchesByMsQueryInitialId += msQueryInitialId -> sortedPepMatches
    }
  
    pepMatchesByMsQueryInitialId 
  }
  
  
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
    
   
  /**
   *  Compute Pair of TDCompetitionCounts based on specified scores.
   *  Pair of Score are organized as [target value, decoy value]
   *    
   */
  def computeTdCompetition( scoreJointTable: Seq[Pair[Double,Double]], scoreThreshold: Double 
                          ): Pair[TDCompetitionCounts,TDCompetitionCounts] = {
  
    val competitionCounts = Map( "target" -> TDCompetitionCounts(),
                                 "decoy" -> TDCompetitionCounts() )                                
    case class TDCompetitionResult( winner: String, winnerScore: Double, looserScore: Double )
    
    for( val scores <- scoreJointTable ) {
      
      val( targetScore, decoyScore ) = ( scores._1, scores._2 )
      var compet: TDCompetitionResult = null
      
      // If decoy value equals target value we consider that decoy wins
      if( targetScore > decoyScore ) { compet = TDCompetitionResult("target", targetScore, decoyScore ) }
      else { compet = TDCompetitionResult("decoy", decoyScore, targetScore ) }
      
      // Assign competition winner to a given class = better, only or under
      if( compet.winnerScore >= scoreThreshold ) {
        if( compet.looserScore >= scoreThreshold ) { competitionCounts(compet.winner).better += 1 }
        else { competitionCounts(compet.winner).only += 1 }
      }
      else { competitionCounts(compet.winner).under += 1 }
    }
    
    Pair(competitionCounts("target"), competitionCounts("decoy"))
  }
  
  def rocAnalysis(pmJointMap: Map[Int, Seq[PeptideMatch]],
                  validationFilter: IOptimizablePeptideMatchFilter ): Array[ValidationResult] = {

    var filterThreshold = validationFilter.getThresholdStartValue
    var fdr = 100.0f
    val rocPoints = new ArrayBuffer[ValidationResult]

    while (fdr > 0) { // iterate from FDR = 100.0 to 0.0 

      var (tB, tO, dB, dO) = (0, 0, 0, 0) // Counts to target / decoy only or better 

      pmJointMap.iterator.foreach(entry => {

        //Save previous isValidated status
        val isValStatus: Seq[Boolean] = entry._2.map(_.isValidated)

        //-- Filter incrementally without traceability 
        validationFilter.setThresholdValue(filterThreshold)
        validationFilter.filterPeptideMatches(entry._2, true, false)

        //Keep only validated PSM
        val selectedPsm = entry._2.filter(_.isValidated)

        //-- Look which type of PSM (Target or Decoy) are still valid        
        var foundDecoy = false
        var foundTarget = false
        //Specify which type of PSM (Target or Decoy) was first found. Set to -1 for Decoy and to 1 for Target 
        var firstFound = 0

        // verify witch type of PSM (Target or Decoy) are still valid
        selectedPsm.foreach(psm => {
          if (psm.isDecoy) {
            foundDecoy = true
            if (firstFound == 0) firstFound = -1
          } else {
            foundTarget = true
            if (firstFound == 0) firstFound = 1
          }
        })

        // Upadte Target/Decoy count
        if (foundDecoy && !foundTarget)
          tO += 1
        if (foundTarget && !foundDecoy)
          dO += 1
        if (foundTarget && foundDecoy) {
          //VDS Should found better : take first in list... Should use Filter sort method ?? 
          if (firstFound == -1)
            dB += 1
          else
            tB += 1
        }

        //Reinit previous isValidated status
        var psmIndex = 0
        val psmList: Seq[PeptideMatch] = entry._2
        while (psmIndex < psmList.size) {
          psmList(psmIndex).isValidated = isValStatus(psmIndex)
        }

      }) // End go through pmJointMap entries

      // Compute FDR (note that FDR may be greater than 100%) for current filterThreshold
      fdr = TargetDecoyComputer.computeTdFdr(tB, tO, dB, dO)

      // Add ROC point to the list
      val rocPoint = ValidationResult(nbTargetMatches = tB + tO + dB,
        nbDecoyMatches = Some(dB + dO + tB),
        fdr = Some(fdr),
        properties = Some(HashMap(PepMatchFilterPropertyKeys.THRESHOLD_PROP_NAME -> filterThreshold))
      )
      rocPoints += rocPoint

      // Update threshold value 
      filterThreshold = validationFilter.getNextValue(filterThreshold)
      if (filterThreshold == null)
        fdr = 0 //Break current loop

    } //Go through all possible threshold value until FDR is positive

    rocPoints.toArray
  }

  /** Classic method for FDR computation. */
  def computeFdr( tp: Int, fp: Int ): Float = { 
    require( tp > 0 && fp >= 0 )
    
    (100 * fp).toFloat / (tp + fp )
  }

  /** Computes FDR for separate target/decoy databases (Matrix Science).
  * tp = target positive  dp = decoy positive
  */
  def computeSdFdr( tp: Int, dp: Int ): Float = {
    require( tp > 0 && dp >= 0 )
    
    (100 * dp).toFloat / tp
  }
  
  /** Computes FDR for concatenated target/decoy databases (Elias and Gygi, Nature Methods, 2007)
  * tp = target positive  dp = decoy positive
  */
  def computeCdFdr( tp: Int, dp: Int ): Float = {
    require( tp > 0 && dp >= 0 )
    
    (100 * 2 * dp).toFloat / (tp + dp )
  }

  /** Computes FDR using the refined method described by Navarro et al. (JPR, 2009)
  * tB = target better ; tO = target only ; dB = decoy better ; dO = decoy only
  */
  def computeTdFdr( tB: Int, tO: Int, dB: Int, dO: Int ): Float = { 
    require( tB + tO + dB > 0 )
    
    (100 * (2 * dB + dO)).toFloat / (tB + tO + dB)
  }

}