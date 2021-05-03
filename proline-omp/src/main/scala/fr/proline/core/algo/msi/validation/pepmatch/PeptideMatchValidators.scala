package fr.proline.core.algo.msi.validation.pepmatch

import com.typesafe.scalalogging.LazyLogging
import fr.proline.core.algo.msi.filtering.pepmatch.ScorePSMFilter
import fr.proline.core.algo.msi.validation._
import fr.proline.core.om.model.msi.PeptideMatch
import fr.proline.core.algo.msi.filtering.{FilterPropertyKeys, IOptimizablePeptideMatchFilter, IPeptideMatchFilter, PepMatchFilterParams}

import scala.util.Try


/** Implementation of IPeptideMatchValidator performing a BH analysis. */

class BHPepMatchValidator(
    val expectedFdr: Option[Float]
) extends IPeptideMatchValidator {

  val validatorName = "BH peptide match validator"
  val validatorDescription = "Peptide match validator optimizing FDR value based on BH"

  val validationFilter = new ScorePSMFilter()

  def validatePeptideMatches( pepMatches: Seq[PeptideMatch], decoyPepMatches: Option[Seq[PeptideMatch]] = None, tdAnalyzer: Option[ITargetDecoyAnalyzer] = None): ValidationResults = {

    val bhFilter = BuildPeptideMatchFilter(PepMatchFilterParams.BH_AJUSTED_PVALUE.toString, expectedFdr.get/100.0f)

    // Apply validation filter
    val allPeptideMatches = pepMatches ++ decoyPepMatches.getOrElse(Seq.empty[PeptideMatch])
    bhFilter.filterPeptideMatches(allPeptideMatches, true, true)

    validationFilter.setThresholdValue(allPeptideMatches.filter(_.isValidated).map(_.score).min)

    // Calculate statistics after filter have been applied
    val valResult = ValidationResult( targetMatchesCount = pepMatches.count(_.isValidated), fdr = expectedFdr )

    // Add validation filter properties to validation results
    valResult.addProperties( validationFilter.getFilterProperties )

    // Return validation results
    ValidationResults( valResult )
  }

}

/** Implementation of IPeptideMatchValidator performing a simple target/decoy analysis. */

class TDPepMatchValidator(
  val validationFilter: IPeptideMatchFilter
) extends IPeptideMatchValidator {

  val validatorName = "Peptide match validator"
  val validatorDescription = "Peptide match validator that count the number of validated target and decoy matches "
  val expectedFdr: Option[Float] = None

  def validatePeptideMatches( pepMatches: Seq[PeptideMatch], decoyPepMatches: Option[Seq[PeptideMatch]] = None, tdAnalyzer: Option[ITargetDecoyAnalyzer] = None): ValidationResults = {
        
    // Apply validation filter
    validationFilter.filterPeptideMatches(pepMatches ++ decoyPepMatches.getOrElse(Seq.empty[PeptideMatch]), true, true)
    
    // Calculate statistics after filter have been applied
    val valResult = if (decoyPepMatches.isDefined && tdAnalyzer.isDefined) {
      tdAnalyzer.get.calcTDStatistics(pepMatches, decoyPepMatches.get)
    } else {
      ValidationResult( pepMatches.count(_.isValidated), Try(decoyPepMatches.get.count(_.isValidated)).toOption)
    }
    
    // Add validation filter properties to validation results
    valResult.addProperties( validationFilter.getFilterProperties )

    // Return validation results
    ValidationResults( valResult )
  }
  
}


/**
 * Implementation of IPeptideMatchValidator performing a target/decoy analysis with a FDR optimization procedure.
 *
 **/

