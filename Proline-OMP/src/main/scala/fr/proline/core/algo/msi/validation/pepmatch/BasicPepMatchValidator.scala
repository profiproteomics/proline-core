package fr.proline.core.algo.msi.validation.pepmatch

import fr.proline.core.algo.msi.validation._
import fr.proline.core.om.model.msi.PeptideMatch
import fr.proline.core.algo.msi.filter.IOptimizablePeptideMatchFilter
import fr.proline.core.algo.msi.filter.NullOptimizablePSMFilter

/** Basic implementation of IPeptideMatchValidator. */
class BasicPeptideMatchValidator extends IPeptideMatchValidator {
  
  // FIXME: create a NullPeptideMatchFilter to fake this
  val validationFilter: IOptimizablePeptideMatchFilter = NullOptimizablePSMFilter
  
  def validatePeptideMatches( peptideMatches: Seq[PeptideMatch], decoyPepMaches: Option[Seq[PeptideMatch]] = None ): ValidationResults = {
    
    // Set validation flag to true for all provided peptide matches
    peptideMatches.foreach( _.isValidated = true )
    
    // Return the number of provided peptide matches as validation result
    ValidationResults(
      ValidationResult(
        nbTargetMatches = peptideMatches.length
      )
    )
    
  }
  
}