package fr.proline.core.algo.msi.validation.proteinset

import math.{sqrt,abs}
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import com.weiglewilczek.slf4s.Logging
import fr.proline.core.algo.msi.validation._
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.om.model.msi.ProteinSet
import fr.proline.core.om.model.msi.PeptideMatch
import fr.proline.core.algo.msi.filtering._

class MascotProteinSetScoreValidator extends IProteinSetValidator with Logging {

  def validateWithComputerParams( protSetFilter: IProteinSetFilter,
                                  targetRsm: ResultSummary,
                                  decoyRsm: ResultSummary
                                ): ValidationResults = {
    require (protSetFilter.isInstanceOf[ParamProteinSetFilter])
    val paramProtSetFilter = protSetFilter.asInstanceOf[ParamProteinSetFilter]
    
    var( targetProtSets, decoyProtSets ) = ( targetRsm.proteinSets, decoyRsm.proteinSets )
    val targetBestPepMatchesByProtSetId = targetRsm.getBestPepMatchesByProtSetId
    val decoyBestPepMatchesByProtSetId = decoyRsm.getBestPepMatchesByProtSetId
    
    // Retrieve some vars
    val expectedFdr = paramProtSetFilter.expectedFdr
//    val valResultProps = targetRsm.properties.get.getValidationProperties().get
    //("results").asInstanceOf[Map[String,Any]]
   // val pepMatchValidationProps = valResultProps("peptide_matches").asInstanceOf[Map[String,Any]]
    //var pValueThreshold = pepMatchValidationProps("p_value_threshold").asInstanceOf[Float]
//    val pepMatchValidationProps = valResultProps.getResults().getPeptideResults().get
    
    var pValueThreshold = paramProtSetFilter.pValueThreshold
    
    // Compute initial p-value threshold: we just need something slightly greater than the peptide validation one
    pValueThreshold = sqrt(pValueThreshold).toFloat
    
    val minPepSeqLength = paramProtSetFilter.minPepSeqLength
    var currentFdr = 100.0f
    val maxFdr = expectedFdr * 1.2 // 20% of FDR
    var rocCurves = new ArrayBuffer[ArrayBuffer[ValidationResult]]
    val expectedFdrRocPoints = new ArrayBuffer[ValidationResult](0)
    var rocCurveId = 0
    
    // TODO: another algo could start by normalizing peptide match scores :
    // 1) translates score = norm_score = score - threshold
    // 2) min norm_score =0 = norm_score -= min(norm_scores)
    // 3) prot score score = sum(norm_scores)
    // 4) compute the score threshold to obtain the expected FDR
    // End of TO DO
    
    var lowestProtSetScoreThreshold = 0.0f
    var reachedExpectedFdr = false
    while( currentFdr > 0 && ! reachedExpectedFdr ) {
      rocCurveId += 1
      
      this.logger.debug( "p-value threshold: " + pValueThreshold )
      
      // Compute score threshold offset with a reference p-value of 0.05 (default p-value used to compute Mascot thresholds)
      val pepScoreThresholdOffset = MascotValidationHelper.calcScoreThresholdOffset( pValueThreshold, 0.05 )
      this.logger.debug( "peptide match score threshold delta:" + pepScoreThresholdOffset )
      
      this.updateScoreOfProteinSets( targetProtSets, targetBestPepMatchesByProtSetId,
                                     pepScoreThresholdOffset, minPepSeqLength )
      this.updateScoreOfProteinSets( decoyProtSets, decoyBestPepMatchesByProtSetId,
                                     pepScoreThresholdOffset, minPepSeqLength )
      
      var rocPoints = new ArrayBuffer[ValidationResult]
      var expectedFdrRocPoint: ValidationResult = null
      var( protSetScoreThreshold, rocPointId ) = (0.0f,0)
      
      while( expectedFdrRocPoint == null ) {
        rocPointId += 1
        
        this.logger.debug( "protein set score threshold: " + protSetScoreThreshold )
        
        val validTargetProtSets = this.validateProteinSets( targetProtSets, protSetScoreThreshold )
        val targetValidProteinSetCount = validTargetProtSets.length
        this.logger.debug( targetValidProteinSetCount + " target" )
        
        val validDecoyProtSets = this.validateProteinSets( decoyProtSets, protSetScoreThreshold )
        val decoyValidProteinSetCount = validDecoyProtSets.length
        this.logger.debug( decoyValidProteinSetCount + " decoy" )
        
        currentFdr =(100 * decoyValidProteinSetCount).toFloat / targetValidProteinSetCount
        this.logger.debug( "current fdr: " + currentFdr )
        
        val rocPoint = ValidationResult(  //id = rocPointId,
                                          targetMatchesCount = targetValidProteinSetCount,
                                          decoyMatchesCount = Some(decoyValidProteinSetCount),
                                          fdr = Some(currentFdr),
                                          properties = Some( HashMap( "p_value_threshold" -> pValueThreshold,
                                                                      "pep_score_threshold_offset" -> pepScoreThresholdOffset,
                                                                      "prot_set_score_threshold" -> protSetScoreThreshold,
                                                                      "roc_curve_id" -> rocCurveId
                                                                      ) )
                                        )
        
        rocPoints += rocPoint
        
        if( currentFdr <= maxFdr ) {
          // Add ROC point to the list
          expectedFdrRocPoint = rocPoint
          expectedFdrRocPoints += rocPoint
        }
        
        //protSetScoreThreshold += sprintf("%.1f",abs(currentFdr-expectedFdr)/5)
        protSetScoreThreshold += (sqrt( 1 + abs(currentFdr-expectedFdr) ) - 1).toFloat
        
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
        pValueThreshold *= 0.90f
      }
    }
    
    val expectedRocPoint = expectedFdrRocPoints.reduce { (a,b) => if( a.targetMatchesCount >= b.targetMatchesCount ) a else b } 
    this.logger.debug(expectedRocPoint.toString)
    
    // Retrieve the best ROC curve and delete the ROC curve identifier from the data points
    val expectedRocPointProps = expectedRocPoint.properties.get
    val expectedRocPointCurveId = expectedRocPointProps("roc_curve_id").asInstanceOf[Int]
    val bestRocCurve = rocCurves( expectedRocPointCurveId - 1 )
    expectedRocPointProps -= "roc_curve_id"
    
    // Set validation rules probability thresholds using the previously obtained expected ROC point
    val pepScoreThresholdOffset = expectedRocPointProps("pep_score_threshold_offset").asInstanceOf[Float]
    val protSetScoreThreshold = expectedRocPointProps("prot_set_score_threshold").asInstanceOf[Float]
    
    // Validate results with the p-value thresholds which provide the best results
    this.updateScoreOfProteinSets( targetProtSets, targetBestPepMatchesByProtSetId,
                                   pepScoreThresholdOffset, minPepSeqLength )
    val validTargetProtSets = this.validateProteinSets( targetProtSets, protSetScoreThreshold )
    val targetValidProteinSetCount = validTargetProtSets.length
    this.logger.info( targetValidProteinSetCount + " final target count" )
    
    this.updateScoreOfProteinSets( decoyProtSets, decoyBestPepMatchesByProtSetId,
                                   pepScoreThresholdOffset, minPepSeqLength )
    val validDecoyProtSets = this.validateProteinSets( decoyProtSets, protSetScoreThreshold )
    val decoyValidProteinSetCount = validDecoyProtSets.length
    this.logger.info( decoyValidProteinSetCount + " final decoy count" )
    
    val finalFdr = expectedRocPoint.fdr.get
    this.logger.info( "final fdr: " + finalFdr )
    
    ValidationResults( expectedRocPoint, Some(bestRocCurve) )
  }


