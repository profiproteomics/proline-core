package fr.proline.core.algo.msi.filter

import fr.proline.core.om.model.msi.PeptideMatch
import fr.proline.core.om.model.msi.FilterDescriptor
import fr.proline.core.algo.msi.validation.ValidationResults

object FilterPropertyKeys {
  final val THRESHOLD_VALUE = "threshold_value"
}

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
  val MASCOT_ADJUSTED_EVALUE = Value("MASCOT_ADJUSTED_EVALUE")
  val PEPTIDE_SEQUENCE_LENGTH = Value("PEP_SEQ_LENGTH")
  val RANK = Value("RANK")
  val SCORE = Value("SCORE")
  val NONE = Value("NONE")
}

trait IFilter {

  val filterParameter: String
  val filterDescription: String
  
   /**
    * Return all properties that will be usefull to know wich kind iof filter have been applied
    * and be able to reapply it. 
    *  
    */
  def getFilterProperties(): Map[String, Any]
  
  def toFilterDescriptor(): FilterDescriptor = {
    new FilterDescriptor( filterParameter, Some(filterDescription), Some(getFilterProperties) )
  }
  
  /**
   * Returns the Threshold value that has been set.
   */
  def getThresholdValue(): AnyVal
  
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
   * Default behavior will be to exclude PeptideMatch which do not pass filter parameters
   * 
   * @param pepMatches : All PeptiMatches for a single query.
   * @param incrementalValidation : if incrementalValidation is set to false, 
   * all PeptideMatch's isValidated property will be explicitly set to true or false. 
   * Otherwise, only excluded PeptideMatch will be changed bu setting their isValidated prooperty to false   
   * @param traceability : specify if filter could saved information in peptideMatch properties 
   *  
   */
  def filterPeptideMatches( pepMatches: Seq[PeptideMatch], incrementalValidation: Boolean, traceability: Boolean): Unit
  
  /**
   * Returns the value that will be used to filter the peptide match.
   */
  def getPeptideMatchValueForFiltering( pepMatch: PeptideMatch ): AnyVal
  
  /**
   * Sorts peptide matches in the order corresponding to the filter parameter,
   * from thes best peptide match to the worst.
   */
  def sortPeptideMatches( pepMatches: Seq[PeptideMatch] ): Seq[PeptideMatch]
  
  
  // TODO: is incrementalValidation still needed ?
  /**
   * Resets the validation status of peptide matches.
   */
  def resetPepMatchValidationStatus( pepMatches: Seq[PeptideMatch] ) = {
    pepMatches.foreach( _.isValidated = true )
  }
  
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
  def getNextValue( currentVal: AnyVal ): AnyVal
   
}

trait IOptimizablePeptideMatchFilter extends IPeptideMatchFilter with IOptimizableFilter


trait IProteinSetFilter extends IFilter {}
trait IOptimizableProteinSetFilter extends IProteinSetFilter with IOptimizableFilter

//VDS TODO: replace with real filter 
class ParamProteinSetFilter( val filterParameter: String, val minPepSeqLength: Int, val expectedFdr: Float ) extends IProteinSetFilter  {
   
  val filterDescription = "no description"
  var pValueThreshold : Float = 0.00f
 
  def getFilterProperties() : Map[String, Any] = {
    Map.empty[String, Any]
  }
  
  def getThresholdValue(): AnyVal = pValueThreshold
  
  def setThresholdValue( currentVal : AnyVal ){
    pValueThreshold = currentVal.asInstanceOf[Float]
  }
  
}


