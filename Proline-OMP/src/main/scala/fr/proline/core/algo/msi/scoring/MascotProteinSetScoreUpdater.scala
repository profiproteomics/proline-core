package fr.proline.core.algo.msi.scoring

import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.algo.msi.validation.MascotValidationHelper
import fr.proline.util.math.MathUtils


class MascotProteinSetScoreUpdater() extends IProteinSetScoreUpdater {

  def updateScoreOfProteinSets( rsm: ResultSummary, params: Any* ) {
    
    val bestPepMatchesByProtSetId = rsm.getBestPepMatchesByProtSetId
    val scoreThresholdOffset = MathUtils.toFloat(params(0))
    
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