package fr.proline.core.algo.msi.validation.protein_set

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap

import fr.proline.core.algo.msi.validation._
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.om.model.msi.ProteinSet
import fr.proline.core.om.model.msi.PeptideMatch

class MascotPeptideMatchRulesValidator extends IProteinSetValidator {
  
  case class ValidationRule( minPeptideCount: Int, var pValueThreshold: Float, minPepSeqLength: Int )
  
  def validateWithComputerParams( validationParams: ComputerValidationParams,
                                  targetRsm: ResultSummary,
                                  decoyRsm: ResultSummary
                                 ): ValidationResults = {
    
    var( validTargetProtSets, validDecoyProtSets ) = ( targetRsm.proteinSets, decoyRsm.proteinSets )
    val targetBestPepMatchesByProtSetId = targetRsm.getBestPepMatchesByProtSetId
    val decoyBestPepMatchesByProtSetId = decoyRsm.getBestPepMatchesByProtSetId
    
    // Retrieve some vars
    val wantedFdr = validationParams.wantedFdr
    val valResultProps = targetRsm.validationProperties("results").asInstanceOf[Map[String,Any]]
    val pepMatchValidationProps = valResultProps("peptide_matches").asInstanceOf[Map[String,Any]]
    val pValueThreshold = pepMatchValidationProps("p_value_threshold").asInstanceOf[Float]
    
    // TODO retrieve min seq length from params
    val validationRules = Array( ValidationRule( 1, pValueThreshold, validationParams.minPepSeqLength ),
                                 ValidationRule( 2, pValueThreshold, validationParams.minPepSeqLength )
                               )
    
    var currentFdr = 100.0f
    val maxFdr = wantedFdr * 1.2 // 20% of FDR
    var rocPoints = new ArrayBuffer[ValidationResult]
    var wantedRocPoint: ValidationResult = null     
    
    while( currentFdr > 0 && wantedRocPoint == null ) {
      
      println( "LOOP 1 (single peptide rule)" )
      println( "single pep rule p-value threshold: " + validationRules(0).pValueThreshold )
      
      validTargetProtSets = this.validateProteinSets( validTargetProtSets, targetBestPepMatchesByProtSetId, Array(validationRules(0)) )
      val targetValidProteinSetCount = validTargetProtSets.length
      println( targetValidProteinSetCount + " target" )
      
      validDecoyProtSets = this.validateProteinSets( validDecoyProtSets, decoyBestPepMatchesByProtSetId, Array(validationRules(0)) )
      val decoyValidProteinSetCount = validDecoyProtSets.length
      println( decoyValidProteinSetCount + " decoy" )
      
      currentFdr = 100 * decoyValidProteinSetCount / targetValidProteinSetCount
      println( "current fdr: " + currentFdr )
      
      if( currentFdr <= wantedFdr ) {
        
        // Reset the p-value threshold of the two peptides rule
        validationRules(1).pValueThreshold = pValueThreshold
        
        // Reset other temp vars
        rocPoints = new ArrayBuffer[ValidationResult]
        validTargetProtSets = targetRsm.proteinSets
        validDecoyProtSets = decoyRsm.proteinSets
        
        // Decrease two peps rule p-value threshold iteratively
        while( currentFdr > 0 && validationRules(1).pValueThreshold >= validationRules(0).pValueThreshold ) {
          
          println( "LOOP 2 (two peptides rule)" )
          println( "two peps rule p-value threshold: " + validationRules(1).pValueThreshold )
          
          validTargetProtSets = this.validateProteinSets( validTargetProtSets, targetBestPepMatchesByProtSetId, validationRules )
          val targetValidProteinSetCount = validTargetProtSets.length
          println( targetValidProteinSetCount + " target" )
          
          validDecoyProtSets = this.validateProteinSets( validDecoyProtSets, decoyBestPepMatchesByProtSetId, validationRules )
          val decoyValidProteinSetCount = validDecoyProtSets.length
          println( decoyValidProteinSetCount + " decoy" )
          
          currentFdr = 100 * decoyValidProteinSetCount / targetValidProteinSetCount
          println( "current fdr: currentFdr" )
          
          // Add ROC point to the list
          val rocPoint = ValidationResult( nbTargetMatches = targetValidProteinSetCount,
                                           nbDecoyMatches = Some(decoyValidProteinSetCount),
                                           fdr = Some(currentFdr),
                                           properties = Some( HashMap("p_value_rule_1" -> validationRules(0).pValueThreshold,
                                                                      "p_value_rule_2" -> validationRules(1).pValueThreshold )) 
                                          )

          rocPoints += rocPoint
          
          // Check if we have reached the wanted FDR
          if( wantedRocPoint == null && currentFdr <= maxFdr ) { wantedRocPoint = rocPoint }

          // Check if current FDR equals zero
          if( currentFdr >= 0 ) {          
            /// Update probablity threshold of the two peptides rule
            if( wantedRocPoint != null ) { validationRules(1).pValueThreshold *= 0.5f } // arbitrary value
            else { validationRules(1).pValueThreshold *= 0.80f } // arbitrary value
          }
        }
      }
      
      /// Update probablity threshold of the single peptide rule
      // Lower p-value decrease when near from wanted FDR
      if( currentFdr < maxFdr ) { validationRules(0).pValueThreshold *= 0.95f } // arbitrary value
      else { validationRules(0).pValueThreshold *= 0.80f } // arbitrary value
      
    }
    
    // Set validation rules probability thresholds using the previously obtained wanted ROC point
    validationRules(0).pValueThreshold= wantedRocPoint.properties.get("p_value_rule_1").asInstanceOf[Float]
    validationRules(1).pValueThreshold = wantedRocPoint.properties.get("p_value_rule_2").asInstanceOf[Float]
    
    println(wantedRocPoint)
    
    // Validate results with the p-value thresholds which provide the best results
    validTargetProtSets = this.validateProteinSets( targetRsm.proteinSets, targetBestPepMatchesByProtSetId, validationRules )
    val targetValidProteinSetCount = validTargetProtSets.length
    println( targetValidProteinSetCount +" final target count\n" )
    
    validDecoyProtSets = this.validateProteinSets( decoyRsm.proteinSets, decoyBestPepMatchesByProtSetId, validationRules )
    val decoyValidProteinSetCount = validDecoyProtSets.length
    println( decoyValidProteinSetCount +" final decoy count\n" )
    
    // val finalFdr = targetDecoyHelper.computeFdr( targetValidProteinSetCount, decoyValidProteinSetCount )
    val finalFdr = wantedRocPoint.fdr.get
    println( "final fdr: " + finalFdr )
    
    ValidationResults( wantedRocPoint, Some(rocPoints) )
  }
  
