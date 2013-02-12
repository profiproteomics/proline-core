package fr.proline.core.algo.msi.validation.pepmatch

import fr.proline.core.algo.msi.filter.IPeptideMatchFilter
import fr.proline.core.algo.msi.validation.TargetDecoyModes
import fr.proline.core.algo.msi.validation.ValidationResult
import fr.proline.core.algo.msi.filter.IComputedFDRPeptideMatchFilter
import fr.proline.core.algo.msi.validation.ValidationResults

trait IPeptideMatchValidator {

  /**
   * Run specified IPeptideMatchFilter. 
   * 
   * @param filter to apply
   * @param targetDecoyMode TargetDecoy Mode used for searches 
   * @param filter result containing nbr decoy / nbr target / associated fdr and useful properties (used threshold)  
   * 
   */
  def applyPSMFilter( filter: IPeptideMatchFilter,
                      targetDecoyMode: Option[TargetDecoyModes.Mode] ): ValidationResult
  
  /**
   * Compute specified filter threshold to get expected FDR also specified in ComputedFDRPeptideMatchFilter.
   * This method will return the valid filter result in ValidationResults.expectedResult : Valid filter is filter 
   * with threshold set to get nearest expected FDR as possible.
   *  ValidationResults.computedResults will contain all calculated filter 
   * 
   */
  def applyComputedPSMFilter( filter: IComputedFDRPeptideMatchFilter,
                              targetDecoyMode: Option[TargetDecoyModes.Mode] ): ValidationResults
                      
//  def validateWithComputerParams( validationParams: ComputerValidationParams,
//                                  targetPeptideMatches: Seq[PeptideMatch],
//                                  decoyPeptideMatches: Seq[PeptideMatch]
//                                  ): ValidationResults
//  
//  def validateWithUserParams( validationParams: UserValidationParams,
//                              targetPeptideMatches: Seq[PeptideMatch],
//                              decoyPeptideMatches: Option[Seq[PeptideMatch]],
//                              targetDecoyMode: Option[TargetDecoyModes.Mode]
//                              ): ValidationResult
//  
}
