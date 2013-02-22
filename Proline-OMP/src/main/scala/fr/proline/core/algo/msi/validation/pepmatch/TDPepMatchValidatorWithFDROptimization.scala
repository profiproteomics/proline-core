package fr.proline.core.algo.msi.validation.pepmatch

import math.abs
import fr.proline.core.algo.msi.validation._
import fr.proline.core.om.model.msi.PeptideMatch
import fr.proline.core.algo.msi.filter.IOptimizablePeptideMatchFilter
import fr.proline.core.algo.msi.TargetDecoyComputer
import fr.proline.core.algo.msi.filter.PepMatchFilterPropertyKeys

/** Implementation of IPeptideMatchValidator performing a target/decoy analysis with a FDR optimization procedure. */
class TDPepMatchValidatorWithFDROptimization(  
  val validationFilter: IOptimizablePeptideMatchFilter,
  val targetDecoyMode: TargetDecoyModes.Mode,
  val expectedFdr: Float
) extends IPeptideMatchValidator {
  
  val tdComputer = fr.proline.core.algo.msi.TargetDecoyComputer

  def validatePeptideMatches( pepMatches: Seq[PeptideMatch], decoyPepMatches: Option[Seq[PeptideMatch]] = None ): ValidationResults = {
    require(decoyPepMatches.isDefined, "decoy peptide matches must be provided")

    var psmByQueries: Map[Int, Seq[PeptideMatch]] = tdComputer.buildPeptideMatchJointMap(pepMatches, decoyPepMatches)

    val rocPoints = TargetDecoyComputer.rocAnalysis(psmByQueries, validationFilter)

    // Retrieve the nearest ROC point of the expected FDR and associated threshold
    val expectedRocPoint = rocPoints.reduce { (a, b) => if (abs(a.fdr.get - expectedFdr) < abs(b.fdr.get - expectedFdr)) a else b }
    val thrToApply = expectedRocPoint.properties.get(PepMatchFilterPropertyKeys.THRESHOLD_PROP_NAME)

    // Update threshold and apply validation filter with this final value
    validationFilter.setThresholdValue(thrToApply.asInstanceOf[AnyVal])    
    validationFilter.filterPeptideMatches(pepMatches, false, true)
    validationFilter.filterPeptideMatches(decoyPepMatches.get, false, true)

    new ValidationResults(expectedRocPoint, Some(rocPoints))
  }
}