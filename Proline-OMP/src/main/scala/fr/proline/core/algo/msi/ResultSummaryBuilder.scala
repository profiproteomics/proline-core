package fr.proline.core.algo.msi

import scala.collection.mutable.{ArrayBuffer,HashMap,HashSet}
import fr.proline.core.om.model.msi._
import com.typesafe.scalalogging.slf4j.Logging
import fr.proline.core.algo.msi.validation.TargetDecoyModes
import fr.proline.util.StringUtils.isEmpty
import fr.proline.core.algo.msi.scoring.IPeptideSetScoreUpdater

  

class ResultSummaryBuilder(val resultSetId: Long, val isDecoy:Boolean, pepSetScoreUpdater: IPeptideSetScoreUpdater, seqLengthByProtId: Option[Map[Long,Int]] = None) extends Logging {
  
  val rsBuilder = new ResultSetBuilder(resultSetId, isDecoy, seqLengthByProtId)
  
  def addResultSummary(rsm:ResultSummary) {
    
    logger.info("Start adding ResultSummary #"+rsm.id)
    val start = System.currentTimeMillis()
    val selector = new ResultSummarySelector(rsm)
    rsBuilder.addResultSet(rsm.resultSet.get, selector)
    
    logger.info("ResultSummary #"+rsm.id+" merged/added in "+(System.currentTimeMillis()-start)+" ms")
  }
  
   
  def toResultSummary() : ResultSummary = {
	 val start = System.currentTimeMillis()
   
	 val mergedResultSet = rsBuilder.toResultSet()
	 
	 // Instantiate a protein inference algo and build the merged result summary
    val protInferenceAlgo = ProteinSetInferer( InferenceMethods.communist )
    val mergedRsm = protInferenceAlgo.computeResultSummary( mergedResultSet )
    
    //TODO FIXME VDS: Add algo to go through mergedRsm PeptideInstance and update their totalLeavesMatchCount 
    // totalLeavesMatchCount = Sum totalLeavesMatchCount of each child RSM
    
    // Update score of peptide sets
    pepSetScoreUpdater.updateScoreOfPeptideSets(mergedRsm)
    
    
    this.logger.info( "Result Summaries have been merged:")
    this.logger.info( "- nb merged peptide instances = " + mergedRsm.peptideInstances.length)
    this.logger.info( "- nb merged peptide sets = " + mergedRsm.peptideSets.length )
    this.logger.info( "- nb merged protein sets = " + mergedRsm.proteinSets.length )

    logger.info("Merged ResultSummary #"+mergedRsm.id+" created in "+(System.currentTimeMillis()-start)+" ms")

    mergedRsm
  }
  

  
}
