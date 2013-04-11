package fr.proline.core.algo.msi.scoring

import fr.proline.core.om.model.msi.ResultSummary


class MascotStandardScoreUpdater extends IProtSetAndPepSetScoreUpdater {

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
      proteinSet.peptideSet.score = proteinSet.score
    }
    
    for(orphanPepSet <- rsm.peptideSets.filterNot(_.getProteinSetId>0)){
       orphanPepSet.score = 0
       val pepMatchesByPepInsID = orphanPepSet.getPeptideInstances.map( pi => pi.id -> pi.peptideMatches) 
       pepMatchesByPepInsID.foreach( entry => {
	   val pepMatches = entry._2.sortWith((a,b) => a.score > b.score)
           orphanPepSet.score += pepMatches(0).score
       })
    }
    
    ()
  }

}