package fr.proline.core.algo.msi.filtering.pepmatch

import scala.collection.mutable.HashMap
import scala.collection.Seq
import com.weiglewilczek.slf4s.Logging

import fr.proline.core.algo.msi.filtering._
import fr.proline.core.algo.msi.validation.MascotValidationHelper
import fr.proline.core.om.model.msi.{PeptideMatch}

object ScorePSMFilter {
  val thresholdStartValue = 13.0f
  val thresholdIncreaseValue = 0.1f
}

class ScorePSMFilter(var scoreThreshold: Float = ScorePSMFilter.thresholdStartValue ) extends IOptimizablePeptideMatchFilter with Logging {

  val filterParameter = PepMatchFilterParams.SCORE.toString
  val filterDescription = "peptide match score filter"
    
  def getPeptideMatchValueForFiltering(pepMatch: PeptideMatch): AnyVal = pepMatch.score
  
  def filterPeptideMatches( pepMatches: Seq[PeptideMatch], incrementalValidation: Boolean, traceability: Boolean ): Unit = {
    
    // Reset validation status if validation is not incremental
    if( !incrementalValidation ) this.resetPepMatchValidationStatus(pepMatches)
    
    pepMatches.filter(_.score < scoreThreshold ).foreach( _.isValidated = false )
  }
  
  def sortPeptideMatches( pepMatches: Seq[PeptideMatch] ): Seq[PeptideMatch] = {
    pepMatches.sortWith( _.score > _.score )
  }

  def getFilterProperties(): Map[String, Any] = {
    val props = new HashMap[String, Any]
    props += (FilterPropertyKeys.THRESHOLD_VALUE -> scoreThreshold )
    props.toMap
  }

  def getNextValue( currentVal: AnyVal ) = currentVal.asInstanceOf[Float] + ScorePSMFilter.thresholdIncreaseValue
  
  def getThresholdStartValue(): AnyVal = ScorePSMFilter.thresholdStartValue
  
  def getThresholdValue(): AnyVal = scoreThreshold
  
  def setThresholdValue( currentVal: AnyVal ){
    scoreThreshold = currentVal.asInstanceOf[Float]
  }
}