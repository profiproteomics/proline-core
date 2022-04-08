package fr.proline.core.algo.msi.validation

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include
import fr.proline.core.algo.msi.filtering._
import fr.proline.core.algo.msi.filtering.pepinstance.BHPepInstanceFilter
import fr.proline.core.algo.msi.filtering.pepmatch._
import fr.proline.core.algo.msi.filtering.proteinset._
import fr.proline.core.algo.msi.validation.pepinstance.BasicPepInstanceBuilder
import fr.proline.core.algo.msi.validation.pepmatch._
import fr.proline.core.algo.msi.validation.proteinset._
import fr.proline.core.om.model.msi._

import scala.collection.mutable.{ArrayBuffer, HashMap}

object TargetDecoyModes extends Enumeration {
  type Mode = Value
  val CONCATENATED = Value("CONCATENATED")
  val SEPARATED = Value("SEPARATED")
  val MIXED = Value("MIXED") // for merged result sets having children in CONCATENATED and SEPARATED modes
}

object TargetDecoyEstimators extends Enumeration {
  type Estimator = Value
  val KALL_STOREY_COMPUTER = Value("KALL_STOREY")
  val GIGY_COMPUTER = Value("GIGY")
}

object TargetDecoyAnalyzers extends Enumeration {
  type Analyzer = Value
  val BASIC = Value("BASIC")
  val GORSHKOV = Value("GORSHKOV")
}

object FDRAnalyzerMethods extends Enumeration {
  type MethodName = Value
  val TARGET_DECOY = Value("TARGET_DECOY")
  val BH = Value("BH")
}

object PeptideInstanceBuilders extends Enumeration {
  type Builder = Value
  val STANDARD = Value("STANDARD")
}

object PeptideInstanceValidationMethods extends Enumeration {
  type MethodName = Value
  val TARGET_DECOY = Value("TARGET_DECOY")
  val BH = Value("BH")
}

object ProtSetValidationMethods extends Enumeration {
  type MethodName = Value
  val BASIC = Value("BASIC")
  val PEPTIDE_MATCH_RULES = Value("PEPTIDE_MATCH_RULES")
  val PROTEIN_SET_RULES = Value("PROTEIN_SET_RULES")
  val BH = Value("BH")
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
case class ValidationResult(
  targetMatchesCount: Int,
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
      
      computedResults.withFilter(_.fdr.isDefined).foreach { computedResult =>
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
      case PepMatchFilterParams.PRETTY_RANK => new PrettyRankPSMFilter()
      case PepMatchFilterParams.SCORE => new ScorePSMFilter()
      case PepMatchFilterParams.SCORE_IT_PVALUE => new MascotPValuePSMFilter(useHomologyThreshold = false)
      case PepMatchFilterParams.SCORE_HT_PVALUE => new MascotPValuePSMFilter(useHomologyThreshold = true)
      case PepMatchFilterParams.SINGLE_PSM_PER_QUERY => new SinglePSMPerQueryFilter()
      case PepMatchFilterParams.SINGLE_PSM_PER_RANK=> new SinglePSMPerPrettyRankFilter()
      case PepMatchFilterParams.ISOTOPE_OFFSET => new IsotopeOffsetPSMFilter()
      case PepMatchFilterParams.BH_AJUSTED_PVALUE => new BHPSMFilter()
      case PepMatchFilterParams.SINGLE_SEQ_PER_RANK => new SingleSequencePerPrettyRankFilter()
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


object BuildPeptideInstanceBuilder {

  def apply(builderParamsStr: String): IPeptideInstanceBuilder = {
    this.apply(PeptideInstanceBuilders.withName(builderParamsStr))
  }

  def apply(builderParam: PeptideInstanceBuilders.Builder): IPeptideInstanceBuilder = {
    builderParam match {
      case PeptideInstanceBuilders.STANDARD => new BasicPepInstanceBuilder()
    }
  }

}

object BuildPeptideInstanceFilter {

  def apply(filterParamStr: String): IPeptideInstanceFilter = {
    this.apply( PepInstanceFilterParams.withName(filterParamStr) )
  }

  def apply(filterParamStr: String, thresholdValue: AnyVal): IPeptideInstanceFilter = {
    val filter = this.apply( filterParamStr )
    filter.setThresholdValue(thresholdValue)
    filter
  }

  def apply(filterParam: PepInstanceFilterParams.Param): IPeptideInstanceFilter = {
    filterParam match {
      case PepInstanceFilterParams.BH_ADJUSTED_PVALUE => new BHPepInstanceFilter()
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
      case ProtSetFilterParams.BH_ADJUSTED_PVALUE => new BHProtSetFilter()
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
        
          new PepMatchRulesValidatorWithFDROptimization(filter1,filter2,expectedFdrOpt)
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
          
          new ProtSetRulesValidatorWithFDROptimization(filter1,filter2,expectedFdrOpt)
        }
      }
    }
  }
}

trait IPeptideInstanceBuilder {
  def buildPeptideInstance(pepMatchGroup: Array[PeptideMatch], resultSummaryId: Long): PeptideInstance
}

trait IValidatorConfig {
  def validatorName: String
  def validatorDescription: String

