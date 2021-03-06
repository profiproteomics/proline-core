package fr.proline.core.algo.msi.validation.proteinset

import com.typesafe.scalalogging.LazyLogging
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import fr.proline.core.algo.msi.validation._
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.om.model.msi.ProteinSet
import fr.proline.core.om.model.msi.PeptideMatch
import fr.proline.core.algo.msi.filtering._

/**
 * This class will allow to filter ProteinSet with single peptide with an IProteinFilter (protSetFilterRule1) 
 * and others ProteinSets with an other one (protSetFilterRule2).
 * 
 */
class ProtSetRulesValidator(
  val protSetFilterRule1: IProteinSetFilter,
  val protSetFilterRule2: IProteinSetFilter
) extends IProteinSetValidator with LazyLogging {
  require( protSetFilterRule1.filterParameter == protSetFilterRule2.filterParameter )

  def validatorName: String = "Protein set rules validator"
  def validatorDescription: String = "Protein set rules validator"
  def filterParameter = protSetFilterRule1.filterParameter
  def filterDescription = protSetFilterRule1.filterDescription
  def getFilterProperties = {
    Map(
      ValidationPropertyKeys.RULE_1_THRESHOLD_VALUE -> protSetFilterRule1.getThresholdValue,
      ValidationPropertyKeys.RULE_2_THRESHOLD_VALUE -> protSetFilterRule2.getThresholdValue
    )
  }
  
  val expectedFdr = Option.empty[Float]

  def validateProteinSets(targetRsm: ResultSummary, decoyRsm: Option[ResultSummary], tdAnalyzer: Option[ITargetDecoyAnalyzer]): ValidationResults = {
    
    // Retrieve some vars
    val targetProtSets = targetRsm.proteinSets
    val decoyProtSets = decoyRsm.map(_.proteinSets)
    val allProtSets = targetProtSets ++ decoyProtSets.getOrElse(Array()) 
    
    // Partition protein sets by considering the number of identified peptides
    val(singlePepProtSets,multiPepProtSets) = allProtSets.partition( _.peptideSet.items.length == 1)
    
    // Validate protein sets identified with a single peptide 
    protSetFilterRule1.filterProteinSets(singlePepProtSets,true,true)
    
    // Validate protein sets identified with multiple peptides
    protSetFilterRule2.filterProteinSets(multiPepProtSets,true,true)
    
    // Update validatedProteinSetsCount of peptide instances
    ProteinSetFiltering.updateValidatedProteinSetsCount(targetProtSets)
    decoyProtSets.map(ProteinSetFiltering.updateValidatedProteinSetsCount(_))
    
    // Compute validation result
    val valResult = this.computeValidationResult(targetRsm, decoyRsm, tdAnalyzer)
    
    // Update validation result properties
    /*valResult.addProperties(
      Map(
        ValidationPropertyKeys.RULE_1_THRESHOLD_VALUE -> protSetFilterRule1.getThresholdValue,
        ValidationPropertyKeys.RULE_2_THRESHOLD_VALUE -> protSetFilterRule2.getThresholdValue
      )
    )*/
    
    // Return validation results
    ValidationResults( valResult )
  }
  
}