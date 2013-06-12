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
    pepMatchSorter: Option[IPeptideMatchSorter]): Option[ITargetDecoyAnalyzer] = {

    require(rs != null, "ResultSet is null")

    val rsId = rs.id
    val decoyRS = rs.decoyResultSet

    if ((decoyRS != null) && decoyRS.isDefined) {

      /*val tdAnalyzer = if (useTdCompetition) {
        // UseTdCompetition : Build target decoy analyzer if a peptide match validator is provided
        require(
          pepMatchSorter != null && pepMatchSorter.isDefined,
          "Invalid IPeptideMatchSorter for CompetitionBasedTDAnalyzer"
        )

        new CompetitionBasedTDAnalyzer(pepMatchSorter.get)
      } else {
        */
      val tdModeAsStrOpt = rs.getTargetDecoyMode
      require(tdModeAsStrOpt.isDefined, "ResultSet #" + rsId + " has no valid TargetDecoyMode Property")

      val tdMode = TargetDecoyModes.withName(tdModeAsStrOpt.get)

      val tdAnalyzer = if (tdMode == TargetDecoyModes.MIXED) {
        this.logger.warn("ResultSet #" + rsId + " has mixed target/decoy modes => competition based TD analyzer has to be used")
        // TODO: add to result set creation log
        //new CompetitionBasedTDAnalyzer(pepMatchSorter.get)
        new BasicTDAnalyzer(TargetDecoyModes.CONCATENATED) // arbitrary
      } else
        new BasicTDAnalyzer(tdMode)

      //}

      Some(tdAnalyzer)
    } else {
      logger.warn("ResultSet #" + rsId + " has no associated decoy ResultSet")

      None
    }

  }

}

abstract class ITargetDecoyAnalyzer {

  def calcTDStatistics(targetPepMatches: Seq[PeptideMatch], decoyPepMatches: Seq[PeptideMatch]): ValidationResult

  def updateValidationResult(oldValResult: ValidationResult,
                             pepMatchJointMap: Map[Long, Seq[PeptideMatch]],
                             newValidDecoyPSM: PeptideMatch): ValidationResult

  def calcTDStatistics(pepMatchJointMap: Map[Long, Seq[PeptideMatch]]): ValidationResult

  def performROCAnalysis(
    targetPepMatches: Seq[PeptideMatch],
    decoyPepMatches: Seq[PeptideMatch],
    validationFilter: IOptimizablePeptideMatchFilter): Array[ValidationResult]

}

abstract class AbstractTargetDecoyAnalyzer extends ITargetDecoyAnalyzer with Logging {

  private val MAX_FDR = 50f

  def performROCAnalysisV1(
    targetPepMatches: Seq[PeptideMatch],
    decoyPepMatches: Seq[PeptideMatch],
    validationFilter: IOptimizablePeptideMatchFilter): Array[ValidationResult] = {

    // Memorize validation status of peptide matches
    val allPepMatches = (targetPepMatches ++ decoyPepMatches)
    val pepMatchValStatusMap = PeptideMatchFiltering.getPepMatchValidationStatusMap(allPepMatches)

    // Build the peptide match joint map
    val pmJointMap = TargetDecoyComputer.buildPeptideMatchJointMap(targetPepMatches, Some(decoyPepMatches))
    logger.debug("ROCAnalysisV1 PeptideMatches joint Map size: " + pmJointMap.size)

    // Sort decoy PSMs from the best to the worst according to the validation filter
    val sortedDecoyPepMatches = validationFilter.sortPeptideMatches(decoyPepMatches)

    // Retrieve the worst decoy peptide match
    val worstDecoyPepMatch = sortedDecoyPepMatches.last

    // Initialize filterThreshold to the worst decoy value
    var filterThreshold = validationFilter.getPeptideMatchValueForFiltering(worstDecoyPepMatch)
    //var filterThreshold = validationFilter.getThresholdStartValue

    logger.debug("ROCAnalysisV1 entering FDR loop with initial filterThreshold: " + filterThreshold + " ...")

    val start = System.currentTimeMillis

    // Define some vars
    var fdr = 100.0f
    val rocPoints = new ArrayBuffer[ValidationResult]

    var loopCount: Int = 0

    while (fdr > 0) { // iterate from FDR = 100.0 to 0.0

      if ((loopCount % 10) == 0) {
        logger.debug("Starting ROCAnalysisV1 loop Pass: " + loopCount + " current FDR: " + fdr + " for threshold: " + filterThreshold)
      }

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
      logger.trace("New FDR = " + fdr + " for threshold = " + filterThreshold)

      // Update threshold value
      filterThreshold = validationFilter.getNextValue(filterThreshold)

      //if (filterThreshold == null) fdr = 0 //Break current loop

      loopCount += 1
    } //Go through all possible threshold value while FDR is greater than zero

    val end = System.currentTimeMillis
    logger.debug("ROCAnalysisV1 FDR loop completed in " + (end - start) + "ms, starting restorePepMatchValidationStatus ...")

    // Restore peptide matches validation status
    PeptideMatchFiltering.restorePepMatchValidationStatus(allPepMatches, pepMatchValStatusMap)

    logger.debug("ROCAnalysisV1 restorePepMatchValidationStatus done")

    rocPoints.toArray
  }

