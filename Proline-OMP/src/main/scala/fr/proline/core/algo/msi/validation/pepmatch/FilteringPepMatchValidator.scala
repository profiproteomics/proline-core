package fr.proline.core.algo.msi.validation.pepmatch

import fr.proline.core.algo.msi.validation._
import fr.proline.core.om.model.msi.PeptideMatch
import fr.proline.core.algo.msi.filter.IOptimizablePeptideMatchFilter

/** Implementation of IPeptideMatchValidator which performs a filtering operation. */
class FilteringPeptideMatchValidator( val validationFilter: IOptimizablePeptideMatchFilter ) extends IPeptideMatchValidator {
  
  def validatePeptideMatches( peptideMatches: Seq[PeptideMatch], decoyPepMaches: Option[Seq[PeptideMatch]] = None ): ValidationResults = {
    
    // Set validation flag to true for all provided peptide matches
    validationFilter.filterPeptideMatches(peptideMatches, false, true)
    
    // Return the number of provided peptide matches as validation result
    ValidationResults(
      ValidationResult(
        nbTargetMatches = peptideMatches.count(_.isValidated)
      )
    )
    
  }
  
}