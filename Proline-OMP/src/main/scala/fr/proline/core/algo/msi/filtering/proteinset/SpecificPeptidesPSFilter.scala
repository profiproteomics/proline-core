package fr.proline.core.algo.msi.filtering.proteinset

import scala.collection.Seq
import scala.collection.mutable.HashMap

import com.typesafe.scalalogging.slf4j.Logging

import fr.proline.core.algo.msi.filtering.{FilterPropertyKeys, IProteinSetFilter, ProtSetFilterParams, ProteinSetFiltering}
import fr.proline.core.om.model.msi.ProteinSet
import fr.profi.util.primitives._

class SpecificPeptidesPSFilter(
  var minNbrPep: Int = 1) extends IProteinSetFilter with Logging {

  val filterParameter = ProtSetFilterParams.SPECIFIC_PEP.toString
  val filterDescription = "protein set filter on specifique peptide (protein set context)"

  // IFilter methods  
  def getFilterProperties(): Map[String, Any] = {
    val props = new HashMap[String, Any]
    props += (FilterPropertyKeys.THRESHOLD_VALUE -> minNbrPep)
    props.toMap
  }

  def getThresholdValue(): AnyVal = minNbrPep

  def setThresholdValue(currentVal: AnyVal) {
    minNbrPep = toInt(currentVal)
  }


  // IProteinSetFilter  methods   
  def filterProteinSets(protSets: Seq[ProteinSet], incrementalValidation: Boolean, traceability: Boolean): Unit = {

    protSets.sortBy(_.peptideSet.score)

    // Reset validation status if validation is not incremental
    if (!incrementalValidation) ProteinSetFiltering.resetProteinSetValidationStatus(protSets)

    protSets.foreach(pSet => {
      var nbrPepSpecific = 0     
      pSet.peptideSet.getPeptideInstances.foreach(pInst => {
		if(pInst.validatedProteinSetsCount <= 1){
            nbrPepSpecific += 1
        }
      })

      if (nbrPepSpecific < minNbrPep) {
        pSet.isValidated = false
        pSet.peptideSet.getPeptideInstances.foreach(pInst => {
          pInst.validatedProteinSetsCount = pInst.validatedProteinSetsCount-1
        })
      }
    })
  }

}