  def getValidatorProperties(): Option[Map[String, Any]] = None

  def toValidatorDescriptor(): ValidatorDescriptor = {
    new ValidatorDescriptor(validatorName, Some(validatorDescription), getValidatorProperties)
  }

}
trait IPeptideMatchValidator extends IValidatorConfig {
  
  val validationFilter: IPeptideMatchFilter
  val expectedFdr: Option[Float]

  /**
   * Validates peptide matches.
   * @param pepMatches The list of peptide matches to validate
   * @param decoyPepMatches An optional list of decoy peptide matches to validate
   * @return An instance of the ValidationResults case class
   */
  def validatePeptideMatches( pepMatches: Seq[PeptideMatch], decoyPepMatches: Option[Seq[PeptideMatch]] = None, tdAnalyzer: Option[ITargetDecoyAnalyzer]): ValidationResults
  
  def validatePeptideMatches( targetRs: ResultSet, tdAnalyzer: Option[ITargetDecoyAnalyzer]): ValidationResults = {
    
    val targetPepMatches: Seq[PeptideMatch] = targetRs.peptideMatches
    val decoyRs = if( targetRs.decoyResultSet == null ) None else targetRs.decoyResultSet
    val decoyPepMatches: Option[Seq[PeptideMatch]] = decoyRs.map(_.peptideMatches)
    
    this.validatePeptideMatches( targetPepMatches, decoyPepMatches, tdAnalyzer )
  }
 
}

trait IPeptideInstanceValidator extends IValidatorConfig {

  val validationFilter: IPeptideInstanceFilter
  val expectedFdr: Option[Float]

  def validatePeptideMatches(pepInstances: Seq[PeptideInstance], decoyPepInstances: Option[Seq[PeptideInstance]] = None, tdAnalyzer: Option[ITargetDecoyAnalyzer] = None): ValidationResults

}

trait IProteinSetValidator extends IFilterConfig with IValidatorConfig {
  
  val expectedFdr: Option[Float]

  /**
   * Validates protein sets.
   * @param targetRsm The target result summary
   * @param decoyRsm An optional decoy result summary
   * @return An instance of the ValidationResults case class
   */
  def validateProteinSets(targetRsm: ResultSummary, decoyRsm: Option[ResultSummary], tdAnalyzer: Option[ITargetDecoyAnalyzer]): ValidationResults
    
  def validateProteinSets(targetRsm: ResultSummary, tdAnalyzer: Option[ITargetDecoyAnalyzer]): ValidationResults = {
    val decoyRsm = if( targetRsm.decoyResultSummary == null ) None else targetRsm.decoyResultSummary
    this.validateProteinSets( targetRsm, decoyRsm, tdAnalyzer)
  }
  
  protected def computeValidationResult(targetRsm: ResultSummary, decoyRsm: Option[ResultSummary], tdAnalyzer: Option[ITargetDecoyAnalyzer]): ValidationResult = {
    val targetProtSets = targetRsm.proteinSets
    val decoyProtSets = decoyRsm.map(_.proteinSets)
    
    this.computeValidationResult(targetProtSets,decoyProtSets, tdAnalyzer)
  }
  
  protected def computeValidationResult(targetProtSets: Array[ProteinSet], decoyProtSets: Option[Array[ProteinSet]], tdAnalyzer: Option[ITargetDecoyAnalyzer]): ValidationResult = {
    
    // Count validated target protein sets
    val targetValidProtSetsCount = targetProtSets.count( _.isValidated )
    
    // Count validated decoy protein sets
    val decoyValidProtSetsCount = decoyProtSets.map( _.count( _.isValidated ) )
      
    this.computeValidationResult(targetValidProtSetsCount, decoyValidProtSetsCount, tdAnalyzer)
  }
  
  protected def computeValidationResult(targetProtSetsCount: Int, decoyProtSetsCount: Option[Int], tdAnalyzer: Option[ITargetDecoyAnalyzer]): ValidationResult = {

    if (targetProtSetsCount == 0 || decoyProtSetsCount.isEmpty || !tdAnalyzer.isDefined ) {
      ValidationResult(
        targetMatchesCount = targetProtSetsCount,
        decoyMatchesCount = decoyProtSetsCount,
        fdr = None
      )
    } else {
       tdAnalyzer.get.calcTDStatistics(targetProtSetsCount, decoyProtSetsCount.get)
    }
  }
  
  
  /**
   * Increases the counts of the provided validation result.
   * @param currentValResult The current validation result.
   * @param increaseDecoy A boolean which specifies if target or decoy count has to be increased.
   * @return A new ValidationResult with updated counts.
   */
  protected def updateValidationResult( currentValResult: ValidationResult, increaseDecoy: Boolean, tdAnalyzer: Option[ITargetDecoyAnalyzer]): ValidationResult = {
    
    // Retrieve old counts
    var( targetMatchesCount, decoyMatchesCount ) = (currentValResult.targetMatchesCount,currentValResult.decoyMatchesCount.get)
    
    if( increaseDecoy ) decoyMatchesCount += 1
    else targetMatchesCount += 1
    
    this.computeValidationResult(targetMatchesCount, Some(decoyMatchesCount), tdAnalyzer)
  }
 
}
