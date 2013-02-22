package fr.proline.core.algo.msi.filter

import fr.proline.core.om.model.msi.PeptideMatch
import scala.collection.mutable.HashMap

object NullOptimizablePSMFilter extends IOptimizablePeptideMatchFilter {
  
  val filterParameter = PepMatchFilterParams.NONE.toString
  val filterDescription = "a filter which does nothing"

  def filterPeptideMatches( pepMatches: Seq[PeptideMatch], incrementalValidation: Boolean, traceability: Boolean ): Unit = {
  }

  def getFilterProperties(): Option[Map[String, Any]] = None
  
  def getNextValue( currentVal: AnyVal ) = 0.0
  
  def getThresholdStartValue(): AnyVal = 0.0
  
  def setThresholdValue( currentVal : AnyVal ) { }
  

}

object NullPSMFilter extends IPeptideMatchFilter {
  
  val filterParameter = PepMatchFilterParams.NONE.toString
  val filterDescription = "a filter which does nothing"

  def filterPeptideMatches( pepMatches: Seq[PeptideMatch], incrementalValidation: Boolean, traceability: Boolean ): Unit = {
  }

  def getFilterProperties(): Option[Map[String, Any]] = None
  
  def setThresholdValue( currentVal : AnyVal ) { }
  

}