class TDPepMatchValidatorWithFDROptimization(
    val validationFilter: IOptimizablePeptideMatchFilter,
    val expectedFdr: Option[Float]
) extends IPeptideMatchValidator with LazyLogging {

  val validatorName = "TD Peptide match validator"
  val validatorDescription = "Peptide match validator optimizing FDR value based on Target/Decoy counts"


  def validatePeptideMatches( pepMatches: Seq[PeptideMatch], decoyPepMatches: Option[Seq[PeptideMatch]] = None, tdAnalyzer: Option[ITargetDecoyAnalyzer] = None): ValidationResults = {

    require(tdAnalyzer.isDefined, "a target/decoy analyzer must be provided")
    require(decoyPepMatches.isDefined, "decoy peptide matches must be provided")

    val expectedFdrValue = expectedFdr.get

    //Start by verifying fdr is not already less than expected one
    val initialValidationResult =  tdAnalyzer.get.calcTDStatistics(pepMatches.filter(_.isValidated), decoyPepMatches.get.filter(_.isValidated))
    if(initialValidationResult.fdr.isDefined && initialValidationResult.fdr.get < expectedFdrValue) {
      //no need to perform ROC Analysis, expected already reached
      return  ValidationResults(null, None)
    }

    val rocPoints : Array[ValidationResult] = tdAnalyzer.get.performROCAnalysis(pepMatches, decoyPepMatches.get, validationFilter)

    if( rocPoints.length == 0 ) {
      this.logger.warn("ROC analysis returned empty results, validation filter has not been applied")
      ValidationResults(ValidationResult(pepMatches.count(_.isValidated),Some(decoyPepMatches.get.count(_.isValidated)),None))
    } else {


      val fdrThreshold = 0.95 * expectedFdrValue

      // Retrieve the nearest ROC point of the expected FDR and associated threshold
      val rocPointsWithDefinedFdr = rocPoints.filter(_.fdr.isDefined)
      val rocPointsLowerThanExpectedFdr = rocPointsWithDefinedFdr.filter( _.fdr.get < expectedFdrValue )
      val rocPointsNearExpectedFdr = rocPointsLowerThanExpectedFdr.filter( _.fdr.get >= fdrThreshold )

      // If we have defined ROC points lower than expected FDR and higher or equal than 0.95*expectedFDR
      val expectedRocPoint = if (!rocPointsNearExpectedFdr.isEmpty) {
        // Select the ROC point with maximum number of target matches
        rocPointsNearExpectedFdr.maxBy( _.targetMatchesCount )
      } else {
        val filteredRocPoints = if (rocPointsLowerThanExpectedFdr.isEmpty) rocPointsWithDefinedFdr else rocPointsLowerThanExpectedFdr

        logger.debug(" Do we choose fdr from ALL roc points with defined fdr: "+rocPointsLowerThanExpectedFdr.isEmpty)

        // If filteredRocPoints is empty => NO Valid FDR
        if (filteredRocPoints.isEmpty) null
        else {
          // Retrieve the nearest FDR compared to the expected value from (ALL) or (Lower than expected) depending if (lower than expect) is empty
          //VDS get the one maximizing the nbr of target if search in rocPointsLowerThanExpectedFdr
          val reducesRP: ValidationResult = filteredRocPoints.reduce { (a, b) => if ((a.fdr.get - expectedFdrValue).abs < (b.fdr.get - expectedFdrValue).abs) a else b }
          logger.debug("  search nearest FDR "+reducesRP)
          reducesRP
        }
      }

      if (expectedRocPoint != null) {

        this.logger.debug("Effective FDR of expected ROC point = " + expectedRocPoint.fdr)

        val threshToApply = expectedRocPoint.properties.get(FilterPropertyKeys.THRESHOLD_VALUE)
        this.logger.debug("Threshold value of expected ROC point = " + threshToApply)

        // Update threshold and apply validation filter with this final value
        validationFilter.setThresholdValue(threshToApply.asInstanceOf[AnyVal])
        validationFilter.filterPeptideMatches( (pepMatches ++ decoyPepMatches.get), true, true)

        ValidationResults(expectedRocPoint, Some(rocPoints))
      } else {
        ValidationResults(null, Some(rocPoints))
      }
    }

  }
}