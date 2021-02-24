package fr.proline.core.algo.msi.validation.proteinset

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import fr.proline.core.algo.msi.validation._
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.om.model.msi.ProteinSet
import fr.proline.core.om.model.msi.PeptideMatch
import fr.proline.core.algo.msi.filtering._

case class ValidationRule( minPeptideCount: Int, pepMatchFilter: IOptimizablePeptideMatchFilter )

abstract class AbstractPepMatchRulesValidator extends IProteinSetValidator {

  override def validatorName: String = "Peptide match rules validator"
  override def validatorDescription = "Peptide match rules validator method"

  val pepMatchFilterRule1: IOptimizablePeptideMatchFilter
  val pepMatchFilterRule2: IOptimizablePeptideMatchFilter
  
  val validationRules = Array( ValidationRule(1,pepMatchFilterRule1), ValidationRule(2,pepMatchFilterRule2) )
  
  protected def _validateProteinSets(
    proteinSets: Seq[ProteinSet],
    bestPepMatchesByPepSetId: Map[Long,Array[PeptideMatch]],
    rules: Array[ValidationRule]
  ): Unit = {
    
    for( proteinSet <- proteinSets ) {
      
      val bestPepMatches = bestPepMatchesByPepSetId(proteinSet.peptideSet.id)
      var isProteinSetValid = false
      
      for (rule <- rules) {
        
        // Count the number of valid peptide matches
        val validPepMatchesCount = bestPepMatches.count( rule.pepMatchFilter.isPeptideMatchValid( _ ) )
        
        // Check if we have enough valid peptide matches
        if( validPepMatchesCount >= rule.minPeptideCount ) isProteinSetValid = true
      }
      
      // Update protein set validity
      if (isProteinSetValid == false) proteinSet.isValidated = false
      
    }
    
  }

}