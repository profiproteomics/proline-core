package fr.proline.core.algo.msi.filter

import scala.collection.mutable.HashMap
import scala.collection.Seq

import fr.proline.core.algo.msi.validation.MascotValidationHelper
import fr.proline.core.om.model.msi.{PeptideMatch}

class ScorePSMFilter(var scoreThreshold: Double = 1.0, val startThreshold: Double = 1.0 ) extends IComputablePeptideMatchFilter {

  val filterParameter = PeptideMatchFilterParams.SCORE.toString
  val filterDescription = "peptide match score filter"

  def filterPSM( pepMatches: Seq[PeptideMatch], incrementalValidation: Boolean, traceability: Boolean ): Unit = {

    //Create a Map : List of PeptideMatch by eValue
    val psmsByScore = pepMatches.groupBy( _.score )

    //Get Map entry for valid eValue
    val validePsmsByEValue = psmsByScore.filterKeys( _ <= scoreThreshold )

    //Get Map entry for invalid eValue
    val invalidePsmsByEValue = psmsByScore.filterKeys( _ > scoreThreshold )

    // Set isValidated property 
    if ( !incrementalValidation ) {
      validePsmsByEValue.flatten( _._2 ).foreach( _.isValidated = true )
    }

    invalidePsmsByEValue.flatten( _._2 ).foreach( _.isValidated = false )
   
  }

  def getFilterProperties(): Option[Map[String, Any]] = {
    val props =new HashMap[String, Any]
    props += (FiltersPropertyKeys.SCORE_THRESHOLD ->  scoreThreshold )
    Some( props.toMap )
  }

  def getNextValue( currentVal: AnyVal ) = {
    currentVal.asInstanceOf[Double] + 1.0
  }

  def getThresholdStartValue(): AnyVal = {
    1
  }

  def setThresholdValue( currentVal: AnyVal ){
    scoreThreshold = currentVal.asInstanceOf[Double]
  }
}