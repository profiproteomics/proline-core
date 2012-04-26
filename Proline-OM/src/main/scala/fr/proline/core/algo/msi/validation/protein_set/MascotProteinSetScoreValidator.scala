package fr.proline.core.algo.msi.validation.protein_set

import math.{sqrt,abs}
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap

import fr.proline.core.algo.msi.validation._
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.om.model.msi.ProteinSet
import fr.proline.core.om.model.msi.PeptideMatch

class MascotProteinSetScoreValidator extends IProteinSetValidator {

  def validateWithComputerParams( validationParams: ComputerValidationParams,
                                  targetRsm: ResultSummary,
                                  decoyRsm: ResultSummary
                                ): ValidationResults = {
    
    var( targetProtSets, decoyProtSets ) = ( targetRsm.proteinSets, decoyRsm.proteinSets )
    val targetBestPepMatchesByProtSetId = targetRsm.getBestPepMatchesByProtSetId
    val decoyBestPepMatchesByProtSetId = decoyRsm.getBestPepMatchesByProtSetId
    
    // Retrieve some vars
    val wantedFdr = validationParams.wantedFdr
    val valResultProps = targetRsm.validationProperties("results").asInstanceOf[Map[String,Any]]
    val pepMatchValidationProps = valResultProps("peptide_matches").asInstanceOf[Map[String,Any]]
    var pValueThreshold = pepMatchValidationProps("p_value_threshold").asInstanceOf[Float]
    
    // Compute initial p-value threshold: we just need something slightly greater than the peptide validation one
    pValueThreshold = sqrt(pValueThreshold).toFloat
    
    val minPepSeqLength = validationParams.minPepSeqLength
    var currentFdr = 100.0f
    val maxFdr = wantedFdr * 1.2 // 20% of FDR
    var rocCurves = new ArrayBuffer[ArrayBuffer[ValidationResult]]
    val wantedFdrRocPoints = new ArrayBuffer[ValidationResult](0)
    var rocCurveId = 0
    
    // TODO: another algo could start by normalizing peptide match scores :
    // 1) translates score = norm_score = score - threshold
    // 2) min norm_score =0 = norm_score -= min(norm_scores)
    // 3) prot score score = sum(norm_scores)
    // 4) compute the score threshold to obtain the wanted FDR
    // End of TO DO
    
    var lowestProtSetScoreThreshold = 0.0f
    var reachedWantedFdr = false
    while( currentFdr > 0 && ! reachedWantedFdr ) {
      rocCurveId += 1
      
      println( "p-value threshold: " + pValueThreshold )
      
      // Compute score threshold offset with a reference p-value of 0.05 (default p-value used to compute Mascot thresholds)
      val pepScoreThresholdOffset = MascotValidationHelper.calcScoreThresholdOffset( pValueThreshold, 0.05 )
      println( "peptide match score threshold delta:" + pepScoreThresholdOffset )
      
      this.updateScoreOfProteinSets( targetProtSets, targetBestPepMatchesByProtSetId,
                                     pepScoreThresholdOffset, minPepSeqLength )
      this.updateScoreOfProteinSets( decoyProtSets, decoyBestPepMatchesByProtSetId,
                                     pepScoreThresholdOffset, minPepSeqLength )
      
      var rocPoints = new ArrayBuffer[ValidationResult]
      var wantedFdrRocPoint: ValidationResult = null
      var( protSetScoreThreshold, rocPointId ) = (0.0f,0)
      
      while( wantedFdrRocPoint == null ) {
        rocPointId += 1
        
        println( "protein set score threshold: " + protSetScoreThreshold )
        
        val validTargetProtSets = this.validateProteinSets( targetProtSets, protSetScoreThreshold )
        val targetValidProteinSetCount = validTargetProtSets.length
        println( targetValidProteinSetCount + " target" )
        
        val validDecoyProtSets = this.validateProteinSets( decoyProtSets, protSetScoreThreshold )
        val decoyValidProteinSetCount = validDecoyProtSets.length
        println( decoyValidProteinSetCount + " decoy" )
        
        currentFdr = 100 * decoyValidProteinSetCount / targetValidProteinSetCount
        println( "current fdr: " + currentFdr )
        
        val rocPoint = ValidationResult(  //id = rocPointId,
                                          nbTargetMatches = targetValidProteinSetCount,
                                          nbDecoyMatches = Some(decoyValidProteinSetCount),
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
          wantedFdrRocPoint = rocPoint
          wantedFdrRocPoints += rocPoint
        }
        
        //protSetScoreThreshold += sprintf("%.1f",abs(currentFdr-wantedFdr)/5)
        protSetScoreThreshold += (sqrt( 1 + abs(currentFdr-wantedFdr) ) - 1).toFloat
        
      }
      
      rocCurves += rocPoints
      
      // Check if we have reached the wanted FDR
      //if( !defined wantedRocPoint and currentFdr <= maxFdr ) {
      if( protSetScoreThreshold > lowestProtSetScoreThreshold ) {
        reachedWantedFdr = true
      }
      else {
        lowestProtSetScoreThreshold = protSetScoreThreshold
        
        // Update p-value threshold
        pValueThreshold *= 0.90f
      }
    }
    
    val wantedRocPoint = wantedFdrRocPoints.reduce { (a,b) => if( a.nbTargetMatches >= b.nbTargetMatches ) a else b } 
    println(wantedRocPoint)
    
    // Retrieve the best ROC curve and delete the ROC curve identifier from the data points
    val wantedRocPointProps = wantedRocPoint.properties.get
    val wantedRocPointCurveId = wantedRocPointProps("roc_curve_id").asInstanceOf[Int]
    val bestRocCurve = rocCurves( wantedRocPointCurveId - 1 )
    wantedRocPointProps -= "roc_curve_id"
    
    // Set validation rules probability thresholds using the previously obtained wanted ROC point
    val pepScoreThresholdOffset = wantedRocPointProps("pep_score_threshold_offset").asInstanceOf[Float]
    val protSetScoreThreshold = wantedRocPointProps("prot_set_score_threshold").asInstanceOf[Float]
    
    // Validate results with the p-value thresholds which provide the best results
    this.updateScoreOfProteinSets( targetProtSets, targetBestPepMatchesByProtSetId,
                                   pepScoreThresholdOffset, minPepSeqLength )
    val validTargetProtSets = this.validateProteinSets( targetProtSets, protSetScoreThreshold )
    val targetValidProteinSetCount = validTargetProtSets.length
    println( targetValidProteinSetCount + " final target count" )
    
    this.updateScoreOfProteinSets( decoyProtSets, decoyBestPepMatchesByProtSetId,
                                   pepScoreThresholdOffset, minPepSeqLength )
    val validDecoyProtSets = this.validateProteinSets( decoyProtSets, protSetScoreThreshold )
    val decoyValidProteinSetCount = validDecoyProtSets.length
    println( decoyValidProteinSetCount + " final decoy count" )
    
    val finalFdr = wantedRocPoint.fdr.get
    print( "final fdr: " + finalFdr )
    
    ValidationResults( wantedRocPoint, Some(bestRocCurve) )
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
      proteinSet.scoreType = "mascot:prosper score"
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