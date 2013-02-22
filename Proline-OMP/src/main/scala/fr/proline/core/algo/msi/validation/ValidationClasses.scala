package fr.proline.core.algo.msi.validation

import scala.collection.mutable.HashMap

import com.codahale.jerkson.JsonSnakeCase
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include

import fr.proline.core.algo.msi.filtering._
import fr.proline.core.algo.msi.validation.pepmatch._
import fr.proline.core.om.model.msi.PeptideMatch
import fr.proline.core.om.model.msi.ProteinSet
import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.om.model.msi.ResultSummary

object TargetDecoyModes extends Enumeration {
  type Mode = Value
  val CONCATENATED = Value("CONCATENATED")
  val SEPARATED = Value("SEPARATED")  
}

object ProtSetValidationMethods extends Enumeration {
  type MethodName = Value
  val PEPTIDE_MATCH_RULES = Value("PEPTIDE_MATCH_RULES")
  val PROTEIN_SET_SCORE = Value("PROTEIN_SET_SCORE")  
}

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class ValidationResult( targetMatchesCount: Int,
                             decoyMatchesCount: Option[Int] = None,
                             fdr: Option[Float] = None,
                             var properties: Option[HashMap[String,Any]] = None
                            ) {
  def addProperties( newProps: Map[String,Any] ) {
    var props = this.properties.getOrElse( new collection.mutable.HashMap[String,Any]() )
    props ++= newProps
    this.properties = Some(props)
  }
}

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class ValidationResults( finalResult: ValidationResult, computedResults: Option[Seq[ValidationResult]] = None )

/** A factory to instantiate the appropriate peptide match validator */
object BuildPeptideMatchValidator {
  
  def apply(
    validationFilter: IPeptideMatchFilter,
    expectedFdrOpt: Option[Float] = None,
    tdAnalyzerOpt: Option[ITargetDecoyAnalyzer] = None
  ): IPeptideMatchValidator = {
    
    if (expectedFdrOpt.isDefined) {
      require( validationFilter.isInstanceOf[IOptimizablePeptideMatchFilter], "an optimizable fitler must be provided" )
      
      val valFilter = validationFilter.asInstanceOf[IOptimizablePeptideMatchFilter]
      
      new TDPepMatchValidatorWithFDROptimization( valFilter, expectedFdrOpt, tdAnalyzerOpt )
    }
    else {
      new BasicPepMatchValidator( validationFilter, tdAnalyzerOpt )
    }
  }
}

trait IPeptideMatchValidator {
  
  val validationFilter: IPeptideMatchFilter
  val expectedFdr: Option[Float]
  var tdAnalyzer: Option[ITargetDecoyAnalyzer]  
  
  /**
   * Validates peptide matches.
   * @param pepMatches The list of peptide matches to validate
   * @param decoyPepMatches An optional list of decoy peptide matches to validate
   * @return An instance of the ValidationResults case class
   */
  def validatePeptideMatches( pepMatches: Seq[PeptideMatch], decoyPepMatches: Option[Seq[PeptideMatch]] = None ): ValidationResults
  
  def validatePeptideMatches( targetRs: ResultSet ): ValidationResults = {
    
    val targetPepMatches: Seq[PeptideMatch] = targetRs.peptideMatches
    
    val decoyRs = if( targetRs.decoyResultSet == null ) None else targetRs.decoyResultSet
    val decoyPepMatches: Option[Seq[PeptideMatch]] = decoyRs.map(_.peptideMatches)
    
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
    
    val decoyRsm = if( targetRsm.decoyResultSummary == null ) None else targetRsm.decoyResultSummary
    val decoyProtSets: Option[Seq[ProteinSet]] = decoyRsm.map(_.proteinSets)
    
    this.validateProteinSets( targetProtSets, decoyProtSets )
  }
 
}
