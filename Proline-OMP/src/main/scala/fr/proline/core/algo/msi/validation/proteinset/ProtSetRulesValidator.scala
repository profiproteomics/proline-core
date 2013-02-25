package fr.proline.core.algo.msi.validation.proteinset

import com.weiglewilczek.slf4s.Logging
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import fr.proline.core.algo.msi.validation._
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.om.model.msi.ProteinSet
import fr.proline.core.om.model.msi.PeptideMatch
import fr.proline.core.algo.msi.filtering._
import fr.proline.core.algo.msi.scoring.IProteinSetScoreUpdater

class ProtSetRulesValidator(
  val protSetScoreUpdater: Option[IProteinSetScoreUpdater], // TODO: update score before
  val protSetFilterRule1: IOptimizableProteinSetFilter,
  val protSetFilterRule2: IOptimizableProteinSetFilter
) extends IProteinSetValidator with Logging {
  
  val expectedFdr: Option[Float] = None
  
  def validateProteinSets( targetRsm: ResultSummary, decoyRsm: Option[ResultSummary] ): ValidationResults = {
    
    // Retrieve some vars
    val targetProtSets = targetRsm.proteinSets
    val decoyProtSets = decoyRsm.map(_.proteinSets)
    val allProtSets = targetProtSets ++ decoyProtSets.getOrElse(Array())
    
    // Update score of protein sets
    protSetScoreUpdater.get.updateScoreOfProteinSets( targetRsm )
    if (decoyRsm.isDefined) protSetScoreUpdater.get.updateScoreOfProteinSets( decoyRsm.get )    
    
    // Partition protein sets by considering the number of identified peptides
    val(singlePepProtSets,multiPepProtSets) = allProtSets.partition( _.peptideSet.items.length == 1)
    
    // Validate protein sets identified with a single peptide 
    protSetFilterRule1.filterProteinSets(singlePepProtSets,true,true)
    
    // Validate protein sets identified with multiple peptides
    protSetFilterRule2.filterProteinSets(multiPepProtSets,true,true)
    
    // Compute validation result
    val valResult = this.computeValidationResult(targetRsm, decoyRsm)
    
    // Update validation result properties
    valResult.addProperties(
      Map(
        ValidationPropertyKeys.RULE_1_THRESHOLD_VALUE -> protSetFilterRule1.getThresholdValue,
        ValidationPropertyKeys.RULE_2_THRESHOLD_VALUE -> protSetFilterRule2.getThresholdValue
      )
    )
    
    // Return validation results
    ValidationResults( valResult )
  }
  
}