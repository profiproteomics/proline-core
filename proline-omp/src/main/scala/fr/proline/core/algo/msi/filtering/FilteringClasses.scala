package fr.proline.core.algo.msi.filtering

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import fr.proline.core.algo.msi.validation.BuildPeptideMatchFilter
import fr.proline.core.algo.msi.validation.BuildProteinSetFilter
import fr.proline.core.om.model.msi._
import fr.profi.util.StringUtils
import fr.profi.util.primitives.toDouble
import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.om.model.msi.ResultSummaryProperties
import com.typesafe.scalalogging.LazyLogging

object FilterPropertyKeys {
  final val THRESHOLD_VALUE = "threshold_value"
}

object PepMatchFilterParams extends Enumeration {
  type Param = Value
  val MASCOT_EVALUE = Value("MASCOT_EVALUE")
  val MASCOT_ADJUSTED_EVALUE = Value("MASCOT_ADJUSTED_EVALUE")
  val PEPTIDE_SEQUENCE_LENGTH = Value("PEP_SEQ_LENGTH")
  val PRETTY_RANK = Value("PRETTY_RANK")
  val SCORE = Value("SCORE")
  val SCORE_IT_PVALUE = Value("SCORE_IT_P-VALUE")
  val SCORE_HT_PVALUE = Value("SCORE_HT_P-VALUE")
  val SINGLE_PSM_PER_QUERY = Value("SINGLE_PSM_PER_QUERY")
  val SINGLE_PSM_PER_RANK = Value("SINGLE_PSM_PER_RANK")  // TODO: rename SINGLE_PSM_PER_PRETTY_RANK
  val ISOTOPE_OFFSET = Value("ISOTOPE_OFFSET")
  val BH_AJUSTED_PVALUE = Value("BH_AJUSTED_PVALUE")
  val SINGLE_SEQ_PER_RANK = Value("SINGLE_SEQ_PER_PRETTY_RANK")
}

object PepInstanceFilterParams extends Enumeration {
  type Param = Value
  val BH_ADJUSTED_PVALUE = Value("BH_AJUSTED_PVALUE")
}

object ProtSetFilterParams extends Enumeration {
  type Param = Value
  val SCORE = Value("SCORE")
  val SPECIFIC_PEP = Value("SPECIFIC_PEP")
  val PEP_COUNT = Value("PEP_COUNT")
  val PEP_SEQ_COUNT = Value("PEP_SEQ_COUNT")
  val BH_ADJUSTED_PVALUE = Value("BH_ADJUSTED_PVALUE")
}

trait IFilterConfig {
  
  def filterParameter: String
  def filterDescription: String

  /**
   * Return all properties that will be useful to know which kind iof filter have been applied
   * and be able to reapply it.
   *
   */
  def getFilterProperties(): Map[String, Any]

  def toFilterDescriptor(): FilterDescriptor = {
    // FIXME: remove .asInstanceOf[Map[String,AnyRef]] when Jacks supports scala.Any deserialization
    new FilterDescriptor(filterParameter, Some(filterDescription), Some(getFilterProperties))
  }
}

trait IFilter extends IFilterConfig {
  
  val filterParameter: String
  val filterDescription: String
  var isPropagating : Boolean = false

  /**
   * Returns the Threshold value that has been set.
   */
  def getThresholdValue(): Any

  /**
   * Returns the Threshold value that has been set with conversion to a Double.
   */
  def getThresholdValueAsDouble(): Double = {
    toDouble(this.getThresholdValue)
  }


  def setThresholdValue(newThreshold: Any): Unit

  /**
    * Specify if the Filter is used in Propagation process. Some definition or
    * threshold adjustment may be needed
    *
    * @param isPropagatingMode
    */
  def setPropagateMode(isPropagatingMode : Boolean ): Unit = {
    this.isPropagating = isPropagatingMode
  }

}

trait IOptimizableFilter extends IFilter {

  /**
   * Get the higher or lowest (depending on the filter type) threshold value for this filter.
   */
  def getThresholdStartValue(): Any

