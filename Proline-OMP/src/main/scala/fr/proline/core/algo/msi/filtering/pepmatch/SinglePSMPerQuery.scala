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

class SinglePSMPerQuery(var targetRSSet: ResultSet = null) extends IPeptideMatchFilter with Logging with IFilterNeedingResultSet {

  val filterParameter = PepMatchFilterParams.SINGLE_PSM_PER_QUERY.toString
  val filterDescription = "single peptide match per query filter using score and peptide matches count per protein values"

  def setTargetRS(targetRS: ResultSet)  {
    targetRSSet = targetRS
  }

  def filterPeptideMatches(pepMatches: Seq[PeptideMatch], incrementalValidation: Boolean, traceability: Boolean): Unit = {
    require(targetRSSet == null, " Target Search Result Should be specified before running this filter.")
    val psmPerQuery = pepMatches.groupBy(_.msQueryId)

    // Reset validation status if validation is not incremental
    if (!incrementalValidation) PeptideMatchFiltering.resetPepMatchValidationStatus(pepMatches)

    //For each query find a unique PSM. Used ruels:
    // - PSM with best score
    // - if equality : PSM which identify ProtMatches with higher number of valid PSMs
    val startTime = System.currentTimeMillis()
    logger.debug("Start creating Maps")
    val pepMatchesPerProtMatch: Map[ProteinMatch, ArrayBuffer[PeptideMatch]] = targetRSSet.getPeptideMatchesByProteinMatch

    //Create reverse map 
    val protMatchesPerPepMatchIdBuillder = new HashMap[Long, ArrayBuffer[ProteinMatch]]()
    pepMatchesPerProtMatch.foreach(entry => {
      val pepMatches = entry._2
      pepMatches.foreach(pepMatch => {
        val protArrays = protMatchesPerPepMatchIdBuillder.getOrElseUpdate(pepMatch.id, new ArrayBuffer[ProteinMatch]())
        protArrays += entry._1
      })
    })

    val endTime = System.currentTimeMillis()
    logger.debug("END creating Maps "+(endTime-startTime))
    
    // Filter query per query
    psmPerQuery.foreach(entry => {
      var queryPsms = entry._2
      queryPsms = queryPsms.sortWith((a, b) => a.score > b.score).filter(_.isValidated)
      var bestQueryPsm: PeptideMatch = null

      if (queryPsms.size > 1 && queryPsms(0).score.equals(queryPsms(1))) {    	  
        //Same Score Should found other way to filter
        val equalsPSMs = queryPsms.filter(_.score.equals(queryPsms(0).score))
        
        logger.debug(" ------  Same Score Should found other way to filter QId "+entry._1+" size "+ equalsPSMs.size+" on "+queryPsms.size)
        
        equalsPSMs.foreach(currentPsm => {
          if (bestQueryPsm == null) {
            bestQueryPsm = currentPsm
          } else {
            // filter using ProteinMatch nbrPeptideCount.
            if (protMatchesPerPepMatchIdBuillder.get(currentPsm.id).isDefined &&
              (protMatchesPerPepMatchIdBuillder.get(bestQueryPsm.id).isEmpty
                || (protMatchesPerPepMatchIdBuillder.get(currentPsm.id).get.size > protMatchesPerPepMatchIdBuillder.get(bestQueryPsm.id).get.size)))
              bestQueryPsm = currentPsm
          }
        }) //end go throughall equals psms

      } else if (!queryPsms.isEmpty) {
        logger.debug(" ------ Multiple PSM but <> Score QId "+entry._1+" size "+ queryPsms.size)
        //Keep only 1st PSM
        bestQueryPsm = queryPsms(0)
      }

      //Invalid others PSMs
      queryPsms.foreach(currentPsm =>{         
        if(!currentPsm.equals(bestQueryPsm))
            currentPsm.isValidated = false
      })

    }) //end filter query per query

  }

  // No Specific threshold ... set to 1...
  def getFilterProperties(): Map[String, Any] = {
    Map.empty[String, Any]
  }

  def getPeptideMatchValueForFiltering(pepMatch: PeptideMatch): AnyVal = { 1 }

  def getThresholdValue(): AnyVal = 1

  def setThresholdValue(currentVal: AnyVal) {}

}