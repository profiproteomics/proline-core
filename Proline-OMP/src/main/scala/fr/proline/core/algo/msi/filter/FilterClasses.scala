package fr.proline.core.algo.msi.filter

import fr.proline.core.om.model.msi.PeptideMatch
import fr.proline.core.om.model.msi.FilterDescriptor
import fr.proline.core.algo.msi.validation.ValidationResults

object PepMatchFilterPropertyKeys {
  final val THRESHOLD_PROP_NAME = "threshold_value"
  final val MASCOT_EVALUE_THRESHOLD = "mascot_evalue_threshold"
  final val MIN_PEPTIDE_SEQUENCE_LENGTH = "min_pep_seq_length"
  final val MAX_RANK = "max_rank"
  final val SCORE_THRESHOLD = "score_threshold"
}

object PepMatchFilterParams extends Enumeration {
  type Param = Value
  val MASCOT_EVALUE = Value("MASCOT_EVALUE")
  val PEPTIDE_SEQUENCE_LENGTH = Value("PEP_SEQ_LENGTH")
  val RANK = Value("RANK")
  val SCORE = Value("SCORE")
  val NONE = Value("NONE")
}


object BuildPeptideMatchFilter {
  
  def apply(filterParamStr: String): IPeptideMatchFilter = {    
    this.apply( PepMatchFilterParams.withName(filterParamStr) )
  }
  
  def apply(filterParam: PepMatchFilterParams.Param): IPeptideMatchFilter = {    
    filterParam match {
      case PepMatchFilterParams.MASCOT_EVALUE => new MascotEValuePSMFilter()
      case PepMatchFilterParams.PEPTIDE_SEQUENCE_LENGTH => new PepSeqLengthPSMFilter()
      case PepMatchFilterParams.RANK => new RankPSMFilter()
      case PepMatchFilterParams.SCORE => new ScorePSMFilter()
      case PepMatchFilterParams.NONE => NullOptimizablePSMFilter
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
      case PepMatchFilterParams.SCORE => new ScorePSMFilter()
      case PepMatchFilterParams.NONE => NullOptimizablePSMFilter
    }
  }
  
}


trait IFilter {

  val filterParameter: String
  val filterDescription: String
  
   /**
    * Return all properties that will be usefull to know wich kind iof filter have been applied
    * and be able to reapply it. 
    *  
    */
  def getFilterProperties(): Option[Map[String, Any]]
  
  def toFilterDescriptor(): FilterDescriptor = {
    new FilterDescriptor( filterParameter, Some(filterDescription), getFilterProperties )
  }
  
  /**
   * Given a current Threshold value, return the next possible value. This 
   * is useful for ComputedValidationPSMFilter in order to determine 
   * best threshold value to reach specified FDR 
   */
  def setThresholdValue( currentVal : AnyVal )
  
}

trait IPeptideMatchFilter extends IFilter {
  
  /**
   * Validate each PeptideMatch by setting their isValidated attribute.
   * Validation criteria will depend on implementation.
   * 
   * @param pepMatches : All PeptiMatches for a single query.
   * @param incrementalValidation : if incrementalValidation is set to false, 
   * all PeptideMatch's isValidated property will explicitly be set to true and false. 
   * Otherwise, only excluded PeptideMatch will be changed to isValidated = false   
   * @param traceability : specify if filter could saved information in peptideMatch properties 
   *  
   */
  // TODO: rename to selectPeptideMatches and add a isSelected flag to peptideMatches
  def filterPeptideMatches( pepMatches : Seq[PeptideMatch], incrementalValidation: Boolean, traceability : Boolean) : Unit  
  
//def updatePeptideMatchProperties(pepMatch : PeptideMatch){
//    
//      var pepMatchValProps = pepMatch.validationProperties.orElse( Some( new PeptideMatchValidationProperties() ) ).get 
//      var filtersPropByRank = pepMatchValProps.getValidationFiltersProperties.getOrElse( Map.empty[Int, FilterProperties])
//	  
//      //Read last filter Rank to add new one
//       val lastRank = filtersPropByRank.maxBy(_._1 )._1
//              
//       val filterProp = new FilterProperties(name = filterName)
//            
//	filterProp.propeties = getFilterProperties()
//	filtersPropByRank += (lastRank+1 -> filterProp)
//	pepMatchValProps.validationFiltersProperties = Some(filtersPropByRank)
//        pepMatch.validationProperties = Some( pepMatchValProps )
//   }
}

trait IOptimizableFilter extends IFilter {

  /**
   * Get the higher or lowest (depending on the filter type) threshold value for this filter.  
   */
  def getThresholdStartValue(): AnyVal
  
  /**
   * Given a current Threshold value, return the next possible value. This 
   * is useful for ComputedValidationPSMFilter in order to determine 
   * best threshold value to reach specified FDR 
   */
  def getNextValue( currentVal : AnyVal ): AnyVal
   
}

trait IOptimizablePeptideMatchFilter extends IPeptideMatchFilter with IOptimizableFilter


trait IProteinSetFilter extends IFilter {}
trait IOptimizableProteinSetFilter extends IProteinSetFilter with IOptimizableFilter

//VDS TODO: replace with real filter 
class ParamProteinSetFilter( val filterParameter: String, val minPepSeqLength: Int, val expectedFdr: Float ) extends IProteinSetFilter  {
   
  val filterDescription = "no description"
  var pValueThreshold : Float = 0.00f
 
  def getFilterProperties() : Option[Map[String, Any]] = {
    None
  }
  
  def setThresholdValue( currentVal : AnyVal ){
    pValueThreshold = currentVal.asInstanceOf[Float]
  }
  
}


