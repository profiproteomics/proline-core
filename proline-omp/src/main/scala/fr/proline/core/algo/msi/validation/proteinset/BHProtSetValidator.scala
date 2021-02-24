package fr.proline.core.algo.msi.validation.proteinset

import com.typesafe.scalalogging.LazyLogging
import fr.proline.core.algo.msi.filtering._
import fr.proline.core.algo.msi.validation._
import fr.proline.core.om.model.msi.ResultSummary


/**
 * This Validator will filter all ProteinSet from a IProteinSetFilter
 * A ValidationResult will be created by using the expected fdr value
 * 
 */ 
class BHProtSetValidator(
    val validationFilter: IProteinSetFilter,
    val expectedFdr: Option[Float]
) extends IProteinSetValidator with LazyLogging {

  override def validatorName: String = "BH protein set validator"
  override def validatorDescription: String = "protein set validator optimizing FDR value based on BH"
  def filterParameter = validationFilter.filterParameter
  def filterDescription = validationFilter.filterDescription
  def getFilterProperties = validationFilter.getFilterProperties
  
  def validateProteinSets( targetRsm: ResultSummary, decoyRsm: Option[ResultSummary], tdAnalyzer: Option[ITargetDecoyAnalyzer]): ValidationResults = {
    
    // Retrieve some vars
    val targetProtSets = targetRsm.proteinSets
    val decoyProtSets = decoyRsm.map(_.proteinSets)
    val allProtSets = targetProtSets ++ decoyProtSets.getOrElse(Array())
    
    // Filter protein sets
    validationFilter.filterProteinSets(allProtSets, true, true)
    
    // Update validatedProteinSetsCount of peptide instances
    ProteinSetFiltering.updateValidatedProteinSetsCount(targetProtSets)
    decoyProtSets.map(ProteinSetFiltering.updateValidatedProteinSetsCount(_))
    
    // Compute validation result
    val valResult = ValidationResult(
      targetMatchesCount = targetProtSets.count(_.isValidated),
      decoyMatchesCount = None,
      fdr = expectedFdr
    )
    
    // Update validation result properties
    valResult.addProperties(validationFilter.getFilterProperties)
    
    // Return validation results
    ValidationResults( valResult )
  }
  
}