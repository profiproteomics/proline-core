package fr.proline.core.algo.msi.validation.pepmatch

import fr.proline.core.algo.msi.validation._
import fr.proline.core.om.model.msi.PeptideMatch
import fr.proline.core.algo.msi.filter.IPeptideMatchFilter


/** Implementation of IPeptideMatchValidator performing a simple target/decoy analysis. */
class TDPepMatchValidator(
  val validationFilter: IPeptideMatchFilter,
  val targetDecoyMode: TargetDecoyModes.Mode
) extends IPeptideMatchValidator {
  
  val tdComputer = fr.proline.core.algo.msi.TargetDecoyComputer

  def validatePeptideMatches( pepMatches: Seq[PeptideMatch], decoyPepMatches: Option[Seq[PeptideMatch]] = None ): ValidationResults = {
    require( decoyPepMatches.isDefined, "decoy peptide matches must be provided")
    
    // Create pepMatchJointMap.  Map each query to a list to PSM from target and/or decoy resultSet     
    val psmByQueries: Map[Int, Seq[PeptideMatch]] = tdComputer.buildPeptideMatchJointMap(pepMatches, decoyPepMatches)
    /*if (decoyPepMaches.isDefined) {
      psmByQueries = tdComputer.buildPeptideMatchJointMap(pepMatches, decoyPepMaches)
    } else {
      psmByQueries = pepMatches.groupBy(_.msQueryId)
    }*/

    //Apply specified Filter to each map value (PeptideMatch array for one queryID) 
    psmByQueries.foreach(entry => validationFilter.filterPeptideMatches(entry._2, false, true))

    // Calculate FDR after filter have been applied and create ValidationResult
    val nbTargetMatches = pepMatches.count(_.isValidated)
    val nbDecoyMatches = decoyPepMatches.get.count(_.isValidated)
   
    val fdr = targetDecoyMode match {
      case TargetDecoyModes.concatenated => Some(tdComputer.computeCdFdr(nbTargetMatches, nbDecoyMatches))
      case TargetDecoyModes.separated    => Some(tdComputer.computeSdFdr(nbTargetMatches, nbDecoyMatches))
      case _                             => throw new Exception("unknown target decoy mode: " + targetDecoyMode)
    }

    ValidationResults(
      ValidationResult(
        nbTargetMatches = nbTargetMatches,
        nbDecoyMatches = Some(nbDecoyMatches),
        fdr = fdr
      )
    )
  }
  
}