package fr.proline.core.algo.msi.filtering.proteinset

import scala.collection.Seq
import scala.collection.mutable.HashMap

import com.weiglewilczek.slf4s.Logging

import fr.proline.core.algo.msi.filtering.{FilterPropertyKeys, IProteinSetFilter, ProtSetFilterParams, ProteinSetFiltering}
import fr.proline.core.om.model.msi.ProteinSet
import fr.proline.util.primitives.toInt

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

  private def getPepInstanceProtSetCount(protSets: Seq[ProteinSet]): collection.mutable.Map[Int, Int] = {
    val pepInst = protSets.flatten(_.peptideSet.getPeptideInstances)
    collection.mutable.Map() ++ (pepInst.map(pi => pi.id -> pi.proteinSetsCount).toMap)
  }

  // IProteinSetFilter  methods   
  def filterProteinSets(protSets: Seq[ProteinSet], incrementalValidation: Boolean, traceability: Boolean): Unit = {

    protSets.sortBy(_.score)
    val protSetCountByPepInst = getPepInstanceProtSetCount(protSets)

    // Reset validation status if validation is not incremental
    if (!incrementalValidation) ProteinSetFiltering.resetProteinSetValidationStatus(protSets)

    protSets.foreach(pSet => {
      var nbrPepSpecific = 0
      var pSetPepInstIdSeq = Seq.newBuilder[Int]
      pSet.peptideSet.getPeptideInstances.foreach(pInst => {
        pSetPepInstIdSeq += pInst.id
        if (protSetCountByPepInst(pInst.id) == 1)
          nbrPepSpecific += 1

      })

      if (nbrPepSpecific < minNbrPep) {
        pSet.isValidated = false
        pSetPepInstIdSeq.result.foreach(pId => {
          protSetCountByPepInst(pId) = protSetCountByPepInst(pId) - 1
        })
      }
    })
  }

}