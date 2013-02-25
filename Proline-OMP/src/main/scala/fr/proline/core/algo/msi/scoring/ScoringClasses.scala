package fr.proline.core.algo.msi.scoring

import fr.proline.core.om.model.msi.ResultSummary

object ProtSetScoring extends Enumeration {
  type Param = Value
  val MASCOT_PROTEIN_SET_SCORE = Value("mascot:protein set score")
  val MASCOT_STANDARD_SCORE = Value("mascot:standard score")
  val MASCOT_MUDPIT_SCORE = Value("mascot:mudpit score")
}

trait IProteinSetScoreUpdater {
  
  def updateScoreOfProteinSets( rsm: ResultSummary ): Unit
  
}

