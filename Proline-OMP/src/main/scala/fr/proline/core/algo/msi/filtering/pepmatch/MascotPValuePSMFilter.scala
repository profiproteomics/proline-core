package fr.proline.core.algo.msi.filtering.pepmatch

import com.typesafe.scalalogging.LazyLogging
import fr.profi.util.primitives._
import fr.proline.core.algo.msi.filtering.{FilterPropertyKeys, IOptimizablePeptideMatchFilter, PepMatchFilterParams, PeptideMatchFiltering}
import fr.proline.core.algo.msi.validation.MascotValidationHelper
import fr.proline.core.om.model.msi.{MsQueryProperties, PeptideMatch}

import scala.collection.Seq
import scala.collection.mutable.HashMap

// TODO: use MascotThresholdTypes enumeration value instead of useHomologyThreshold
// TODO: usefilterPeptideMatchesDBO
class MascotPValuePSMFilter(var pValue: Float = 0.05f, var useHomologyThreshold: Boolean = false, var pValueStartValue: Float = 0.5f) extends IOptimizablePeptideMatchFilter with LazyLogging {

  var maxPValuethresholdIncreaseValue: Float = 0.01f
  var minPValuethresholdIncreaseValue: Float = 0.001f
  
  val filterParameter = if (useHomologyThreshold) PepMatchFilterParams.SCORE_HT_PVALUE.toString else PepMatchFilterParams.SCORE_IT_PVALUE.toString

  val filterDescription = if (useHomologyThreshold) "peptide match Mascot homology thresholds filter using p-value" else "peptide match identity threshold filter using p-value"

  def getPeptideMatchValueForFiltering(pepMatch: PeptideMatch): Any = {
    if (pepMatch.msQuery.properties.isEmpty  ) {
    	logger.warn(" Filtering error, UNABLE TO CALCULATE P VALUE  (No msQuery.properties) for peptide " + pepMatch.id+" Use pValue = 0.05 ")
    	return 0.05
    }
    
    var pVal = 0.05
    var useTargetProp = !pepMatch.isDecoy
    
    if(!useTargetProp)  {
       if(pepMatch.msQuery.properties.get.getDecoyDbSearch.isEmpty ) {
		  logger.warn(" Filtering error,  UNABLE TO CALCULATE P VALUE  (No Decoy Search properties) for peptide  " + pepMatch.id+" Use associated target search properties if exist. ")
		  useTargetProp = true
		  
       } else {
		  val dRSCandPSM = pepMatch.msQuery.properties.get.getDecoyDbSearch.get.getCandidatePeptidesCount
		  pVal = MascotValidationHelper.calcProbability(pepMatch.score, dRSCandPSM)
	  }
    }
    
    if(useTargetProp)  {
    	if(pepMatch.msQuery.properties.get.getTargetDbSearch.isEmpty ) {
    		logger.warn("Filtering error, UNABLE TO CALCULATE P VALUE  (No Target Search properties ) for peptide  " + pepMatch.id+" Use pValue = 0.05 ")    		
    	} else {
		  val tRSCandPSM = pepMatch.msQuery.properties.get.getTargetDbSearch.get.getCandidatePeptidesCount
		  pVal = MascotValidationHelper.calcProbability(pepMatch.score, tRSCandPSM)
    	}
    }
		  
    pVal
  }

  def isPeptideMatchValid(pepMatch: PeptideMatch): Boolean = {
    throw new Exception("Not Yet Implemented")
  }

  def filterPeptideMatches(pepMatches: Seq[PeptideMatch], incrementalValidation: Boolean, traceability: Boolean): Unit = {

    // Reset validation status if validation is not incremental
    if (!incrementalValidation) PeptideMatchFiltering.resetPepMatchValidationStatus(pepMatches)

    val pepMatchesByMsqId = pepMatches.groupBy(_.msQueryId)
    pepMatchesByMsqId.foreach(entry => {
      if (entry._2 != null && entry._2.head.msQuery.properties.isDefined) {
        val msQProp: MsQueryProperties = entry._2.head.msQuery.properties.get

        //---- get Threshold ------
        var targetTh = 0.0
        var decoyTh = 0.0

        //Infer IT 
        if (msQProp.getTargetDbSearch.isEmpty) {
          logger.warn(" UNABLE TO CALCULATE P VALUE  !getTargetDbSearch!" + entry._2.head.msQueryId)
        } else {

          val thresholds = MascotValidationHelper.calcPeptideMatchTDThresholds(entry._2.head, pValue)

          if (!useHomologyThreshold) {
            targetTh = thresholds._1.identityThreshold
            decoyTh = thresholds._2.identityThreshold
          } else {
            targetTh = thresholds._1.homologyThreshold
            if (targetTh == 0) targetTh = thresholds._1.identityThreshold
            decoyTh = thresholds._2.homologyThreshold
            if (decoyTh == 0) decoyTh = thresholds._2.identityThreshold
          }

        } // END NO TargetDbSearch properties
//        logger.debug("\tQID\t"+entry._2(0).msQueryId+"\t"+ targetTh+"\t"+decoyTh);
        //---- Apply Threshold ------
        // !!! Should only set isValidated when false !
        entry._2.foreach(psm => {
          if (psm.isDecoy)
            psm.isValidated = psm.isValidated && psm.score >= decoyTh
          else
            psm.isValidated = psm.isValidated && psm.score >= targetTh
        })

      } // PSM for query exist

    }) //End go through Map QID->[PSM]

  }

  def filterPeptideMatchesDBO(pepMatches: Seq[PeptideMatch], incrementalValidation: Boolean, traceability: Boolean): Unit = {

    // Reset validation status if validation is not incremental
    if (!incrementalValidation) PeptideMatchFiltering.resetPepMatchValidationStatus(pepMatches)

    // Thresholds are computed for default pValue (0.05)
    val mascotThresholdsByPmId = MascotValidationHelper.getMascotThresholdsByPepMatchId(pepMatches)

    // Compute score threshold offset for the current pValue
    val scoreThreshOffset = MascotValidationHelper.calcScoreThresholdOffset(pValue, 0.05) // positive if pValue < 0.05

    pepMatches.foreach { pepMatch =>

      // Retrieve peptide match thresholds
      val pepMatchThresholds = mascotThresholdsByPmId(pepMatch.id)

      // TODO: handle lowest threshold case
      var threshold = if (useHomologyThreshold) {
        pepMatchThresholds.homologyThreshold
      } else {
        pepMatchThresholds.identityThreshold
      }

      // Add score threshold offset to the threshold value in order to consider the current pValue
      threshold += scoreThreshOffset

      // Updater status of peptide match if it is invalid
      if (pepMatch.score < threshold) pepMatch.isValidated = false

    }
  }

  def compare(a: PeptideMatch, b: PeptideMatch): Int = {
    b.score compare a.score
  }

  def getFilterProperties(): Map[String, Any] = {
    val props = new HashMap[String, Any]
    props += (FilterPropertyKeys.THRESHOLD_VALUE -> pValue)
    props.toMap
  }

  def getNextValue(currentVal: Any): Any = {
    if( toFloat(currentVal)<= 0.1f){
       toFloat(currentVal) - minPValuethresholdIncreaseValue
    } else {
      toFloat(currentVal)- maxPValuethresholdIncreaseValue      
    }
  }

  def getThresholdStartValue(): Any = pValueStartValue

  def getThresholdValue(): Any = pValue

  def setThresholdValue(currentVal: Any): Unit ={
    pValue = toFloat(currentVal)
  }
}