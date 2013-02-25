package fr.proline.core.algo.msi.validation.proteinset

import com.weiglewilczek.slf4s.Logging
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import fr.proline.core.algo.msi.validation._
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.om.model.msi.ProteinSet
import fr.proline.core.om.model.msi.PeptideMatch
import fr.proline.core.algo.msi.filtering._

class BasicProtSetValidator( val protSetFilter: IProteinSetFilter ) extends IProteinSetValidator with Logging {
  
  val expectedFdr: Option[Float] = None
  
  def validateProteinSets( targetRsm: ResultSummary, decoyRsm: Option[ResultSummary] ): ValidationResults = {
    
    // Retrieve some vars
    val targetProtSets = targetRsm.proteinSets
    val decoyProtSets = decoyRsm.map(_.proteinSets)
    val allProtSets = targetProtSets ++ decoyProtSets.getOrElse(Array())
    
    // Filter protein sets
    protSetFilter.filterProteinSets( allProtSets, true, true )
    
    // Compute validation result
    val valResult = this.computeValidationResult(targetRsm, decoyRsm)
    
    // Update validation result properties
    valResult.addProperties( protSetFilter.getFilterProperties )
    
    // Return validation results
    ValidationResults( valResult )
  }
  
}