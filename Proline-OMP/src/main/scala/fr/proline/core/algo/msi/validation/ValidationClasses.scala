package fr.proline.core.algo.msi.validation

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include
import fr.proline.core.algo.msi.filtering.pepmatch._
import fr.proline.core.algo.msi.filtering.proteinset._
import fr.proline.core.algo.msi.filtering._
import fr.proline.core.algo.msi.validation.pepmatch._
import fr.proline.core.algo.msi.validation.proteinset._
import fr.proline.core.om.model.msi.{ResultSummary, ResultSet, ProteinSet, PeptideMatch}
import fr.proline.core.om.model.msi.FilterDescriptor
import fr.proline.core.om.model.msi.MsiRocCurve

object TargetDecoyModes extends Enumeration {
  type Mode = Value
  val CONCATENATED = Value("CONCATENATED")
  val SEPARATED = Value("SEPARATED")
  val MIXED = Value("MIXED") // for merged result sets having children in CONCATENATED and SEPARATED modes
}

object ProtSetValidationMethods extends Enumeration {
  type MethodName = Value
  val BASIC = Value("BASIC")
  val PEPTIDE_MATCH_RULES = Value("PEPTIDE_MATCH_RULES")
  val PROTEIN_SET_RULES = Value("PROTEIN_SET_RULES")  
}

trait ValidationSharedKeys {
  final val THRESHOLD_VALUE = "threshold_value"
  final val RULE_1_THRESHOLD_VALUE = "rule_1_threshold_value"
  final val RULE_2_THRESHOLD_VALUE = "rule_2_threshold_value"
}

object ValidationThresholdKeys extends ValidationSharedKeys

object ValidationPropertyKeys extends ValidationSharedKeys {  
  final val P_VALUE_THRESHOLD = "p_value_threshold"
  final val PEP_SCORE_THRESHOLD_OFFSET = "pep_score_threshold_offset"
  final val PROT_SET_SCORE_THRESHOLD = "prot_set_score_threshold"
  final val ROC_CURVE_ID = "roc_curve_id"
}

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

@JsonInclude( Include.NON_NULL )
case class ValidationResults( finalResult: ValidationResult, computedResults: Option[Seq[ValidationResult]] = None ) {
  
  def getRocCurve(): Option[MsiRocCurve] = {
    this.computedResults.map { computedResults =>
      
      val rocPointsCount = computedResults.length
      val xValues = new ArrayBuffer[Float](rocPointsCount)
      val yValues = new ArrayBuffer[Float](rocPointsCount)
      val thresholds = new ArrayBuffer[Float](rocPointsCount)
      
      computedResults.foreach { computedResult =>
        xValues += computedResult.fdr.get
        yValues += computedResult.targetMatchesCount
        thresholds += fr.profi.util.primitives.toFloat( computedResult.properties.get(FilterPropertyKeys.THRESHOLD_VALUE) )            
      }
      
      MsiRocCurve( xValues.toArray, yValues.toArray, thresholds.toArray )
    }
  }
  
}


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
      case PepMatchFilterParams.SCORE_IT_PVALUE => new MascotPValuePSMFilter(useHomologyThreshold = false)
      case PepMatchFilterParams.SCORE_HT_PVALUE => new MascotPValuePSMFilter(useHomologyThreshold = true)
      case PepMatchFilterParams.SINGLE_PSM_PER_QUERY => new SinglePSMPerQueryFilter()
      case PepMatchFilterParams.SINGLE_PSM_PER_RANK=> new SinglePSMPerPrettyRankFilter()
    }
  }
} 

object BuildOptimizablePeptideMatchFilter {
  
  def apply(filterParamStr: String): IOptimizablePeptideMatchFilter = {    
    this.apply( PepMatchFilterParams.withName(filterParamStr) )
  }
  
  def apply(filterParamStr: String, thresholdValue: AnyVal): IOptimizablePeptideMatchFilter = {    
    val filter = this.apply( filterParamStr )
    filter.setThresholdValue(thresholdValue)
    filter
  }
  