  /**
   * Given a current Threshold value, return the next possible value. This
   * is useful for ComputedValidationPSMFilter in order to determine
   * best threshold value to reach specified FDR
   */
  def getNextValue(currentVal: Any): Any

}

object PeptideMatchFiltering {

  // TODO: is incrementalValidation still needed ?
  /**
   * Resets the validation status of peptide matches.
   */
  def resetPepMatchValidationStatus(pepMatches: Seq[PeptideMatch]) {
    pepMatches.foreach(_.isValidated = true)
  }

  def getPepMatchValidationStatusMap(pepMatches: Seq[PeptideMatch]): Map[Long, Boolean] = {
    Map() ++ pepMatches.map(pm => pm.id -> pm.isValidated)
  }

  def restorePepMatchValidationStatus(pepMatches: Seq[PeptideMatch], pepMatchValStatusMap: Map[Long, Boolean]) {
    pepMatches.foreach { pm => pm.isValidated = pepMatchValStatusMap(pm.id) }
  }
}

trait IPeptideMatchSorter {
  /**
   * Sorts peptide matches in the order corresponding to the filter parameter,
   * from the "best" peptide match to the "worst".
   */
  def sortPeptideMatches(pepMatches: Seq[PeptideMatch]): Seq[PeptideMatch]
}

trait IPeptideMatchFilter extends IFilter  {
 
  protected var _postValidation : Boolean = false
 
  /**
   * Validate each PeptideMatch by setting their isValidated attribute.
   * Validation criteria will depend on implementation.
   *
   * Default behavior will be to exclude PeptideMatch which do not pass filter parameters
   *
   * @param pepMatches All PeptideMatches
   * @param incrementalValidation If incrementalValidation is set to false,
   * PeptideMatch.isValidated will be explicitly set to true or false.
   * Otherwise, only excluded PeptideMatch will be changed by setting their isValidated property to false.
   * @param traceability Specify if filter could saved information in peptideMatch properties
   *
   */
  def filterPeptideMatches(pepMatches: Seq[PeptideMatch], incrementalValidation: Boolean, traceability: Boolean): Unit

}

/**
 * Add a constraint on filter specifying that target ResultSet should be specified before 
 * calling filter method
 */
trait IFilterNeedingResultSet {
  
  def setTargetRS(targetRS: IResultSetLike) : Unit
  
} 

trait IOptimizablePeptideMatchFilter extends IPeptideMatchFilter with IOptimizableFilter with IPeptideMatchSorter with Ordering[PeptideMatch] {

  def isPeptideMatchValid(pepMatch: PeptideMatch): Boolean

  /**
   * Returns the value that will be used to filter the peptide match.
   */
  def getPeptideMatchValueForFiltering(pepMatch: PeptideMatch): Any

  /**
   * Compare peptide matches to produce an order corresponding to the filter parameter,
   * from the "best" peptide match to the "worst".
   */
  def compare(a: PeptideMatch, b: PeptideMatch): Int
  
  /**
   * Sorts peptide matches in the order corresponding to the filter parameter,
   * from the "best" peptide match to the "worst".
   */
  def sortPeptideMatches(pepMatches: Seq[PeptideMatch]): Seq[PeptideMatch] = pepMatches.sorted( this )

}


trait IPeptideInstanceFilter extends IFilter {

  def filterPeptideInstances(pepInstances: Seq[PeptideInstance])

}

object ProteinSetFiltering {

  // TODO: is incrementalValidation still needed ?
  /**
   * Resets the validation status of peptide matches.
   */
  def resetProteinSetValidationStatus(protSets: Seq[ProteinSet]) = {
    protSets.foreach(_.isValidated = true)
  }

  def getProtSetValidationStatusMap(protSets: Seq[ProteinSet]): Map[Long, Boolean] = {
    Map() ++ protSets.map(ps => ps.id -> ps.isValidated)
  }

  def restoreProtSetValidationStatus(protSets: Seq[ProteinSet], protSetValStatusMap: Map[Long, Boolean]) {
    protSets.foreach { ps => ps.isValidated = protSetValStatusMap(ps.id) }
  }
  
