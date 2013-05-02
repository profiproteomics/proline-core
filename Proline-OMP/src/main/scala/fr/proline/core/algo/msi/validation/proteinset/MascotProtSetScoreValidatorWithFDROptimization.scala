package fr.proline.core.algo.msi.validation.proteinset

/*
import math.{sqrt,abs}
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import com.weiglewilczek.slf4s.Logging
import fr.proline.core.algo.msi.validation._
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.om.model.msi.ProteinSet
import fr.proline.core.om.model.msi.PeptideMatch
import fr.proline.core.algo.msi.filtering._
import fr.proline.core.algo.msi.filtering.pepmatch.ScorePSMFilter

class MascotProtSetScoreValidatorWithFDROptimization(
  val initialThresholdValue: Option[AnyVal],
  val expectedFdr: Option[Float] = None
  ) extends IProteinSetValidator with Logging {
  
  // Initialize p-value threshold value
  val pValueThreshold = if( !initialThresholdValue.isDefined ) 1.0
  else initialThresholdValue.asInstanceOf[Double]
  
  def validateProteinSets( targetRsm: ResultSummary, decoyRsm: Option[ResultSummary] ): ValidationResults = {
    require( decoyRsm.isDefined, "a decoy result summary must be provided")
    
    // Retrieve some vars
    val( targetProtSets, decoyProtSets ) = ( targetRsm.proteinSets, decoyRsm.get.proteinSets )
    val allProtSets = targetProtSets ++ decoyProtSets
    val allPepMatchesByProtSetId = targetRsm.getBestPepMatchesByProtSetId() ++ decoyRsm.get.getBestPepMatchesByProtSetId()
    val protSetValStatusMap = ProteinSetFiltering.getProtSetValidationStatusMap(allProtSets)    
    
    // Define some vars
    var pValueThresholdVar = pValueThreshold    
    var currentFdr = 100.0f
    val maxFdr = expectedFdr.get * 1.2 // 20% of FDR
    var rocCurves = new ArrayBuffer[ArrayBuffer[ValidationResult]]
    val expectedFdrRocPoints = new ArrayBuffer[ValidationResult](0)
    var rocCurveId = 0
    
    // TODO: another algo could start by normalizing peptide match scores :
    // 1) translates score = norm_score = score - threshold
    // 2) min norm_score =0 = norm_score -= min(norm_scores)
    // 3) prot score score = sum(norm_scores)
    // 4) compute the score threshold to obtain the expected FDR
    // End of TODO
    
    var lowestProtSetScoreThreshold = 0.0f
    var reachedExpectedFdr = false
    while( currentFdr > 0 && ! reachedExpectedFdr ) {
      rocCurveId += 1
      
      this.logger.debug( "p-value threshold: " + pValueThresholdVar )
      
      // Compute score threshold offset with a reference p-value of 0.05 (default p-value used to compute Mascot thresholds)
      val pepScoreThresholdOffset = MascotValidationHelper.calcScoreThresholdOffset( pValueThresholdVar, 0.05 )
      this.logger.debug( "peptide match score threshold delta:" + pepScoreThresholdOffset )
      
      this._updateScoreOfProteinSets( allProtSets, allPepMatchesByProtSetId, pepScoreThresholdOffset )
      
      val rocPoints = new ArrayBuffer[ValidationResult]
      var expectedFdrRocPoint: ValidationResult = null
      var( protSetScoreThreshold, rocPointId ) = (0.0f,0)
      
      while( expectedFdrRocPoint == null ) {
        rocPointId += 1
        
        this.logger.debug( "protein set score threshold: " + protSetScoreThreshold )
        
        // Validate protein sets
        this._validateProteinSets( allProtSets, protSetScoreThreshold )
        
        // Compute validation result
        val rocPoint = this.computeValidationResult(targetRsm, decoyRsm)
        
        // Update current FDR
        currentFdr = rocPoint.fdr.get
        
        // Log validation result
        this.logger.debug( rocPoint.targetMatchesCount + " target protein sets" )
        this.logger.debug( rocPoint.decoyMatchesCount.get + " decoy protein sets" )
        this.logger.debug( "Current protein sets FDR = " + currentFdr )
        
        // Update roc point properties
        rocPoint.addProperties(
          Map(
            ValidationPropertyKeys.P_VALUE_THRESHOLD -> pValueThresholdVar,
            ValidationPropertyKeys.PEP_SCORE_THRESHOLD_OFFSET -> pepScoreThresholdOffset,
            ValidationPropertyKeys.PROT_SET_SCORE_THRESHOLD -> protSetScoreThreshold,
            ValidationPropertyKeys.ROC_CURVE_ID -> rocCurveId
          )
        )
        
        rocPoints += rocPoint
        
        if( currentFdr <= maxFdr ) {
          // Add ROC point to the list
          expectedFdrRocPoint = rocPoint
          expectedFdrRocPoints += rocPoint
        }
        
        //protSetScoreThreshold += sprintf("%.1f",abs(currentFdr-expectedFdr)/5)
        protSetScoreThreshold += (sqrt( 1 + abs(currentFdr-expectedFdr.get) ) - 1).toFloat
        
      }
      
      rocCurves += rocPoints
      
      // Check if we have reached the expected FDR
      //if( !defined expectedRocPoint and currentFdr <= maxFdr ) {
      if( protSetScoreThreshold > lowestProtSetScoreThreshold ) {
        reachedExpectedFdr = true
      }
      else {
        lowestProtSetScoreThreshold = protSetScoreThreshold
        
        // Update p-value threshold
        pValueThresholdVar *= 0.90f
      }
    }
    
    val expectedRocPoint = expectedFdrRocPoints.reduce { (a,b) => if( a.targetMatchesCount >= b.targetMatchesCount ) a else b } 
    this.logger.debug(expectedRocPoint.toString)
    
    // Retrieve the best ROC curve and delete the ROC curve identifier from the data points
    val expectedRocPointProps = expectedRocPoint.properties.get
    val expectedRocPointCurveId = expectedRocPointProps(ValidationPropertyKeys.ROC_CURVE_ID).asInstanceOf[Int]
    val bestRocCurve = rocCurves( expectedRocPointCurveId - 1 )
    expectedRocPointProps -= ValidationPropertyKeys.ROC_CURVE_ID
    
    // Set validation rules probability thresholds using the previously obtained expected ROC point
    val pepScoreThresholdOffset = expectedRocPointProps(ValidationPropertyKeys.PEP_SCORE_THRESHOLD_OFFSET).asInstanceOf[Float]
    val protSetScoreThreshold = expectedRocPointProps(ValidationPropertyKeys.PROT_SET_SCORE_THRESHOLD).asInstanceOf[Float]
    
    // Validate results with the thresholds that provide the best results
    this._updateScoreOfProteinSets( allProtSets, allPepMatchesByProtSetId, pepScoreThresholdOffset )
    this._validateProteinSets( allProtSets, protSetScoreThreshold )
    
    // Return validation results
    ValidationResults( expectedRocPoint, Some(bestRocCurve) )
  }


  private def _updateScoreOfProteinSets(
    proteinSets: Array[ProteinSet],
    bestPepMatchesByProtSetId: Map[Int,Array[PeptideMatch]],
    pepScoreThresholdOffset: Float
  ): Unit = {
    
    for( proteinSet <- proteinSets ) {
      
      val proteinSetId = proteinSet.id
      val bestPepMatches = bestPepMatchesByProtSetId(proteinSetId)
      
      val protSetScore = MascotValidationHelper.sumPeptideMatchesScoreOffsets( bestPepMatches, pepScoreThresholdOffset )
      proteinSet.score = protSetScore
      proteinSet.scoreType = "mascot:protein set score" // TODO: create an enumeration of scores
    }
    
  }
  
  private def _validateProteinSets( proteinSets: Seq[ProteinSet], protSetScoreThreshold: Double ): Unit = {
    
    for( proteinSet <- proteinSets ) {      
      // Invalidate protein sets with a too low score
      if( proteinSet.score < protSetScoreThreshold ) proteinSet.isValidated = false
    }

  }

}*/