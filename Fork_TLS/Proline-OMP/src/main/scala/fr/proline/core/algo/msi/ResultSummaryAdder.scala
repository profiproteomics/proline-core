package fr.proline.core.algo.msi

import com.typesafe.scalalogging.LazyLogging

import fr.proline.core.algo.msi.scoring.IPeptideSetScoreUpdater
import fr.proline.core.om.model.msi._

class ResultSummaryAdder(
  val resultSetId: Long,
  val isDecoy: Boolean = false,
  pepSetScoreUpdater: IPeptideSetScoreUpdater,
  val additionMode: AdditionMode.Value = AdditionMode.AGGREGATION
) extends LazyLogging {

  val rsAdder = new ResultSetAdder(resultSetId, true, isDecoy, additionMode)
  
  def addResultSummaries(resultSummaries: Iterable[ResultSummary]): ResultSummaryAdder = {
    for( rsm <- resultSummaries ) this.addResultSummary(rsm)
    this
  }

  def addResultSummary(rsm: ResultSummary): ResultSummaryAdder = {

    logger.info("Start adding ResultSummary #" + rsm.id)
    val start = System.currentTimeMillis()
    rsAdder.addResultSet(rsm.getValidatedResultSet().get)

    logger.info("ResultSummary #" + rsm.id + " merged/added in " + (System.currentTimeMillis() - start) + " ms")
    
    this
  }

  def toResultSummary(): ResultSummary = {
    val start = System.currentTimeMillis()

    val mergedResultSet = rsAdder.toResultSet()
    
    // Instantiate a protein inference algo and build the merged result summary
    val protInferenceAlgo = ProteinSetInferer(InferenceMethod.PARSIMONIOUS)
    val mergedRsm = protInferenceAlgo.computeResultSummary(mergedResultSet, keepSubsummableSubsets = true)
    
     // Update score of peptide sets
    pepSetScoreUpdater.updateScoreOfPeptideSets(mergedRsm)

    //Update peptideInstance SC : In this case peptideInstance.totalLeaveMatchCount = bestPSM.SpectralCount
    updatePepInstanceSC(mergedRsm)
    
    this.logger.info("Result Summaries have been merged:")
    this.logger.info("- nb merged peptide instances = " + mergedRsm.peptideInstances.length)
    this.logger.info("- nb merged peptide sets = " + mergedRsm.peptideSets.length)
    this.logger.info("- nb merged protein sets = " + mergedRsm.proteinSets.length)

    logger.info("Merged ResultSummary #" + mergedRsm.id + " created in " + (System.currentTimeMillis() - start) + " ms")

    mergedRsm
  }
  
  private def updatePepInstanceSC(rsm : ResultSummary) = {
    val pepMatchesById = rsm.resultSet.get.getPeptideMatchById()
    
    rsm.peptideInstances.foreach (pepI => {
      val pepMatch =  pepMatchesById(pepI.bestPeptideMatchId)
       pepI.totalLeavesMatchCount = pepMatch.properties.get.spectralCount.get
      })    
  }

}
