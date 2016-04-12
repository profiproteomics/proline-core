package fr.proline.core.algo.msq.summarizing

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.LongMap

import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.om.model.msq._

/**
 * @author David Bouyssie
 *
 */
trait IMQPeptideIonSummarizer {

  def computeMasterQuantPeptideIons(
    masterQuantChannel: MasterQuantChannel,
    quantMergedRSM: ResultSummary,
    resultSummaries: Seq[ResultSummary]
  ): Array[MasterQuantPeptideIon]
  
}

trait IMQPeptideSummarizer {

  def computeMasterQuantPeptides(
    masterQuantChannel: MasterQuantChannel,
    quantMergedRSM: ResultSummary,
    resultSummaries: Seq[ResultSummary]
  ): Array[MasterQuantPeptide]
}

trait IMQProteinSetSummarizer {
  
  def computeMasterQuantProteinSets(
    masterQuantChannel: MasterQuantChannel,
    masterQuantPeptides: Seq[MasterQuantPeptide],
    quantMergedRSM: ResultSummary,
    resultSummaries: Seq[ResultSummary]
  ): Array[MasterQuantProteinSet] = {
    
    val qcCount = masterQuantChannel.quantChannels.length
   
    val mqPepByPepInstId = masterQuantPeptides
      .withFilter(_.peptideInstance.isDefined)
      .map { mqp => mqp.peptideInstance.get.id -> mqp } toMap
    
    val mqProtSets = new ArrayBuffer[MasterQuantProteinSet](quantMergedRSM.proteinSets.length)
    for( mergedProtSet <- quantMergedRSM.proteinSets ) {
      
      val mergedPepInsts = mergedProtSet.peptideSet.getPeptideInstances 
      
      val selectedMQPepIds = new ArrayBuffer[Long](mergedPepInsts.length)
      val mqPeps = new ArrayBuffer[MasterQuantPeptide](mergedPepInsts.length)
      val abundanceSumByQcId = new LongMap[Float](qcCount)
      val rawAbundanceSumByQcId = new LongMap[Float](qcCount)
      val pepMatchesCountByQcId = new LongMap[Int](qcCount)
      
      for( mergedPepInst <- mergedPepInsts ) {
        
        // If the peptide has been quantified
        val mqPepOpt = mqPepByPepInstId.get(mergedPepInst.id)
        if( mqPepOpt.isDefined ) {
          
          val mqPep = mqPepOpt.get
          mqPeps += mqPep
          
          if( mqPep.selectionLevel >= 2 ) selectedMQPepIds += mqPep.id
          
          for( (qcId,quantPep) <- mqPep.quantPeptideMap ) {
            
            if( quantPep.rawAbundance.isNaN == false ) {
              rawAbundanceSumByQcId.getOrElseUpdate(qcId,0)
              rawAbundanceSumByQcId(qcId) += quantPep.rawAbundance
            }
            
            if( quantPep.abundance.isNaN == false ) {
              abundanceSumByQcId.getOrElseUpdate(qcId,0)
              abundanceSumByQcId(qcId) += quantPep.abundance
            }

            pepMatchesCountByQcId.getOrElseUpdate(qcId,0)
            pepMatchesCountByQcId(qcId) += quantPep.peptideMatchesCount
          }
        }
      }
      
      val quantProteinSetByQcId = new LongMap[QuantProteinSet](qcCount)
      for( (qcId,abundanceSum) <- abundanceSumByQcId ) {
        quantProteinSetByQcId(qcId) = new QuantProteinSet(
          rawAbundance = rawAbundanceSumByQcId(qcId),
          abundance = abundanceSum,
          peptideMatchesCount = pepMatchesCountByQcId(qcId),
          quantChannelId = qcId,
          selectionLevel = 2
        )
      }
      
      val mqProtSetProps = new MasterQuantProteinSetProperties()
      mqProtSetProps.setSelectedMasterQuantPeptideIds( Some(selectedMQPepIds.toArray) )
      
      val mqProteinSet = new MasterQuantProteinSet(
        proteinSet = mergedProtSet,
        quantProteinSetMap = quantProteinSetByQcId.toMap,
        masterQuantPeptides = mqPeps.toArray,
        selectionLevel = 2,
        properties = Some(mqProtSetProps)
      )
      
      mqProtSets += mqProteinSet
    }
    
    mqProtSets.toArray
  }
  
}

trait IMqPepAndProtEntitiesSummarizer extends IMQPeptideSummarizer with IMQProteinSetSummarizer {
  
  def computeMasterQuantPeptidesAndProteinSets(
    masterQuantChannel: MasterQuantChannel,
    quantMergedRSM: ResultSummary,
    resultSummaries: Seq[ResultSummary]
  ): (Array[MasterQuantPeptide], Array[MasterQuantProteinSet]) = {
        
    // Compute master quant peptides
    val mqPeptides = this.computeMasterQuantPeptides(
      masterQuantChannel,
      quantMergedRSM,
      resultSummaries
    )
    
    // Compute master quant protein sets
    val mqProtSets = this.computeMasterQuantProteinSets(
      masterQuantChannel,
      mqPeptides,
      quantMergedRSM,
      resultSummaries
    )
    
    (mqPeptides, mqProtSets)
  }
  
}