package fr.proline.core.algo.msi.scoring

import fr.proline.core.om.model.msi.ResultSummary

object PepSetScoring extends Enumeration {  
  val MASCOT_STANDARD_SCORE = Value("mascot:standard score")
  val MASCOT_MUDPIT_SCORE = Value("mascot:mudpit score")
  val MASCOT_MODIFIED_MUDPIT_SCORE = Value("mascot:peptide set score")
}

trait IPeptideSetScoreUpdater {
  
  def updateScoreOfPeptideSets( rsm: ResultSummary, params:Any* ): Unit
  
}


object PeptideSetScoreUpdater {
  
  def apply( methodName: PepSetScoring.Value ): IPeptideSetScoreUpdater = {
    methodName match {
      case PepSetScoring.MASCOT_MODIFIED_MUDPIT_SCORE => new MascotModifiedMudpitScoreUpdater()
      case PepSetScoring.MASCOT_MUDPIT_SCORE => new MascotMudpitScoreUpdater()
      case PepSetScoring.MASCOT_STANDARD_SCORE => new MascotStandardScoreUpdater()          
    }
  }

}
