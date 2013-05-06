package fr.proline.core.algo.msi.validation

import scala.collection.mutable.ArrayBuffer
import scala.util.control.Breaks._

import com.codahale.jerkson.JsonSnakeCase
import com.fasterxml.jackson.annotation.JsonInclude
import com.weiglewilczek.slf4s.Logging

import fr.proline.core.algo.msi.filtering.{ IOptimizablePeptideMatchFilter, IPeptideMatchSorter, PeptideMatchFiltering }
import fr.proline.core.om.model.msi.{ PeptideMatch, ResultSet }

object BuildTDAnalyzer extends Logging {

  def apply(
    useTdCompetition: Boolean,
    rs: ResultSet,
    pepMatchSorter: Option[IPeptideMatchSorter]
  ): Option[ITargetDecoyAnalyzer] = {
    
    require(rs != null, "ResultSet is null")

    val rsId = rs.id
    val decoyRS = rs.decoyResultSet
    
    if ((decoyRS != null) && decoyRS.isDefined) {

      val tdAnalyzer = if (useTdCompetition) {
        // UseTdCompetition : Build target decoy analyzer if a peptide match validator is provided
        require(
          pepMatchSorter != null && pepMatchSorter.isDefined,
          "Invalid IPeptideMatchSorter for CompetitionBasedTDAnalyzer"
        )

        new CompetitionBasedTDAnalyzer(pepMatchSorter.get)
      } else {
        
        val tdModeAsStrOpt = rs.getTargetDecoyMode
        require( tdModeAsStrOpt.isDefined, "ResultSet #" + rsId + " has no valid TargetDecoyMode Property")
        
        val tdMode = TargetDecoyModes.withName(tdModeAsStrOpt.get)
        
        if( tdMode == TargetDecoyModes.MIXED ) {
          this.logger.warn("ResultSet #" + rsId + "has mixed target/decoy modes => competition based TD analyzer has to be used")
          new CompetitionBasedTDAnalyzer(pepMatchSorter.get)
        } else
          new BasicTDAnalyzer(tdMode)
        
      }

      Some(tdAnalyzer)
    } else {
      logger.warn("ResultSet #" + rsId + " has no associated decoy ResultSet")

      None
    }

  }

}

abstract class ITargetDecoyAnalyzer {

  def calcTDStatistics(targetPepMatches: Seq[PeptideMatch], decoyPepMatches: Seq[PeptideMatch]): ValidationResult

  def calcTDStatistics(pepMatchJointMap: Map[Int, Seq[PeptideMatch]]): ValidationResult

  def performROCAnalysis(
    targetPepMatches: Seq[PeptideMatch],
    decoyPepMatches: Seq[PeptideMatch],
    validationFilter: IOptimizablePeptideMatchFilter): Array[ValidationResult]

}

abstract class AbstractTargetDecoyAnalyzer extends ITargetDecoyAnalyzer with Logging {

  def performROCAnalysisV1(
    targetPepMatches: Seq[PeptideMatch],
    decoyPepMatches: Seq[PeptideMatch],
    validationFilter: IOptimizablePeptideMatchFilter): Array[ValidationResult] = {

    // Memorize validation status of peptide matches
    val allPepMatches = (targetPepMatches ++ decoyPepMatches)
    val pepMatchValStatusMap = PeptideMatchFiltering.getPepMatchValidationStatusMap(allPepMatches)

    // Build the peptide match joint map
    val pmJointMap = TargetDecoyComputer.buildPeptideMatchJointMap(targetPepMatches, Some(decoyPepMatches))

    // Define some vars
    var filterThreshold = validationFilter.getThresholdStartValue
    var fdr = 100.0f
    val rocPoints = new ArrayBuffer[ValidationResult]
    logger.debug(" # Map entries " + pmJointMap.size)

    while (fdr > 0) { // iterate from FDR = 100.0 to 0.0

      // Restore peptide matches validation status to include previous filtering steps
      PeptideMatchFiltering.restorePepMatchValidationStatus(allPepMatches, pepMatchValStatusMap)

      // Update filter threshold
      validationFilter.setThresholdValue(filterThreshold)

      // Apply filter on target and decoy peptide matches
      validationFilter.filterPeptideMatches(allPepMatches, true, false)

      // Compute the ROC point
      val rocPoint = this.calcTDStatistics(pmJointMap)

      // Set ROC point validation properties
      rocPoint.addProperties(validationFilter.getFilterProperties)

      // Add ROC point to the curve
      rocPoints += rocPoint

      // Update the current FDR
      if (rocPoint.fdr.isDefined) fdr = rocPoint.fdr.get
      //      logger.debug("New FDR = "+fdr + " for threshold = "+filterThreshold)

      // Update threshold value
      filterThreshold = validationFilter.getNextValue(filterThreshold)

      //if (filterThreshold == null) fdr = 0 //Break current loop

    } //Go through all possible threshold value while FDR is greater than zero

    // Restore peptide matches validation status
    PeptideMatchFiltering.restorePepMatchValidationStatus(allPepMatches, pepMatchValStatusMap)

    rocPoints.toArray
  }
  
