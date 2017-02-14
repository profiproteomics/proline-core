package fr.proline.core.algo.msi.scoring

import fr.proline.core.om.model.msi.ResultSummary

/**
 * An implementation of IPeptideSetScoreUpdater which computes a Mascot Standard Score for each peptide set.
 * 
 * Mascot documentation for Standard Scoring:
 * This is is simply calculated by adding the ions scores for each match.
 * For duplicate peptides, just the highest score is taken.
 * The protein score can be lower than the sum of the ions scores, particularly when the protein is large.
 * This is because a correction is applied to compensate for the accumulation of random ions scores from random matches.
 * The difference is more substantial when doing a no-enzyme search, because there are orders of magnitude more random matches.
 * See the function ms_mascotresults::getIonsScoreCorrected() for details of how the correction is calculated.
 * If the correction causes the ions score to become negative, then this ions score is ignored when calculating the protein score.
 * 
 * ms_mascotresults::getIonsScoreCorrected documentation:
 * 
 * This function apply the following correction
 * corr_score = ions_score - (-10 * log(multiplicity) * sqrt(ITOL * ITOL + 0.0625) / log(10))
 * 
 * The multiplicity value is the number of times that the precursor mass for the specified peptide got a match in this protein.
 * With a tight tolerance and no variable modifications, this will normally be a small number.
 * For a large protein, with no enzyme specificity and a large number of modifications (or an error tolerant search),
 * this can be a large number.
 *
 * FIXME: implement the score correction ???
 */
class MascotStandardScoreUpdater extends IPeptideSetScoreUpdater { 

  def updateScoreOfPeptideSets(rsm: ResultSummary, params: Any*) {
    
    // For duplicate peptides, just the highest score is taken => retrieve only the best peptide matches
    val bestPepMatchesByPepSetId = rsm.getBestValidatedPepMatchesByPepSetId()
    
    for (pepSet <- rsm.peptideSets) {
      pepSet.score = bestPepMatchesByPepSetId(pepSet.id).foldLeft(0f)( (sum,prot) => sum + prot.score )      
      pepSet.scoreType = PepSetScoring.MASCOT_STANDARD_SCORE.toString
    }

    ()
  }

}