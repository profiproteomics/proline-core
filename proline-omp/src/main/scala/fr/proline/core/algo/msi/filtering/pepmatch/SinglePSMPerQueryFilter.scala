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
  val filterDescription = "single peptide match per query filter using score and peptide matches count per protein values and/or specified list of PSM for query"

  var pepIdPerQueryThreshold: Map[Long, Long] = Map.empty[Long, Long]
  var useMapAsThreshold : Boolean = false

  def setTargetRS(targetRS: IResultSetLike) {
    targetRs = targetRS
  }

  override def setPropagateMode(isPropagatingMode: Boolean): Unit = {
    useMapAsThreshold = isPropagatingMode
    super.setPropagateMode(isPropagatingMode)
  }

  /**
    * Filter PSM in order to have a single PSM per query. If more than one PSM for a query the selected PSM will be
    *  - the one specified in the map Query->PSM (through threshold value map)
    *  - if no map is specified : the highest score
    *  - if there is multiple PSM with the same highest score : the PSM identifying the protein with the max number of PSM
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
    val pepMatchByMsQueryId = pepMatches.filter(_.isValidated).groupBy(_.msQueryId)

    //For each query find a unique PSM. Used rules:
    // - if useMapAsThreshold look if specified in psmPerQueryThreshold
    // - PSM with best score
    // - if equality : PSM which identify ProtMatches with higher number of valid PSMs
    var protMatchesByPepMatchId = ResultSet.getProteinMatchesByPeptideMatchId(targetRs)
    val decoyRsOpt = targetRs.getDecoyResultSet()
    if (decoyRsOpt.isDefined) {
      protMatchesByPepMatchId ++= ResultSet.getProteinMatchesByPeptideMatchId(decoyRsOpt.get)
    }

    // Filter query per query
    pepMatchByMsQueryId.foreach(entry => {
      val queryPsms = entry._2.filter(_.isValidated).sortWith((a, b) => a.score > b.score)
      val higherScore = queryPsms.head.score

      val specifiedPepIdOpt : Option[Long] = if(useMapAsThreshold) pepIdPerQueryThreshold.get(entry._1) else None
      var bestQueryPsm: PeptideMatch = null

      //more than one, chose best one
      if (queryPsms.size > 1 && nearlyEqual(higherScore, queryPsms(1).score)) {
        // 1. See if specified PSM exist in current query/rank list
        if(specifiedPepIdOpt.isDefined && queryPsms.exists(_.peptideId == specifiedPepIdOpt.get)) {
          bestQueryPsm = queryPsms.filter(_.peptideId == specifiedPepIdOpt.get).head//use specified one !
          //            logger.debug("**** Choose PSM specified in threshold "+bestQueryPsm.peptide.sequence)

        } else {
          //2. Multiple PSM with Same Score. Should found other way to select
          val equalsPSMs = queryPsms.filter( psm => {nearlyEqual(psm.score, higherScore)})

          //        logger.debug(" ------  Same Score Should found other way to filter QId "+entry._1+" size "+ equalsPSMs.size+" on "+queryPsms.size)

          equalsPSMs.foreach(currentPsm => {
            if (bestQueryPsm == null) {
              bestQueryPsm = currentPsm
            } else {
              val protMatchesOpt = protMatchesByPepMatchId.get(currentPsm.id)
              val bestPsmProtMatchesOpt = protMatchesByPepMatchId.get(bestQueryPsm.id)
              // filter using ProteinMatch nbrPeptideCount or deltaMass
              if (protMatchesOpt.isDefined &&
                  (bestPsmProtMatchesOpt.isEmpty
                    || (getMaxNbrPepForProtMatches(protMatchesOpt.get) > getMaxNbrPepForProtMatches(bestPsmProtMatchesOpt.get))
                    || (getMaxNbrPepForProtMatches(protMatchesOpt.get) == getMaxNbrPepForProtMatches(bestPsmProtMatchesOpt.get) && nearlyEqual(currentPsm.deltaMoz, bestQueryPsm.deltaMoz) && currentPsm.peptide.sequence < bestQueryPsm.peptide.sequence  )
                    || (getMaxNbrPepForProtMatches(protMatchesOpt.get) == getMaxNbrPepForProtMatches(bestPsmProtMatchesOpt.get) && currentPsm.deltaMoz<bestQueryPsm.deltaMoz) ))
                bestQueryPsm = currentPsm
            }
          }) //end go through all equals psms
        }

      } else if (queryPsms.nonEmpty) {
        //3. Only one PSM or score are different enough

        //        if(queryPsms.size > 1)
        //        	logger.debug(" ------ Multiple PSM but <> Score QId "+entry._1+" size "+ queryPsms.size)
        //Keep only 1st PSM
        bestQueryPsm = queryPsms.head
      }

      //Save chosen one
      pepIdPerQueryThreshold += (entry._1 -> bestQueryPsm.peptideId)

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

  def getPeptideMatchValueForFiltering(pepMatch: PeptideMatch): Any = { 1 }

  def getThresholdValue(): Any = {
    pepIdPerQueryThreshold
  }

  def setThresholdValue(currentVal: Any): Unit = {
    if(classOf[Map[Long, Long]].isInstance(currentVal)) {
      val currentValAdMap = currentVal.asInstanceOf[Map[Long, Long]]
      useMapAsThreshold = true
      if(currentValAdMap.isEmpty)
        useMapAsThreshold = false
      pepIdPerQueryThreshold = currentValAdMap
    } else if(classOf[Int].isInstance(currentVal) && (currentVal.asInstanceOf[Int] == 1)) {
      pepIdPerQueryThreshold = Map.empty[Long, Long]
      useMapAsThreshold = false
    }
  }

}