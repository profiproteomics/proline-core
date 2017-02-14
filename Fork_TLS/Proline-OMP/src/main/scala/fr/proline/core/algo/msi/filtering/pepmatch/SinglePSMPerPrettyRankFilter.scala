package fr.proline.core.algo.msi.filtering.pepmatch

import scala.collection.Seq
import scala.collection.immutable.Map

import com.typesafe.scalalogging.LazyLogging

import fr.profi.util.MathUtils
import fr.proline.core.algo.msi.filtering.IFilterNeedingResultSet
import fr.proline.core.algo.msi.filtering.IPeptideMatchFilter
import fr.proline.core.algo.msi.filtering.PepMatchFilterParams
import fr.proline.core.algo.msi.filtering.PeptideMatchFiltering
import fr.proline.core.om.model.msi.IResultSetLike
import fr.proline.core.om.model.msi.PeptideMatch
import fr.proline.core.om.model.msi.ProteinMatch
import fr.proline.core.om.model.msi.ResultSet

class SinglePSMPerPrettyRankFilter(var targetRs: IResultSetLike = null) extends IPeptideMatchFilter with LazyLogging with IFilterNeedingResultSet {

  val filterParameter = PepMatchFilterParams.SINGLE_PSM_PER_RANK.toString
  val filterDescription = "single peptide match per pretty rank filter using peptide matches count per protein values"

  def setTargetRS(targetRS: IResultSetLike) {
    targetRs = targetRS
  }

  def filterPeptideMatches(pepMatches: Seq[PeptideMatch], incrementalValidation: Boolean, traceability: Boolean): Unit = {
    require(targetRs != null, " Target Search Result Should be specified before running this filter.")

    // Reset validation status if validation is not incremental
    if (!incrementalValidation) PeptideMatchFiltering.resetPepMatchValidationStatus(pepMatches)

    // Memorize peptide matches rank
    val pepMatchRankMap = PrettyRankPSMFilter.getPeptideMatchesRankMap(pepMatches)

    // Rerank peptide matches
    PrettyRankPSMFilter.rerankPeptideMatches(pepMatches)

    // Group peptide matches by MS query
    val pepMatchesByMsqId = pepMatches.filter(_.isValidated).groupBy(_.msQueryId)

    //For each query find a unique PSM per rank. Used rules:
    // - PSM with best score
    // - if equality : PSM which identify ProtMatches with higher number of valid PSMs

    var protMatchesByPepMatchId = ResultSet.getProteinMatchesByPeptideMatchId(targetRs)
    val decoyRsOpt = targetRs.getDecoyResultSet()
    if (decoyRsOpt.isDefined) {
      protMatchesByPepMatchId ++= ResultSet.getProteinMatchesByPeptideMatchId(decoyRsOpt.get)
    }

    // Filter query per query
    pepMatchesByMsqId.foreach(entry => {
      var psmsByRank = entry._2.groupBy(_.rank);

      // Filter rank per rank
      psmsByRank.foreach(sameRankPSMs => {
        var bestRankPsm: PeptideMatch = null
        //For all PSMs of a specific rank select 1

        val currentRankPsms = sameRankPSMs._2
        if (currentRankPsms != null && !currentRankPsms.isEmpty) { // al least one PSM.

          if (currentRankPsms.size == 1) { //One PSM... choose it
            bestRankPsm = currentRankPsms(0)
            //            logger.debug("**** One PSM... choose it  "+bestRankPsm.peptide.sequence)
          } else { //more than one, choose using protein matches pepMatch count 
            currentRankPsms.foreach(currentPsm => {
              if (bestRankPsm == null) {
                bestRankPsm = currentPsm
                //                logger.debug("**** more than One PSMs... Start with first "+bestRankPsm.peptide.sequence)
              } else {
                val protMatchesOpt = protMatchesByPepMatchId.get(currentPsm.id)
                val bestPsmProtMatchesOpt = protMatchesByPepMatchId.get(bestRankPsm.id)
                // filter using ProteinMatch nbrPeptideCount.
                if (protMatchesOpt.isDefined &&
                  (bestPsmProtMatchesOpt.isEmpty
                    || (getMaxNbrPepForProtMatches(protMatchesOpt.get) > getMaxNbrPepForProtMatches(bestPsmProtMatchesOpt.get))))
                  bestRankPsm = currentPsm
                //                  logger.debug("**** more than One PSMs... Found new best "+bestRankPsm.peptide.sequence)
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

  protected def getMaxNbrPepForProtMatches(protMatches: Array[ProteinMatch]): Int = {
    var maxNbrPep: Int = 0
    protMatches.foreach(pm => {
      maxNbrPep = maxNbrPep.max(pm.peptideMatchesCount)
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