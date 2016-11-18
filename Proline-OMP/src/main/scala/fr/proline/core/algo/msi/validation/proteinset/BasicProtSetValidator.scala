package fr.proline.core.algo.msi.validation.proteinset

import com.fasterxml.jackson.annotation.JsonInclude
import com.typesafe.scalalogging.LazyLogging

import fr.proline.core.algo.msi.filtering.IProteinSetFilter
import fr.proline.core.algo.msi.filtering.ProteinSetFiltering
import fr.proline.core.algo.msi.validation.IProteinSetValidator
import fr.proline.core.algo.msi.validation.TargetDecoyModes
import fr.proline.core.algo.msi.validation.ValidationResults
import fr.proline.core.om.model.msi.ResultSummary

/**
 * This Validator will filter all ProteinSet from target and decoy using specified IProteinSetFilter
 * A ValidationResult will be created by counting # valid target ProteinSet,  # valid decoy ProteinSet
 * and calculating a FDR using : 100 *  # valid decoy ProteinSet / # valid target ProteinSet
 * 
 */ 
class BasicProtSetValidator( val validationFilter: IProteinSetFilter ) extends IProteinSetValidator with LazyLogging {
  
  def filterParameter = validationFilter.filterParameter
  def filterDescription = validationFilter.filterDescription
  def getFilterProperties = validationFilter.getFilterProperties
  
  val expectedFdr = Option.empty[Float]
  var targetDecoyMode = Option.empty[TargetDecoyModes.Value]
  
  def validateProteinSets( targetRsm: ResultSummary, decoyRsm: Option[ResultSummary] ): ValidationResults = {
    
    // Retrieve some vars
    val targetProtSets = targetRsm.proteinSets
    val decoyProtSets = decoyRsm.map(_.proteinSets)
    val allProtSets = targetProtSets ++ decoyProtSets.getOrElse(Array())
    
    // Filter protein sets
    validationFilter.filterProteinSets( allProtSets, true, true )
    
    // Update validatedProteinSetsCount of peptide instances
    ProteinSetFiltering.updateValidatedProteinSetsCount(allProtSets)

    
    // Compute validation result
    val valResult = this.computeValidationResult(targetRsm, decoyRsm)
    
    // Update validation result properties
    valResult.addProperties( validationFilter.getFilterProperties )
    
    // Return validation results
    ValidationResults( valResult )
  }
  
}