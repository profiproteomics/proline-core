package fr.proline.core.algo.msi.filtering.proteinset

import scala.collection.mutable.HashMap
import com.typesafe.scalalogging.LazyLogging
import fr.profi.util.primitives.toInt
import fr.proline.core.algo.msi.filtering.FilterPropertyKeys
import fr.proline.core.algo.msi.filtering.IProteinSetFilter
import fr.proline.core.algo.msi.filtering.ProtSetFilterParams
import fr.proline.core.om.model.msi.ProteinSet
import fr.proline.core.algo.msi.filtering.ProteinSetFiltering

class PeptidesCountPSFilter(var minNbrPep: Int = 1) extends IProteinSetFilter with LazyLogging {

  val filterParameter = ProtSetFilterParams.PEP_COUNT.toString
  val filterDescription = "protein set filter on  peptide count"

  // IFilter methods  
  def getFilterProperties(): Map[String, Any] = {
    val props = new HashMap[String, Any]
    props += (FilterPropertyKeys.THRESHOLD_VALUE -> minNbrPep)
    props.toMap
  }

  def getThresholdValue(): Any = minNbrPep

  def setThresholdValue(currentVal: Any): Unit ={
    minNbrPep = toInt(currentVal)
  }

  // IProteinSetFilter  methods   
  def filterProteinSets(protSets: Seq[ProteinSet], incrementalValidation: Boolean, traceability: Boolean): Unit = {

    // Reset validation status if validation is not incremental
    if (!incrementalValidation) ProteinSetFiltering.resetProteinSetValidationStatus(protSets)

    protSets.foreach(pSet => {
      if (pSet.peptideSet.getPeptideIds.length < minNbrPep) {
        pSet.isValidated = false
        //Decrease associated PeptideInstance validatedProteinSetsCount 
        pSet.peptideSet.getPeptideInstances.foreach(pInst => {
          pInst.validatedProteinSetsCount = pInst.validatedProteinSetsCount - 1
        })
      }
    })
  }
}