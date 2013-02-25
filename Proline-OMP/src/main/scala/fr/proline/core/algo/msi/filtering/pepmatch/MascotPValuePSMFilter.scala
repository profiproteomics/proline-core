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

class MascotPValuePSMFilter(var pValue: Float = 0.05f, var useHomologyThreshold : Boolean = false, var pValueStartValue : Float = 0.05f) extends IOptimizablePeptideMatchFilter with Logging {

  var pValuethresholdIncreaseValue : Float = 0.001f 
  val filterParameter = if(useHomologyThreshold) PepMatchFilterParams.SCORE_HT_PVALUE.toString else PepMatchFilterParams.SCORE_IT_PVALUE.toString
            
  val filterDescription =if(useHomologyThreshold) "peptide match homology threshold filter using p-value" else  "peptide match identity threshold filter using p-value"
    
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
          logger.warn(" UNABLE TO CALCULATE P VALUE  !!")
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
                  logger.warn(" ------ UNABLE TO CALCULATE P VALUE  !!")
                }else {
                  
                    val tRs_ht0_5: Float =  msQProp.getTargetDbSearch.get.getMascotHomologyThreshold.get 
                    val dRs_ht0_5: Float = if ( msQProp.getDecoyDbSearch.isDefined && msQProp.getDecoyDbSearch.get.getMascotHomologyThreshold.isDefined) msQProp.getDecoyDbSearch.get.getMascotHomologyThreshold.get else tRs_ht0_5
            
        	    val targetHtProbCstValue = MascotValidationHelper.calcCandidatePeptidesCount( tRs_ht0_5, 0.5 )
        	    targetTh = MascotValidationHelper.calcIdentityThreshold( targetHtProbCstValue, pValue )
        	    val decoyHtProbCstValue = MascotValidationHelper.calcCandidatePeptidesCount( dRs_ht0_5, 0.5 )
        	    decoyTh =  MascotValidationHelper.calcIdentityThreshold( decoyHtProbCstValue, pValue )
                }
          } //ERnd use IT or HT                     
        } // END NO TargetDbSearch properties
        logger.debug(" --------------- QID "+entry._2(0).msQueryId+" => Tth "+ targetTh+" Dth ");
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
    pValue = currentVal.asInstanceOf[Float]
  }
}