  private def updateScoreOfProteinSets( proteinSets: Array[ProteinSet],
                                        bestPepMatchesByProtSetId: Map[Int,Array[PeptideMatch]],
                                        pepScoreThresholdOffset: Float,
                                        minPepSeqLength: Int ): Unit = {
    
    for( proteinSet <- proteinSets ) {
      
      val proteinSetId = proteinSet.id
      val bestPepMatches = bestPepMatchesByProtSetId(proteinSetId)
      val filteredBestPepMatches = bestPepMatches filter { pm => pm.peptide.sequence.length >= minPepSeqLength } 
      
      val protSetScore = MascotValidationHelper.sumPeptideMatchesScoreOffsets( filteredBestPepMatches, pepScoreThresholdOffset )
      proteinSet.score = protSetScore
      proteinSet.scoreType = "mascot:protein set score"
    }
    
  }
  
  private def validateProteinSets( proteinSets: Seq[ProteinSet], protSetScoreThreshold: Double ): Array[ProteinSet] = {
    
    val validProtSets = new ArrayBuffer[ProteinSet]
    for( proteinSet <- proteinSets ) {
      
      // Update protein set validity
      if( proteinSet.score > protSetScoreThreshold ) {
        proteinSet.isValidated = true
        validProtSets += proteinSet
      }
      else { proteinSet.isValidated = false }
    }
    
    validProtSets.toArray
  }

}