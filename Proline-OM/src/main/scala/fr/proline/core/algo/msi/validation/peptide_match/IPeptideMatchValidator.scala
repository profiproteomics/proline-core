package fr.proline.core.algo.msi.validation.peptide_match

import fr.proline.core.algo.msi.validation._
import fr.proline.core.om.model.msi._

trait IPeptideMatchValidator {
    
  def validateWithComputerParams( validationParams: ComputerValidationParams,
                                  targetPeptideMatches: Seq[PeptideMatch],
                                  decoyPeptideMatches: Seq[PeptideMatch]
                                  ): ValidationResults
  
  def validateWithUserParams( validationParams: UserValidationParams,
                              targetPeptideMatches: Seq[PeptideMatch],
                              decoyPeptideMatches: Option[Seq[PeptideMatch]],
                              targetDecoyMode: Option[String] ): ValidationResult
  
}
