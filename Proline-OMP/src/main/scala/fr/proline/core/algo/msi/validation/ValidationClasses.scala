package fr.proline.core.algo.msi.validation

import scala.collection.mutable.HashMap
import com.codahale.jerkson.JsonSnakeCase
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include
import fr.proline.core.algo.msi.filtering._
import fr.proline.core.algo.msi.filtering.pepmatch._
import fr.proline.core.algo.msi.filtering.proteinset._
import fr.proline.core.algo.msi.validation.pepmatch._
import fr.proline.core.algo.msi.validation.proteinset._
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
  val BASIC = Value("BASIC")
  val PEPTIDE_MATCH_RULES = Value("PEPTIDE_MATCH_RULES")
  val PROTEIN_SET_RULES = Value("PROTEIN_SET_RULES")  
}

object ValidationPropertyKeys {
  final val RULE_1_THRESHOLD_VALUE = "rule_1_threshold_value"
  final val RULE_2_THRESHOLD_VALUE = "rule_2_threshold_value"
  final val P_VALUE_THRESHOLD = "p_value_threshold"
  final val PEP_SCORE_THRESHOLD_OFFSET = "pep_score_threshold_offset"
  final val PROT_SET_SCORE_THRESHOLD = "prot_set_score_threshold"
  final val ROC_CURVE_ID = "roc_curve_id"
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


object BuildPeptideMatchFilter {
  
  def apply(filterParamStr: String): IPeptideMatchFilter = {    
    this.apply( PepMatchFilterParams.withName(filterParamStr) )
  }
  
  def apply(filterParamStr: String, thresholdValue: AnyVal): IPeptideMatchFilter = {    
    val filter = this.apply( filterParamStr )
    filter.setThresholdValue(thresholdValue)
    filter
  }
  
  def apply(filterParam: PepMatchFilterParams.Param): IPeptideMatchFilter = {    
    filterParam match {
      case PepMatchFilterParams.MASCOT_EVALUE => new MascotEValuePSMFilter()
      case PepMatchFilterParams.MASCOT_ADJUSTED_EVALUE => new MascotAdjustedEValuePSMFilter()
      case PepMatchFilterParams.PEPTIDE_SEQUENCE_LENGTH => new PepSeqLengthPSMFilter()
      case PepMatchFilterParams.RANK => new RankPSMFilter()
      case PepMatchFilterParams.SCORE => new ScorePSMFilter()
      case PepMatchFilterParams.SCORE_IT_PVALUE => new MascotPValuePSMFilter()
      case PepMatchFilterParams.SCORE_HT_PVALUE => new MascotPValuePSMFilter()
    }
  }
}

object BuildOptimizablePeptideMatchFilter {
  
  def apply(filterParamStr: String): IOptimizablePeptideMatchFilter = {    
    this.apply( PepMatchFilterParams.withName(filterParamStr) )
  }
  
  def apply(filterParam: PepMatchFilterParams.Param): IOptimizablePeptideMatchFilter = {    
    filterParam match {
      case PepMatchFilterParams.MASCOT_EVALUE => new MascotEValuePSMFilter()
      case PepMatchFilterParams.MASCOT_ADJUSTED_EVALUE => new MascotAdjustedEValuePSMFilter()
      case PepMatchFilterParams.SCORE => new ScorePSMFilter()
      case PepMatchFilterParams.SCORE_IT_PVALUE => new MascotPValuePSMFilter()
      case PepMatchFilterParams.SCORE_HT_PVALUE => new MascotPValuePSMFilter()
    }
  }
  
}

/** A factory to instantiate the appropriate peptide match validator */
object BuildPeptideMatchValidator {
  
  def apply(
    validationFilter: IPeptideMatchFilter,
    expectedFdrOpt: Option[Float] = None,
    tdAnalyzerOpt: Option[ITargetDecoyAnalyzer] = None
  ): IPeptideMatchValidator = {
    
    if (expectedFdrOpt.isDefined) {
      require( validationFilter.isInstanceOf[IOptimizablePeptideMatchFilter], "an optimizable filter must be provided" )
      
      val valFilter = validationFilter.asInstanceOf[IOptimizablePeptideMatchFilter]
      
      new TDPepMatchValidatorWithFDROptimization( valFilter, expectedFdrOpt, tdAnalyzerOpt )
    }
    else {
      new BasicPepMatchValidator( validationFilter, tdAnalyzerOpt )
    }
  }
}

object BuildProteinSetFilter {
  
