package fr.proline.core.algo.msi.validation.proteinset

import com.weiglewilczek.slf4s.Logging
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import fr.proline.core.algo.msi.validation._
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.om.model.msi.ProteinSet
import fr.proline.core.om.model.msi.PeptideMatch
import fr.proline.core.algo.msi.filtering._

class PepMatchRulesValidatorWithFDROptimization(
  val pepMatchFilterRule1: IOptimizablePeptideMatchFilter,
  val pepMatchFilterRule2: IOptimizablePeptideMatchFilter, 
  val expectedFdr: Option[Float],
  var targetDecoyMode: Option[TargetDecoyModes.Value]
  ) extends AbstractPepMatchRulesValidator with Logging {
  require( pepMatchFilterRule1.filterParameter == pepMatchFilterRule2.filterParameter )
  
  def filterParameter = pepMatchFilterRule1.filterParameter
  def filterDescription = pepMatchFilterRule1.filterDescription
  def getFilterProperties = {
    Map(
      ValidationPropertyKeys.RULE_1_THRESHOLD_VALUE -> pepMatchFilterRule1.getThresholdValue,
      ValidationPropertyKeys.RULE_2_THRESHOLD_VALUE -> pepMatchFilterRule2.getThresholdValue
    )
  }
  
  // The initial threshold value must correspond to the one used for peptide match validation
  val initialThresholdValueAsDouble = pepMatchFilterRule1.getThresholdValueAsDouble
  
  def validateProteinSets( targetRsm: ResultSummary, decoyRsm: Option[ResultSummary] ): ValidationResults = {
    require( decoyRsm.isDefined, "a decoy result summary must be provided")
    
    // Retrieve some vars
    //val valResultProps = targetRsm.validationProperties("results").asInstanceOf[Map[String,Any]]
    //val pepMatchValidationProps = valResultProps("peptide_matches").asInstanceOf[Map[String,Any]]
//    val valResultProps = targetRsm.properties.get.getValidationProperties().get
//    val pepMatchValidationProps = valResultProps.getResults().getPeptideResults().get
    
    // Retrieve some vars
    val( targetProtSets, decoyProtSets ) = ( targetRsm.proteinSets, decoyRsm.get.proteinSets )
    val allProtSets = targetProtSets ++ decoyProtSets
    val allPepMatchesByProtSetId = targetRsm.getBestValidatedPepMatchesByPepSetId() ++ decoyRsm.get.getBestValidatedPepMatchesByPepSetId()
    val protSetValStatusMap = ProteinSetFiltering.getProtSetValidationStatusMap(allProtSets)
    
    // Define some vars
    var currentFdr = 100.0f
    val maxFdr = expectedFdr.get * 1.2 // 20% of FDR
    var rocPoints = new ArrayBuffer[ValidationResult]
    var expectedRocPoint: ValidationResult = null
    
    while( currentFdr > 0 && expectedRocPoint == null ) {
      
      this.logger.debug( "LOOP 1 (single peptide rule)" )
      this.logger.debug( "single pep rule threshold: " + pepMatchFilterRule1.getThresholdValue )
      
      // Validate protein sets using first rule
      this._validateProteinSets( allProtSets, allPepMatchesByProtSetId, Array(validationRules(0)) )
      
      // Compute validation result
      val valResult = this.computeValidationResult(targetRsm, decoyRsm)
      
      // Update current FDR
      currentFdr = valResult.fdr.get
      
      // Log validation result
      this.logger.debug( valResult.targetMatchesCount + " target protein sets" )
      this.logger.debug( valResult.decoyMatchesCount.get + " decoy protein sets" )
      this.logger.debug( "Current protein sets FDR = " + currentFdr )
      
      // Restore protein sets validation status
      ProteinSetFiltering.restoreProtSetValidationStatus(allProtSets, protSetValStatusMap)
      
      if( currentFdr <= expectedFdr.get ) {
        
        // Reset the threshold value of the second peptide rule to the initial value
        pepMatchFilterRule2.setThresholdValue(initialThresholdValueAsDouble)
        
        // Reset other temp vars
        rocPoints = new ArrayBuffer[ValidationResult]
        
        // Decrease two peps rule p-value threshold iteratively
        while( currentFdr > 0 && pepMatchFilterRule2.getThresholdValueAsDouble >= pepMatchFilterRule1.getThresholdValueAsDouble ) {
          
          this.logger.debug( "LOOP 2 (two peptides rule)" )
          this.logger.debug( "two peps rule p-value threshold: " + pepMatchFilterRule2.getThresholdValue )
          
          // Validate protein sets using the two rules
          this._validateProteinSets( allProtSets, allPepMatchesByProtSetId, validationRules )
          
          // Compute validation result
          val rocPoint = this.computeValidationResult(targetRsm, decoyRsm)
          
          // Update current FDR
          currentFdr = rocPoint.fdr.get
          
          // Log validation result
          this.logger.debug( rocPoint.targetMatchesCount + " target protein sets" )
          this.logger.debug( rocPoint.decoyMatchesCount.get + " decoy protein sets" )
          this.logger.debug( "Current protein sets FDR = "+ currentFdr )
          
          // Restore protein sets validation status
          ProteinSetFiltering.restoreProtSetValidationStatus(allProtSets, protSetValStatusMap)
          
          val thresholdRule1 = pepMatchFilterRule1.getThresholdValue
          val thresholdRule2 = pepMatchFilterRule2.getThresholdValue
          
          // Update roc point properties
          rocPoint.addProperties(
            Map(
              ValidationPropertyKeys.RULE_1_THRESHOLD_VALUE -> thresholdRule1,
              ValidationPropertyKeys.RULE_2_THRESHOLD_VALUE -> thresholdRule2
            )
          )
          
          // Add ROC point to the list
          rocPoints += rocPoint
          
          // Check if we have reached the expected FDR
          if( expectedRocPoint == null && currentFdr <= maxFdr ) { expectedRocPoint = rocPoint }

          // Check if current FDR equals zero
          if( currentFdr >= 0 ) {
            // Update threshold of the second peptide rule
            pepMatchFilterRule2.setThresholdValue(pepMatchFilterRule2.getNextValue(thresholdRule2))
            //if( expectedRocPoint != null ) { pepMatchFilterRule2.getNextValue(thresholdRule2 * 0.50f) } // arbitrary value
            //else { pepMatchFilterRule2.pValueThreshold *= 0.80f } // arbitrary value
          }

        }
      }
      
      /// Update probablity threshold of the single peptide rule
      // Lower p-value decrease when near from expected FDR
      pepMatchFilterRule1.setThresholdValue(pepMatchFilterRule1.getNextValue(pepMatchFilterRule1.getThresholdValue))
      
      //if( currentFdr < maxFdr ) { pepMatchFilterRule1.pValueThreshold *= 0.95f } // arbitrary value
      //else { pepMatchFilterRule1.pValueThreshold *= 0.80f } // arbitrary value
    }
    
    // Set validation rules probability thresholds using the previously obtained expected ROC point
    val rocPointProps = expectedRocPoint.properties.get
    pepMatchFilterRule1.setThresholdValue( rocPointProps(ValidationPropertyKeys.RULE_1_THRESHOLD_VALUE).asInstanceOf[AnyVal] )
    pepMatchFilterRule2.setThresholdValue( rocPointProps(ValidationPropertyKeys.RULE_2_THRESHOLD_VALUE).asInstanceOf[AnyVal] )
    
    // Validate results with the thresholds that provide the best results
    this._validateProteinSets( allProtSets, allPepMatchesByProtSetId, validationRules )    
    
    // Return validation results
    ValidationResults( expectedRocPoint, Some(rocPoints) )
  }
  
}