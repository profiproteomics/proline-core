package fr.proline.core.algo.msi.filter

import scala.collection.mutable.HashMap
import scala.collection.Seq

import fr.proline.core.algo.msi.validation.MascotValidationHelper
import fr.proline.core.om.model.msi.{PeptideMatch,PeptideMatchResultSummaryProperties}

class MascotEValuePSMFilter( var eValueThreshold: Double = 1.0 ) extends IOptimizablePeptideMatchFilter {
  
  val filterParameter = PepMatchFilterParams.MASCOT_EVALUE.toString
  val filterDescription = "peptide match mascot eValue filter"

  def filterPeptideMatches( pepMatches: Seq[PeptideMatch], incrementalValidation: Boolean, traceability: Boolean ): Unit = {

    //Create a Map : List of PeptideMatch by eValue
    val psmsByEValue = pepMatches.groupBy( MascotValidationHelper.calcPepMatchEvalue( _ ) )

    //Get Map entry for valid eValue
    val validePsmsByEValue = psmsByEValue.filterKeys( _ <= eValueThreshold )

    //Get Map entry for invalid eValue
    val invalidePsmsByEValue = psmsByEValue.filterKeys( _ > eValueThreshold )

    // Set isValidated property 
    if ( !incrementalValidation ) {
      validePsmsByEValue.flatten( _._2 ).foreach( _.isValidated = true )
    }

    invalidePsmsByEValue.flatten( _._2 ).foreach( _.isValidated = false )

    if ( traceability ) {
      pepMatches.foreach( updatePeptideMatchProperties( _ ) )
    }
  }

  def updatePeptideMatchProperties( pepMatch: PeptideMatch ) {

    var pepMatchValProps = pepMatch.validationProperties.orElse( Some( new PeptideMatchResultSummaryProperties() ) ).get
    var filtersPropByRank = pepMatchValProps.setMascotAdjustedExpectationValue( Some( MascotValidationHelper.calcPepMatchEvalue( pepMatch ) ) )

    pepMatch.validationProperties = Some( pepMatchValProps )
  }

  def getFilterProperties(): Option[Map[String, Any]] = {
    val props =new HashMap[String, Any]
    props += (PepMatchFilterPropertyKeys.MASCOT_EVALUE_THRESHOLD -> eValueThreshold )
    Some( props.toMap )
  }

  def getNextValue( currentVal: AnyVal ) = {
    currentVal.asInstanceOf[Double] * 0.95
  }

  def getThresholdStartValue(): AnyVal = 1.0

  def setThresholdValue( currentVal : AnyVal ) {
    eValueThreshold = currentVal.asInstanceOf[Double]
  }
}