  private def validateProteinSets( proteinSets: Seq[ProteinSet],
                                   bestPepMatchesByProtSetId: Map[Int,Array[PeptideMatch]],
                                   rules: Array[ValidationRule] ): Array[ProteinSet] = {
        
    val validProtSets = new ArrayBuffer[ProteinSet]
    for( proteinSet <- proteinSets ) {
      
      val proteinSetId = proteinSet.id
      val bestPepMatches = bestPepMatchesByProtSetId(proteinSetId)      
      var isProteinSetValid = false //= this._isProteinSetValid( bestPepMatches, rules )
      
      for( rule <- rules ) {
        
        val pValueThreshold = rule.pValueThreshold
        val minPeptideCount = rule.minPeptideCount
        val minPepSeqLength = rule.minPepSeqLength
        
        var validPeptideCount = 0
        for( bestPepMatch <- bestPepMatches ) {          
          if(  MascotValidationHelper.isPepMatchValid( bestPepMatch, pValueThreshold, minPepSeqLength ) ) {
            validPeptideCount += 1
          }
        }
        
        if( validPeptideCount >= minPeptideCount ) isProteinSetValid = true
      }
      
      // Update protein set validity
      if( isProteinSetValid ) {
        proteinSet.isValidated = true
        validProtSets += proteinSet
      }
      else { proteinSet.isValidated = false }
    }
    
    validProtSets.toArray
    
  }
  
  private def userValidationParamsToValidationRules( validationParams: UserValidationParams ): Array[ValidationRule] = {
    
    val minPepSeqLength = validationParams.minPepSeqLength
    val valProps = validationParams.properties.get
    val pValueRule1 = valProps("p_value_rule_1").asInstanceOf[Float]
    val pValueRule2 = valProps("p_value_rule_2").asInstanceOf[Float]
    
    Array( ValidationRule( 1, pValueRule1, minPepSeqLength ),
           ValidationRule( 2, pValueRule2, minPepSeqLength )
         )
  }

  def validateWithUserParams( validationParams: UserValidationParams, rsm: ResultSummary ): ValidationResults = {
    
    val validationRules = this.userValidationParamsToValidationRules( validationParams )    
    val validProtSets = this.validateProteinSets( rsm.proteinSets, rsm.getBestPepMatchesByProtSetId, validationRules )
    
    val wantedRocPoint = ValidationResult( validProtSets.length )
    
    ValidationResults( wantedRocPoint, None )
    
  }
  
  def validateWithUserParams( validationParams: UserValidationParams,
                              targetRsm: ResultSummary,
                              decoyRsm: ResultSummary ): ValidationResults = {
    
    val validationRules = this.userValidationParamsToValidationRules( validationParams )
                           
    val validTargetProtSets = this.validateProteinSets( targetRsm.proteinSets, targetRsm.getBestPepMatchesByProtSetId, validationRules )
    val validDecoyProtSets = this.validateProteinSets( decoyRsm.proteinSets, decoyRsm.getBestPepMatchesByProtSetId, validationRules )
    
    val( nbTargetMatches, nbDecoyMatches ) = ( validTargetProtSets.length, validDecoyProtSets.length )    
    val fdr = 100 * nbDecoyMatches / nbTargetMatches
    
    val wantedResult = ValidationResult( nbTargetMatches, Some(nbTargetMatches), Some(fdr) )    
    ValidationResults( wantedResult, None )

  }
}