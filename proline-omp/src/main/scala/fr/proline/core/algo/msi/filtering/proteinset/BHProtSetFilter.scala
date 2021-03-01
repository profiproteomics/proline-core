package fr.proline.core.algo.msi.filtering.proteinset

import com.typesafe.scalalogging.LazyLogging
import fr.profi.util.primitives.toFloat
import fr.proline.core.algo.msi.filtering.pepmatch.BHFilter
import fr.proline.core.algo.msi.filtering.{FilterPropertyKeys, IProteinSetFilter, ProtSetFilterParams, ProteinSetFiltering}
import fr.proline.core.om.model.msi.ProteinSet

import scala.collection.mutable.HashMap

class BHProtSetFilter(var threshold: Float = 0.01f)  extends IProteinSetFilter with LazyLogging{

  val filterParameter = ProtSetFilterParams.BH_ADJUSTED_PVALUE.toString
  val filterDescription = "protein set filter on  adjusted pvalue"

  // IFilter methods
  def getFilterProperties(): Map[String, Any] = {
    val props = new HashMap[String, Any]
    props += (FilterPropertyKeys.THRESHOLD_VALUE -> threshold)
    props.toMap
  }

  def getThresholdValue(): Any = threshold

  def setThresholdValue(currentVal: Any): Unit ={
    threshold = toFloat(currentVal)
  }

  // IProteinSetFilter  methods
  def filterProteinSets(protSets: Seq[ProteinSet], incrementalValidation: Boolean, traceability: Boolean): Unit = {

    // Reset validation status if validation is not incremental
    if (!incrementalValidation) ProteinSetFiltering.resetProteinSetValidationStatus(protSets)

    var pValues = protSets.map(pSet => Math.pow(10, -pSet.peptideSet.score/10.0)).toArray
    var adjustedPValues = BHFilter.adjustPValues(pValues)

    logger.debug("accession; score; adjusted_pvalue")

    for (i <- 0 to pValues.length-1) {
      val pSet = protSets(i)
      logger.debug(pSet.getRepresentativeProteinMatch().get.accession+";"+pSet.peptideSet.score+";"+adjustedPValues(i))
      if (adjustedPValues(i) >= threshold) {
        pSet.isValidated = false
        //Decrease associated PeptideInstance validatedProteinSetsCount
        pSet.peptideSet.getPeptideInstances.foreach(pInst => {
          pInst.validatedProteinSetsCount = pInst.validatedProteinSetsCount - 1
        })
      }
    }
  }

}
