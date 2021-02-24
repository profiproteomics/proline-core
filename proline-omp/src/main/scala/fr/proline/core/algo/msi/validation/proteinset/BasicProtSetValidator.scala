package fr.proline.core.algo.msi.validation.proteinset

import com.typesafe.scalalogging.LazyLogging
import fr.proline.core.algo.msi.filtering._
import fr.proline.core.algo.msi.validation._
import fr.proline.core.om.model.msi.ResultSummary


/**
 * This Validator will filter all ProteinSet from target and decoy using specified IProteinSetFilter
 * A ValidationResult will be created by counting # valid target ProteinSet,  # valid decoy ProteinSet
 * and calculating a FDR using : 100 *  # valid decoy ProteinSet / # valid target ProteinSet
 * 
 */ 
class BasicProtSetValidator( val validationFilter: IProteinSetFilter ) extends IProteinSetValidator with LazyLogging {

  override def validatorName: String = "Basic protein set validator"
  override def validatorDescription: String = "Basic protein set validator method"

  def filterParameter = validationFilter.filterParameter
  def filterDescription = validationFilter.filterDescription
  def getFilterProperties = validationFilter.getFilterProperties
  
  val expectedFdr = Option.empty[Float]

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
    val valResult = this.computeValidationResult(targetRsm, decoyRsm, tdAnalyzer)
    
    // Update validation result properties
    valResult.addProperties(validationFilter.getFilterProperties)
    
    // Return validation results
    ValidationResults( valResult )
  }
  
}