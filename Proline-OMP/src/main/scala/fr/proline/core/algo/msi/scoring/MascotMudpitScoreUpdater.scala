package fr.proline.core.algo.msi.scoring

import fr.proline.core.algo.msi.validation.MascotValidationHelper
import fr.proline.core.om.model.msi.ResultSummary

/**
 * An implementation of IPeptideSetScoreUpdater which computes a Mascot MudPIT Score for each peptide set.
 * 
 * Mascot documentation for MudPIT Scoring:
 * 
 * For each peptide match {
 *   If there is a homology threshold and ions score > homology threshold {
 *     Protein score += ions score - homology threshold
 *   } else if ions score > identity threshold {
 *     Protein score += ions score - identity threshold
 *   }
 * }
 * 
 * Protein score += 1 * average of all the subtracted thresholds
 * 
 * In Version 2.2 and later, the thresholds are calculated using the minProbability value
 * passed to the ms_peptidesummary::ms_peptidesummary constructor.
 * In versions prior to 2.2, a default value of 1 in 20 was always used.
 * For MudPIT protein scoring, the standard ions score rather than the corrected ions score is used.
 */
class MascotMudpitScoreUpdater extends IPeptideSetScoreUpdater {
  
  def updateScoreOfPeptideSets( rsm: ResultSummary, params:Any*) {
    val bestPepMatchesByPepSetId = rsm.getBestValidatedPepMatchesByPepSetId()

    for (peptideSet <- rsm.peptideSets) {

      val peptideSetId = peptideSet.id
      val bestPepMatches = bestPepMatchesByPepSetId(peptideSetId)

      val pepSetScore = MascotValidationHelper.calcMascotMudpitScore(bestPepMatches)
      peptideSet.score = pepSetScore
      peptideSet.scoreType = PepSetScoring.MASCOT_MUDPIT_SCORE.toString
    }

    ()
  }
}