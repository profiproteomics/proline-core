package fr.proline.core.algo.msi.validation

import com.typesafe.scalalogging.LazyLogging
import fr.profi.util.primitives._
import fr.proline.core.algo.msi.filtering._
import fr.proline.core.algo.msi.filtering.pepmatch.MascotPValuePSMFilter
import fr.proline.core.om.model.msi.{PeptideMatch, ResultSet}

import scala.collection.mutable.ArrayBuffer


class TDAnalyzerBuilder(
    var analyzer: TargetDecoyAnalyzers.Analyzer,
    var estimator: Option[TargetDecoyEstimators.Estimator] = None,
    var params: Option[Map[String, AnyVal]] = None
) extends LazyLogging {

  def build(targetRs: Option[ResultSet]): Option[ITargetDecoyAnalyzer] = {

    require(targetRs.isDefined || estimator.isDefined, "An estimator or a ResultSet must be defined to build a TDAnalyzer")

    val tdMode = {
      if (targetRs.isDefined) {
        val rsId = targetRs.get.id
        val decoyRS = targetRs.get.decoyResultSet
        require((decoyRS != null) && decoyRS.isDefined, "ResultSet #" + rsId + " has no Decoy Result Set")
        val tdModeAsStrOpt = targetRs.get.getTargetDecoyMode()
        require(tdModeAsStrOpt.isDefined, "ResultSet #" + rsId + " has no valid TargetDecoyMode Property")
        Some(TargetDecoyModes.withName(tdModeAsStrOpt.get))
      } else {
        None
      }
    }

    val tdAnalyzer = {
      if (analyzer == TargetDecoyAnalyzers.GORSHKOV) {
        val ratio = toFloat(params.getOrElse(Map.empty).getOrElse("ratio", "1.0").asInstanceOf[AnyVal])
        Some(new GorshkovTDAnalyzer(ratio))
      } else if (tdMode.isDefined && tdMode.get == TargetDecoyModes.MIXED) {
        this.logger.warn("Mixed target/decoy modes detected => competition based TD analyzer has to be used")
        // TODO: add to result set creation log
        Some(new BasicTDAnalyzer(TargetDecoyComputer.calcCdFDR)) // arbitrary decision
      } else {
        estimator match {
          case Some(TargetDecoyEstimators.KALL_STOREY_COMPUTER) => Some(new BasicTDAnalyzer(TargetDecoyComputer.calcSdFDR))
          case Some(TargetDecoyEstimators.GIGY_COMPUTER) => Some(new BasicTDAnalyzer(TargetDecoyComputer.calcCdFDR))
          case None => {
            // No estimator specified: use auto determination based on the targetRs mode
            tdMode match {
              case Some(TargetDecoyModes.CONCATENATED) => Some(new BasicTDAnalyzer(TargetDecoyComputer.calcCdFDR))
              case Some(TargetDecoyModes.SEPARATED) => Some(new BasicTDAnalyzer(TargetDecoyComputer.calcSdFDR))
              case None => None
            }
          }
        }
      }
    }

    logger.info("Built Target Decoy Analyzer: " + tdAnalyzer.toString)
    tdAnalyzer
  }

}

abstract class ITargetDecoyAnalyzer {

  def calcTDStatistics(targetPepMatches: Seq[PeptideMatch], decoyPepMatches: Seq[PeptideMatch]): ValidationResult

  def calcTDStatistics(pepMatchJointMap: Map[Long, Seq[PeptideMatch]]): ValidationResult

  def calcTDStatistics(targetMatchesCount: Int, decoyMatchesCount: Int): ValidationResult

  def performROCAnalysis(
    targetPepMatches: Seq[PeptideMatch],
    decoyPepMatches: Seq[PeptideMatch],
    validationFilter: IOptimizablePeptideMatchFilter
  ): Array[ValidationResult]

}

abstract class AbstractTargetDecoyAnalyzer extends ITargetDecoyAnalyzer with LazyLogging {

  private val MAX_FDR = 50f

  //FIXME VDS : Use this algo for MascotPValuePSMFilter...
  // Revert change : get threshold start Value from  getThresholdStartValue instead of Initialize filterThreshold to the worst decoy value

