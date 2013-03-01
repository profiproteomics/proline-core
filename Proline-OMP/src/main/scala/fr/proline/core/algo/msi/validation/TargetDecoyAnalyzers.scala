package fr.proline.core.algo.msi.validation

import scala.collection.mutable.ArrayBuffer
import fr.proline.core.om.model.msi.PeptideMatch
import fr.proline.core.algo.msi.filtering._
import com.weiglewilczek.slf4s.Logging

object BuildTDAnalyzer {
  
  import fr.proline.core.om.model.msi.ResultSet
  
  def apply(
    useTdCompetition: Boolean,
    rs: ResultSet,
    pepMatchValidator: Option[IPeptideMatchValidator] ): Option[ITargetDecoyAnalyzer] = {
    
    // Build target decoy analyzer if a peptide match validator is provided
    if( rs.decoyResultSet != null && rs.decoyResultSet.isDefined && pepMatchValidator.isDefined ) {
      val tdAnalyzer = if( useTdCompetition == false ) {
        val ssProps = rs.msiSearch.searchSettings.properties.get
        val tdModeAsStr = ssProps.getTargetDecoyMode.getOrElse(
          throw new Exception("missing target/decoy mode in search settings properties")
        )
        val tdMode = TargetDecoyModes.withName(tdModeAsStr)
        new BasicTDAnalyzer(tdMode)
      }
      else  {        
        new CompetitionBasedTDAnalyzer(
          pepMatchValidator.get.validationFilter.asInstanceOf[IOptimizablePeptideMatchFilter]
        )
      }
      Some(tdAnalyzer)
    } else None
  }
}

abstract class ITargetDecoyAnalyzer {
  
  def calcTDStatistics( targetPepMatches: Seq[PeptideMatch], decoyPepMatches: Seq[PeptideMatch] ): ValidationResult
  
  def calcTDStatistics( pepMatchJointMap: Map[Int, Seq[PeptideMatch]]): ValidationResult
  
  def performROCAnalysis(
    targetPepMatches: Seq[PeptideMatch], 
    decoyPepMatches: Seq[PeptideMatch], 
    validationFilter: IOptimizablePeptideMatchFilter
  ): Array[ValidationResult]
  
}

abstract class AbstractTargetDecoyAnalyzer extends ITargetDecoyAnalyzer with Logging {
  
  def performROCAnalysis(
    targetPepMatches: Seq[PeptideMatch], 
    decoyPepMatches: Seq[PeptideMatch], 
    validationFilter: IOptimizablePeptideMatchFilter
  ): Array[ValidationResult] = {
    
    // Memorize validation status of peptide matches
    val allPepMatches = ( targetPepMatches ++ decoyPepMatches )
    val pepMatchValStatusMap = PeptideMatchFiltering.getPepMatchValidationStatusMap(allPepMatches)
    
    // Build the peptide match joint map
    val pmJointMap = TargetDecoyComputer.buildPeptideMatchJointMap(targetPepMatches, Some(decoyPepMatches))
    
    // Define some vars
    var filterThreshold = validationFilter.getThresholdStartValue
    var fdr = 100.0f
    val rocPoints = new ArrayBuffer[ValidationResult]
    logger.debug(" # Map entries "+pmJointMap.size)
	
    while (fdr > 0) { // iterate from FDR = 100.0 to 0.0
          
      // Restore peptide matches validation status to include previous filtering steps
      PeptideMatchFiltering.restorePepMatchValidationStatus(allPepMatches,pepMatchValStatusMap)
      
      // Update filter threshold
      validationFilter.setThresholdValue(filterThreshold)
      
      // Apply filter on target and decoy peptide matches
      validationFilter.filterPeptideMatches(allPepMatches, true, false)
      
      // Compute the ROC point
      val rocPoint = this.calcTDStatistics(pmJointMap)
      
      // Set ROC point validation properties
      rocPoint.addProperties( validationFilter.getFilterProperties )
      
      // Add ROC point to the curve
      rocPoints += rocPoint
      
      // Update the current FDR
      if( rocPoint.fdr.isDefined ) fdr = rocPoint.fdr.get
//      logger.debug("New FDR = "+fdr + " for threshold = "+filterThreshold)
      
      // Update threshold value
      filterThreshold = validationFilter.getNextValue(filterThreshold)
      
      //if (filterThreshold == null) fdr = 0 //Break current loop

    } //Go through all possible threshold value while FDR is greater than zero
    
    // Restore peptide matches validation status
    PeptideMatchFiltering.restorePepMatchValidationStatus(allPepMatches,pepMatchValStatusMap)
    
    rocPoints.toArray
  }
  
}

class BasicTDAnalyzer( targetDecoyMode: TargetDecoyModes.Value ) extends AbstractTargetDecoyAnalyzer {

  def calcTDStatistics( targetPepMatches: Seq[PeptideMatch], decoyPepMatches: Seq[PeptideMatch] ): ValidationResult = {
    
    val targetMatchesCount = targetPepMatches.count( _.isValidated )
    val decoyMatchesCount = decoyPepMatches.count( _.isValidated )
    
    val fdr = if( targetMatchesCount == 0 ) Float.NaN
    else {
      targetDecoyMode match {
        case TargetDecoyModes.CONCATENATED => TargetDecoyComputer.calcCdFDR(targetMatchesCount, decoyMatchesCount)
        case TargetDecoyModes.SEPARATED    => TargetDecoyComputer.calcSdFDR(targetMatchesCount, decoyMatchesCount)
        case _                             => throw new Exception("unknown target decoy mode: " + targetDecoyMode)
      }
    }
    
    ValidationResult(
      targetMatchesCount = targetMatchesCount,
      decoyMatchesCount = Some(decoyMatchesCount),
      fdr = if( fdr.isNaN ) None else Some(fdr)
    )
    
  }
  
  def calcTDStatistics( pepMatchJointMap: Map[Int, Seq[PeptideMatch]]): ValidationResult = {
    val allPepMatches = pepMatchJointMap.flatMap( _._2 ).toSeq
    val( decoyPepMatches, targetPepMatches ) = allPepMatches.partition( _.isDecoy)
    
    this.calcTDStatistics(targetPepMatches, decoyPepMatches )
  }
  
}

class CompetitionBasedTDAnalyzer( val validationFilter: IPeptideMatchFilter ) extends AbstractTargetDecoyAnalyzer {

  def calcTDStatistics( targetPepMatches: Seq[PeptideMatch], decoyPepMatches: Seq[PeptideMatch] ): ValidationResult = {    
    val pmJointMap = TargetDecoyComputer.buildPeptideMatchJointMap(targetPepMatches,Some(decoyPepMatches))
    this.calcTDStatistics( pmJointMap )
  }
  
  def calcTDStatistics( pepMatchJointMap: Map[Int, Seq[PeptideMatch]]): ValidationResult = {
    TargetDecoyComputer.createCompetitionBasedValidationResult(pepMatchJointMap, validationFilter)
  }
  
}