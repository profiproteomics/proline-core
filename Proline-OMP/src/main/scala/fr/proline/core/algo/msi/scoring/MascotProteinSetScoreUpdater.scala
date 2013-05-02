package fr.proline.core.algo.msi.scoring

import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.algo.msi.validation.MascotValidationHelper
import fr.proline.util.primitives._
import fr.proline.core.om.model.msi.PeptideMatch

class MascotPeptideSetScoreUpdater() extends IPeptideSetScoreUpdater {

  def updateScoreOfPeptideSets(rsm: ResultSummary, params: Any*) {

    val bestPepMatchesByProtSetId = rsm.getBestValidatedPepMatchesByPepSetId()

    val scoreThresholdOffset = if (params != null && params.length > 0) toFloat(params(0)) else 0f

    for (peptideSet <- rsm.peptideSets) {

      val proteinSetId = peptideSet.id
      val bestPepMatches = bestPepMatchesByProtSetId(proteinSetId)

      val pepSetScore = MascotValidationHelper.sumPeptideMatchesScoreOffsets(bestPepMatches, scoreThresholdOffset)
      peptideSet.score = pepSetScore
      peptideSet.scoreType = PepSetScoring.MASCOT_PEPTIDE_SET_SCORE.toString
    }
    
    ()
  }

}