package fr.proline.core.algo.msi.filtering

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap

import fr.proline.core.om.model.msi.PeptideMatch
import fr.proline.core.om.model.msi.FilterDescriptor
import fr.proline.core.algo.msi.validation.ValidationResults
import fr.proline.core.om.model.msi.PeptideInstance
import fr.proline.core.om.model.msi.ProteinSet
import fr.proline.util.primitives._

object FilterPropertyKeys {
  final val THRESHOLD_VALUE = "threshold_value"
}

object PepMatchFilterParams extends Enumeration {
  type Param = Value
  val MASCOT_EVALUE = Value("MASCOT_EVALUE")
  val MASCOT_ADJUSTED_EVALUE = Value("MASCOT_ADJUSTED_EVALUE")
  val PEPTIDE_SEQUENCE_LENGTH = Value("PEP_SEQ_LENGTH")
  val RANK = Value("RANK")
  val SCORE = Value("SCORE")
  val SCORE_IT_PVALUE = Value("SCORE_IT_P-VALUE")
  val SCORE_HT_PVALUE = Value("SCORE_HT_P-VALUE")
}

object ProtSetFilterParams extends Enumeration {
  type Param = Value
  val SCORE = Value("SCORE")
  val SPECIFIC_PEP= Value("SPECIFIC_PEP")
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
   * Returns the Threshold value that has been set with conversion to a Double.
   */
  def getThresholdValueAsDouble(): Double = {
    toDouble( this.getThresholdValue )
  }
  
  /**
   * Given a current Threshold value, return the next possible value. This 
   * is useful for ComputedValidationPSMFilter in order to determine 
   * best threshold value to reach specified FDR 
   */
  def setThresholdValue( currentVal : AnyVal )
  
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

object PeptideMatchFiltering {
  
  // TODO: is incrementalValidation still needed ?
  /**
   * Resets the validation status of peptide matches.
   */
  def resetPepMatchValidationStatus( pepMatches: Seq[PeptideMatch] ) {
    pepMatches.foreach( _.isValidated = true )
  }
  
  def getPepMatchValidationStatusMap( pepMatches: Seq[PeptideMatch] ):  Map[Int,Boolean] = {
    Map() ++ pepMatches.map( pm => pm.id -> pm.isValidated )
  }
  
  def restorePepMatchValidationStatus( pepMatches: Seq[PeptideMatch], pepMatchValStatusMap: Map[Int,Boolean] ) {
    pepMatches.foreach { pm => pm.isValidated = pepMatchValStatusMap(pm.id) }
  }
}


trait IPeptideMatchSorter {
   /**
   * Sorts peptide matches in the order corresponding to the filter parameter,
   * from the "best" peptide match to the "worst".
   */
  def sortPeptideMatches( pepMatches: Seq[PeptideMatch] ): Seq[PeptideMatch]
}

trait IPeptideMatchFilter extends IFilter with IPeptideMatchSorter {
  
  
  /**
   * Validate each PeptideMatch by setting their isValidated attribute.
   * Validation criteria will depend on implementation.
   * 
   * Default behavior will be to exclude PeptideMatch which do not pass filter parameters
   * 
   * @param pepMatches : All PeptideMatches.
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
  
}

trait IOptimizablePeptideMatchFilter extends IPeptideMatchFilter with IOptimizableFilter {

  
  def isPeptideMatchValid( pepMatch: PeptideMatch ): Boolean
  
}

object ProteinSetFiltering {
  
  // TODO: is incrementalValidation still needed ?
  /**
   * Resets the validation status of peptide matches.
   */
  def resetProteinSetValidationStatus( protSets: Seq[ProteinSet] ) = {
    protSets.foreach( _.isValidated = true )
  }
  
  def getProtSetValidationStatusMap( protSets: Seq[ProteinSet] ):  Map[Int,Boolean] = {
    Map() ++ protSets.map( ps => ps.id -> ps.isValidated )
  }
  
  def restoreProtSetValidationStatus( protSets: Seq[ProteinSet], protSetValStatusMap: Map[Int,Boolean] ) {
    protSets.foreach { ps => ps.isValidated = protSetValStatusMap(ps.id) }
  }
  
}

trait IProteinSetFilter extends IFilter {
  
  /**
   * Validate each ProteinSet by setting their isValidated attribute.
   * Validation criteria will depend on implementation.
   * 
   * Default behavior will be to exclude ProteinSet which do not pass filter parameters
   * 
   * @param proteinSets : All proteinSets.
   * @param incrementalValidation : if incrementalValidation is set to false, 
   * all ProteinSet's isValidated property will be explicitly set to true or false. 
   * Otherwise, only excluded ProteinSet will be changed by setting their isValidated property to false   
   * @param traceability : specify if filter could saved information in ProteinSet properties 
   * 
   */
  def filterProteinSets(protSets: Seq[ProteinSet], incrementalValidation: Boolean, traceability: Boolean): Unit
  
}

trait IOptimizableProteinSetFilter extends IProteinSetFilter with IOptimizableFilter {
  
  /**
   * Returns the validity status of the protein set, considering the current filter.
   */
  def isProteinSetValid( protSet: ProteinSet ): Boolean
  
  /**
   * Sorts protein sets in the order corresponding to the filter parameter,
   * from the best protein set to the worst.
   */
  def sortProteinSets( protSets: Seq[ProteinSet] ): Seq[ProteinSet]
  
  /**
   * Validate each ProteinSet by setting their isValidated attribute.
   * Validation criteria will depend on implementation.
   * 
   * Default behavior will be to exclude ProteinSet which do not pass filter parameters
   * 
   * @param protSets : All ProteinSets.
   * @param incrementalValidation : if incrementalValidation is set to false, 
   * all ProteinSets isValidated property will be explicitly set to true or false. 
   * Otherwise, only excluded ProteinSets will be changed by setting their isValidated property to false   
   * @param traceability : specify if filter could saved information in ProteinSet properties 
   *  
   */
  def filterProteinSets(protSets: Seq[ProteinSet], incrementalValidation: Boolean, traceability: Boolean): Unit = {
    
    // Reset validation status if validation is not incremental
    if( !incrementalValidation ) ProteinSetFiltering.resetProteinSetValidationStatus(protSets)
    
    // Apply the filtering procedure
    protSets.filter( ! isProteinSetValid(_) ).foreach( _.isValidated = false )
    
    // Map protein sets by peptide instance
    val protSetsByPepInst = new HashMap[PeptideInstance,ArrayBuffer[ProteinSet]]
    protSets.map { protSet =>
      protSet.peptideSet.getPeptideInstances.foreach { pepInst =>
        protSetsByPepInst.getOrElseUpdate(pepInst, new ArrayBuffer[ProteinSet]() ) += protSet
      }
    }
    
    // Update validatedProteinSetsCount
    for( (pepInst,protSets) <- protSetsByPepInst ) {
      // TODO: is distinct needed ???
      pepInst.validatedProteinSetsCount = protSets.distinct.count( _.isValidated )
    }

  }
  
  /**
   * Returns the value that will be used to filter the protein set.
   */
  def getProteinSetValueForFiltering( protSet: ProteinSet ): AnyVal
}

