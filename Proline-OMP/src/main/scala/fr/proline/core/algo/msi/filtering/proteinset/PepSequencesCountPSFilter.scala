package fr.proline.core.algo.msi.filtering.proteinset

import scala.collection.Seq
import scala.collection.mutable.HashMap

import com.typesafe.scalalogging.LazyLogging

import fr.proline.core.algo.msi.filtering.{FilterPropertyKeys, IProteinSetFilter, ProtSetFilterParams, ProteinSetFiltering}
import fr.proline.core.om.model.msi.ProteinSet
import fr.profi.util.primitives._

class PepSequencesCountPSFilter(
  var minNbrSeq: Int = 1) extends IProteinSetFilter with LazyLogging {

  val filterParameter = ProtSetFilterParams.PEP_SEQ_COUNT.toString
  val filterDescription = "protein set filter on peptide sequences count "

  // IFilter methods  
  def getFilterProperties(): Map[String, Any] = {
    val props = new HashMap[String, Any]
    props += (FilterPropertyKeys.THRESHOLD_VALUE -> minNbrSeq)
    props.toMap
  }

  def getThresholdValue(): AnyVal = minNbrSeq

  def setThresholdValue(currentVal: AnyVal) {
    minNbrSeq = toInt(currentVal)
  }


  // IProteinSetFilter  methods   
  def filterProteinSets(protSets: Seq[ProteinSet], incrementalValidation: Boolean, traceability: Boolean): Unit = {

    protSets.sortBy(_.peptideSet.score)

    // Reset validation status if validation is not incremental
    if (!incrementalValidation) ProteinSetFiltering.resetProteinSetValidationStatus(protSets)

    protSets.foreach(pSet => {
    
      val pepSeq2PepInst = pSet.peptideSet.getPeptideInstances.groupBy(_.peptide.sequence)
      if(pepSeq2PepInst.size  < minNbrSeq){
    	  pSet.isValidated = false
		  pSet.peptideSet.getPeptideInstances.foreach(pInst => {
			  pInst.validatedProteinSetsCount = pInst.validatedProteinSetsCount-1
		  })
	  }

    })
    
  }

}