  // TODO: call this method in a trait common to all validators (need API refactoring)
  def updateValidatedProteinSetsCount(protSets: Seq[ProteinSet]): Unit = {

    // Map protein sets by peptide instance
    val protSetsByPepInst = new HashMap[PeptideInstance, ArrayBuffer[ProteinSet]]
    protSets.map { protSet =>
      protSet.peptideSet.getPeptideInstances.foreach { pepInst =>
        protSetsByPepInst.getOrElseUpdate(pepInst, new ArrayBuffer[ProteinSet]()) += protSet
      }
    }

    // Update validatedProteinSetsCount
    for ((pepInst, protSets) <- protSetsByPepInst) {
      // TODO: is distinct needed ???
      pepInst.validatedProteinSetsCount = protSets.distinct.count(_.isValidated)
    }

  }

}

trait IProteinSetFilter extends IFilter {

  /**
   * Validate each ProteinSet by setting their isValidated attribute.
   * Validation criteria will depend on implementation.
   *
   * Default behavior will be to exclude ProteinSet which do not pass filter parameters
   *
   * @param incrementalValidation : if incrementalValidation is set to false,
   * all ProteinSet's isValidated property will be explicitly set to true or false.
   * Otherwise, only excluded ProteinSet will be changed by setting their isValidated property to false
   * @param traceability : specify if filter could saved information in ProteinSet properties
   *
   */
  def filterProteinSets(protSets: Seq[ProteinSet], incrementalValidation: Boolean, traceability: Boolean): Unit

}

trait IOptimizableProteinSetFilter extends IProteinSetFilter with IOptimizableFilter with LazyLogging  {

  /**
   * Returns the validity status of the protein set, considering the current filter.
   */
  def isProteinSetValid(protSet: ProteinSet): Boolean

  /**
   * Sorts protein sets in the order corresponding to the filter parameter,
   * from the best protein set to the worst.
   */
  def sortProteinSets(protSets: Seq[ProteinSet]): Seq[ProteinSet]

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
  def filterProteinSets(
    protSets: Seq[ProteinSet],
    incrementalValidation: Boolean,
    traceability: Boolean
  ): Unit = {

    // Reset validation status if validation is not incremental
    if (!incrementalValidation) ProteinSetFiltering.resetProteinSetValidationStatus(protSets)

    // Apply the filtering procedure
    protSets.filter(!isProteinSetValid(_)).foreach(_.isValidated = false)
  }

  /**
   * Returns the value that will be used to filter the protein set.
   */
  def getProteinSetValueForFiltering(protSet: ProteinSet): Any
}

object ResultSummaryFilterBuilder {

  def getPeptideExpectedFDR(rsm: IResultSummaryLike) : Option[Float] = {
    require(rsm != null, "rsm is null")
    if (rsm.properties.isEmpty || rsm.properties.get.validationProperties.isEmpty ) return None
    return if(rsm.properties.get.validationProperties.get.params.psmExpectedFdr.isDefined) Some(rsm.properties.get.validationProperties.get.params.psmExpectedFdr.get) else None
  }

  def getProteinSetExpectedFDR(rsm: IResultSummaryLike) : Option[Float] = {
    require(rsm != null, "rsm is null")
    if (rsm.properties.isEmpty || rsm.properties.get.validationProperties.isEmpty ) return None
    return if(rsm.properties.get.validationProperties.get.params.proteinExpectedFdr.isDefined) Some(rsm.properties.get.validationProperties.get.params.proteinExpectedFdr.get) else None
  }

