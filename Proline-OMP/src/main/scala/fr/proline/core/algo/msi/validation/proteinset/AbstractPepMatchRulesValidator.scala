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
  
  val pepMatchFilterRule1: IOptimizablePeptideMatchFilter
  val pepMatchFilterRule2: IOptimizablePeptideMatchFilter
  
  val validationRules = Array( ValidationRule(1,pepMatchFilterRule1), ValidationRule(2,pepMatchFilterRule2) )
  
  protected def _validateProteinSets(
    proteinSets: Seq[ProteinSet],
    bestPepMatchesByPepSetId: Map[Long,Array[PeptideMatch]],
    rules: Array[ValidationRule] ): Unit = {
    
    for( proteinSet <- proteinSets ) {
      
      val bestPepMatches = bestPepMatchesByPepSetId(proteinSet.peptideSet.id)      
      var isProteinSetValid = false
      
      for( rule <- rules ) {
        
        // Count the number of valid peptide matches
        val validPepMatchesCount = bestPepMatches.count( rule.pepMatchFilter.isPeptideMatchValid( _ ) )
        
        // Check if we have enough valid peptide matches
        if( validPepMatchesCount >= rule.minPeptideCount ) isProteinSetValid = true
      }
      
      // Update protein set validity
      if( isProteinSetValid == false ) proteinSet.isValidated = false
      
    }
    
  }
  
//  
//  private def userValidationParamsToValidationRules( validationParams: UserValidationParams ): Array[ValidationRule] = {
//    
//    val minPepSeqLength = validationParams.minPepSeqLength
//    val valProps = validationParams.properties.get
//    val pValueRule1 = valProps("p_value_rule_1").asInstanceOf[Float]
//    val pValueRule2 = valProps("p_value_rule_2").asInstanceOf[Float]
//    
//    Array( ValidationRule( 1, pValueRule1, minPepSeqLength ),
//           ValidationRule( 2, pValueRule2, minPepSeqLength )
//         )
//  }

//  def validateWithUserParams( validationParams: UserValidationParams, rsm: ResultSummary ): ValidationResults = {
//    
//    val validationRules = this.userValidationParamsToValidationRules( validationParams )    
//    val validProtSets = this.validateProteinSets( rsm.proteinSets, rsm.getBestPepMatchesByProtSetId, validationRules )
//    
//    val expectedRocPoint = ValidationResult( validProtSets.length )
//    
//    ValidationResults( expectedRocPoint, None )
//    
//  }
//  
//  def validateWithUserParams( validationParams: UserValidationParams,
//                              targetRsm: ResultSummary,
//                              decoyRsm: ResultSummary ): ValidationResults = {
//    
//    val validationRules = this.userValidationParamsToValidationRules( validationParams )
//                           
//    val validTargetProtSets = this.validateProteinSets( targetRsm.proteinSets, targetRsm.getBestPepMatchesByProtSetId, validationRules )
//    val validDecoyProtSets = this.validateProteinSets( decoyRsm.proteinSets, decoyRsm.getBestPepMatchesByProtSetId, validationRules )
//    
//    val( nbTargetMatches, nbDecoyMatches ) = ( validTargetProtSets.length, validDecoyProtSets.length )    
//    val fdr = (100 * nbDecoyMatches).toFloat / nbTargetMatches
//    
//    val expectedResult = ValidationResult( nbTargetMatches, Some(nbTargetMatches), Some(fdr) )    
//    ValidationResults( expectedResult, None )
//
//  }
}