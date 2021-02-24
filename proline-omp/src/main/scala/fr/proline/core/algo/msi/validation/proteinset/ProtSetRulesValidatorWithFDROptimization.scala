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
  val expectedFdr: Option[Float]
) extends IProteinSetValidator with LazyLogging {
  require( protSetFilterRule1.filterParameter == protSetFilterRule2.filterParameter )

  override def validatorName: String = "TD protein set rules validator"
  override def validatorDescription: String = "Protein set rules validator optimizing FDR value based on Target/Decoy counts"

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

  def validateProteinSets( targetRsm: ResultSummary, decoyRsm: Option[ResultSummary], tdAnalyzer: Option[ITargetDecoyAnalyzer]): ValidationResults = {
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
      () => this.computeValidationResult(multiPepTargetProtSets, Some(multiPepDecoyProtSets), tdAnalyzer),
      tdAnalyzer
    )
    
    // Apply filter for rule 2 if we have validation results
    if( rule2ValResults.isDefined ) {
      protSetFilterRule2.setThresholdValue(rule2ValResults.get.finalResult.properties.get(ValidationPropertyKeys.THRESHOLD_VALUE).asInstanceOf[AnyVal])
      
      // Validate protein sets identified with multiple peptides
      protSetFilterRule2.filterProteinSets(multiPepProtSets,true,true)
    } else {
      // Else invalid all multi peptides protein sets
      multiPepProtSets.foreach(_.isValidated = false)
    }
    
    // --- Rule1: Remaining protein sets validation ---
    // TODO: remove the non validated multi from one hits wonders
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
      () => this.computeValidationResult(targetProtSets, Some(decoyProtSets), tdAnalyzer),
      tdAnalyzer
    )
    
    // Apply filter for rule 1 if we have validation results
    if( rule1ValResults.isDefined ) {
      // --- Set validation rules probability thresholds using the previously obtained expected ROC point ---
      protSetFilterRule1.setThresholdValue(rule1ValResults.get.finalResult.properties.get(ValidationPropertyKeys.THRESHOLD_VALUE).asInstanceOf[AnyVal])
      
      // Validate protein sets identified with a single peptide 
      protSetFilterRule1.filterProteinSets(remainingProtSets,true,true)
    } else {
      // Else invalid all single peptide protein sets
      remainingProtSets.foreach(_.isValidated = false)
    }

    // Update validatedProteinSetsCount of peptide instances
    ProteinSetFiltering.updateValidatedProteinSetsCount(targetProtSets)
    ProteinSetFiltering.updateValidatedProteinSetsCount(decoyProtSets)

    // Return validation results
    ValidationResults( this.computeValidationResult(targetProtSets, Some(decoyProtSets), tdAnalyzer) )
  }

  private def _validateProteinSets(
    protSetFilterRule: IOptimizableProteinSetFilter,
    targetProtSets: Array[ProteinSet],
    decoyProtSets: Array[ProteinSet],
    protSetValStatusMap: Map[Long,Boolean],
    computeRocPointFn: () => ValidationResult,
    tdAnalyzer: Option[ITargetDecoyAnalyzer]
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
        
        curRocPoint = this.updateValidationResult(curRocPoint,curProtSet.isDecoy, tdAnalyzer)
      
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

}