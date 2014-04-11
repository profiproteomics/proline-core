package fr.proline.core.algo.msi.filtering.pepmatch

import scala.collection.Seq
import fr.proline.core.algo.msi.filtering.IPeptideMatchFilter
import scala.collection.immutable.Map
import fr.proline.core.om.model.msi.PeptideMatch
import fr.proline.core.algo.msi.filtering.PepMatchFilterParams
import fr.proline.core.algo.msi.filtering.PeptideMatchFiltering
import fr.proline.core.om.model.msi.ResultSet
import scala.collection.mutable.ArrayBuffer
import fr.proline.core.om.model.msi.ProteinMatch
import scala.collection.mutable.HashMap
import fr.proline.core.algo.msi.filtering.IFilterNeedingResultSet
import com.typesafe.scalalogging.slf4j.Logging
import fr.proline.util.MathUtils

class SinglePSMPerPrettyRankFilter(var targetRSSet: ResultSet = null) extends IPeptideMatchFilter with Logging with IFilterNeedingResultSet {

  val filterParameter = PepMatchFilterParams.SINGLE_PSM_PER_RANK.toString
  val filterDescription = "single peptide match per pretty rank filter using peptide matches count per protein values"

  def setTargetRS(targetRS: ResultSet) {
    targetRSSet = targetRS
  }

  def filterPeptideMatches(pepMatches: Seq[PeptideMatch], incrementalValidation: Boolean, traceability: Boolean): Unit = {
    require(targetRSSet != null, " Target Search Result Should be specified before running this filter.")

    // Reset validation status if validation is not incremental
    if (!incrementalValidation) PeptideMatchFiltering.resetPepMatchValidationStatus(pepMatches)

    // Memorize peptide matches rank
    val pepMatchRankMap = RankPSMFilter.getPeptideMatchesRankMap(pepMatches)

    // Rerank peptide matches
    RankPSMFilter.rerankPeptideMatches(pepMatches)

    // Group peptide matches by MS query
    val pepMatchesByMsqId = pepMatches.filter(_.isValidated).groupBy(_.msQueryId)

    //For each query find a unique PSM per rank. Used rules:
    // - PSM with best score
    // - if equality : PSM which identify ProtMatches with higher number of valid PSMs
    val startTime = System.currentTimeMillis()
    logger.debug("Start creating Maps")      
    val pepMatchesPerProtMatch: Map[ProteinMatch, ArrayBuffer[PeptideMatch]] = if(targetRSSet.decoyResultSet.isDefined) {targetRSSet.getPeptideMatchesByProteinMatch ++ targetRSSet.decoyResultSet.get.getPeptideMatchesByProteinMatch} else targetRSSet.getPeptideMatchesByProteinMatch
        
    //Create reverse map 
    val protMatchesPerPepMatchId = new HashMap[Long, ArrayBuffer[ProteinMatch]]()
    pepMatchesPerProtMatch.foreach(entry => {
      val pepMatches = entry._2
      pepMatches.foreach(pepMatch => {
        val protArrays = protMatchesPerPepMatchId.getOrElseUpdate(pepMatch.id, new ArrayBuffer[ProteinMatch]())
        protArrays += entry._1
      })
    })

    val endTime = System.currentTimeMillis()
    logger.debug("END creating Maps " + (endTime - startTime))

    // Filter query per query
    pepMatchesByMsqId.foreach(entry => {
      logger.debug("**** Filter MSQuery "+entry._1)
      var psmsByRank = entry._2.groupBy(_.rank);
       if(entry._1.equals(176394l)){
         logger.debug("**** psmsByRank  "+psmsByRank.size)
       } 
         
      // Filter rank per rank
      psmsByRank.foreach(sameRankPSMs => {
        var bestRankPsm: PeptideMatch = null
        //For all PSMs of a specific rank select 1
        if(entry._1.equals(176394l))  
        	logger.debug("**** Filter MSQuery - Rank  "+sameRankPSMs._1)
        	
        val currentRankPsms = sameRankPSMs._2
        if (currentRankPsms != null && !currentRankPsms.isEmpty) { // al least one PSM.

          if (currentRankPsms.size == 1) { //One PSM... choose it
            bestRankPsm = currentRankPsms(0)
            logger.debug("**** One PSM... choose it  "+bestRankPsm.peptide.sequence)
          } else { //more than one, choose using protein matches pepMatch count 
            currentRankPsms.foreach(currentPsm => {
              if (bestRankPsm == null) {
                bestRankPsm = currentPsm
                logger.debug("**** more than One PSMs... Start with first "+bestRankPsm.peptide.sequence)
              } else {
                logger.debug("**** more than One PSMs... Is new one best ?? currentPsm exist :  "+protMatchesPerPepMatchId.get(currentPsm.id).isDefined)
                logger.debug("**** more than One PSMs... Is new one best ?? bestRankPsm exist :  "+protMatchesPerPepMatchId.get(bestRankPsm.id).isDefined)
                logger.debug("**** more than One PSMs... Is new one best ?? currentPsm size :  "+protMatchesPerPepMatchId.get(currentPsm.id).get.size )
                logger.debug("**** more than One PSMs... Is new one best ?? bestRankPsm size :  "+ protMatchesPerPepMatchId.get(bestRankPsm.id).get.size)
                
                // filter using ProteinMatch nbrPeptideCount.
                if ( protMatchesPerPepMatchId.get(currentPsm.id).isDefined &&
                		( protMatchesPerPepMatchId.get(bestRankPsm.id).isEmpty
                			|| ( getMaxNbrPepForProtMatches(protMatchesPerPepMatchId.get(currentPsm.id).get) > getMaxNbrPepForProtMatches(protMatchesPerPepMatchId.get(bestRankPsm.id).get) ))) {
                  bestRankPsm = currentPsm
                  logger.debug("**** more than One PSMs... Found new best "+bestRankPsm.peptide.sequence)
                }
              }
            }) //end go throughall equals psms
          } //End more than one PSM 

          //Invalidate others PSMs
          currentRankPsms.foreach(currentPsm => {
            if (!currentPsm.equals(bestRankPsm))
              currentPsm.isValidated = false
          })

        } //End at least ONE PSM

      }) // end filter rank per rank

    }) //end filter query per query

  }

  protected  def getMaxNbrPepForProtMatches(protMatches : ArrayBuffer[ProteinMatch]) : Int = {
    var maxNbrPep:Int = 0
  	protMatches.foreach(pm => {  	  
  	    maxNbrPep =maxNbrPep.max(pm.peptideMatchesCount)
  	})
  	maxNbrPep
  }
  
  protected def nearlyEqual(a: Float, b: Float): Boolean = {
    (a - b).abs < MathUtils.EPSILON_FLOAT
  }

  // No Specific threshold ... set to 1...
  def getFilterProperties(): Map[String, Any] = {
    Map.empty[String, Any]
  }

  def getPeptideMatchValueForFiltering(pepMatch: PeptideMatch): AnyVal = { 1 }

  def getThresholdValue(): AnyVal = 1

  def setThresholdValue(currentVal: AnyVal) {}

}