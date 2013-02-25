package fr.proline.core.algo.msi.validation.pepmatch

import fr.proline.core.algo.msi.validation._
import fr.proline.core.om.model.msi.PeptideMatch
import fr.proline.core.algo.msi.filtering.IPeptideMatchFilter

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

    // Return validation results
    ValidationResults( valResult )
  }
  
}