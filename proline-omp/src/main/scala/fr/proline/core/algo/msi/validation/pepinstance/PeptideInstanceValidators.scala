package fr.proline.core.algo.msi.validation.pepinstance

import fr.proline.core.algo.msi.filtering.{IPeptideInstanceFilter, IPeptideMatchFilter}
import fr.proline.core.algo.msi.validation._
import fr.proline.core.om.model.msi.{PeptideInstance, PeptideMatch}

/** Implementation of IPeptideInstanceValidator performing a BH analysis. */
class BHPeptideInstanceValidator(
  val validationFilter: IPeptideInstanceFilter,
  val expectedFdr: Option[Float]
) extends IPeptideInstanceValidator {

  val validatorName = "BH peptide validator"
  val validatorDescription = "Peptide validator optimizing FDR value based on BH"

  def validatePeptideMatches( pepInstances: Seq[PeptideInstance], decoyPepInstances: Option[Seq[PeptideInstance]] = None, tdAnalyzer: Option[ITargetDecoyAnalyzer] = None): ValidationResults = {
        
    // Apply validation filter
    validationFilter.filterPeptideInstances(pepInstances ++ decoyPepInstances.getOrElse(Seq.empty[PeptideInstance]))
    
    // Calculate statistics after filter have been applied
    val valResult = ValidationResult( targetMatchesCount =  pepInstances.count(_.isValidated), fdr = expectedFdr )

    // Add validation filter properties to validation results
    valResult.addProperties( validationFilter.getFilterProperties )

    // Return validation results
    ValidationResults( valResult )
  }
  
}

/** Implementation of IPeptideInstanceValidator performing a simple target/decoy analysis. */

class TDPeptideInstanceValidator(
    val validationFilter: IPeptideInstanceFilter
) extends IPeptideInstanceValidator {

  val validatorName = "Peptide match validator"
  val validatorDescription = "Peptide match validator that count the number of validated target and decoy matches "

  val expectedFdr: Option[Float] = None

  def validatePeptideMatches( pepInstances: Seq[PeptideInstance], decoyPepInstances: Option[Seq[PeptideInstance]] = None, tdAnalyzer: Option[ITargetDecoyAnalyzer] = None): ValidationResults = {

    // Apply validation filter
    validationFilter.filterPeptideInstances(pepInstances ++ decoyPepInstances.getOrElse(Seq.empty[PeptideInstance]))

    // Calculate statistics after filter have been applied
    val valResult = if (decoyPepInstances.isDefined) {
      require( tdAnalyzer.isDefined, "a target/decoy analyzer must be provided")
      tdAnalyzer.get.calcTDStatistics(pepInstances.size, decoyPepInstances.get.size)
    } else {
      ValidationResult( pepInstances.count(_.isValidated) )
    }

    // Add validation filter properties to validation results
    valResult.addProperties( validationFilter.getFilterProperties )

    // Return validation results
    ValidationResults( valResult )
  }

}