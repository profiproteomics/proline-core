package fr.proline.core.algo.msi.validation.pepmatch

import fr.proline.core.algo.msi.validation._
import fr.proline.core.om.model.msi.PeptideMatch
import fr.proline.core.algo.msi.filter.IPeptideMatchFilter
import fr.proline.core.algo.msi.filter.NullPSMFilter

/** Basic implementation of IPeptideMatchValidator. */
class BasicPeptideMatchValidator extends IPeptideMatchValidator {
  
  val validationFilter: IPeptideMatchFilter = NullPSMFilter
  
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