  def apply(filterParam: PepMatchFilterParams.Param): IOptimizablePeptideMatchFilter = {    
    filterParam match {
      case PepMatchFilterParams.MASCOT_EVALUE => new MascotEValuePSMFilter()
      case PepMatchFilterParams.MASCOT_ADJUSTED_EVALUE => new MascotAdjustedEValuePSMFilter()
      case PepMatchFilterParams.SCORE => new ScorePSMFilter()
      case PepMatchFilterParams.SCORE_IT_PVALUE => new MascotPValuePSMFilter(useHomologyThreshold = false)
      case PepMatchFilterParams.SCORE_HT_PVALUE => new MascotPValuePSMFilter(useHomologyThreshold = true)
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

object BuildProteinSetFilter  {

  def apply(filterParamStr: String): IProteinSetFilter = {
    this.apply(ProtSetFilterParams.withName(filterParamStr))
  }

  def apply(filterParamStr: String, thresholdValue: AnyVal): IProteinSetFilter = {
    val filter = this.apply(filterParamStr)
    filter.setThresholdValue(thresholdValue)
    filter
  }

  def apply(filterParam: ProtSetFilterParams.Param): IProteinSetFilter = {
    filterParam match {
      case ProtSetFilterParams.SPECIFIC_PEP => new SpecificPeptidesPSFilter()
      case ProtSetFilterParams.PEP_COUNT => new PeptidesCountPSFilter()
      case ProtSetFilterParams.PEP_SEQ_COUNT => new PepSequencesCountPSFilter()
      case ProtSetFilterParams.SCORE => new ScoreProtSetFilter()
    }
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
    thresholds: Option[Map[String,AnyVal]] = None,
    expectedFdrOpt: Option[Float] = None,
    targetDecoyModeOpt: Option[TargetDecoyModes.Value] = None
  ): IProteinSetValidator = {
    
    validationMethod match {
      case ProtSetValidationMethods.BASIC => {
        val threshold = thresholds.get(ValidationThresholdKeys.THRESHOLD_VALUE)
        new BasicProtSetValidator(BuildProteinSetFilter(validationFilterParam))
      }
      case ProtSetValidationMethods.PEPTIDE_MATCH_RULES => {

        if( expectedFdrOpt.isEmpty ) {
          
          // Retrieve the thresholds
          val threshold1 = thresholds.get(ValidationThresholdKeys.RULE_1_THRESHOLD_VALUE)
          val threshold2 = thresholds.get(ValidationThresholdKeys.RULE_2_THRESHOLD_VALUE)
          
          // Build the filters
          val filter1 = BuildOptimizablePeptideMatchFilter(validationFilterParam,threshold1)
          val filter2 = BuildOptimizablePeptideMatchFilter(validationFilterParam,threshold2)
          
          new PepMatchRulesValidator(filter1,filter2)
        } else {
          
          // Build the filters
          val filter1 = BuildOptimizablePeptideMatchFilter(validationFilterParam)
          val filter2 = BuildOptimizablePeptideMatchFilter(validationFilterParam)
        
          new PepMatchRulesValidatorWithFDROptimization(filter1,filter2,expectedFdrOpt,targetDecoyModeOpt)
        }
      }
      case ProtSetValidationMethods.PROTEIN_SET_RULES => {
        
        if( expectedFdrOpt.isEmpty ) {
          
          // Retrieve the thresholds
          val threshold1 = thresholds.get(ValidationThresholdKeys.RULE_1_THRESHOLD_VALUE)
          val threshold2 = thresholds.get(ValidationThresholdKeys.RULE_2_THRESHOLD_VALUE)
          
          // Build the filters
          val filter1 = BuildProteinSetFilter(validationFilterParam,threshold1)
          val filter2 = BuildProteinSetFilter(validationFilterParam,threshold2)
        
          new ProtSetRulesValidator(filter1,filter2)
        } else {
          
          // Build the filters
          val filter1 = BuildOptimizableProteinSetFilter(validationFilterParam)
          val filter2 = BuildOptimizableProteinSetFilter(validationFilterParam)
          
          new ProtSetRulesValidatorWithFDROptimization(filter1,filter2,expectedFdrOpt,targetDecoyModeOpt)
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

trait IProteinSetValidator extends IFilterConfig {
  
  val expectedFdr: Option[Float]
  var targetDecoyMode: Option[TargetDecoyModes.Value]

  /*def toFilterDescriptor(): FilterDescriptor = {
    new FilterDescriptor( "protein set validator", None, Some( Map("expected_fdr"-> this.expectedFdr) ) )
  }*/
  
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
    
    // Count validated decoy protein sets
    val decoyValidProtSetsCount = decoyProtSets.map( _.count( _.isValidated ) )
      
    this.computeValidationResult(targetValidProtSetsCount, decoyValidProtSetsCount)
  }
  
  protected def computeValidationResult(targetProtSetsCount: Int, decoyProtSetsCount: Option[Int]): ValidationResult = {
    
    val fdr = if (targetProtSetsCount == 0 || decoyProtSetsCount.isEmpty || targetDecoyMode.isEmpty ) Float.NaN
    else {
      
      // Compute FDR => TODO: switch on TargetDecoyMode
      //val fdr = (100 * decoyProtSetsCount.get).toFloat / targetProtSetsCount
      
      targetDecoyMode.get match {
        case TargetDecoyModes.CONCATENATED => TargetDecoyComputer.calcCdFDR(targetProtSetsCount, decoyProtSetsCount.get)
        case TargetDecoyModes.SEPARATED    => TargetDecoyComputer.calcSdFDR(targetProtSetsCount, decoyProtSetsCount.get)
        case _                             => throw new Exception("unsupported target decoy mode: " + targetDecoyMode)
      }
    }

    ValidationResult(
      targetMatchesCount = targetProtSetsCount,
      decoyMatchesCount = decoyProtSetsCount,
      fdr = if (fdr.isNaN) None else Some(fdr)
    )
    
  }
  
  
  /**
   * Increases the counts of the provided validation result.
   * @param currentValResult The current validation result.
   * @param increaseDecoy A boolean which specifies if target or decoy count has to be increased.
   * @return A new ValidationResult with updated counts.
   */
  protected def updateValidationResult( currentValResult: ValidationResult, increaseDecoy: Boolean ): ValidationResult = {
    
    // Retrieve old counts
    var( targetMatchesCount, decoyMatchesCount ) = (currentValResult.targetMatchesCount,currentValResult.decoyMatchesCount.get)
    
    if( increaseDecoy ) decoyMatchesCount += 1
    else targetMatchesCount += 1
    
    this.computeValidationResult(targetMatchesCount, Some(decoyMatchesCount) )
  }
 
}
