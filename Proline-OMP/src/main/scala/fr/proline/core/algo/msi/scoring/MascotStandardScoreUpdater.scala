package fr.proline.core.algo.msi.scoring

import fr.proline.core.om.model.msi.ResultSummary


class MascotStandardScoreUpdater extends IProtSetAndPepSetScoreUpdater {

  def updateScoreOfProteinSets( rsm: ResultSummary, params: Any* ) {
    
    for(pepSet <- rsm.peptideSets){
       pepSet.score = 0
       val pepMatchesByPepInsID = pepSet.getPeptideInstances.map( pi => pi.id -> pi.peptideMatches) 
       pepMatchesByPepInsID.foreach( entry => {
	   val pepMatches = entry._2.sortWith((a,b) => a.score > b.score)
           pepSet.score += pepMatches(0).score
       })
       if(pepSet.proteinSet!=null && pepSet.proteinSet.isDefined ){
         pepSet.proteinSet.get.score=pepSet.score
         pepSet.proteinSet.get.scoreType = ProtSetScoring.MASCOT_STANDARD_SCORE.toString
       }
    }
    
    ()
  }

}