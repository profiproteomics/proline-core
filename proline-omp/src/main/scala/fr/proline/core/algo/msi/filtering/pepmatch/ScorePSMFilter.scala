package fr.proline.core.algo.msi.filtering.pepmatch

import com.typesafe.scalalogging.LazyLogging
import fr.profi.util.primitives._
import fr.proline.core.algo.msi.filtering._
import fr.proline.core.om.model.msi.PeptideMatch

import scala.collection.Seq
import scala.collection.mutable.HashMap

object ScorePSMFilter {
  val thresholdIncreaseValue = 0.1f
}

class ScorePSMFilter(var scoreThreshold: Float = 0.0f, var thresholdStartValue : Float = 0.0f ) extends IOptimizablePeptideMatchFilter with LazyLogging {

  val filterParameter = PepMatchFilterParams.SCORE.toString
  val filterDescription = "peptide match score filter"
    
  def getPeptideMatchValueForFiltering(pepMatch: PeptideMatch): Any = pepMatch.score
  
  def filterPeptideMatches( pepMatches: Seq[PeptideMatch], incrementalValidation: Boolean, traceability: Boolean ): Unit = {
    
    // Reset validation status if validation is not incremental
    if( !incrementalValidation ) PeptideMatchFiltering.resetPepMatchValidationStatus(pepMatches)
    
    pepMatches.filter( ! isPeptideMatchValid(_) ).foreach( _.isValidated = false )
  }
  
  def isPeptideMatchValid( pepMatch: PeptideMatch ): Boolean = {
    pepMatch.score >= scoreThreshold    
  }
  
  def compare(a: PeptideMatch, b: PeptideMatch): Int = {
    b.score compare a.score
  }

  def getFilterProperties(): Map[String, Any] = {
    val props = new HashMap[String, Any]
    props += (FilterPropertyKeys.THRESHOLD_VALUE -> scoreThreshold )
    props.toMap
  }

  def getNextValue( currentVal: Any): Any = toFloat(currentVal) + ScorePSMFilter.thresholdIncreaseValue
  
  def getThresholdStartValue(): Any = thresholdStartValue
  
  def getThresholdValue(): Any = scoreThreshold
  
  def setThresholdValue( currentVal: Any ): Unit ={
    scoreThreshold = toFloat(currentVal)
  }
}