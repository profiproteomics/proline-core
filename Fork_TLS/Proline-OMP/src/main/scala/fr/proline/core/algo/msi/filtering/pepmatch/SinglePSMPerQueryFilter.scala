package fr.proline.core.algo.msi.filtering.pepmatch

import scala.collection.Seq
import scala.collection.immutable.Map

import com.typesafe.scalalogging.LazyLogging

import fr.profi.util.MathUtils
import fr.proline.core.algo.msi.filtering.IFilterNeedingResultSet
import fr.proline.core.algo.msi.filtering.IPeptideMatchFilter
import fr.proline.core.algo.msi.filtering.PepMatchFilterParams
import fr.proline.core.algo.msi.filtering.PeptideMatchFiltering
import fr.proline.core.om.model.msi._

class SinglePSMPerQueryFilter(var targetRs: IResultSetLike = null) extends IPeptideMatchFilter with LazyLogging with IFilterNeedingResultSet {

  val filterParameter = PepMatchFilterParams.SINGLE_PSM_PER_QUERY.toString
  val filterDescription = "single peptide match per query filter using score and peptide matches count per protein values"

  def setTargetRS(targetRS: IResultSetLike) {
    targetRs = targetRS
  }

  def filterPeptideMatches(pepMatches: Seq[PeptideMatch], incrementalValidation: Boolean, traceability: Boolean): Unit = {
    require(targetRs != null, " Target Search Result Should be specified before running this filter.")

    // Reset validation status if validation is not incremental
    if (!incrementalValidation) PeptideMatchFiltering.resetPepMatchValidationStatus(pepMatches)
    val psmPerQuery = pepMatches.filter(_.isValidated).groupBy(_.msQueryId)

    //For each query find a unique PSM. Used ruels:
    // - PSM with best score
    // - if equality : PSM which identify ProtMatches with higher number of valid PSMs
    var protMatchesByPepMatchId = ResultSet.getProteinMatchesByPeptideMatchId(targetRs)
    val decoyRsOpt = targetRs.getDecoyResultSet()
    if (decoyRsOpt.isDefined) {
      protMatchesByPepMatchId ++= ResultSet.getProteinMatchesByPeptideMatchId(decoyRsOpt.get)
    }

    // Filter query per query
    psmPerQuery.foreach(entry => {
      var queryPsms = entry._2
      queryPsms = queryPsms.sortWith((a, b) => a.score > b.score).filter(_.isValidated)
      var bestQueryPsm: PeptideMatch = null

      if (queryPsms.size > 1 && nearlyEqual(queryPsms(0).score, queryPsms(1).score)) {
        //Same Score Should found other way to filter
        val equalsPSMs = queryPsms.filter(_.score.equals(queryPsms(0).score))

        //        logger.debug(" ------  Same Score Should found other way to filter QId "+entry._1+" size "+ equalsPSMs.size+" on "+queryPsms.size)

        equalsPSMs.foreach(currentPsm => {
          if (bestQueryPsm == null) {
            bestQueryPsm = currentPsm
          } else {
            val protMatchesOpt = protMatchesByPepMatchId.get(currentPsm.id)
            val bestPsmProtMatchesOpt = protMatchesByPepMatchId.get(bestQueryPsm.id)
            // filter using ProteinMatch nbrPeptideCount
            if (protMatchesOpt.isDefined &&
              (bestPsmProtMatchesOpt.isEmpty
                || (getMaxNbrPepForProtMatches(protMatchesOpt.get) > getMaxNbrPepForProtMatches(bestPsmProtMatchesOpt.get))))

              bestQueryPsm = currentPsm
          }
        }) //end go throughall equals psms

      } else if (!queryPsms.isEmpty) {
        //        if(queryPsms.size > 1)
        //        	logger.debug(" ------ Multiple PSM but <> Score QId "+entry._1+" size "+ queryPsms.size)
        //Keep only 1st PSM
        bestQueryPsm = queryPsms(0)
      }

      //Invalidate others PSMs
      queryPsms.foreach(currentPsm => {
        if (!currentPsm.equals(bestQueryPsm))
          currentPsm.isValidated = false
      })

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