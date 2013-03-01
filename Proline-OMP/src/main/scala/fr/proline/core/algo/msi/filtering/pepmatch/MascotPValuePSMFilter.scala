package fr.proline.core.algo.msi.filtering.pepmatch


import scala.collection.mutable.HashMap
import scala.collection.Seq
import fr.proline.core.algo.msi.validation.MascotValidationHelper
import fr.proline.core.om.model.msi.{PeptideMatch}
import com.weiglewilczek.slf4s.Logging
import fr.proline.core.om.model.msi.MsQueryDbSearchProperties
import scala.collection.script.Reset
import fr.proline.core.om.model.msi.MsQueryProperties
import fr.proline.core.algo.msi.filtering.PepMatchFilterParams
import fr.proline.core.algo.msi.filtering.IOptimizablePeptideMatchFilter
import fr.proline.core.algo.msi.filtering.FilterPropertyKeys
import fr.proline.core.algo.msi.filtering.PeptideMatchFiltering

// TODO: use MascotThresholdTypes enumeration value instead of useHomologyThreshold
// TODO: usefilterPeptideMatchesDBO
class MascotPValuePSMFilter(var pValue: Float = 0.05f, var useHomologyThreshold : Boolean = false, var pValueStartValue : Float = 0.05f) extends IOptimizablePeptideMatchFilter with Logging {

  var pValuethresholdIncreaseValue : Float = 0.001f 
  val filterParameter = if(useHomologyThreshold) PepMatchFilterParams.SCORE_HT_PVALUE.toString else PepMatchFilterParams.SCORE_IT_PVALUE.toString
  
  val filterDescription =if(useHomologyThreshold) "peptide match Mascot homology thresholds filter using p-value" else  "peptide match identity threshold filter using p-value"
  
  def getPeptideMatchValueForFiltering(pepMatch: PeptideMatch): AnyVal = pepMatch.score
  
  def isPeptideMatchValid( pepMatch: PeptideMatch ): Boolean = {
    throw new Exception("Not Yet Implemented") 
  }
  
  def filterPeptideMatches( pepMatches: Seq[PeptideMatch], incrementalValidation: Boolean, traceability: Boolean ): Unit = {
    
    // Reset validation status if validation is not incremental
    if( !incrementalValidation ) PeptideMatchFiltering.resetPepMatchValidationStatus(pepMatches)
    
    val pepMatchesByMsqId = pepMatches.groupBy(_.msQueryId)
    pepMatchesByMsqId.foreach(entry => {
      if(entry._2 != null && entry._2(0).msQuery.properties.isDefined){
        val msQProp : MsQueryProperties =entry._2(0).msQuery.properties.get
        
       //---- get Threshold ------
       var targetTh = 0.0
       var decoyTh = 0.0

        //Infer IT 
        if(!msQProp.getTargetDbSearch.isDefined){
          logger.warn(" UNABLE TO CALCULATE P VALUE  !getTargetDbSearch!"+entry._2(0).msQueryId)
        } else {
          //WARNING !!!!! If no decoy prop suppose same as target 
            val tRSCandPSM = msQProp.getTargetDbSearch.get.getCandidatePeptidesCount 
            val dRSCandPSM = if(msQProp.getDecoyDbSearch.isDefined) msQProp.getDecoyDbSearch.get.getCandidatePeptidesCount else tRSCandPSM
             if(!useHomologyThreshold){
        	targetTh = MascotValidationHelper.calcIdentityThreshold(tRSCandPSM, pValue)
                decoyTh = MascotValidationHelper.calcIdentityThreshold(dRSCandPSM, pValue)
            } else {
        	//Infer HT 
                if(!msQProp.getTargetDbSearch.get.getMascotHomologyThreshold.isDefined){
                  logger.warn(" ------ UNABLE TO CALCULATE P VALUE  getMascotHomologyThreshold !!"+entry._2(0).msQueryId)
                }else {
                  
                    val tRs_ht0_05: Float =  msQProp.getTargetDbSearch.get.getMascotHomologyThreshold.get 
                    val dRs_ht0_05: Float = if ( msQProp.getDecoyDbSearch.isDefined && msQProp.getDecoyDbSearch.get.getMascotHomologyThreshold.isDefined) msQProp.getDecoyDbSearch.get.getMascotHomologyThreshold.get else tRs_ht0_05
            
        	    val targetHtProbCstValue = MascotValidationHelper.calcCandidatePeptidesCount( tRs_ht0_05, 0.05 )
        	    targetTh = MascotValidationHelper.calcIdentityThreshold( targetHtProbCstValue, pValue )
        	    val decoyHtProbCstValue = MascotValidationHelper.calcCandidatePeptidesCount( dRs_ht0_05, 0.05 )
        	    decoyTh =  MascotValidationHelper.calcIdentityThreshold( decoyHtProbCstValue, pValue )
                }
          } //ERnd use IT or HT                     
        } // END NO TargetDbSearch properties
        logger.debug("\tQID\t"+entry._2(0).msQueryId+"\t"+ targetTh+"\t"+decoyTh);
        //---- Apply Threshold ------
        entry._2.foreach(psm => {
          if(psm.isDecoy)
            psm.isValidated = psm.score >= decoyTh
          else
            psm.isValidated = psm.score >= targetTh
        })
        
      } // PSM for query exist
      
    })//End go through Map QID->[PSM]
    
  }
  
  def filterPeptideMatchesDBO( pepMatches: Seq[PeptideMatch], incrementalValidation: Boolean, traceability: Boolean ): Unit = {
    
    // Reset validation status if validation is not incremental
    if( !incrementalValidation ) PeptideMatchFiltering.resetPepMatchValidationStatus(pepMatches)
    
    // Thresholds are computed for default pValue (0.05)
    val mascotThresholdsByPmId = MascotValidationHelper.getMascotThresholdsByPepMatchId(pepMatches)
    
    // Compute score threshold offset for the current pValue
    val scoreThreshOffset = MascotValidationHelper.calcScoreThresholdOffset(pValue,0.05) // positive if pValue < 0.05
    
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
      if( pepMatch.score < threshold ) pepMatch.isValidated = false
      
    }
  }
  
  def sortPeptideMatches( pepMatches: Seq[PeptideMatch] ): Seq[PeptideMatch] = {
    pepMatches.sortWith( _.score > _.score )
  }

  def getFilterProperties(): Map[String, Any] = {
    val props = new HashMap[String, Any]
   props += (FilterPropertyKeys.THRESHOLD_VALUE -> pValue )
    props.toMap
  }

  def getNextValue( currentVal: AnyVal ) = currentVal.asInstanceOf[Float] + pValuethresholdIncreaseValue
  
  def getThresholdStartValue(): AnyVal = pValueStartValue
  
  def getThresholdValue(): AnyVal = pValue
  
  def setThresholdValue( currentVal: AnyVal ){
    pValue = currentVal.asInstanceOf[java.lang.Number].asInstanceOf[Float]
  }
}