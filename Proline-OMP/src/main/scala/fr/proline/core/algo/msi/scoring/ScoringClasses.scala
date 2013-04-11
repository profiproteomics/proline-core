package fr.proline.core.algo.msi.scoring

import fr.proline.core.om.model.msi.ResultSummary

object ProtSetScoring extends Enumeration {
  type Updater = Value
  val MASCOT_PROTEIN_SET_SCORE = Value("mascot:protein set score")
  val MASCOT_STANDARD_SCORE = Value("mascot:standard score")
  val MASCOT_MUDPIT_SCORE = Value("mascot:mudpit score")
}

trait IProtSetAndPepSetScoreUpdater {
  
  def updateScoreOfProteinSets( rsm: ResultSummary, params:Any* ): Unit
  
}


object ProtSetAndPepSetScoreUpdater {
  
  def apply( methodName: ProtSetScoring.Updater ): IProtSetAndPepSetScoreUpdater = { methodName match {
    case ProtSetScoring.MASCOT_MUDPIT_SCORE => new MascotMudpitScoreUpdater()
    case ProtSetScoring.MASCOT_STANDARD_SCORE => new MascotStandardScoreUpdater()    
    case ProtSetScoring.MASCOT_PROTEIN_SET_SCORE => new MascotProteinSetScoreUpdater()
    }
  }

}
