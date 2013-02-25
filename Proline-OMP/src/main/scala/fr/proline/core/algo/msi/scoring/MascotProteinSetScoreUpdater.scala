package fr.proline.core.algo.msi.scoring

import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.algo.msi.validation.MascotValidationHelper

class MascotProteinSetScoreUpdater(scoreThresholdOffset: Float = 0f) extends IProteinSetScoreUpdater {

  def updateScoreOfProteinSets( rsm: ResultSummary ) {
    
    val bestPepMatchesByProtSetId = rsm.getBestPepMatchesByProtSetId
    
    for( proteinSet <- rsm.proteinSets ) {
      
      val proteinSetId = proteinSet.id
      val bestPepMatches = bestPepMatchesByProtSetId(proteinSetId)
      
      val protSetScore = MascotValidationHelper.sumPeptideMatchesScoreOffsets(bestPepMatches,scoreThresholdOffset)
      proteinSet.score = protSetScore
      proteinSet.scoreType = ProtSetScoring.MASCOT_PROTEIN_SET_SCORE.toString
    }
    
    ()
  }
  
}