  def performROCAnalysis(
    targetPepMatches: Seq[PeptideMatch],
    decoyPepMatches: Seq[PeptideMatch],
    validationFilter: IOptimizablePeptideMatchFilter): Array[ValidationResult] = {
    
    // Memorize validation status of peptide matches
    val allPepMatches = (targetPepMatches ++ decoyPepMatches)
    val pepMatchValStatusMap = PeptideMatchFiltering.getPepMatchValidationStatusMap(allPepMatches)

    // Build the peptide match joint map
    val pmJointMap = TargetDecoyComputer.buildPeptideMatchJointMap(targetPepMatches, Some(decoyPepMatches))
    logger.debug(" # Map entries " + pmJointMap.size)
    
    // Define some vars
    val rocPoints = new ArrayBuffer[ValidationResult]    
    
    // Sort decoy PSMs from the best to the worst according to the validation filter
    val sortedDecoyPepMatches = validationFilter.sortPeptideMatches(decoyPepMatches)
    
    // Iterate over sorted decoy PSMs => each PSM is taken as a new threshold (breaks if FDR is greater than 50%)
    breakable {
      for( threshDecoyPepMatch <- sortedDecoyPepMatches ) {
        
        // Restore peptide matches validation status to include previous filtering steps
        PeptideMatchFiltering.restorePepMatchValidationStatus(allPepMatches, pepMatchValStatusMap)
        
        // Update filter threshold
        val thresholdValue = validationFilter.getPeptideMatchValueForFiltering(threshDecoyPepMatch)
        
        // Increase the threshold just a little bit in order to exclude the current decoy PSM (should maximize sensitivity)
        // and inject the obtained value in the validation filter
        validationFilter.setThresholdValue(validationFilter.getNextValue(thresholdValue))
        
        // Apply filter on target and decoy peptide matches
        validationFilter.filterPeptideMatches(allPepMatches, true, false)
        
        // Compute the ROC point
        val rocPoint = this.calcTDStatistics(pmJointMap)
        
        // Set ROC point validation properties
        rocPoint.addProperties(validationFilter.getFilterProperties)
        
        // Add ROC point to the curve
        rocPoints += rocPoint
        
        // Breaks if current FDR equals zero
        if (rocPoint.fdr.isDefined && rocPoint.fdr.get > 50f) break
        
      }
    }
    
    // Restore peptide matches validation status
    PeptideMatchFiltering.restorePepMatchValidationStatus(allPepMatches, pepMatchValStatusMap)

    rocPoints.toArray
  }

}

class BasicTDAnalyzer(targetDecoyMode: TargetDecoyModes.Value) extends AbstractTargetDecoyAnalyzer {

  def calcTDStatistics(targetPepMatches: Seq[PeptideMatch], decoyPepMatches: Seq[PeptideMatch]): ValidationResult = {

    val targetMatchesCount = targetPepMatches.count(_.isValidated)
    val decoyMatchesCount = decoyPepMatches.count(_.isValidated)

    val fdr = if (targetMatchesCount == 0) Float.NaN
    else {
      targetDecoyMode match {
        case TargetDecoyModes.CONCATENATED => TargetDecoyComputer.calcCdFDR(targetMatchesCount, decoyMatchesCount)
        case TargetDecoyModes.SEPARATED    => TargetDecoyComputer.calcSdFDR(targetMatchesCount, decoyMatchesCount)
        case _                             => throw new Exception("unsupported target decoy mode: " + targetDecoyMode)
      }
    }

    ValidationResult(
      targetMatchesCount = targetMatchesCount,
      decoyMatchesCount = Some(decoyMatchesCount),
      fdr = if (fdr.isNaN) None else Some(fdr)
    )

  }

  def calcTDStatistics(pepMatchJointMap: Map[Int, Seq[PeptideMatch]]): ValidationResult = {
    val allPepMatches = pepMatchJointMap.flatMap(_._2).toSeq
    val (decoyPepMatches, targetPepMatches) = allPepMatches.partition(_.isDecoy)

    this.calcTDStatistics(targetPepMatches, decoyPepMatches)
  }

}

class CompetitionBasedTDAnalyzer(val psmSorter: IPeptideMatchSorter) extends AbstractTargetDecoyAnalyzer {

  def calcTDStatistics(targetPepMatches: Seq[PeptideMatch], decoyPepMatches: Seq[PeptideMatch]): ValidationResult = {
    val pmJointMap = TargetDecoyComputer.buildPeptideMatchJointMap(targetPepMatches, Some(decoyPepMatches))
    this.calcTDStatistics(pmJointMap)
  }

  def calcTDStatistics(pepMatchJointMap: Map[Int, Seq[PeptideMatch]]): ValidationResult = {
    TargetDecoyComputer.createCompetitionBasedValidationResult(pepMatchJointMap, psmSorter)
  }

}