package fr.proline.core.algo.msq.summarizing

import collection.mutable.ArrayBuffer
import scala.collection.mutable.LongMap
import scala.collection.mutable.HashMap
import collection.JavaConversions.iterableAsScalaIterable
import com.typesafe.scalalogging.LazyLogging
import fr.proline.core.om.model.msi._
import fr.proline.core.om.model.msq._

//VDS : not used..
object Ms2CountEntitiesSummarizer extends IMqPepAndProtEntitiesSummarizer with LazyLogging {

  def computeMasterQuantPeptides(
    masterQuantChannel: MasterQuantChannel,
    quantMergedRSM: ResultSummary,
    resultSummaries: Seq[ResultSummary]
  ): Array[MasterQuantPeptide] = {
    
    // Map some values
    val rsIdByRsmId = new LongMap[Long]
    val rsmById = new LongMap[ResultSummary]
    val pepInstIdByRsIdAndPepId = new HashMap[(Long,Long),Long]
    
    for( rsm <- resultSummaries ) {
      val rsId = rsm.getResultSetId
      rsIdByRsmId(rsm.id) = rsId
      rsmById(rsm.id) = rsm
      
      for( pepInst <- rsm.peptideInstances ) {
        pepInstIdByRsIdAndPepId( (rsId, pepInst.peptide.id) ) = pepInst.id
      }
    }
    
    // Map quant channel id by result set id
    val qcIdByRsId = masterQuantChannel.quantChannels.map {
      qc => rsIdByRsmId(qc.identResultSummaryId) -> qc.id
    }.toMap
    
    // Retrieve all peptide matches
    val peptideMatchById = new HashMap[Long,PeptideMatch]()
    for( resultSummary <- resultSummaries ) {
      
      val resultSetOpt = resultSummary.resultSet
      
      // Map peptide matches by their id
      if(resultSetOpt.isDefined){
        for( p <- resultSetOpt.get.peptideMatches ) peptideMatchById(p.id) = p
      }
    }
    
    // Merge result summaries
    /*val resultSummaryMerger = new ResultSummaryMerger()
    this.logger.info( "merging result summaries..." )
    val mergedRSM = resultSummaryMerger.mergeResultSummaries( resultSummaries, seqLengthByProtId )*/
    
    // Retrieve peptide instances of the merged result summary
    val mergedPepInstances = quantMergedRSM.peptideInstances
    val mergedPepMatchById = if(quantMergedRSM.resultSet.isDefined) quantMergedRSM.resultSet.get.getPeptideMatchById() else Map.empty[Long, PeptideMatch] 
    
    // Iterate over merged peptide instances to create quant peptide instances
    this.logger.info( "create quant peptide instances..." )
    
    val mqPeptides = new ArrayBuffer[MasterQuantPeptide]
    for( mergedPepInst <- mergedPepInstances ) {
      
      val masterQuantPeptideId = MasterQuantPeptide.generateNewId
      val peptideId = mergedPepInst.peptide.id
      val mergedPepInstPepMatchIds = mergedPepInst.getPeptideMatchIds
      require( mergedPepInstPepMatchIds.length == 1, "peptide matches have not been correctly merged" )
      
      // Retrieve parent peptide match
      val parentPepMatch = mergedPepMatchById(mergedPepInstPepMatchIds(0))
      val childPepMatchIds = parentPepMatch.getChildrenIds
      
      // Retrieve all child peptide matches corresponding to this peptide
      val childPeptideMatches = childPepMatchIds.map { peptideMatchById(_) }
      val childPepMatchesByCharge = childPeptideMatches.groupBy { _.msQuery.charge }
      
      val mqPepIons = new ArrayBuffer[MasterQuantPeptideIon]
      val quantPepIonsByQcId = new LongMap[ArrayBuffer[QuantPeptideIon]]
      
      for( (charge,childPepMatchGroup) <- childPepMatchesByCharge ) {
        
        val childPepMatchesByRsId = childPepMatchGroup.groupBy { _.resultSetId }        
        val quantPepIonByQcId = new LongMap[QuantPeptideIon]
        var bestPepMatchScore = 0f
        var bestPepMatch: PeptideMatch = null
        var bestQCAbundance = 0.0
        var bestQCId: Long = 0L
        
        for( (rsId,rsPepMatches) <- childPepMatchesByRsId ) {
          
          var bestRsPepMatchScore = 0f
          var bestRsPepMatch: PeptideMatch = null
          val msQueryIds = new ArrayBuffer[Long]
          
          // Iterate over peptide matches to retrieve the best peptide match for this result set
          for( pepMatch <- rsPepMatches ) {
            msQueryIds += pepMatch.msQueryId
            
            if( pepMatch.score >= bestRsPepMatchScore ) {              
              bestRsPepMatchScore = pepMatch.score
              bestRsPepMatch = pepMatch
            }
          }
          
          // Check if best peptide match in the master context
          if( bestRsPepMatchScore >= bestPepMatchScore ) {
            bestPepMatch = bestRsPepMatch
          }
          
          // Define some values
          val rsId = bestRsPepMatch.resultSetId
          val qcId = qcIdByRsId( bestRsPepMatch.resultSetId )
          val pepMatchesCount = rsPepMatches.length
          
          // Create a quant peptide ion corresponding the these peptide matches
          val quantPeptideIon = new QuantPeptideIon(
            rawAbundance = pepMatchesCount,
            abundance = pepMatchesCount,
            moz = bestRsPepMatch.msQuery.moz,
            elutionTime = 0,
            duration = 0,
            correctedElutionTime = 0,
            scanNumber = 0,
            peptideMatchesCount = pepMatchesCount,
            ms2MatchingFrequency = Some(1f),
            bestPeptideMatchScore = Some(bestRsPepMatchScore),
            quantChannelId = qcId,
            peptideId = Some(bestRsPepMatch.peptideId),
            peptideInstanceId = Some(pepInstIdByRsIdAndPepId(rsId,peptideId)),
            msQueryIds = Some(msQueryIds.toArray),
            lcmsFeatureId = None
          )
          if( pepMatchesCount > bestQCAbundance ) {
            bestQCAbundance = pepMatchesCount
            bestQCId = qcId
          }
          
          quantPepIonByQcId( qcId ) = quantPeptideIon
          quantPepIonsByQcId.getOrElseUpdate( qcId, new ArrayBuffer[QuantPeptideIon] ) += quantPeptideIon
        }
        
        val mqPepIon = new MasterQuantPeptideIon(
          id = MasterQuantPeptideIon.generateNewId(),
          unlabeledMoz = bestPepMatch.msQuery.moz,
          charge = bestPepMatch.msQuery.charge,
          elutionTime = 0,
          peptideMatchesCount = childPepMatchGroup.length,
          masterQuantPeptideId = masterQuantPeptideId,
          bestPeptideMatchId = Some(bestPepMatch.id),
          resultSummaryId = quantMergedRSM.id,
          selectionLevel = 2,
          quantPeptideIonMap = quantPepIonByQcId
        )
        
        if( bestQCId != 0 ) {
          val mqPepIonProps = new MasterQuantPeptideIonProperties()
          mqPepIonProps.setBestQuantChannelId( Some(bestQCId) )
          mqPepIon.properties = Some( mqPepIonProps )
        }
        
        mqPepIons += mqPepIon
      
      }
      
      val quantPepByQcId = new LongMap[QuantPeptide]
      for( (qcId,quantPepIons) <- quantPepIonsByQcId ) {
        
        // Sum the number of MS2
        var ms2Sum = 0
        for( quantPepIon <- quantPepIons ) {
          ms2Sum += quantPepIon.peptideMatchesCount
        }
        
        // Retrieve the first quant peptide ion
        val firstQuantPepIon = quantPepIons(0)
        
        // Build the quant peptide
        val qp = new QuantPeptide(
          rawAbundance = ms2Sum,
          abundance = ms2Sum,
          elutionTime = 0,
          peptideMatchesCount = ms2Sum,
          quantChannelId = qcId,
          //peptideId = firstQuantPepIon.peptideId.get, // only for labeling ???
          //peptideInstanceId = firstQuantPepIon.peptideInstanceId.get, // only for labeling ???
          selectionLevel = 2
        )
        
        quantPepByQcId(qcId) = qp
      }
      
      mqPeptides += new MasterQuantPeptide(
        id = masterQuantPeptideId,
        peptideInstance = Some(mergedPepInst),
        quantPeptideMap = quantPepByQcId,
        // TODO: decide if attach or not
        masterQuantPeptideIons = mqPepIons.toArray,
        selectionLevel = 2,
        resultSummaryId = quantMergedRSM.id
      )
    }
    
    mqPeptides.toArray
  }
  
