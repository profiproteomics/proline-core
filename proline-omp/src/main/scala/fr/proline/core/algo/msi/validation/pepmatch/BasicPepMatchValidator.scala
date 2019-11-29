package fr.proline.core.algo.msi.validation.pepmatch

import fr.proline.core.algo.msi.validation._
import fr.proline.core.om.model.msi.PeptideMatch
import fr.proline.core.algo.msi.filtering.IPeptideMatchFilter

/** Implementation of IPeptideMatchValidator performing a simple target/decoy analysis. */
class BasicPepMatchValidator(
  val validationFilter: IPeptideMatchFilter
) extends IPeptideMatchValidator {
  
  val expectedFdr: Option[Float] = None

  def validatePeptideMatches( pepMatches: Seq[PeptideMatch], decoyPepMatches: Option[Seq[PeptideMatch]] = None, tdAnalyzer: Option[ITargetDecoyAnalyzer] = None): ValidationResults = {
        
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