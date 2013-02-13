package fr.proline.core.algo.msi.validation

import scala.collection.mutable.HashMap
import fr.proline.core.algo.msi.filter.IOptimizablePeptideMatchFilter
import fr.proline.core.om.model.msi.PeptideMatch
import fr.proline.core.om.model.msi.ProteinSet
import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.om.model.msi.ResultSummary

object TargetDecoyModes extends Enumeration {
  type Mode = Value
  val separated = Value("separated")
  val concatenated = Value("concatenated")
}


case class ValidationResult( nbTargetMatches: Int,
                             nbDecoyMatches: Option[Int] = None,
                             fdr: Option[Float] = None,
                             properties: Option[HashMap[String,Any]] = None
                            )
                            
case class ValidationResults( finalResult: ValidationResult, computedResults: Option[Seq[ValidationResult]] = None )


trait IPeptideMatchValidator {
  
  val validationFilter: IOptimizablePeptideMatchFilter
 
  /**
   * Validates peptide matches.
   * @param pepMatches The list of peptide matches to validate
   * @param decoyPepMatches An optional list of decoy peptide matches to validate
   * @return An instance of the ValidationResults case class
   */
  def validatePeptideMatches( pepMatches: Seq[PeptideMatch], decoyPepMatches: Option[Seq[PeptideMatch]] = None ): ValidationResults
  
  def validatePeptideMatches( targetRs: ResultSet ): ValidationResults = {
    
    val targetPepMatches: Seq[PeptideMatch] = targetRs.peptideMatches
    val decoyPepMatches: Option[Seq[PeptideMatch]] = targetRs.decoyResultSet.map(_.peptideMatches)
    
    this.validatePeptideMatches( targetPepMatches, decoyPepMatches )
  }
 
}

trait IProteinSetValidator {
  
  /**
   * Validates protein sets.
   * @param protSets The list of protein sets to validate
   * @param decoyPepMaches An optional list of decoy protein sets to validate
   * @return An instance of the ValidationResults case class
   */
  def validateProteinSets( protSets: Seq[ProteinSet], decoyProtSets: Option[Seq[ProteinSet]] = None ): ValidationResults
  
  def validateProteinSets( targetRsm: ResultSummary ): ValidationResults = {
    
    val targetProtSets: Seq[ProteinSet] = targetRsm.proteinSets
    val decoyProtSets: Option[Seq[ProteinSet]] = targetRsm.decoyResultSummary.map(_.proteinSets)
    
    this.validateProteinSets( targetProtSets, decoyProtSets )
  }
 
}
