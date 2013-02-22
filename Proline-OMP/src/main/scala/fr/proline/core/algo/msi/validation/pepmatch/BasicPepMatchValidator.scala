package fr.proline.core.algo.msi.validation.pepmatch

import fr.proline.core.algo.msi.validation._
import fr.proline.core.om.model.msi.PeptideMatch
import fr.proline.core.algo.msi.filter.IPeptideMatchFilter


/** Implementation of IPeptideMatchValidator performing a simple target/decoy analysis. */
class BasicPepMatchValidator(
  val validationFilter: IPeptideMatchFilter,
  var tdAnalyzer: Option[ITargetDecoyAnalyzer] = None
) extends IPeptideMatchValidator {
  
  val expectedFdr: Option[Float] = None

  def validatePeptideMatches( pepMatches: Seq[PeptideMatch], decoyPepMatches: Option[Seq[PeptideMatch]] = None ): ValidationResults = {
    //require( decoyPepMatches.isDefined, "decoy peptide matches must be provided")
    
    // Create pepMatchJointMap.  Map each query to a list to PSM from target and/or decoy resultSet     
    //val psmByQueries: Map[Int, Seq[PeptideMatch]] = tdComputer.buildPeptideMatchJointMap(pepMatches, decoyPepMatches)

    //Apply specified Filter to each map value (PeptideMatch array for one queryID) 
    //psmByQueries.foreach(entry => validationFilter.filterPeptideMatches(entry._2, false, true))
    
    // Apply validation filter
    validationFilter.filterPeptideMatches(pepMatches ++ decoyPepMatches.getOrElse(Seq.empty[PeptideMatch]), true, true)
    
    // Calculate statistics after filter have been applied
    val valResult = if (decoyPepMatches.isDefined) {
      require( tdAnalyzer.isDefined, "a target/decoy analyzer must be provided")
      tdAnalyzer.get.calcTDStatistics(pepMatches, decoyPepMatches.get)
    } else {
      ValidationResult( pepMatches.count(_.isValidated) )
    }
    
    // Add validation filter properties to validation results
    valResult.addProperties( validationFilter.getFilterProperties )
    
    /*
    // Calculate FDR after filter have been applied
    val targetMatchesCount = pepMatches.count(_.isValidated)
    val decoyMatchesCount = decoyPepMatches.get.count(_.isValidated)
    
    val fdr = if( decoyPepMatches.isDefined == false ) Option.empty[Float]
    else if( targetDecoyMode.isDefined)
      targetDecoyMode.get match {
        case TargetDecoyModes.CONCATENATED => Some(tdComputer.calcCdFDR(targetMatchesCount, decoyMatchesCount))
        case TargetDecoyModes.SEPARATED    => Some(tdComputer.calcSdFDR(targetMatchesCount, decoyMatchesCount))
        case _                             => throw new Exception("unknown target decoy mode: " + targetDecoyMode)
      }
    else {
      val pmJointMap = TargetDecoyComputer.buildPeptideMatchJointMap(pepMatches,decoyPepMatches)
      val valResult = TargetDecoyComputer.validatePepMatchesWithCompetition(pmJointMap, validationFilter)
      valResult.fdr
    }*/

    // Return validation results
    ValidationResults( valResult )
  }
  
}