  def buildPeptideMatchFilters(rsm: IResultSummaryLike): Array[IPeptideMatchFilter] = {
    require(rsm != null, "rsm is null")
    if (rsm.properties.isEmpty) return Array()

    val rsmProperties = rsm.properties.get

    val result = new ArrayBuffer[IPeptideMatchFilter]()

    val validationPropsOpt = rsmProperties.getValidationProperties
    if (validationPropsOpt.isDefined) {
      val validationProperties = validationPropsOpt.get

      val params = validationProperties.getParams

      val optionalPepFilters = params.getPsmFilters
      if (optionalPepFilters.isDefined) {
        val peptideFilters = optionalPepFilters.get

        for (filterDescr <- peptideFilters) {
          val filterParameterStr = filterDescr.getParameter

          if (!StringUtils.isEmpty(filterParameterStr)) {
            var optionalThresholdValue: Option[AnyVal] = None

            val optionalFiltProperties = filterDescr.getProperties
            if ((optionalFiltProperties != null) && optionalFiltProperties.isDefined) {
              val filterProperties = optionalFiltProperties.get

              val optionalRawValue = filterProperties.get(FilterPropertyKeys.THRESHOLD_VALUE)
              if (optionalRawValue.isDefined) {
                val threshold = optionalRawValue.get.asInstanceOf[AnyVal] // Force a cast to Scala Primitive wrapper
                optionalThresholdValue = Some(threshold)
              }

            } // End if (filterProperties is defined)

            if (optionalThresholdValue.isDefined) {
              val nextFilter = BuildPeptideMatchFilter(filterParameterStr, optionalThresholdValue.get)
              if (nextFilter.isInstanceOf[IFilterNeedingResultSet])
                nextFilter.asInstanceOf[IFilterNeedingResultSet].setTargetRS(rsm.getResultSet().get)
              result += nextFilter
            } else {
              val nextFilter = BuildPeptideMatchFilter(filterParameterStr)
              if (nextFilter.isInstanceOf[IFilterNeedingResultSet])
                nextFilter.asInstanceOf[IFilterNeedingResultSet].setTargetRS(rsm.getResultSet().get)
              result += nextFilter
            }

          } // End (if filterParameterStr is not empty)

        } // End loop for each peptideFilter

      } // En if (peptideFilters is define)

    } // End if (validationProperties is defined)

    result.toArray
  }

  def buildProteinSetsFilters(rsm: IResultSummaryLike): Array[IProteinSetFilter] = {
    require(rsm != null, "rsm is null")
    if (rsm.properties.isEmpty) return Array()

    val rsmProperties = rsm.properties.get

    val result = new ArrayBuffer[IProteinSetFilter]()

    val validationPropsOpt = rsmProperties.getValidationProperties
    if (validationPropsOpt.isDefined) {
      val validationProperties = validationPropsOpt.get

      val params = validationProperties.getParams

      val optionalProtSetFilters = params.getProteinFilters
      if (optionalProtSetFilters.isDefined) {
        val protSetFilters = optionalProtSetFilters.get

        for (filterDescr <- protSetFilters) {
          val filterParameterStr = filterDescr.getParameter

          if (!StringUtils.isEmpty(filterParameterStr)) {
            var optionalThresholdValue: Option[AnyVal] = None

            val optionalFiltProperties = filterDescr.getProperties
            if ((optionalFiltProperties != null) && optionalFiltProperties.isDefined) {
              val filterProperties = optionalFiltProperties.get

              val optionalRawValue = filterProperties.get(FilterPropertyKeys.THRESHOLD_VALUE)
              if (optionalRawValue.isDefined) {
                val threshold = optionalRawValue.get.asInstanceOf[AnyVal] // Force a cast to Scala Primitive wrapper
                optionalThresholdValue = Some(threshold)
              }

            } // End if (filterProperties is defined)

            if (optionalThresholdValue.isDefined) {
              val nextFilter = BuildProteinSetFilter(filterParameterStr, optionalThresholdValue.get)
              if (nextFilter.isInstanceOf[IFilterNeedingResultSet])
                nextFilter.asInstanceOf[IFilterNeedingResultSet].setTargetRS(rsm.getResultSet().get)
              result += nextFilter
            } else {
              val nextFilter = BuildProteinSetFilter(filterParameterStr)
              if (nextFilter.isInstanceOf[IFilterNeedingResultSet])
                nextFilter.asInstanceOf[IFilterNeedingResultSet].setTargetRS(rsm.getResultSet().get)
              result += nextFilter
            }

          } // End (if filterParameterStr is not empty)

        } // End loop for each peptideFilter

      } // En if (peptideFilters is define)

    } // End if (validationProperties is defined)

    result.toArray
  }

}