  def performROCAnalysisV1(
    targetPepMatches: Seq[PeptideMatch],
    decoyPepMatches: Seq[PeptideMatch],
    validationFilter: IOptimizablePeptideMatchFilter
  ): Array[ValidationResult] = {

    // Memorize validation status of peptide matches
    val allPepMatches = targetPepMatches ++ decoyPepMatches
    val pepMatchValStatusMap = PeptideMatchFiltering.getPepMatchValidationStatusMap(allPepMatches)

    // Build the peptide match joint map
    val pmJointMap = TargetDecoyComputer.buildPeptideMatchJointMap(targetPepMatches, Some(decoyPepMatches))
    logger.debug("ROCAnalysisV1 PeptideMatches joint Map size: " + pmJointMap.size)

    var filterThreshold = validationFilter.getThresholdStartValue()

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
      validationFilter.filterPeptideMatches(allPepMatches, incrementalValidation = true, traceability = false)

      // Compute the ROC point
      val rocPoint = this.calcTDStatistics(pmJointMap)

      // Set ROC point validation properties
      rocPoint.addProperties(validationFilter.getFilterProperties())

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


  def performROCAnalysis(
    targetPepMatches: Seq[PeptideMatch],
    decoyPepMatches: Seq[PeptideMatch],
    validationFilter: IOptimizablePeptideMatchFilter
  ): Array[ValidationResult] = {

    // Return empty validation result if no target/decoy peptide match is provided
    if( targetPepMatches.isEmpty || decoyPepMatches.isEmpty ) {
      return Array.empty[ValidationResult]
    }

    // FIXME VDS: Use performROCAnalysisV1 algo for MascotPValuePSMFilter... If this FDR optimization on PValue seems to be interesting, implemet better fix !
    if(validationFilter.isInstanceOf[MascotPValuePSMFilter]){
      return performROCAnalysisV1(targetPepMatches, decoyPepMatches, validationFilter)
    }

    // Build the peptide match joint map
    //val pmJointMap = TargetDecoyComputer.buildPeptideMatchJointMap(targetPepMatches, Some(decoyPepMatches))
    //logger.debug("performROCAnalysis => PeptideMatches joint Map size: " + pmJointMap.size)

    // Remember validation status of peptide matches
    val allPepMatches = targetPepMatches ++ decoyPepMatches
    val pepMatchValStatusMap: Map[Long, Boolean] = PeptideMatchFiltering.getPepMatchValidationStatusMap(allPepMatches)

    // Retrieve filtered peptide matches
    // TODO: use selection levels instead of isValidated boolean value
    val filteredPepMatches = allPepMatches.filter(_.isValidated)
    if (filteredPepMatches.isEmpty) {
      return Array.empty[ValidationResult]
    }

    // Sort all filtered PSMs from the best to the worst according to the validation filter
    val sortedPepMatches = validationFilter.sortPeptideMatches(filteredPepMatches)
    val sortedDistinctThresholds = sortedPepMatches.map( validationFilter.getPeptideMatchValueForFiltering(_) ).distinct

    // Set the filter threshold
    validationFilter.setThresholdValue(sortedDistinctThresholds.head)

    // Group peptide matches by their threshold value
    // This allows to handle the case where several peptide matches are validated with the same threshold value
    val filteredPepMatchesGroupedByThreshold = filteredPepMatches.groupBy { pm =>
      validationFilter.getPeptideMatchValueForFiltering(pm)
    }

    logger.debug("performROCAnalysis => applying filters on Target & Decoy PSMs ..."+filteredPepMatches.length+" PSMS with "+filteredPepMatchesGroupedByThreshold.size+" different threshold value")

    // Apply filter on target and decoy peptide matches
    validationFilter.filterPeptideMatches(filteredPepMatches, incrementalValidation = true, traceability = false)

    logger.debug("performROCAnalysis => entering sortedDistinctThresholds loop ...")

    // Initialize the target/decoy peptide matches counters
    var (targetMatchesCount, decoyMatchesCount) = (0, 0)

    // Define some vars
    val rocPoints = new ArrayBuffer[ValidationResult](sortedDistinctThresholds.length)

    val start = System.currentTimeMillis

    // Iterate over sorted PSMs => each PSM is taken as a new threshold
    for (thresholdValue <- sortedDistinctThresholds) {

      // Retrieve peptide matches corresponding to this threshold value
      val curPepMatches = filteredPepMatchesGroupedByThreshold(thresholdValue)

      // Compute the next ROC point
      val curDecoyMatchesCount = curPepMatches.count(_.isDecoy)
      val curTargetMatchesCount = curPepMatches.length - curDecoyMatchesCount

      targetMatchesCount += curTargetMatchesCount
      decoyMatchesCount += curDecoyMatchesCount

      val curRocPoint: ValidationResult = this.calcTDStatistics(targetMatchesCount, decoyMatchesCount)

      // Set ROC point validation properties
      validationFilter.setThresholdValue(thresholdValue)
      curRocPoint.addProperties(validationFilter.getFilterProperties())

      // Add ROC point to the curve
      rocPoints += curRocPoint

      logger.trace("New FDR = " + curRocPoint.fdr+" nbr decoy/target  \t"+decoyMatchesCount+"\t"+targetMatchesCount+"\t"+thresholdValue)
    }

    val end = System.currentTimeMillis
    logger.debug("performROCAnalysis => sortedPepMatches loop completed in " + (end - start) + "ms, starting restorePepMatchValidationStatus ...")

    // Restore peptide matches validation status
    PeptideMatchFiltering.restorePepMatchValidationStatus(filteredPepMatches, pepMatchValStatusMap)
    logger.debug("performROCAnalysis => restorePepMatchValidationStatus done")

    rocPoints.toArray
  }

}

object RocPointsFilter {