  // Note : this implementation seeems to be too long to process (too many decoy peptide matches)
  // TODO: remove me
  def performROCAnalysisV2(
    targetPepMatches: Seq[PeptideMatch],
    decoyPepMatches: Seq[PeptideMatch],
    validationFilter: IOptimizablePeptideMatchFilter): Array[ValidationResult] = {

    // Memorize validation status of peptide matches
    val allPepMatches = (targetPepMatches ++ decoyPepMatches)
    val pepMatchValStatusMap = PeptideMatchFiltering.getPepMatchValidationStatusMap(allPepMatches)

    // Build the peptide match joint map
    val pmJointMap = TargetDecoyComputer.buildPeptideMatchJointMap(targetPepMatches, Some(decoyPepMatches))
    logger.debug("ROCAnalysisV2 PeptideMatches joint Map size: " + pmJointMap.size)

    // Define some vars
    val rocPoints = new ArrayBuffer[ValidationResult]

    // Sort decoy PSMs from the best to the worst according to the validation filter
    val sortedDecoyPepMatches = validationFilter.sortPeptideMatches(decoyPepMatches)

    logger.debug("ROCAnalysisV2 entering sortedDecoyPepMatches loop ...")

    val start = System.currentTimeMillis

    // Iterate over sorted decoy PSMs => each PSM is taken as a new threshold (breaks if FDR is greater than 50%)
    breakable {
      for (threshDecoyPepMatch <- sortedDecoyPepMatches) {

        // Restore peptide matches validation status to include previous filtering steps
        PeptideMatchFiltering.restorePepMatchValidationStatus(allPepMatches, pepMatchValStatusMap)

        // Retrieve next filter threshold
        val thresholdValue = validationFilter.getPeptideMatchValueForFiltering(threshDecoyPepMatch)

        // Increase the threshold just a little bit in order to exclude the current decoy PSM (should maximize sensitivity)
        // and inject the obtained value in the validation filter
        validationFilter.setThresholdValue(validationFilter.getNextValue(thresholdValue))

        // Apply filter on target and decoy peptide matches
        validationFilter.filterPeptideMatches(allPepMatches, true, false)

        // Compute the ROC point
        val rocPoint = this.calcTDStatistics(pmJointMap)
        logger.trace("New FDR = " + rocPoint.fdr)

        // Set ROC point validation properties
        rocPoint.addProperties(validationFilter.getFilterProperties)

        // Add ROC point to the curve
        rocPoints += rocPoint

        // Breaks if current FDR equals zero
        if (rocPoint.fdr.isDefined && rocPoint.fdr.get > MAX_FDR) break

      }
    }

    val end = System.currentTimeMillis
    logger.debug("ROCAnalysisV2 sortedDecoyPepMatches loop completed in " + (end - start) + "ms, starting restorePepMatchValidationStatus ...")

    // Restore peptide matches validation status
    PeptideMatchFiltering.restorePepMatchValidationStatus(allPepMatches, pepMatchValStatusMap)

    logger.debug("ROCAnalysisV2 restorePepMatchValidationStatus done")

    rocPoints.toArray
  }

