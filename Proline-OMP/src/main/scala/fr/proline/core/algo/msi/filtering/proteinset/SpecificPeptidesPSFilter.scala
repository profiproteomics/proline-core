package fr.proline.core.algo.msi.filtering.proteinset

import scala.collection.Seq
import scala.collection.mutable.HashMap

import com.typesafe.scalalogging.LazyLogging

import fr.proline.core.algo.msi.filtering.{FilterPropertyKeys, IProteinSetFilter, ProtSetFilterParams, ProteinSetFiltering}
import fr.proline.core.om.model.msi.ProteinSet
import fr.profi.util.primitives._

class SpecificPeptidesPSFilter(
  var minNbrPep: Int = 1) extends IProteinSetFilter with LazyLogging {

  val filterParameter = ProtSetFilterParams.SPECIFIC_PEP.toString
  val filterDescription = "protein set filter on specific peptide (protein set context)"

  // IFilter methods  
  def getFilterProperties(): Map[String, Any] = {
    val props = new HashMap[String, Any]
    props += (FilterPropertyKeys.THRESHOLD_VALUE -> minNbrPep)
    props.toMap
  }

  def getThresholdValue(): Any = minNbrPep

  def setThresholdValue(currentVal: Any): Unit= {
    minNbrPep = toInt(currentVal)
  }


  // IProteinSetFilter  methods   
  def filterProteinSets(protSets: Seq[ProteinSet], incrementalValidation: Boolean, traceability: Boolean): Unit = {   

    // Reset validation status if validation is not incremental
    if (!incrementalValidation) ProteinSetFiltering.resetProteinSetValidationStatus(protSets)
    
   val sortedProtSets = protSets.sortBy(_.peptideSet.score)

    sortedProtSets.foreach(pSet => {
      var nbrPepSpecific = 0     
      pSet.peptideSet.getPeptideInstances.foreach(pInst => {
		if(pInst.validatedProteinSetsCount <= 1){
            nbrPepSpecific += 1
        }
      })

      if (nbrPepSpecific < minNbrPep) {
        pSet.isValidated = false
        logger.info("INVALID PROT SET => "+pSet.toString())
        pSet.peptideSet.getPeptideInstances.foreach(pInst => {
          pInst.validatedProteinSetsCount = pInst.validatedProteinSetsCount-1
        })
      }
    })
  }

}