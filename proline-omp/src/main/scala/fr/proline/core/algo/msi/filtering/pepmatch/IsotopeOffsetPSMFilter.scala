package fr.proline.core.algo.msi.filtering.pepmatch

import com.typesafe.scalalogging.LazyLogging
import fr.proline.core.algo.msi.filtering.{FilterPropertyKeys, IPeptideMatchFilter, PepMatchFilterParams, PeptideMatchFiltering}
import fr.proline.core.om.model.msi.PeptideMatch

import scala.collection.mutable.HashMap

class IsotopeOffsetPSMFilter(var offsetThreshold: Int = 1) extends IPeptideMatchFilter with LazyLogging {

  val filterParameter = PepMatchFilterParams.ISOTOPE_OFFSET.toString
  val filterDescription = "peptide match filter on isotope offset"

  /**
    * Validate each PeptideMatch by setting their isValidated attribute. A valid PeptideMatch is a PepTideMatch
    * whose Isotope offset used for matching is less or equal to specified threshold
    *
    *
    * @param pepMatches            All PeptideMatches
    * @param incrementalValidation If incrementalValidation is set to false,
    * PeptideMatch.isValidated will be explicitly set to true or false.
    *                              Otherwise, only excluded PeptideMatch will be changed by setting their isValidated property to false.
    * @param traceability          Specify if filter could saved information in peptideMatch properties
    *
    */
  override def filterPeptideMatches(pepMatches: Seq[PeptideMatch], incrementalValidation: Boolean, traceability: Boolean): Unit = {

    // Reset validation status if validation is not incremental
    if( !incrementalValidation ) PeptideMatchFiltering.resetPepMatchValidationStatus(pepMatches)

    pepMatches.foreach( pepM =>{
      if (pepM.properties.isEmpty || pepM.properties.get.isotopeOffset.isEmpty){//suppose offset  0
        if(0 <= offsetThreshold ) //should not happen !
          pepM.isValidated = false
      } else {
        if( pepM.properties.get.isotopeOffset.get > offsetThreshold) { //pepMatches offset greater than threshold => invalidate pepM
          logger.trace(" Filter with "+offsetThreshold+ " <> "+pepM.properties.get.isotopeOffset.get )
          pepM.isValidated = false
        }
      }
    })
  }

//  /**
//    * Returns the value that will be used to filter the peptide match.
//    */
//  override def getPeptideMatchValueForFiltering(pepMatch: PeptideMatch): Any = {
//    if (pepMatch.properties.isEmpty || pepMatch.properties.get.isotopeOffset.isEmpty)
//      return 0
//
//    pepMatch.properties.get.isotopeOffset.get
//  }


  /**
    * Returns the Threshold value that has been set.
    */
  override def getThresholdValue(): Any = offsetThreshold


  override def setThresholdValue(newThr: Any): Unit = {
    require(newThr.isInstanceOf[Int], s"Specified threshold, $newThr, isn't valid for IsotopeOffsetPSMFilter. ")
    offsetThreshold = newThr.asInstanceOf[Int]
  }

  /**
    * Return all properties that will be usefull to know wich kind iof filter have been applied
    * and be able to reapply it.
    *
    */
  override def getFilterProperties(): Map[String, Any] =  {
    val props = new HashMap[String, Any]
    props += (FilterPropertyKeys.THRESHOLD_VALUE -> offsetThreshold )
    props.toMap
  }
}
