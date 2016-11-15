package fr.proline.core.algo.msi.validation.proteinset

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.math.sqrt
import scala.util.control.Breaks._
import com.typesafe.scalalogging.LazyLogging
import fr.proline.core.algo.msi.validation._
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.om.model.msi.ProteinSet
import fr.proline.core.om.model.msi.PeptideMatch
import fr.proline.core.algo.msi.filtering._
import fr.proline.core.algo.msi.filtering.pepmatch.ScorePSMFilter
import fr.proline.core.algo.msi.filtering.proteinset.ScoreProtSetFilter

class ProtSetRulesValidatorWithFDROptimization(
  val protSetFilterRule1: IOptimizableProteinSetFilter,
  val protSetFilterRule2: IOptimizableProteinSetFilter,
  val expectedFdr: Option[Float],
  var targetDecoyMode: Option[TargetDecoyModes.Value]
) extends IProteinSetValidator with LazyLogging {
  require( protSetFilterRule1.filterParameter == protSetFilterRule2.filterParameter )
  
  def filterParameter = protSetFilterRule1.filterParameter
  def filterDescription = protSetFilterRule1.filterDescription
  def getFilterProperties = {
    Map(
      ValidationPropertyKeys.RULE_1_THRESHOLD_VALUE -> protSetFilterRule1.getThresholdValue,
      ValidationPropertyKeys.RULE_2_THRESHOLD_VALUE -> protSetFilterRule2.getThresholdValue
    )
  }
  
  val MAX_FDR = 50f
  val someExpectedFdr = expectedFdr.get
  
  // The initial threshold value must correspond to the one used for peptide match validation
  val thresholdStartValue = protSetFilterRule1.getThresholdStartValue
  
  def validateProteinSetsV1( targetRsm: ResultSummary, decoyRsm: Option[ResultSummary] ): ValidationResults = {
    require( decoyRsm.isDefined, "a decoy result summary must be provided")
    
    // Retrieve some vars
    val( targetProtSets, decoyProtSets ) = ( targetRsm.proteinSets, decoyRsm.get.proteinSets )
    val allProtSets = targetProtSets ++ decoyProtSets
    val protSetValStatusMap = ProteinSetFiltering.getProtSetValidationStatusMap(allProtSets)
    
    // Partition protein sets by considering the number of identified peptides
    val(singlePepProtSets,multiPepProtSets) = allProtSets.partition(_.peptideSet.items.length == 1)
    val multiPepTargetProtSets = targetProtSets.filter(_.peptideSet.items.length > 1)
    val multiPepDecoyProtSets = decoyProtSets.filter(_.peptideSet.items.length > 1)
    
    // Define some vars
    var currentFdr = 100.0f
    val maxFdr = expectedFdr.get * 1.2 // 20% of FDR
    var rocPoints = new ArrayBuffer[ValidationResult]
    var expectedRocPoint: ValidationResult = null
    var valResult: ValidationResult = null
    
    while( currentFdr > 0 && expectedRocPoint == null ) {
      
      // Retrieve single peptide rule threshold
      val thresholdRule2 = protSetFilterRule2.getThresholdValue
      
      this.logger.trace( "LOOP 1 (multiple peptides rule)" )
      this.logger.trace( "multiple peps rule threshold: " + thresholdRule2 )
      
      // Validate protein sets identified with multiple peptides
      protSetFilterRule2.filterProteinSets(multiPepProtSets,true,false)
      
      // Compute validation result
      valResult = this.computeValidationResult(multiPepTargetProtSets, Some(multiPepDecoyProtSets))
      
      // Update current FDR
      currentFdr = valResult.fdr.get
      
      // Log validation result
      this.logger.trace( valResult.targetMatchesCount + " target protein sets" )
      this.logger.trace( valResult.decoyMatchesCount.get + " decoy protein sets" )
      this.logger.trace( "Current protein sets FDR = " + currentFdr )
      
      if( currentFdr <= expectedFdr.get ) {
        
        // Reset the threshold value of the single peptide rule to the initial value
        protSetFilterRule1.setThresholdValue(thresholdStartValue)
        
        // Reset other temp vars
        rocPoints = new ArrayBuffer[ValidationResult]
        currentFdr = 1 // be sure to test rule 1 at least one time
        
        while( currentFdr > 0 && expectedRocPoint == null ) {
          // && protSetFilterRule2.getThresholdValueAsDouble <= protSetFilterRule1.getThresholdValueAsDouble
          
          // Retrieve single peptide rule threshold
          val thresholdRule1 = protSetFilterRule1.getThresholdValue
          
          this.logger.trace( "LOOP 2 (single peptide rule)" )
          this.logger.trace( "single pep rule threshold: " + thresholdRule1 )
          
          // Validate protein sets identified with a single peptide 
          protSetFilterRule1.filterProteinSets(singlePepProtSets,true,false)
          
          // Compute validation result
          val rocPoint = this.computeValidationResult(targetProtSets, Some(decoyProtSets))
          
          // Update current FDR
          currentFdr = rocPoint.fdr.get
          
          // Log validation result
          this.logger.trace( rocPoint.targetMatchesCount + " target protein sets" )
          this.logger.trace( rocPoint.decoyMatchesCount.get + " decoy protein sets" )
          this.logger.trace( "Current protein sets FDR = "+ currentFdr )
          
          // Restore protein sets validation status of protein sets identified with multiple peptides
          ProteinSetFiltering.restoreProtSetValidationStatus(singlePepProtSets, protSetValStatusMap)
          
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
            // Update threshold value of the single peptide rule
            protSetFilterRule1.setThresholdValue(protSetFilterRule1.getNextValue(thresholdRule1))
          }

        }
      }
      
      // Restore protein sets validation status
      ProteinSetFiltering.restoreProtSetValidationStatus(multiPepProtSets, protSetValStatusMap)
      
      // Update threshold value of the single peptide rule
      protSetFilterRule2.setThresholdValue(protSetFilterRule2.getNextValue(thresholdRule2))
      
    }
    
    // Set the expect ROC point to the last validation result if no result found
    if( expectedRocPoint == null ) expectedRocPoint = valResult
    
    // Set validation rules probability thresholds using the previously obtained expected ROC point
    if(expectedRocPoint.properties.isDefined) {
      val rocPointProps = expectedRocPoint.properties.get
      protSetFilterRule1.setThresholdValue( rocPointProps(ValidationPropertyKeys.RULE_1_THRESHOLD_VALUE).asInstanceOf[AnyVal] )
      protSetFilterRule2.setThresholdValue( rocPointProps(ValidationPropertyKeys.RULE_2_THRESHOLD_VALUE).asInstanceOf[AnyVal] )
    }
    
    // Validate protein sets identified with a single peptide 
    protSetFilterRule1.filterProteinSets(singlePepProtSets,true,true)
    
    // Validate protein sets identified with multiple peptides
    protSetFilterRule2.filterProteinSets(multiPepProtSets,true,true)
    
    // Update validatedProteinSetsCount of peptide instances
    ProteinSetFiltering.updateValidatedProteinSetsCount(allProtSets)
    
    // Return validation results
    ValidationResults( expectedRocPoint, Some(rocPoints) )
  }
  
  def validateProteinSetsV2( targetRsm: ResultSummary, decoyRsm: Option[ResultSummary] ): ValidationResults = {
    require( decoyRsm.isDefined, "a decoy result summary must be provided")
    
    // Retrieve some vars
    val( targetProtSets, decoyProtSets ) = ( targetRsm.proteinSets, decoyRsm.get.proteinSets )
    val allProtSets = targetProtSets ++ decoyProtSets
    val protSetValStatusMap = ProteinSetFiltering.getProtSetValidationStatusMap(allProtSets)
    
    // Partition protein sets by considering the number of identified peptides
    val(singlePepProtSets,multiPepProtSets) = allProtSets.partition(_.peptideSet.items.length == 1)
    
    // --- Rule2: Multiple peptides protein sets validation ---
    val multiPepTargetProtSets = targetProtSets.filter(_.peptideSet.items.length > 1)
    val multiPepDecoyProtSets = decoyProtSets.filter(_.peptideSet.items.length > 1)

    this.logger.debug( "Running multiple peptides Protein Sets validation..." )
    val rule2ValResults = this._validateProteinSets(
      protSetFilterRule2,
      multiPepTargetProtSets,
      multiPepDecoyProtSets,
      protSetValStatusMap,
      // Compute ROC points on multi peptides protein sets only
      () => this.computeValidationResult(multiPepTargetProtSets, Some(multiPepDecoyProtSets))
    )
    
    // --- Rule1: Single peptides protein sets validation ---
    val singlePepTargetProtSets = targetProtSets.filter(_.peptideSet.items.length == 1)
    val singlePepDecoyProtSets = decoyProtSets.filter(_.peptideSet.items.length == 1)
    
    this.logger.debug( "Running single peptides Protein Sets validation..." )
    val rule1ValResults = this._validateProteinSets(
      // Filter single peptides protein sets
      protSetFilterRule1,
      singlePepTargetProtSets,
      singlePepDecoyProtSets,
      protSetValStatusMap,
      () => this.computeValidationResult(singlePepTargetProtSets, Some(singlePepDecoyProtSets))
    )
    
    if( rule1ValResults.isDefined ) {
      // --- Set validation rules probability thresholds using the previously obtained expected ROC point ---
      protSetFilterRule1.setThresholdValue(
        rule1ValResults.get.finalResult.properties.get(ValidationPropertyKeys.THRESHOLD_VALUE).asInstanceOf[AnyVal]
      )
      
      // Validate protein sets identified with a single peptide 
      protSetFilterRule1.filterProteinSets(singlePepProtSets,true,true)
    } else {
      // Else invalid all single peptide protein sets
      singlePepProtSets.foreach(_.isValidated = false)
    }
    
    if( rule2ValResults.isDefined ) {
      protSetFilterRule2.setThresholdValue(
        rule2ValResults.get.finalResult.properties.get(ValidationPropertyKeys.THRESHOLD_VALUE).asInstanceOf[AnyVal]
      )
      
      // Validate protein sets identified with multiple peptides
      protSetFilterRule2.filterProteinSets(multiPepProtSets,true,true)
    } else {
      // Else invalid all multi peptides protein sets
      multiPepProtSets.foreach(_.isValidated = false)
    }
    
    // Return validation results
    ValidationResults( this.computeValidationResult(targetProtSets, Some(decoyProtSets)) )
  }
  
  def validateProteinSets( targetRsm: ResultSummary, decoyRsm: Option[ResultSummary] ): ValidationResults = {
    require( decoyRsm.isDefined, "a decoy result summary must be provided")
    
    // Retrieve previously filtered protein sets
    val targetProtSets = targetRsm.proteinSets.filter(_.isValidated == true)
    val decoyProtSets = decoyRsm.get.proteinSets.filter(_.isValidated == true)
    val allProtSets = targetProtSets ++ decoyProtSets
    val protSetValStatusMap = ProteinSetFiltering.getProtSetValidationStatusMap(allProtSets)
    
    // Partition protein sets by considering the number of identified peptides
    val(singlePepTargetProtSets,multiPepTargetProtSets) = targetProtSets.partition(_.peptideSet.items.length == 1)
    val(singlePepDecoyProtSets,multiPepDecoyProtSets) = decoyProtSets.partition(_.peptideSet.items.length == 1)
    
    // --- Rule2: Multiple peptides protein sets validation ---
    val multiPepProtSets = multiPepTargetProtSets ++ multiPepDecoyProtSets

    this.logger.debug( "Running multiple peptides Protein Sets validation..." )
    val rule2ValResults = this._validateProteinSets(
      protSetFilterRule2,
      multiPepTargetProtSets,
      multiPepDecoyProtSets,
      protSetValStatusMap,
      // Compute ROC points on multi peptides protein sets only
      () => this.computeValidationResult(multiPepTargetProtSets, Some(multiPepDecoyProtSets))
    )
    
    // Apply filter for rule 2 if we have validation results
    if( rule2ValResults.isDefined ) {
      protSetFilterRule2.setThresholdValue(
        rule2ValResults.get.finalResult.properties.get(ValidationPropertyKeys.THRESHOLD_VALUE).asInstanceOf[AnyVal]
      )
      
      // Validate protein sets identified with multiple peptides
      protSetFilterRule2.filterProteinSets(multiPepProtSets,true,true)
    } else {
      // Else invalid all multi peptides protein sets
      multiPepProtSets.foreach(_.isValidated = false)
    }
    
    // --- Rule1: Remaining protein sets validation ---
    val remainingTargetProtSets = singlePepTargetProtSets ++ multiPepTargetProtSets.filter(_.isValidated == false)
    val remainingDecoyProtSets = singlePepDecoyProtSets ++ multiPepDecoyProtSets.filter(_.isValidated == false)
    val remainingProtSets = remainingTargetProtSets ++ remainingDecoyProtSets
    
    this.logger.debug( "Running single peptides Protein Sets validation..." )
    val rule1ValResults = this._validateProteinSets(
      // Filter single peptides protein sets
      protSetFilterRule1,
      remainingTargetProtSets,
      remainingDecoyProtSets,
      protSetValStatusMap,
      // Note: we compute the FDR using all protein sets in order to not under-estimate this value at the end of the procedure
      () => this.computeValidationResult(targetProtSets, Some(decoyProtSets))
    )
    
    // Apply filter for rule 1 if we have validation results
    if( rule1ValResults.isDefined ) {
      // --- Set validation rules probability thresholds using the previously obtained expected ROC point ---
      protSetFilterRule1.setThresholdValue(
        rule1ValResults.get.finalResult.properties.get(ValidationPropertyKeys.THRESHOLD_VALUE).asInstanceOf[AnyVal]
      )
      
      // Validate protein sets identified with a single peptide 
      protSetFilterRule1.filterProteinSets(remainingProtSets,true,true)
    } else {
      // Else invalid all single peptide protein sets
      remainingProtSets.foreach(_.isValidated = false)
    }
       
    // Return validation results
    ValidationResults( this.computeValidationResult(targetProtSets, Some(decoyProtSets)) )
  }
  
  private def _validateProteinSets(
    protSetFilterRule: IOptimizableProteinSetFilter,
    targetProtSets: Array[ProteinSet],
    decoyProtSets: Array[ProteinSet],
    protSetValStatusMap: Map[Long,Boolean],
    computeRocPointFn: () => ValidationResult
    ): Option[ValidationResults] = {
    
    // Retrieve filtered protein sets
    val filteredProtSets = (targetProtSets ++ decoyProtSets).filter(_.isValidated)
    
    // Sort all Protein Sets from the best to the worst according to the validation filter
    // Note: the two rules are equivalent for sorting
    val sortedProtSets = protSetFilterRule.sortProteinSets(filteredProtSets).toArray
    
    // Define some vars
    var curRocPoint: ValidationResult = null
    val rocPoints = new ArrayBuffer[ValidationResult]
    
    // Iterate over sorted decoy Protein Sets => each one is taken as a new threshold (breaks if FDR is greater than 50%)
    breakable {
      for( curProtSet <- sortedProtSets ) {
        
        // Retrieve next filter threshold from this decoy protein set
        var thresholdValue = protSetFilterRule.getProteinSetValueForFiltering(curProtSet)
        
        // Increase just a little bit the threshold for decoy protein sets in order to improve sensitivity
        if( curProtSet.isDecoy ) protSetFilterRule.setThresholdValue(protSetFilterRule.getNextValue(thresholdValue))
        else protSetFilterRule.setThresholdValue(thresholdValue)
        
        this.logger.trace( "Protein set threshold: " + protSetFilterRule.getThresholdValue )
        
        // Initialize the ROC point with highest threshold
        if( curRocPoint == null ) {
          // Apply the validation filter
          protSetFilterRule.filterProteinSets(filteredProtSets,true,false)
          curRocPoint = computeRocPointFn()
        }
        
        // Log validation result
        this.logger.trace( curRocPoint.targetMatchesCount + " target protein sets" )
        this.logger.trace( curRocPoint.decoyMatchesCount.get + " decoy protein sets" )
        this.logger.trace( "Current protein sets FDR = " + curRocPoint.fdr )
        
        if( curRocPoint.fdr.isDefined ) {
          // Update ROC point properties
          curRocPoint.addProperties( protSetFilterRule.getFilterProperties )
          
          // Add ROC point to the list if FDR is defined and current protein set is a decoy
          rocPoints += curRocPoint
        }
        
        curRocPoint = this.updateValidationResult(curRocPoint,curProtSet.isDecoy)
      
        if ( curRocPoint.fdr.isDefined && curRocPoint.fdr.get > MAX_FDR ) break
      }
    }
    
    // Reset validation status
    ProteinSetFiltering.restoreProtSetValidationStatus(filteredProtSets, protSetValStatusMap)
    
    if( rocPoints.length == 0 ) None
    else {
      
      // Search for the ROC point which has the closest FDR to the expected one
      val nearestRocPoint = rocPoints.reduceLeft { (a, b) =>
        if ( (a.fdr.get - someExpectedFdr).abs < (b.fdr.get - someExpectedFdr).abs ) a else b
      }
      val nearestFdr = nearestRocPoint.fdr.get
      
      // Search the "best" ROC point for this FDR
      val bestRocPoint = rocPoints
        .filter( rp => (rp.fdr.get - nearestFdr).abs <= nearestFdr * 0.05 ) // allows 5% of tolerance
        .sortWith((a,b) => a.targetMatchesCount > b.targetMatchesCount )
        .head
      
      Some( ValidationResults( finalResult = bestRocPoint, computedResults = Some(rocPoints) ) )
    }

  }
 
  private def _validateProteinSetsV2(
    protSetFilterRule: IOptimizableProteinSetFilter,
    targetProtSets: Array[ProteinSet],
    decoyProtSets: Array[ProteinSet],
    protSetValStatusMap: Map[Long,Boolean],
    computeRocPointFn: () => ValidationResult
    ): Option[ValidationResults] = {
    
    // Sort decoy Protein Sets from the best to the worst according to the validation filter
    val sortedDecoyProtSets = protSetFilterRule2.sortProteinSets(decoyProtSets).toArray
    
    // Define some vars
    val rocPoints = new ArrayBuffer[ValidationResult]
    
    // Iterate over sorted decoy Protein Sets => each one is taken as a new threshold (breaks if FDR is greater than 50%)
    breakable {
      for( threshDecoyProtSet <- sortedDecoyProtSets ) {
        
        // Restore protein sets validation status
        ProteinSetFiltering.restoreProtSetValidationStatus(targetProtSets, protSetValStatusMap)
        ProteinSetFiltering.restoreProtSetValidationStatus(decoyProtSets, protSetValStatusMap)
        
        // Retrieve next filter threshold from this decoy protein set
        var thresholdValue = protSetFilterRule.getProteinSetValueForFiltering(threshDecoyProtSet)
        this.logger.trace( "Protein set threshold: " + thresholdValue )
        
        // Increase the threshold just a little bit in order to exclude
        // the current decoy Protein Set (should maximize sensitivity),
        thresholdValue = protSetFilterRule.getNextValue(thresholdValue)
        
        // Inject the obtained value in the validation filter
        protSetFilterRule.setThresholdValue(thresholdValue)
        
        // Validate protein sets
        protSetFilterRule.filterProteinSets(targetProtSets,true,false)
        protSetFilterRule.filterProteinSets(decoyProtSets,true,false)
        
        // Compute validation result using the ROC point computer
        val rocPoint = computeRocPointFn()
        
        // Update current FDR
        val currentFdr = rocPoint.fdr.get
        
        // Log validation result
        this.logger.trace( rocPoint.targetMatchesCount + " target protein sets" )
        this.logger.trace( rocPoint.decoyMatchesCount.get + " decoy protein sets" )
        this.logger.trace( "Current protein sets FDR = " + currentFdr )
        
        // Add ROC point to the list
        rocPoints += rocPoint
        
        // Update ROC point properties
        rocPoint.addProperties( Map( ValidationPropertyKeys.THRESHOLD_VALUE -> thresholdValue ) )       
      
        if (currentFdr > MAX_FDR) break
      }
    }

    if( rocPoints.length == 0 ) None
    else {
      // Search for the ROC point which has the closest FDR to the expected one
      val expectedRocPoint = rocPoints.reduceLeft { (a, b) =>
        if ( (a.fdr.get - someExpectedFdr).abs < (b.fdr.get - someExpectedFdr).abs ) a else b
      }
      
      Some( ValidationResults( finalResult = expectedRocPoint, computedResults = Some(rocPoints) ) )
    }

  }
  
}