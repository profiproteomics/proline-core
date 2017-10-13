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
  val filterDescription = "single peptide match per pretty rank filter using peptide matches count per protein values and/or specified list of PSM for query/rank."

  // Map of peptide ID to chose (or chosen) for specified rank of specified MsQuery.
  var pepIdPerPrettyRankThreshold : Map[(Long,Int), Long] = Map.empty[(Long,Int), Long]
  var useMapAsThreshold : Boolean = false

  def setTargetRS(targetRS: IResultSetLike) {
    targetRs = targetRS
  }

  override def setPropagateMode(isPropagatingMode: Boolean): Unit = {
    useMapAsThreshold = isPropagatingMode
    super.setPropagateMode(isPropagatingMode)
  }

  /**
    * Filter PSM in order to have a single PSM per  pretty rank per query. If more than one PSM exist for pretty rank the selected PSM will be
    *  - the one specified in map (Query-rank)->PSM (through threshold value)
    *  - if none : the PSM identifying the protein with max number of PSM
    *
    * @param pepMatches All PeptideMatches
    * @param incrementalValidation If incrementalValidation is set to false,
    * PeptideMatch.isValidated will be explicitly set to true or false.
    * Otherwise, only excluded PeptideMatch will be changed by setting their isValidated property to false.
    * @param traceability Specify if filter could saved information in peptideMatch properties
    *
    */
  def filterPeptideMatches(pepMatches: Seq[PeptideMatch], incrementalValidation: Boolean, traceability: Boolean): Unit = {
    require(targetRs != null, " Target Search Result Should be specified before running this filter.")

    // Reset validation status if validation is not incremental
    if (!incrementalValidation) PeptideMatchFiltering.resetPepMatchValidationStatus(pepMatches)

    // Memorize peptide matches rank
    val pepMatchRankMap: Map[Long, Int] = PrettyRankPSMFilter.getPeptideMatchesRankMap(pepMatches)

    // Rerank peptide matches
    PrettyRankPSMFilter.rerankPeptideMatches(pepMatches)

    // Group peptide matches by MS query
    val pepMatchesByMsqId = pepMatches.filter(_.isValidated).groupBy(_.msQueryId)

    //For each query find a unique PSM per rank. Used rules:
    // - if useMapAsThreshold look if specified in psmPerPrettyRank
    // - PSM with best score :  NO scores are slightly the same for same rank
    // - if equality : PSM which identify ProtMatches with higher number of valid PSMs

    var protMatchesByPepMatchId = ResultSet.getProteinMatchesByPeptideMatchId(targetRs)
    val decoyRsOpt = targetRs.getDecoyResultSet()
    if (decoyRsOpt.isDefined) {
      protMatchesByPepMatchId ++= ResultSet.getProteinMatchesByPeptideMatchId(decoyRsOpt.get)
    }

    // Filter query per query
    pepMatchesByMsqId.foreach(entry => {
      var psmsByRank: Map[Int, Seq[PeptideMatch]] = entry._2.groupBy(_.rank)

      // Filter rank per rank
      psmsByRank.foreach(sameRankPSMs => {
        val specifiedPepIdOpt : Option[Long] = if(useMapAsThreshold) pepIdPerPrettyRankThreshold.get(key = (entry._1, sameRankPSMs._1)) else None
        var bestRankPsm: PeptideMatch = null

        val currentRankPsms = sameRankPSMs._2

        //For all PSMs of a specific rank select 1
        if (currentRankPsms != null && currentRankPsms.nonEmpty) { // al least one PSM.

          // 1. See if specifiedPSMOpt exist in current query/rank list
          if(specifiedPepIdOpt.isDefined && currentRankPsms.exists(_.peptideId == specifiedPepIdOpt.get)) {
            bestRankPsm = currentRankPsms.filter( _.peptideId == specifiedPepIdOpt.get).head //use specified one !
            //logger.debug("**** Choose PSM specified in threshold "+bestRankPsm.peptide.sequence +" for "+entry._1+"-"+sameRankPSMs._1)

          } else  if (currentRankPsms.size == 1) {
            //2. Only one PSM... choose it
            bestRankPsm = currentRankPsms.head
            //            logger.debug("**** One PSM... choose it  "+bestRankPsm.peptide.sequence)

          } else { //more than one, choose using protein matches pepMatch count
            currentRankPsms.foreach(currentPsm => {
              if (bestRankPsm == null) {
                bestRankPsm = currentPsm
                logger.debug("**** more than One PSMs... Start with first "+bestRankPsm.peptide.sequence+" for "+entry._1+"-"+sameRankPSMs._1)
              } else {
                val protMatchesOpt = protMatchesByPepMatchId.get(currentPsm.id)
                val bestPsmProtMatchesOpt = protMatchesByPepMatchId.get(bestRankPsm.id)
                // filter using ProteinMatch nbrPeptideCount.
                if (protMatchesOpt.isDefined &&
                  (bestPsmProtMatchesOpt.isEmpty
                    || (getMaxNbrPepForProtMatches(protMatchesOpt.get) > getMaxNbrPepForProtMatches(bestPsmProtMatchesOpt.get))))
                  bestRankPsm = currentPsm
                  logger.debug("**** more than One PSMs... Found new best "+bestRankPsm.peptide.sequence+" for "+entry._1+"-"+sameRankPSMs._1)
              }
            }) //end go through all equals PSMs
          } //End more than one PSM

          //Save chosen one
          pepIdPerPrettyRankThreshold += ((entry._1, sameRankPSMs._1) -> bestRankPsm.peptideId)

          //Invalidate others PSMs
          currentRankPsms.foreach(currentPsm => {
            if (!currentPsm.equals(bestRankPsm))
              currentPsm.isValidated = false
          })

        } //End at least ONE PSM

      }) // end filter rank per rank

    }) //end filter query per query

    // Restore the previous peptide match rank
    PrettyRankPSMFilter.restorePeptideMatchesRank(pepMatches, pepMatchRankMap)

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

  def getPeptideMatchValueForFiltering(pepMatch: PeptideMatch): Any = { 1 }

  def getThresholdValue(): Any =  {
    pepIdPerPrettyRankThreshold
  }

  //Specify Map to use for filtering or (1 or an empty Map) to search without specified list
  def setThresholdValue(currentVal: Any): Unit = {
    if(classOf[Map[(Long,Int), Long]].isInstance(currentVal)) {
      val currentValAdMap = currentVal.asInstanceOf[Map[(Long, Int), Long]]
      useMapAsThreshold = true
      if(currentValAdMap.isEmpty)
        useMapAsThreshold = false
      pepIdPerPrettyRankThreshold = currentValAdMap
    } else if(classOf[Int].isInstance(currentVal) && (currentVal.asInstanceOf[Int] == 1)) {
      pepIdPerPrettyRankThreshold = Map.empty[(Long, Int), Long]
      useMapAsThreshold = false
    }
  }

}