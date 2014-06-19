package fr.proline.core.algo.msi.scoring

import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.algo.msi.validation.MascotValidationHelper
import fr.profi.util.primitives._
import fr.proline.core.om.model.msi.PeptideMatch

class MascotModifiedMudpitScoreUpdater extends IPeptideSetScoreUpdater {

  def updateScoreOfPeptideSets(rsm: ResultSummary, params: Any*) {

    val bestPepMatchesByPepSetId = rsm.getBestValidatedPepMatchesByPepSetId()

    val scoreThresholdOffset = if (params != null && params.length > 0) toFloat(params(0)) else 0f

    for (peptideSet <- rsm.peptideSets) {

      val peptideSetId = peptideSet.id
      val bestPepMatches = bestPepMatchesByPepSetId(peptideSetId)

      val pepSetScore = MascotValidationHelper.sumPeptideMatchesScoreOffsets(bestPepMatches, scoreThresholdOffset)
      peptideSet.score = pepSetScore
      peptideSet.scoreType = PepSetScoring.MASCOT_MODIFIED_MUDPIT_SCORE.toString
    }
    
    ()
  }

}