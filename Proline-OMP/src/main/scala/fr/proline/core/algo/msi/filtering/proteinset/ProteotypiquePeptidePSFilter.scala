package fr.proline.core.algo.msi.filtering.proteinset

import scala.collection.mutable.HashMap
import scala.collection.Seq
import com.weiglewilczek.slf4s.Logging

import fr.proline.core.algo.msi.filtering._
import fr.proline.core.algo.msi.validation.MascotValidationHelper
import fr.proline.core.om.model.msi.ProteinSet


class ProteotypiquePeptidePSFilter(
  var minNbrPep: Int = 1
) extends IProteinSetFilter with Logging {

  val filterParameter = ProtSetFilterParams.PROTEOTYPIQUE_PEP.toString
  val filterDescription = "protein set filter on proteotypique peptide"
  
   // IFilter methods  
  def getFilterProperties(): Map[String, Any] = {
    val props = new HashMap[String, Any]
    props += (FilterPropertyKeys.THRESHOLD_VALUE -> minNbrPep )
    props.toMap
  }

  def getThresholdValue(): AnyVal = minNbrPep

  def setThresholdValue(currentVal: AnyVal) {
    if (currentVal.isInstanceOf[Int]) {
      minNbrPep = currentVal.asInstanceOf[Int]
    } else {
      minNbrPep = currentVal.asInstanceOf[Number].intValue
    }
  }

   // IProteinSetFilter  methods   
  def filterProteinSets(protSets: Seq[ProteinSet], incrementalValidation: Boolean, traceability: Boolean): Unit = {
    
    // Reset validation status if validation is not incremental
    if( !incrementalValidation ) ProteinSetFiltering.resetProteinSetValidationStatus(protSets)
    
    protSets.foreach( pSet => {
      if(pSet.peptideSet.getPeptideInstances.filter(_.proteinSetsCount == 1).length < minNbrPep)
	  pSet.isValidated = false
    })       
  }
  
}