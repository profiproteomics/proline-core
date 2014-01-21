package fr.proline.core.algo.msi.filtering.proteinset

import scala.collection.mutable.HashMap
import scala.collection.Seq
import com.typesafe.scalalogging.slf4j.Logging

import fr.proline.core.algo.msi.filtering._
import fr.proline.core.algo.msi.validation.MascotValidationHelper
import fr.proline.core.om.model.msi.ProteinSet

object ScoreProtSetFilter {
  val thresholdStartValue = 0.0f
  val thresholdIncreaseValue = 0.1f
}

class ScoreProtSetFilter(
  var scoreThreshold: Float = ScoreProtSetFilter.thresholdStartValue
) extends IOptimizableProteinSetFilter with Logging {

  val filterParameter = ProtSetFilterParams.SCORE.toString
  val filterDescription = "protein set score filter"
    
  def getProteinSetValueForFiltering( protSet: ProteinSet ): AnyVal = {
    protSet.peptideSet.score
  }
  
  def isProteinSetValid( protSet: ProteinSet ): Boolean = {
    protSet.peptideSet.score >= scoreThreshold
  }
  
  def sortProteinSets( protSets: Seq[ProteinSet] ): Seq[ProteinSet] = {
    protSets.sortWith( _.peptideSet.score > _.peptideSet.score )
  }

  def getFilterProperties(): Map[String, Any] = {
    val props = new HashMap[String, Any]
    props += (FilterPropertyKeys.THRESHOLD_VALUE -> scoreThreshold )
    props.toMap
  }

  def getNextValue( currentVal: AnyVal ) = currentVal.asInstanceOf[Float] + ScoreProtSetFilter.thresholdIncreaseValue
  
  def getThresholdStartValue(): AnyVal = ScoreProtSetFilter.thresholdStartValue
  
  def getThresholdValue(): AnyVal = scoreThreshold
  
  def setThresholdValue( currentVal: AnyVal ){
    scoreThreshold = currentVal.asInstanceOf[Float]
  }
}