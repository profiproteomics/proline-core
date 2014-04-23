package fr.proline.core.algo.msi.validation.pepmatch

import com.typesafe.scalalogging.slf4j.Logging
import fr.proline.core.algo.msi.validation._
import fr.proline.core.om.model.msi.PeptideMatch
import fr.proline.core.algo.msi.filtering._

/** Implementation of IPeptideMatchValidator performing a target/decoy analysis with a FDR optimization procedure. */
class TDPepMatchValidatorWithFDROptimization(  
  val validationFilter: IOptimizablePeptideMatchFilter,
  val expectedFdr: Option[Float],
  var tdAnalyzer: Option[ITargetDecoyAnalyzer]  
) extends IPeptideMatchValidator with Logging {
  
  require( tdAnalyzer.isDefined, "a target/decoy analyzer must be provided")
  
  def validatePeptideMatches( pepMatches: Seq[PeptideMatch], decoyPepMatches: Option[Seq[PeptideMatch]] = None ): ValidationResults = {
    require(decoyPepMatches.isDefined, "decoy peptide matches must be provided")
    
    val rocPoints = tdAnalyzer.get.performROCAnalysis(pepMatches, decoyPepMatches.get, validationFilter)
    
    if( rocPoints.length == 0 ) {
      this.logger.warn("ROC analysis returned empty results, validation filter has not been applied")
      ValidationResults(ValidationResult(0,None,None))
    } else {
      
      // Retrieve the nearest ROC point of the expected FDR and associated threshold
      var filteredRocPoint = rocPoints.filter( rocP => {rocP.fdr.isDefined && rocP.fdr.get < expectedFdr.get})
      val expectedRocPoint = if(filteredRocPoint !=null && filteredRocPoint.size >1) {
    	 filteredRocPoint.reduce { (a, b) => if ((a.fdr.get - expectedFdr.get).abs < (b.fdr.get - expectedFdr.get).abs) a else b }
      	} else {
      	  filteredRocPoint = rocPoints.filter( rocP => {rocP.fdr.isDefined })      
      	  if(filteredRocPoint !=null && filteredRocPoint.size >1) {
      		  filteredRocPoint.reduce { (a, b) => if ((a.fdr.get - expectedFdr.get).abs < (b.fdr.get - expectedFdr.get).abs) a else b }
      	  } else {
      	    //NO Valid FDR
      	    null
      	  }
      	}
      	
      	
      if(expectedRocPoint != null){
      
    	  this.logger.debug("Effective FDR of expected ROC point = " + expectedRocPoint.fdr)
      
    	  val threshToApply = expectedRocPoint.properties.get(FilterPropertyKeys.THRESHOLD_VALUE)
    	  this.logger.debug("Threshold value of expected ROC point = " + threshToApply)
      
      // 	Update threshold and apply validation filter with this final value
    	  validationFilter.setThresholdValue(threshToApply.asInstanceOf[AnyVal])
    	  validationFilter.filterPeptideMatches(pepMatches ++ decoyPepMatches.get, true, true)
      
    	 return  ValidationResults(expectedRocPoint, Some(rocPoints))    	 
      } else {
         return  ValidationResults(null, Some(rocPoints))    	 
      }
    }
    
  }
}