  def performROCAnalysis(
    targetPepMatches: Seq[PeptideMatch],
    decoyPepMatches: Seq[PeptideMatch],
    validationFilter: IOptimizablePeptideMatchFilter): Array[ValidationResult] = {

    // Build the peptide match joint map
    val pmJointMap = TargetDecoyComputer.buildPeptideMatchJointMap(targetPepMatches, Some(decoyPepMatches))
    logger.debug("ROCAnalysis0 PeptideMatches joint Map size: " + pmJointMap.size)

    // Memorize validation status of peptide matches
    val allPepMatches = (targetPepMatches ++ decoyPepMatches)
    val pepMatchValStatusMap = PeptideMatchFiltering.getPepMatchValidationStatusMap(allPepMatches)

    // Retrieve filtered peptide matches
    val filteredPepMatches = allPepMatches.filter(_.isValidated)

    // Sort all filtered PSMs from the best to the worst according to the validation filter
    val sortedPepMatches = validationFilter.sortPeptideMatches(filteredPepMatches)

    // Set thr filter threshold
    validationFilter.setThresholdValue(validationFilter.getPeptideMatchValueForFiltering(sortedPepMatches(0)))

    logger.debug("ROCAnalysis0 applying filters on Target & Decoy PSMs ...")

    // Apply filter on target and decoy peptide matches
    validationFilter.filterPeptideMatches(filteredPepMatches, true, false)

    logger.debug("ROCAnalysis0 entering sortedPepMatches loop ...")

    // Initialize the ROC point
    var curRocPoint = this.calcTDStatistics(pmJointMap)

    // Define some vars
    val rocPoints = new ArrayBuffer[ValidationResult]

    val start = System.currentTimeMillis

    // Iterate over sorted decoy PSMs => each PSM is taken as a new threshold (breaks if FDR is greater than 50%)
    breakable {
      for (curPepMatch <- sortedPepMatches) {

        // Set ROC point validation properties
        validationFilter.setThresholdValue(validationFilter.getPeptideMatchValueForFiltering(curPepMatch))

        // Initialize the ROC point
        if (curRocPoint == null) {
          // Apply filter on target and decoy peptide matches
          validationFilter.filterPeptideMatches(allPepMatches, true, false)
          curRocPoint = this.calcTDStatistics(pmJointMap)
        }

        curRocPoint.addProperties(validationFilter.getFilterProperties)

        // Add ROC point to the curve
        rocPoints += curRocPoint

        // Compute the next ROC point
        curRocPoint = this.updateValidationResult(curRocPoint, pmJointMap, curPepMatch)
        logger.trace("New FDR = " + curRocPoint.fdr)

        // Breaks if current FDR equals zero
        if (curRocPoint.fdr.isDefined && curRocPoint.fdr.get > MAX_FDR) break

      }
    }

    val end = System.currentTimeMillis
    logger.debug("ROCAnalysis0 sortedPepMatches loop completed in " + (end - start) + "ms, starting restorePepMatchValidationStatus ...")

    // Restore peptide matches validation status
    PeptideMatchFiltering.restorePepMatchValidationStatus(filteredPepMatches, pepMatchValStatusMap)

    logger.debug("ROCAnalysis0 restorePepMatchValidationStatus done")

    rocPoints.toArray
  }

}

class BasicTDAnalyzer(targetDecoyMode: TargetDecoyModes.Value) extends AbstractTargetDecoyAnalyzer {

  def calcTDStatistics(targetPepMatches: Seq[PeptideMatch], decoyPepMatches: Seq[PeptideMatch]): ValidationResult = {

    val targetMatchesCount = targetPepMatches.count(_.isValidated)
    val decoyMatchesCount = decoyPepMatches.count(_.isValidated)

    this.calcTDStatistics(targetMatchesCount, decoyMatchesCount)

  }

  protected def calcTDStatistics(targetMatchesCount: Int, decoyMatchesCount: Int): ValidationResult = {

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

  def calcTDStatistics(pepMatchJointMap: Map[Long, Seq[PeptideMatch]]): ValidationResult = {
    val allPepMatches = pepMatchJointMap.flatMap(_._2).toSeq
    val (decoyPepMatches, targetPepMatches) = allPepMatches.partition(_.isDecoy)

    this.calcTDStatistics(targetPepMatches, decoyPepMatches)
  }

  def updateValidationResult(oldValResult: ValidationResult,
                             pepMatchJointMap: Map[Long, Seq[PeptideMatch]],
                             newValidPepMatch: PeptideMatch): ValidationResult = {

    // Retrieve old counts
    var (targetMatchesCount, decoyMatchesCount) = (oldValResult.targetMatchesCount, oldValResult.decoyMatchesCount.get)

    if (newValidPepMatch.isDecoy) decoyMatchesCount += 1
    else targetMatchesCount += 1

    this.calcTDStatistics(targetMatchesCount, decoyMatchesCount)
  }

}


// FIXME: remove this comment this when we are sure it is used with a rank 1 filter
/*
class CompetitionBasedTDAnalyzer(val psmSorter: IPeptideMatchSorter) extends AbstractTargetDecoyAnalyzer {

  def calcTDStatistics(targetPepMatches: Seq[PeptideMatch], decoyPepMatches: Seq[PeptideMatch]): ValidationResult = {
    val pmJointMap = TargetDecoyComputer.buildPeptideMatchJointMap(targetPepMatches, Some(decoyPepMatches))
    this.calcTDStatistics(pmJointMap)
  }

  def calcTDStatistics(pepMatchJointMap: Map[Long, Seq[PeptideMatch]]): ValidationResult = {
    TargetDecoyComputer.createCompetitionBasedValidationResult(pepMatchJointMap, psmSorter)
  }
  
  def updateValidationResult( oldValResult: ValidationResult,
                              pepMatchJointMap: Map[Int, Seq[PeptideMatch]],
                              msQueryId: Int,
                              newValidDecoyPSM: PeptideMatch
                             ): ValidationResult = {
    null
  }

}*/