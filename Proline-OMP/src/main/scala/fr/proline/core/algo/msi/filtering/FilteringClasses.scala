package fr.proline.core.algo.msi.filtering

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import fr.proline.core.algo.msi.validation.BuildPeptideMatchFilter
import fr.proline.core.om.model.msi.FilterDescriptor
import fr.proline.core.om.model.msi.PeptideInstance
import fr.proline.core.om.model.msi.PeptideMatch
import fr.proline.core.om.model.msi.ProteinSet
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.util.StringUtils
import fr.proline.util.primitives.toDouble
import fr.proline.core.om.model.msi.ResultSet

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
  val SINGLE_PSM_PER_QUERY = Value("SINGLE_PSM_PER_QUERY")
}

object ProtSetFilterParams extends Enumeration {
  type Param = Value
  val SCORE = Value("SCORE")
  val SPECIFIC_PEP = Value("SPECIFIC_PEP")
}

trait IFilterConfig {
  
  def filterParameter: String
  def filterDescription: String

  /**
   * Return all properties that will be usefull to know wich kind iof filter have been applied
   * and be able to reapply it.
   *
   */
  def getFilterProperties(): Map[String, Any]

  def toFilterDescriptor(): FilterDescriptor = {
    // FIXME: remove .asInstanceOf[Map[String,AnyRef]] when Jacks supports scala.Any deserialization
    new FilterDescriptor(filterParameter, Some(filterDescription), Some(getFilterProperties.asInstanceOf[Map[String,AnyRef]]))
  }
}

trait IFilter extends IFilterConfig {
  
  val filterParameter: String
  val filterDescription: String

  /**
   * Returns the Threshold value that has been set.
   */
  def getThresholdValue(): AnyVal

  /**
   * Returns the Threshold value that has been set with conversion to a Double.
   */
  def getThresholdValueAsDouble(): Double = {
    toDouble(this.getThresholdValue)
  }

  /**
   * Given a current Threshold value, return the next possible value. This
   * is useful for ComputedValidationPSMFilter in order to determine
   * best threshold value to reach specified FDR
   */
  def setThresholdValue(currentVal: AnyVal)

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
  def getNextValue(currentVal: AnyVal): AnyVal

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
  def filterPeptideMatches(pepMatches: Seq[PeptideMatch], incrementalValidation: Boolean, traceability: Boolean): Unit

  /**
   * Returns the value that will be used to filter the peptide match.
   */
  def getPeptideMatchValueForFiltering(pepMatch: PeptideMatch): AnyVal

}

/**
 * Add a constraint on filter specifying that target ResultSet should be specified before 
 * calling filter method
 */
trait IFilterNeedingResultSet {
  
  def setTargetRS(targetRS: ResultSet) : Unit
  
} 

trait IOptimizablePeptideMatchFilter extends IPeptideMatchFilter with IOptimizableFilter with IPeptideMatchSorter {

  def isPeptideMatchValid(pepMatch: PeptideMatch): Boolean

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
  def filterProteinSets(protSets: Seq[ProteinSet], incrementalValidation: Boolean, traceability: Boolean): Unit = {

    // Reset validation status if validation is not incremental
    if (!incrementalValidation) ProteinSetFiltering.resetProteinSetValidationStatus(protSets)

    // Apply the filtering procedure
    protSets.filter(!isProteinSetValid(_)).foreach(_.isValidated = false)

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

  /**
   * Returns the value that will be used to filter the protein set.
   */
  def getProteinSetValueForFiltering(protSet: ProteinSet): AnyVal
}


object ResultSummaryFilterBuilder {

  def buildPeptideMatchFilters(rsm: ResultSummary): Array[IPeptideMatchFilter] = {
    require(rsm != null, "Rsm is null")

    val result = new ArrayBuffer[IPeptideMatchFilter]()

    val optionalProperties = rsm.properties
    if ((optionalProperties != null) && optionalProperties.isDefined) {
      val rsmProperties = optionalProperties.get

      val optionalValidProperties = rsmProperties.getValidationProperties
      if ((optionalValidProperties != null) && optionalValidProperties.isDefined) {
        val validationProperties = optionalValidProperties.get

        val params = validationProperties.getParams
        if (params != null) {

          val optionalPepFilters = params.getPeptideFilters
          if ((optionalPepFilters != null) && optionalPepFilters.isDefined) {
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
                  val nextFilter =  BuildPeptideMatchFilter(filterParameterStr, optionalThresholdValue.get)
                  if(nextFilter.isInstanceOf[IFilterNeedingResultSet])
                	  nextFilter.asInstanceOf[IFilterNeedingResultSet].setTargetRS(rsm.resultSet.get)
                  result += nextFilter
                } else {
                  val nextFilter =  BuildPeptideMatchFilter(filterParameterStr)
                  if(nextFilter.isInstanceOf[IFilterNeedingResultSet])
                	  nextFilter.asInstanceOf[IFilterNeedingResultSet].setTargetRS(rsm.resultSet.get)
                  result += nextFilter
                }

              } // End (if filterParameterStr is not empty)

            } // End loop for each peptideFilter

          } // En if (peptideFilters is define)

        } // End if (params is not null)

      } // End if (validationProperties is defined)

    } // End if (rsmProperties is defined)

    result.toArray
  }

}
