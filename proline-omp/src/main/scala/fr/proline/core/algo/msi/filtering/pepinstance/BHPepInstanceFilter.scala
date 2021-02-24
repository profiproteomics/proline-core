package fr.proline.core.algo.msi.filtering.pepinstance

import com.typesafe.scalalogging.LazyLogging
import fr.profi.util.primitives.toFloat
import fr.proline.core.algo.msi.filtering.pepmatch.BHFilter
import fr.proline.core.algo.msi.filtering.{FilterPropertyKeys, IPeptideInstanceFilter, PepInstanceFilterParams}
import fr.proline.core.om.model.msi.PeptideInstance

import scala.collection.mutable.HashMap


class BHPepInstanceFilter(var adjPValueTreshold: Float = 0.01f) extends IPeptideInstanceFilter with LazyLogging {

  val filterParameter = PepInstanceFilterParams.BH_ADJUSTED_PVALUE.toString
  val filterDescription = "peptide filter based on BH adjusted pValue"

  def filterPeptideInstances(pepInstances: Seq[PeptideInstance]): Unit = {

    val filteredPeptideInstances = pepInstances.filter(_.isValidated)
    val (decoyPepInstances, targetPepInstances) = filteredPeptideInstances.partition(_.isDecoy)

    var pValues = targetPepInstances.map(_.scoringProperties.get.pValue).toArray
    var adjustedPValues = BHFilter.adjustPValues(pValues)

    for (i <- 0 to pValues.length-1) {
        targetPepInstances(i).isValidated = (adjustedPValues(i) < adjPValueTreshold)
    }

    pValues = decoyPepInstances.map(_.scoringProperties.get.pValue).toArray
    adjustedPValues = BHFilter.adjustPValues(pValues)

    for (i <- 0 to pValues.length-1) {
      decoyPepInstances(i).isValidated = (adjustedPValues(i) < adjPValueTreshold)
    }
  }

  /**
    * Returns the Threshold value that has been set.
    */
  override def getThresholdValue(): Any = adjPValueTreshold


  override def setThresholdValue(newThr: Any): Unit = {
    adjPValueTreshold = toFloat(newThr)
  }

  /**
    * Return all properties that will be usefull to know wich kind iof filter have been applied
    * and be able to reapply it.
    *
    */
  override def getFilterProperties(): Map[String, Any] =  {
    val props = new HashMap[String, Any]
    props += (FilterPropertyKeys.THRESHOLD_VALUE -> adjPValueTreshold )
    props.toMap
  }
}
