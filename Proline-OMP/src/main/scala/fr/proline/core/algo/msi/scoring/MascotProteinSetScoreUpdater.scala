package fr.proline.core.algo.msi.scoring

import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.algo.msi.validation.MascotValidationHelper
import fr.proline.util.primitives._
import fr.proline.core.om.model.msi.PeptideMatch


class MascotProteinSetScoreUpdater() extends IProtSetAndPepSetScoreUpdater {

  def updateScoreOfProteinSets( rsm: ResultSummary, params: Any* ) {
    
    val bestPepMatchesByProtSetId = rsm.getBestPepMatchesByProtSetId
    
    val scoreThresholdOffset = if(params != null && params.length > 0) toFloat(params(0)) else 0f
    
    for( proteinSet <- rsm.proteinSets ) {
      
      val proteinSetId = proteinSet.id
      val bestPepMatches = bestPepMatchesByProtSetId(proteinSetId)
      
      val protSetScore = MascotValidationHelper.sumPeptideMatchesScoreOffsets(bestPepMatches,scoreThresholdOffset)
      proteinSet.score = protSetScore
      proteinSet.scoreType = ProtSetScoring.MASCOT_PROTEIN_SET_SCORE.toString
      proteinSet.peptideSet.score = proteinSet.score
    }
    
    for(orphanPepSet <- rsm.peptideSets.filterNot(_.getProteinSetId!=0)){
       orphanPepSet.score = 0
       var bestPepMatches = Seq.newBuilder[PeptideMatch]
       val pepMatchesByPepInsID = orphanPepSet.getPeptideInstances.map( pi => pi.id -> pi.peptideMatches)        
       pepMatchesByPepInsID.foreach( entry => {
          val pepMatches = entry._2.sortWith((a,b) => a.score > b.score)
	  bestPepMatches += pepMatches(0)
       })
       
       val pepSetScore = MascotValidationHelper.sumPeptideMatchesScoreOffsets(bestPepMatches.result,scoreThresholdOffset)
       orphanPepSet.score = pepSetScore
    }
    
    ()
  }
  
}