  def byTargetDecoyEdges(rocPoints: Array[ValidationResult]): Array[ValidationResult] = {
    val filteredRocPoints = new ArrayBuffer[ValidationResult](rocPoints.length)
    if (rocPoints.length < 2) filteredRocPoints ++= rocPoints
    else {
      var previousPmWasTarget = true
      rocPoints.sliding(2).foreach { rocPointsWindow =>
        val firstRocPoint = rocPointsWindow(0)
        val secondRocPoint = rocPointsWindow(1)

        if (firstRocPoint.decoyMatchesCount == secondRocPoint.decoyMatchesCount ) {
          previousPmWasTarget = true
        } else {
          // Include only target ROC points preceding a decoy one
          if (previousPmWasTarget) filteredRocPoints += firstRocPoint
          // Add new decoy ROC points
          filteredRocPoints += secondRocPoint
          previousPmWasTarget = false
        }
      }
    }
    filteredRocPoints.toArray
  }

  def byMinFDR(rocPoints: Array[ValidationResult]): Array[ValidationResult] = {
    val filteredRocPoints = new ArrayBuffer[ValidationResult](rocPoints.length)
    if (rocPoints.length < 2) filteredRocPoints ++= rocPoints
    else {
      var minFDR = Float.MaxValue
      rocPoints.reverse.foreach { point =>
        if (point.fdr.get < minFDR ) {
          // Add new decoy ROC points
          filteredRocPoints += point
          minFDR = point.fdr.get
        }
      }
    }
    filteredRocPoints.reverse.toArray
  }
}



class BasicTDAnalyzer(tdComputer: (Int, Int) => Float) extends AbstractTargetDecoyAnalyzer {

  def calcTDStatistics(targetPepMatches: Seq[PeptideMatch], decoyPepMatches: Seq[PeptideMatch] ): ValidationResult = {

    val targetMatchesCount = targetPepMatches.count(_.isValidated)
    val decoyMatchesCount = decoyPepMatches.count(_.isValidated)
    this.calcTDStatistics(targetMatchesCount, decoyMatchesCount)
  }

  def calcTDStatistics(targetMatchesCount: Int, decoyMatchesCount: Int): ValidationResult = {

    val fdr = if (targetMatchesCount == 0) Float.NaN
    else { tdComputer(targetMatchesCount, decoyMatchesCount) }

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

  override def performROCAnalysis(
      targetPepMatches: Seq[PeptideMatch],
      decoyPepMatches: Seq[PeptideMatch],
      validationFilter: IOptimizablePeptideMatchFilter): Array[ValidationResult] = {

    val rocPoints = super.performROCAnalysis(targetPepMatches, decoyPepMatches, validationFilter)
    // maintain the same logic as previous implemzntation : when using performROCAnalysisV1, no rocPoint filters was applied
    //TODO: conserver comme ca ou accepter un fitre des rocpoints ? Si on supprime, penser a modifier les Tests Unitaires
    if(validationFilter.isInstanceOf[MascotPValuePSMFilter]) {
      rocPoints
    } else {
      RocPointsFilter.byTargetDecoyEdges(rocPoints)
    }
  }

  override def toString(): String = {
    "BasicTDAnalyzer("+ tdComputer.toString() +")"
  }
}

object GorshkovTDAnalyzer {

  def calcFDR( tp: Int, dp: Int , r: Float): Float = {
    require( tp > 0 && dp >= 0 )
    (100 * dp + 1).toFloat / (r * tp)
  }

}

class GorshkovTDAnalyzer(dbSizeRatio: Float = 1.0f) extends AbstractTargetDecoyAnalyzer with LazyLogging {

  override def calcTDStatistics(targetPepMatches: Seq[PeptideMatch], decoyPepMatches: Seq[PeptideMatch]): ValidationResult = {
    val targetMatchesCount = targetPepMatches.count(_.isValidated)
    val decoyMatchesCount = decoyPepMatches.count(_.isValidated)

    this.calcTDStatistics(targetMatchesCount, decoyMatchesCount)
  }

  override def calcTDStatistics(pepMatchJointMap: Map[Long, Seq[PeptideMatch]]): ValidationResult = {
    val allPepMatches = pepMatchJointMap.flatMap(_._2).toSeq
    val (decoyPepMatches, targetPepMatches) = allPepMatches.partition(_.isDecoy)

    this.calcTDStatistics(targetPepMatches, decoyPepMatches)
  }

  override def calcTDStatistics(targetMatchesCount: Int, decoyMatchesCount: Int): ValidationResult = {
    val fdr = if (targetMatchesCount == 0) Float.NaN else 100*(decoyMatchesCount + 1).toFloat / (dbSizeRatio*targetMatchesCount)
    ValidationResult(
      targetMatchesCount = targetMatchesCount,
      decoyMatchesCount = Some(decoyMatchesCount),
      fdr = if (fdr.isNaN) None else Some(fdr)
    )
  }

  override def performROCAnalysis(
    targetPepMatches: Seq[PeptideMatch],
    decoyPepMatches: Seq[PeptideMatch],
    validationFilter: IOptimizablePeptideMatchFilter): Array[ValidationResult] = {

    val rocPoints = super.performROCAnalysis(targetPepMatches, decoyPepMatches, validationFilter)
    RocPointsFilter.byMinFDR(rocPoints)
  }

  override def toString(): String = {
    "GorshkovTDAnalyzer(r="+dbSizeRatio+")"
  }
}