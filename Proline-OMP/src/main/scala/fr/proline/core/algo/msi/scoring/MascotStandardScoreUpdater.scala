package fr.proline.core.algo.msi.scoring

import fr.proline.core.om.model.msi.ResultSummary


class MascotStandardScoreUpdater extends IProteinSetScoreUpdater {

  def updateScoreOfProteinSets( rsm: ResultSummary, params: Any* ) {
    
    val bestPepMatchesByProtSetId = rsm.getBestPepMatchesByProtSetId
    
    for( proteinSet <- rsm.proteinSets ) {
      
      val proteinSetId = proteinSet.id
      val bestPepMatches = bestPepMatchesByProtSetId(proteinSetId)
      proteinSet.score = 0
      for (bestPepMatch <- bestPepMatches) {
      	proteinSet.score += bestPepMatch.score
      }
      proteinSet.scoreType = ProtSetScoring.MASCOT_STANDARD_SCORE.toString
    }
    
    ()
  }

}