  override def computeMasterQuantProteinSets(
    masterQuantChannel: MasterQuantChannel,
    masterQuantPeptides: Seq[MasterQuantPeptide],
    mergedRSM: ResultSummary,
    resultSummaries: Seq[ResultSummary]
  ): Array[MasterQuantProteinSet] = {
    
    val mqPepByPepInstId = masterQuantPeptides.map { mqp => mqp.peptideInstance.get.id -> mqp }.toMap
    val mqProtSets = new ArrayBuffer[MasterQuantProteinSet]
    
    for( mergedProtSet <- mergedRSM.proteinSets ) {
      val mergedPepSet = mergedProtSet.peptideSet
      
      val selectedMQPepIds = new HashMap[Long, Int]

      val ms2CountSumByQcId = new LongMap[Int]
      for( mergedPepInst <- mergedPepSet.getPeptideInstances ) {
        val mqp = mqPepByPepInstId( mergedPepInst.id )
        selectedMQPepIds += (mqp.id -> mqp.selectionLevel)
        
        for( (qcId,quantPep) <- mqp.quantPeptideMap ) {
          ms2CountSumByQcId.getOrElseUpdate(qcId,0)
          ms2CountSumByQcId(qcId) += quantPep.peptideMatchesCount
        }
      }
      
      val quantProteinSetByQcId = new LongMap[QuantProteinSet]
      for( (qcId,ms2CountSum) <- ms2CountSumByQcId ) {
        quantProteinSetByQcId(qcId) = new QuantProteinSet(
          rawAbundance = ms2CountSum,
          abundance = ms2CountSum,
          peptideMatchesCount = ms2CountSum,
          peptidesCount = if(mergedPepSet.items != null) Some(mergedPepSet.items.length) else None,
          quantChannelId = qcId,
          selectionLevel = 2
        )
      }
      
      val mqProtSetProps = new MasterQuantProteinSetProperties()
      mqProtSetProps.setSelectionLevelByMqPeptideId(Some(selectedMQPepIds) )
      
      val mqProteinSet = new MasterQuantProteinSet(
        proteinSet = mergedProtSet,
        quantProteinSetMap = quantProteinSetByQcId,
        selectionLevel = 2,
        properties = Some(mqProtSetProps)
      )
      mqProtSets += mqProteinSet
    }
    
    mqProtSets.toArray
  }
  
}