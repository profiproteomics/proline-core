package fr.proline.core.algo.msi.filtering.pepmatch

import scala.collection.mutable.HashMap
import scala.collection.Seq
import fr.proline.core.algo.msi.validation.MascotValidationHelper
import fr.proline.core.om.model.msi.{PeptideMatch,PeptideMatchResultSummaryProperties}
import fr.proline.core.algo.msi.filtering._
import fr.profi.util.primitives._

abstract class AbstractMascotEValueFilter extends IOptimizablePeptideMatchFilter {
  
  var eValueThreshold: Double
  
  def getPeptideMatchValueForFiltering( pepMatch: PeptideMatch ): AnyVal  
  protected def updatePeptideMatchProperties( pepMatch: PeptideMatch ): Unit
  
  def getPeptideMatchEValue( pepMatch: PeptideMatch ): Double = {    
    toDouble(getPeptideMatchValueForFiltering( pepMatch ))
  }
  
  // TODO: maybe we can move this method in IOptimizablePeptideMatchFilter
  def filterPeptideMatches( pepMatches: Seq[PeptideMatch], incrementalValidation: Boolean, traceability: Boolean ): Unit = {
  
    // Reset validation status if validation is not incremental
    if( !incrementalValidation ) PeptideMatchFiltering.resetPepMatchValidationStatus(pepMatches)
    
    /*
    // Create a Map : List of PeptideMatch by eValue
    val psmsByEValue = pepMatches.groupBy( getPeptideMatchEValue( _ ) )

    // Get Map entry for valid eValue
    val validPsmsByEValue = psmsByEValue.filterKeys( _ <= eValueThreshold )

    // Get Map entry for invalid eValue
    val invalidPsmsByEValue = psmsByEValue.filterKeys( _ > eValueThreshold )
    
    // Update validation status of invalid peptide matches
    invalidPsmsByEValue.flatten( _._2 ).foreach( _.isValidated = false )
    */
    
    // Unvalidate peptide matches that doesn't have the riht eValue
    pepMatches.foreach( pm => if(isPeptideMatchValid(pm) == false) pm.isValidated = false )

    if ( traceability ) {
      pepMatches.foreach( updatePeptideMatchProperties( _ ) )
    }
  }
  
  def isPeptideMatchValid( pepMatch: PeptideMatch ): Boolean = {
    getPeptideMatchEValue(pepMatch) <= eValueThreshold    
  }
  
  def sortPeptideMatches( pepMatches: Seq[PeptideMatch] ): Seq[PeptideMatch] = {
    pepMatches.sortBy( this.getPeptideMatchEValue(_) )
  }
  
  def getFilterProperties(): Map[String, Any] = {
    val props =new HashMap[String, Any]
    props += (FilterPropertyKeys.THRESHOLD_VALUE -> eValueThreshold )
    props.toMap
  }

  def getNextValue( currentVal: AnyVal ) = {
    toDouble(currentVal) * 0.95
  }

  def getThresholdStartValue(): AnyVal = 1.0
  
  def getThresholdValue(): AnyVal = eValueThreshold

  def setThresholdValue( currentVal : AnyVal ) = {
    eValueThreshold = toDouble(currentVal)
  }
  
}

class MascotAdjustedEValuePSMFilter( var eValueThreshold: Double = 1.0 ) extends AbstractMascotEValueFilter {
  
  val filterParameter = PepMatchFilterParams.MASCOT_ADJUSTED_EVALUE.toString
  val filterDescription = "peptide match mascot adjusted e-value filter"
    
  def getPeptideMatchValueForFiltering( pepMatch: PeptideMatch ): AnyVal = {
    MascotValidationHelper.calcPepMatchEvalue( pepMatch )
  }

  protected def updatePeptideMatchProperties( pepMatch: PeptideMatch ) {

    val adjustedEvalue = this.getPeptideMatchEValue(pepMatch)
    
    val pepMatchValProps = pepMatch.validationProperties.getOrElse(new PeptideMatchResultSummaryProperties)
    pepMatchValProps.setMascotAdjustedExpectationValue( Some( adjustedEvalue ) )
    pepMatchValProps.setMascotScoreOffset( Some( MascotValidationHelper.calcScoreThresholdOffset(adjustedEvalue, eValueThreshold) ) )

    pepMatch.validationProperties = Some( pepMatchValProps )
  }

}

class MascotEValuePSMFilter( var eValueThreshold: Double = 1.0 ) extends AbstractMascotEValueFilter {
  
  val filterParameter = PepMatchFilterParams.MASCOT_EVALUE.toString
  val filterDescription = "peptide match mascot e-value filter"
  
  def getPeptideMatchValueForFiltering( pepMatch: PeptideMatch ): Double = {
    pepMatch.properties.get.getMascotProperties.get.getExpectationValue
  }
  
  protected def updatePeptideMatchProperties( pepMatch: PeptideMatch ) {
    ()
  }
  
}