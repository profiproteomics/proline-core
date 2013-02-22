package fr.proline.core.algo.msi.validation.pepmatch

import math.abs
import com.weiglewilczek.slf4s.Logging
import fr.proline.core.algo.msi.validation._
import fr.proline.core.om.model.msi.PeptideMatch
import fr.proline.core.algo.msi.filter.IOptimizablePeptideMatchFilter
import fr.proline.core.algo.msi.filter.PepMatchFilterPropertyKeys

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
      val expectedRocPoint = rocPoints.reduce { (a, b) => if (abs(a.fdr.get - expectedFdr.get) < abs(b.fdr.get - expectedFdr.get)) a else b }
      val threshToApply = expectedRocPoint.properties.get(PepMatchFilterPropertyKeys.THRESHOLD_PROP_NAME)
      
      // Update threshold and apply validation filter with this final value
      validationFilter.setThresholdValue(threshToApply.asInstanceOf[AnyVal])
      validationFilter.filterPeptideMatches(pepMatches ++ decoyPepMatches.getOrElse(Seq()), true, true)
      
      ValidationResults(expectedRocPoint, Some(rocPoints))
    }
    
  }
}