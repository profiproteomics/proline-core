package fr.proline.core.algo.msi.inference

import scala.collection.immutable.LongMap
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.collection.mutable.HashSet
import com.typesafe.scalalogging.LazyLogging
import fr.proline.context.IExecutionContext
import fr.proline.core.om.model.msi._
import fr.profi.util.primitives._

class ParsimoniousProteinSetInferer() extends IProteinSetInferer with LazyLogging {

  def computeResultSummary( resultSet: ResultSet, keepSubsummableSubsets: Boolean = true ): ResultSummary = {
    
    // Retrieve some vars
    val proteinMatches = resultSet.proteinMatches
    val proteinMatchById = resultSet.getProteinMatchById
    val validatedPeptideMatches = resultSet.peptideMatches.filter( _.isValidated )
    
    // Group peptide matches into peptide instances and map instance by peptide match id
    val peptideMatchesByPepId = validatedPeptideMatches.groupBy( _.peptide.id )
    
    // Define some vars
    val resultSummaryId = ResultSummary.generateNewId()
    val peptideCount = peptideMatchesByPepId.size
    val peptideInstances = new ArrayBuffer[PeptideInstance](peptideCount)
    val pepInstanceByIdPairs = new ArrayBuffer[(Long,PeptideInstance)](peptideCount)
    val pepInstanceByPepIdPairs = new ArrayBuffer[(Long,PeptideInstance)](peptideCount)
    
    // Build peptide instances and map them
    for( (peptideId, pepMatchGroup) <- (peptideMatchesByPepId) ) {
      
      val pepMatchIds = pepMatchGroup.map( _.id )
      
      // Build peptide instance
      val bestPepMatch = if( pepMatchGroup.length == 1 ) pepMatchGroup(0)
      else pepMatchGroup.maxBy(_.score)

      val peptideInstance = new PeptideInstance(
        id = PeptideInstance.generateNewId(),
        peptide = bestPepMatch.peptide,
        peptideMatchIds = pepMatchIds,
        bestPeptideMatchId = bestPepMatch.id,
        peptideMatches = pepMatchGroup,
        totalLeavesMatchCount = -1,
        //properties = Some(pepInstProps),
        //peptideMatchPropertiesById = peptideMatchPropertiesById,
        resultSummaryId = resultSummaryId
      )
      
      pepInstanceByIdPairs += ( peptideInstance.id -> peptideInstance )
      pepInstanceByPepIdPairs += ( peptideInstance.peptide.id -> peptideInstance )
      peptideInstances += peptideInstance
    }
    
    // Convert some ArrayBuffers into LongMaps
    val pepInstanceById = LongMap( pepInstanceByIdPairs: _* )
    val pepInstanceByPepId = LongMap( pepInstanceByPepIdPairs: _* )
    
    // Map peptide instance ids by protein match id
    val peptideIdSetByProtMatchIdPairs = new ArrayBuffer[(Long,Set[Long])](proteinMatches.length)//collection.immutable.Map.newBuilder[Long,Set[Long]]
    val proteinCountByPepId = new HashMap[Long,Int]()
    peptideInstances.foreach( pepInst => proteinCountByPepId += pepInst.peptideId -> 0 )
    
    for( protMatch <- proteinMatches ) {
      val seqMatches = protMatch.sequenceMatches
      
      // Retrieve only validated peptide matches (i.e. present in peptide matches)
      val protMatchPepIds = new ArrayBuffer[Long](seqMatches.length)
      for( seqMatch <- seqMatches ) {
        val pepId = seqMatch.getPeptideId
        if (pepInstanceByPepId.contains(pepId)) {
          protMatchPepIds += pepId
        }
      }
      
      if (protMatchPepIds.length > 0) {
        
        // Remove duplicated peptide ids (one peptide can be located at different positions of the protein sequence
        val protMatchPepIdSet = protMatchPepIds.toSet
        
        // Increment protein match count for each matching peptide instance
        protMatchPepIdSet.foreach( pepId => {
          // Update counter
          proteinCountByPepId( pepId ) += 1
        } )
      
        peptideIdSetByProtMatchIdPairs += (protMatch.id -> protMatchPepIdSet)
      }
    }
    
    // Clusterize peptides
    val peptideIdSetByProtMatchId = LongMap( peptideIdSetByProtMatchIdPairs: _* )
    val distinctPepIdsByProtMatchId = for( (a,b) <- peptideIdSetByProtMatchId ) yield a -> b.toArray
    val clusters = SetClusterer.clusterizeMappedSets[Long,Long]( peptideIdSetByProtMatchId )
    
    // Define some vars
    val clusterCount = clusters.length
    val proteinSets = new ArrayBuffer[ProteinSet](clusterCount)
    val peptideSets = new ArrayBuffer[PeptideSet](clusterCount)
    val peptideSetIdByClusterIdPairs = new ArrayBuffer[(Long,Long)](clusterCount)
    
    // Generate id for each cluster and map it by the provided cluster id
    for( cluster <- clusters ) {
      // Generate new peptide set id
      val peptideSetId = PeptideSet.generateNewId()
      peptideSetIdByClusterIdPairs += ( cluster.id -> peptideSetId )
    }
    
    // Convert ArrayBuffer into LongMap
    val peptideSetIdByClusterId = LongMap( peptideSetIdByClusterIdPairs: _* )
    
    // Create a protein set count for each peptide instance
    val proteinSetCountByPepInstanceId = new HashMap[Long,Int]()
    peptideInstances.foreach( pepInst => proteinSetCountByPepInstanceId += (pepInst.id -> 0) )
    
    // Iterate over protein match clusters to build peptide sets
    for( cluster <- clusters ) {
      
      val peptideSetId = peptideSetIdByClusterId(cluster.id)
      val isSubset = if( keepSubsummableSubsets == false ) cluster.isSubset
      // Else check it is as strict subset to keep subsummable subsets
      else cluster.isSubset && cluster.oversetId.isDefined
      
      // Retrieve the protein match ids matching the same set of peptides
      val clusterProtMatchIds = cluster.samesetsKeys
      // Retrieve the corresponding set of peptides
      val clusterPepIds = distinctPepIdsByProtMatchId( clusterProtMatchIds(0) )
      
      // Retrieve peptide instances corresponding to this set
      val samesetPeptideInstances = clusterPepIds.map( pepInstanceByPepId(_) )
      
      // Define some vars
      var peptideMatchesCount = 0
      val sequences = new ArrayBuffer[String](samesetPeptideInstances.length)
      
      // Build peptide set items
      val pepSetItems = for( tmpPepInstance <- samesetPeptideInstances ) yield {
        
        sequences += tmpPepInstance.peptide.sequence
        
        // Increment peptide matches count
        peptideMatchesCount += tmpPepInstance.getPeptideMatchIds.length
        
        // Increment protein set count for each matching peptide instance
        if( !isSubset ) {
          val id = tmpPepInstance.id
          proteinSetCountByPepInstanceId(tmpPepInstance.id) = proteinSetCountByPepInstanceId(id) + 1
        }
        
        new PeptideSetItem(
          peptideInstance = tmpPepInstance,
          peptideSetId = peptideSetId,
          selectionLevel = 2,
          resultSummaryId = resultSummaryId
        )
        
      }
      
      // Determine strict and subsumable subsets
      val strictSubsetIdsOpt = cluster.strictSubsetsIds.map { strictSubsetsIds =>
        strictSubsetsIds.map(peptideSetIdByClusterId(_)).toArray
      }
      
      val subsumableSubsetIdsOpt = cluster.subsumableSubsetsIds.map { subsumableSubsetsIds =>
        subsumableSubsetsIds.map(peptideSetIdByClusterId(_)).toArray
      }
      
      // Compute the proteinSetId
      val proteinSetId  = if( isSubset ) 0L
      else ProteinSet.generateNewId()
      
      val peptideSet = new PeptideSet(
        id = peptideSetId,
        items = pepSetItems.toArray,
        isSubset = isSubset,
        sequencesCount = sequences.distinct.length,
        peptideMatchesCount = peptideMatchesCount,
        proteinMatchIds = clusterProtMatchIds.toArray,
        strictSubsetIds = strictSubsetIdsOpt.orNull,
        subsumableSubsetIds = subsumableSubsetIdsOpt.orNull,
        proteinSetId = proteinSetId,
        resultSummaryId = resultSummaryId
      )
      
      // Add peptide set to the list
      peptideSets += peptideSet
      
      // If peptide set is not a subset we create a new protein set
      if( isSubset == false ) {
        
        val sameSetProtMatchIds = peptideSet.proteinMatchIds
        
        // Choose Typical using arbitrary alphabetical order
        val reprProtMatch = sameSetProtMatchIds.map( proteinMatchById(_) ).minBy( _.accession )

        // Add protein set to the list
        proteinSets += ProteinSet(
          id = proteinSetId,
          isDecoy = resultSet.isDecoy,
          peptideSet = peptideSet,
          hasPeptideSubset = peptideSet.hasSubset,
          representativeProteinMatchId = reprProtMatch.id,
          representativeProteinMatch = Some(reprProtMatch),
          samesetProteinMatchIds = sameSetProtMatchIds,
          subsetProteinMatchIds = cluster.subsetsKeys.toArray,
          resultSummaryId = resultSummaryId
        )
      }
    }
    
    // Populate strictSubsets and subsumableSubsets
    val peptideSetById = Map() ++ peptideSets.map( ps => ps.id -> ps )
    for( peptideSet <- peptideSets ) {
      if( peptideSet.hasStrictSubset ) {
        peptideSet.strictSubsets = Some( peptideSet.strictSubsetIds.map( peptideSetById(_) ) )
      }
      if( peptideSet.hasSubsumableSubset ) {
        peptideSet.subsumableSubsets = Some( peptideSet.subsumableSubsetIds.map( peptideSetById(_) ) )
      }
    }
    
    // Update peptide instance counts
    for( pepInstance <- pepInstanceById.values ) {
      
      // Retrieve some vars
      val proteinMatchCount = proteinCountByPepId( pepInstance.peptide.id )
      val proteinSetCount = proteinSetCountByPepInstanceId.getOrElse( pepInstance.id, 0 )
      
      // Update peptide instance
      pepInstance.proteinMatchesCount = proteinMatchCount
      pepInstance.proteinSetsCount = proteinSetCount
      pepInstance.validatedProteinSetsCount = proteinSetCount
    }
    
    // Create result summary
    new ResultSummary(
      id = resultSummaryId,
      peptideInstances = peptideInstances.toArray,
      peptideSets = peptideSets.toArray,
      proteinSets = proteinSets.toArray,
      resultSet = Some(resultSet)
    )
    
  }
  
}