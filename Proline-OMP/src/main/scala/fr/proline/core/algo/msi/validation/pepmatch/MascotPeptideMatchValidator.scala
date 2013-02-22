package fr.proline.core.algo.msi.validation.pepmatch

import math.abs
import fr.proline.core.om.model.msi.PeptideMatch
import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.algo.msi.validation.ValidationResult
import fr.proline.core.algo.msi.filter.IPeptideMatchFilter
import fr.proline.core.algo.msi.validation.TargetDecoyModes
import fr.proline.core.algo.msi.validation.ValidationResults
import fr.proline.core.algo.msi.TargetDecoyComputer
import fr.proline.core.algo.msi.filter.IOptimizablePeptideMatchFilter
import fr.proline.core.algo.msi.filter.PepMatchFilterPropertyKeys

// TODO: remove this class
/**
 * Class that will apply ValidationPSMFilters on ResultSets. If Decoy ResultSet exist, it should have been
 * loaded into Object Model.
 *
 */

/*
class MascotPeptideMatchValidator(targetRs: ResultSet) extends IPeptideMatchValidator {

  // get Peptide Matches from Target ResultSet and related Decoy Result if defined. 
  val targetPeptideMatches: Seq[PeptideMatch] = targetRs.peptideMatches
  lazy val decoyPeptideMatches: Option[Seq[PeptideMatch]] = {
    targetRs.decoyResultSet.map(_.peptideMatches)
  }

  // Object to compute FDR and to build Target / Decoy Tables or Maps
  val tdComputer = fr.proline.core.algo.msi.TargetDecoyComputer

  def applyPSMFilter(filter: IPeptideMatchFilter, targetDecoyMode: Option[TargetDecoyModes.Mode]): ValidationResult = {

    // Create pepMatchJointMap.  Map each query to a list to PSM from target and/or decoy resultSet     
    var psmByQueries: Map[Int, Seq[PeptideMatch]] = null
    if (decoyPeptideMatches.isDefined) {
      psmByQueries = tdComputer.buildPeptideMatchJointMap(targetPeptideMatches, decoyPeptideMatches)
    } else {
      psmByQueries = targetPeptideMatches.groupBy(_.msQueryId)
    }

    //Apply specified Filter to each map value (PeptideMatch array for one queryID) 
    psmByQueries.foreach(entry => filter.filterPSM(entry._2, false, true))

    // Calculate FDR after filter have been applied and create ValidationResult

    val nbTargetMatches = targetPeptideMatches.filter(_.isValidated).size
    val nbDecoyMatchesOp: Option[Int] = if (decoyPeptideMatches.isDefined) Some(decoyPeptideMatches.get.filter(_.isValidated).size) else None
    val fdr = targetDecoyMode.get match {
      case TargetDecoyModes.concatenated => Some(tdComputer.computeCdFdr(nbTargetMatches, nbDecoyMatchesOp.get))
      case TargetDecoyModes.separated    => Some(tdComputer.computeSdFdr(nbTargetMatches, nbDecoyMatchesOp.get))
      case _                             => throw new Exception("unknown target decoy mode: " + targetDecoyMode)
    }

    new ValidationResult(
      nbTargetMatches = nbTargetMatches,
      nbDecoyMatches = nbDecoyMatchesOp,
      fdr = fdr
    )
  }

  /*
     *
     */
  def applyComputedPSMFilter(filter: IComputedFDRPeptideMatchFilter,
                             targetDecoyMode: Option[TargetDecoyModes.Mode]): ValidationResults = {

    require(decoyPeptideMatches.isDefined, "A decoy Result Set is required for Computer Mode filtering")

    val expectedFdr = filter.expectedFdr
    var psmByQueries: Map[Int, Seq[PeptideMatch]] = tdComputer.buildPeptideMatchJointMap(targetPeptideMatches, decoyPeptideMatches)

    val rocPoints = TargetDecoyComputer.rocAnalysis(psmByQueries, filter)

    // Retrieve the nearest ROC point of the expected FDR and associated threshold
    val expectedRocPoint = rocPoints.reduce { (a, b) => if (abs(a.fdr.get - expectedFdr) < abs(b.fdr.get - expectedFdr)) a else b }
    val thrToApply = expectedRocPoint.properties.get(FiltersPropertyKeys.THRESHOLD_PROP_NAME)

    filter.fdrValidationFilter.setThresholdValue(thrToApply.asInstanceOf[AnyVal])
    this.applyPSMFilter(filter.fdrValidationFilter, targetDecoyMode)

    new ValidationResults(expectedRocPoint, Some(rocPoints))

  }

}*/