package fr.proline.core.algo.msi.validation.pepmatch

import com.typesafe.scalalogging.LazyLogging
import fr.proline.core.algo.msi.validation._
import fr.proline.core.om.model.msi.PeptideMatch
import fr.proline.core.algo.msi.filtering._

/** Implementation of IPeptideMatchValidator performing a target/decoy analysis with a FDR optimization procedure. */
class TDPepMatchValidatorWithFDROptimization(  
  val validationFilter: IOptimizablePeptideMatchFilter,
  val expectedFdr: Option[Float],
  var tdAnalyzer: Option[ITargetDecoyAnalyzer]  
) extends IPeptideMatchValidator with LazyLogging {
  
  require( tdAnalyzer.isDefined, "a target/decoy analyzer must be provided")
  
  def validatePeptideMatches( pepMatches: Seq[PeptideMatch], decoyPepMatches: Option[Seq[PeptideMatch]] = None ): ValidationResults = {
    require(decoyPepMatches.isDefined, "decoy peptide matches must be provided")
    
    val rocPoints = tdAnalyzer.get.performROCAnalysis(pepMatches, decoyPepMatches.get, validationFilter)
    
    if( rocPoints.length == 0 ) {
      this.logger.warn("ROC analysis returned empty results, validation filter has not been applied")
      ValidationResults(ValidationResult(0,None,None))
    } else {
      
      val expectedFdrValue = expectedFdr.get
      val fdrThreshold = 0.95 * expectedFdrValue
      
      // Retrieve the nearest ROC point of the expected FDR and associated threshold
      val rocPointsWithDefinedFdr = rocPoints.filter(_.fdr.isDefined)
      val rocPointsLowerThanExpectedFdr = rocPointsWithDefinedFdr.filter( _.fdr.get < expectedFdrValue )
      val rocPointsNearExpectedFdr = rocPointsLowerThanExpectedFdr.filter( _.fdr.get >= fdrThreshold )
      
      // If we have defined ROC points lower than expected FDR and higher than 0.95 of this value
      val expectedRocPoint = if (rocPointsNearExpectedFdr.isEmpty == false) {
        // Select the ROC point with maximum number of target matches
        rocPointsNearExpectedFdr.maxBy( _.targetMatchesCount )
      }
      else {
        val filteredRocPoints = if (rocPointsLowerThanExpectedFdr.isEmpty) rocPointsWithDefinedFdr
        else rocPointsLowerThanExpectedFdr
        
        // If filteredRocPoints is empty => NO Valid FDR
        if (filteredRocPoints.isEmpty) null
        else {
        // Else if no FDR lower than expected one => retrieve the nearest FDR compared to the expected value
          filteredRocPoints.reduce { (a, b) => if ((a.fdr.get - expectedFdrValue).abs < (b.fdr.get - expectedFdrValue).abs) a else b }
        }
      }
      
      if (expectedRocPoint != null) {

        this.logger.debug("Effective FDR of expected ROC point = " + expectedRocPoint.fdr)

        val threshToApply = expectedRocPoint.properties.get(FilterPropertyKeys.THRESHOLD_VALUE)
        this.logger.debug("Threshold value of expected ROC point = " + threshToApply)

        // Update threshold and apply validation filter with this final value
        validationFilter.setThresholdValue(threshToApply.asInstanceOf[AnyVal])
        validationFilter.filterPeptideMatches( (pepMatches ++ decoyPepMatches.get), true, true)

        return ValidationResults(expectedRocPoint, Some(rocPoints))
      } else {
        return ValidationResults(null, Some(rocPoints))
      }
    }
    
  }
}