  def apply(filterParamStr: String): IProteinSetFilter = {
    BuildOptimizableProteinSetFilter(filterParamStr)
  }
  
  def apply(filterParamStr: String, thresholdValue: AnyVal): IProteinSetFilter = {
    BuildOptimizableProteinSetFilter(filterParamStr,thresholdValue)
  }
}

object BuildOptimizableProteinSetFilter {
  
  def apply(filterParamStr: String): IOptimizableProteinSetFilter = {    
    this.apply( ProtSetFilterParams.withName(filterParamStr) )
  }
  
  def apply(filterParamStr: String, thresholdValue: AnyVal): IOptimizableProteinSetFilter = {    
    val filter = this.apply( filterParamStr )
    filter.setThresholdValue(thresholdValue)
    filter
  }
  
  def apply(filterParam: ProtSetFilterParams.Param): IOptimizableProteinSetFilter = {    
    filterParam match {
      case ProtSetFilterParams.SCORE => new ScoreProtSetFilter()
    }
  }
}

object BuildProteinSetValidator {
  
  def apply(
    validationMethod: ProtSetValidationMethods.Value,
    validationFilterParam: String,
    thresholds: Option[Map[String,Any]] = None,
    expectedFdrOpt: Option[Float] = None
  ): IProteinSetValidator = {
    
    // FIXME: set the thresholds
    
    validationMethod match {
      case ProtSetValidationMethods.BASIC => {
        new BasicProtSetValidator(BuildProteinSetFilter(validationFilterParam))
      }
      case ProtSetValidationMethods.PEPTIDE_MATCH_RULES => {
        val filter1 = BuildOptimizablePeptideMatchFilter(validationFilterParam)
        val filter2 = BuildOptimizablePeptideMatchFilter(validationFilterParam)
        if( expectedFdrOpt.isEmpty ) {
          new PepMatchRulesValidator(filter1,filter2)
        } else {
          new PepMatchRulesValidatorWithFDROptimization(filter1,filter2,expectedFdrOpt)
        }
      }
      case ProtSetValidationMethods.PROTEIN_SET_RULES => {
        val filter1 = BuildOptimizableProteinSetFilter(validationFilterParam)
        val filter2 = BuildOptimizableProteinSetFilter(validationFilterParam)
        if( expectedFdrOpt.isEmpty ) {
          new ProtSetRulesValidator(None,filter1,filter2)
        } else {
          new ProtSetRulesValidatorWithFDROptimization(None,filter1,filter2,expectedFdrOpt)
        }
      }
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
  
  val expectedFdr: Option[Float]
  
  /**
   * Validates protein sets.
   * @param targetRsm The target result summary
   * @param decoyRsm An optional decoy result summary
   * @return An instance of the ValidationResults case class
   */
  def validateProteinSets( targetRsm: ResultSummary, decoyRsm: Option[ResultSummary] ): ValidationResults
    
  def validateProteinSets( targetRsm: ResultSummary ): ValidationResults = {
    val decoyRsm = if( targetRsm.decoyResultSummary == null ) None else targetRsm.decoyResultSummary
    this.validateProteinSets( targetRsm, decoyRsm )
  }
  
  protected def computeValidationResult(targetRsm: ResultSummary, decoyRsm: Option[ResultSummary]): ValidationResult = {
    val targetProtSets = targetRsm.proteinSets
    val decoyProtSets = decoyRsm.map(_.proteinSets)
    
    this.computeValidationResult(targetProtSets,decoyProtSets)
  }
  
  protected def computeValidationResult(targetProtSets: Array[ProteinSet], decoyProtSets: Option[Array[ProteinSet]]): ValidationResult = {
    
    // Count validated target protein sets
    val targetValidProtSetsCount = targetProtSets.count( _.isValidated )
    
    val( fdr, decoyValidProtSetsCount) = if( decoyProtSets.isDefined ) {
      
      // Count validated decoy protein sets
      val decoyValidProtSetsCount = decoyProtSets.get.count( _.isValidated )
      
      // Compute FDR => TODO: switch on TargetDecoyMode
      val fdr = (100 * decoyValidProtSetsCount).toFloat / targetValidProtSetsCount
      
      (Some(fdr),Some(decoyValidProtSetsCount))
    } else (None,None)
    
    ValidationResult(
      targetMatchesCount = targetValidProtSetsCount,
      decoyMatchesCount = decoyValidProtSetsCount,
      fdr = fdr
    )
  }
 
}
