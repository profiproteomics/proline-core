package fr.proline.core.algo.msi

import com.typesafe.scalalogging.slf4j.Logging

import fr.proline.core.algo.msi.scoring.IPeptideSetScoreUpdater
import fr.proline.core.om.model.msi._

class ResultSummaryAdder(
  val resultSetId: Long,
  val isDecoy: Boolean = false,
  pepSetScoreUpdater: IPeptideSetScoreUpdater,
  seqLengthByProtId: Option[Map[Long, Int]] = None
) extends Logging {

  val isValidatedContent = true // the method getValidatedResultSet is called in addResultSummary
  val rsAdder = new ResultSetAdder(resultSetId, isValidatedContent, isDecoy, seqLengthByProtId)
  
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

    //TODO FIXME VDS: Add algo to go through mergedRsm PeptideInstance and update their totalLeavesMatchCount 
    // totalLeavesMatchCount = Sum totalLeavesMatchCount of each child RSM

    // Update score of peptide sets
    pepSetScoreUpdater.updateScoreOfPeptideSets(mergedRsm)

    this.logger.info("Result Summaries have been merged:")
    this.logger.info("- nb merged peptide instances = " + mergedRsm.peptideInstances.length)
    this.logger.info("- nb merged peptide sets = " + mergedRsm.peptideSets.length)
    this.logger.info("- nb merged protein sets = " + mergedRsm.proteinSets.length)

    logger.info("Merged ResultSummary #" + mergedRsm.id + " created in " + (System.currentTimeMillis() - start) + " ms")

    mergedRsm
  }

}
