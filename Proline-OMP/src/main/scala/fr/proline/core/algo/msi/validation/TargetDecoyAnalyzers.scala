package fr.proline.core.algo.msi.validation

import scala.collection.mutable.ArrayBuffer
import fr.proline.core.om.model.msi.PeptideMatch
import fr.proline.core.algo.msi.filter.IPeptideMatchFilter
import fr.proline.core.algo.msi.filter.IOptimizablePeptideMatchFilter
import com.weiglewilczek.slf4s.Logging


abstract class ITargetDecoyAnalyzer {
  
  def calcTDStatistics( targetPepMatches: Seq[PeptideMatch], decoyPepMatches: Seq[PeptideMatch] ): ValidationResult
  
  def calcTDStatistics( pepMatchJointMap: Map[Int, Seq[PeptideMatch]]): ValidationResult
  
  def performROCAnalysis(
    targetPepMatches: Seq[PeptideMatch], 
    decoyPepMatches: Seq[PeptideMatch], 
    validationFilter: IOptimizablePeptideMatchFilter
  ): Array[ValidationResult]
  
}

abstract class AbstractTargetDecoyAnalyzer extends ITargetDecoyAnalyzer with Logging {
  
  def performROCAnalysis(
    targetPepMatches: Seq[PeptideMatch], 
    decoyPepMatches: Seq[PeptideMatch], 
    validationFilter: IOptimizablePeptideMatchFilter
  ): Array[ValidationResult] = {
    
    // Memorize validation status of peptide matches
    val allPepMatches = ( targetPepMatches ++ decoyPepMatches )
    val valStatusByPmId = allPepMatches.map( pm => pm.id -> pm.isValidated).toMap
    
    // Build the peptide match joint map
    val pmJointMap = TargetDecoyComputer.buildPeptideMatchJointMap(targetPepMatches, Some(decoyPepMatches))
    
    // Define some vars
    var filterThreshold = validationFilter.getThresholdStartValue
    var fdr = 100.0f
    val rocPoints = new ArrayBuffer[ValidationResult]
    logger.debug(" # Map entries "+pmJointMap.size)
	
    while (fdr > 0) { // iterate from FDR = 100.0 to 0.0
      
      /*
      var (tB, tO, dB, dO) = (0, 0, 0, 0) // Counts to target / decoy only or better 

	  validationFilter.setThresholdValue(filterThreshold)
      pmJointMap.foreach { entry => 
	  
//--------  For Map entry : Query ID => Associated PSM
        // Save previous isValidated status
        val isValStatus: Seq[Boolean] = entry._2.map(_.isValidated)

        //-- Filter incrementally without traceability 
        validationFilter.filterPeptideMatches(entry._2, true, false)

        // Keep only validated PSM
        val selectedPsm = entry._2.filter(_.isValidated)

        //-- Look which type of PSM (Target or Decoy) are still valid        
        var foundDecoy = false
        var foundTarget = false
        // Specify which type of PSM (Target or Decoy) was first found. Set to -1 for Decoy and to 1 for Target 
        var firstFound = 0

        // Verify witch type of PSM (Target or Decoy) are still valid
        selectedPsm.foreach { psm =>
          if (psm.isDecoy) {
            foundDecoy = true
            if (firstFound == 0) firstFound = -1
          } else {
            foundTarget = true
            if (firstFound == 0) firstFound = 1
          }
        }

        // Update Target/Decoy count
        if (foundDecoy && !foundTarget)
          tO += 1
        if (foundTarget && !foundDecoy)
          dO += 1
        if (foundTarget && foundDecoy) {
          // VDS Should found better : take first in list... Should use Filter sort method ?? 
          if (firstFound == -1)
            dB += 1
          else
            tB += 1
        }

        // Reinit previous isValidated status
        var psmIndex = 0      
        while (psmIndex < entry._2.size) {
          entry._2(psmIndex).isValidated = isValStatus(psmIndex)
		  psmIndex+=1
        }

      } // End go through pmJointMap entries
      */
      
      // Update filter threshold
      validationFilter.setThresholdValue(filterThreshold)
      
      // Restore peptide matches validation status to include previous filtering steps
      allPepMatches.foreach( pm => pm.isValidated = valStatusByPmId(pm.id) )
      
      // Apply filter on target and decoy peptide matches
      validationFilter.filterPeptideMatches(allPepMatches, true, false)
      
      // Compute the ROC point
      val rocPoint = this.calcTDStatistics(pmJointMap)
      
      // Set ROC point validation properties
      rocPoint.addProperties( validationFilter.getFilterProperties )
      
      // Add ROC point to the curve
      rocPoints += rocPoint
      
      // Update the current FDR
      if( rocPoint.fdr.isDefined ) fdr = rocPoint.fdr.get
      logger.debug(" New FDR = "+fdr)
      // Update threshold value
      filterThreshold = validationFilter.getNextValue(filterThreshold)
      
      //if (filterThreshold == null) fdr = 0 //Break current loop

    } //Go through all possible threshold value while FDR is greater than zero
    
    // Restore peptide matches validation status
    allPepMatches.foreach( pm => pm.isValidated = valStatusByPmId(pm.id) )
    
    rocPoints.toArray
  }
  
}

class BasicTDAnalyzer( targetDecoyMode: TargetDecoyModes.Mode ) extends AbstractTargetDecoyAnalyzer {

  def calcTDStatistics( targetPepMatches: Seq[PeptideMatch], decoyPepMatches: Seq[PeptideMatch] ): ValidationResult = {
    
    val targetMatchesCount = targetPepMatches.count( _.isValidated )
    val decoyMatchesCount = decoyPepMatches.count( _.isValidated )
    
    val fdr = if( targetMatchesCount == 0 ) Float.NaN
    else {
      targetDecoyMode match {
        case TargetDecoyModes.CONCATENATED => TargetDecoyComputer.calcCdFDR(targetMatchesCount, decoyMatchesCount)
        case TargetDecoyModes.SEPARATED    => TargetDecoyComputer.calcSdFDR(targetMatchesCount, decoyMatchesCount)
        case _                             => throw new Exception("unknown target decoy mode: " + targetDecoyMode)
      }
    }
    
    ValidationResult(
      targetMatchesCount = targetMatchesCount,
      decoyMatchesCount = Some(decoyMatchesCount),
      fdr = if( fdr.isNaN ) None else Some(fdr)
    )
    
  }
  
  def calcTDStatistics( pepMatchJointMap: Map[Int, Seq[PeptideMatch]]): ValidationResult = {
    val allPepMatches = pepMatchJointMap.flatMap( _._2 ).toSeq
    val( decoyPepMatches, targetPepMatches ) = allPepMatches.partition( _.isDecoy)
    
    this.calcTDStatistics(targetPepMatches, decoyPepMatches )
  }
  
}

class CompetitionBasedTDAnalyzer( val validationFilter: IPeptideMatchFilter ) extends AbstractTargetDecoyAnalyzer {

  def calcTDStatistics( targetPepMatches: Seq[PeptideMatch], decoyPepMatches: Seq[PeptideMatch] ): ValidationResult = {    
    val pmJointMap = TargetDecoyComputer.buildPeptideMatchJointMap(targetPepMatches,Some(decoyPepMatches))
    this.calcTDStatistics( pmJointMap )
  }
  
  def calcTDStatistics( pepMatchJointMap: Map[Int, Seq[PeptideMatch]]): ValidationResult = {
    TargetDecoyComputer.validatePepMatchesWithCompetition(pepMatchJointMap, validationFilter)
  }
  
}