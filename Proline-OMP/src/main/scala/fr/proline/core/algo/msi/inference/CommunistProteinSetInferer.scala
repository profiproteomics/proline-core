package fr.proline.core.algo.msi.inference

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.collection.mutable.HashSet
import com.typesafe.scalalogging.slf4j.Logging
import fr.proline.context.IExecutionContext
import fr.proline.core.om.model.msi._
import fr.profi.util.primitives._

class CommunistProteinSetInferer() extends IProteinSetInferer with Logging {

  def computeResultSummary( resultSet: ResultSet, keepSubsummableSubsets: Boolean = true ): ResultSummary = {
    
    // Retrieve some vars
    val proteinMatches = resultSet.proteinMatches
    val validatedPeptideMatches = resultSet.peptideMatches.filter( _.isValidated )
    
    // Group peptide matches into peptide instances and map instance by peptide match id
    val peptideMatchesByPepId = validatedPeptideMatches.groupBy( _.peptide.id )
    
    // Define some vars
    val resultSummaryId = ResultSummary.generateNewId()
    val peptideInstances = new ArrayBuffer[PeptideInstance](peptideMatchesByPepId.size)
    val peptideInstanceById = new HashMap[Long,PeptideInstance]()
    val pepInstanceByPepId = new HashMap[Long,PeptideInstance]()
    
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
      
      peptideInstanceById += ( peptideInstance.id -> peptideInstance )
      pepInstanceByPepId += ( peptideInstance.peptide.id -> peptideInstance )
      peptideInstances += peptideInstance
    }
    
    // Map peptide instance ids by protein match id
    val peptideIdByProtMatchIdBuilder = collection.immutable.Map.newBuilder[Long,Set[Long]]
    val proteinCountByPepId = new HashMap[Long,Int]()
    
    for( protMatch <- proteinMatches ) {
      val seqMatches = protMatch.sequenceMatches
      
      // Retrieve only validated peptide matches (i.e. present in peptide matches)
      val nrPepIdSet = new HashSet[Long]()
      for( seqMatch <- seqMatches ) {
        val pepId = seqMatch.getPeptideId
        if (pepInstanceByPepId.contains(pepId)) {
          nrPepIdSet += pepId
        }
      }
      
      // Increment protein match count for each matching peptide instance
      nrPepIdSet.foreach( key => {
        // Initialize counter
        proteinCountByPepId.getOrElseUpdate(key, 0)
        // Update counter
        proteinCountByPepId( key ) += 1
      } )
      
      if (nrPepIdSet.size > 0)
      	peptideIdByProtMatchIdBuilder += (protMatch.id -> nrPepIdSet.toSet)
    }
    
    // Clusterize peptides
    val peptideIdByProtMatchId = peptideIdByProtMatchIdBuilder.result()
    val clusters = SetClusterer.clusterizeMappedSets[Long,Long]( peptideIdByProtMatchId )
    
    // Define some vars
    val proteinSets = new ArrayBuffer[ProteinSet](clusters.length)
    val peptideSets = new ArrayBuffer[PeptideSet](clusters.length)
    val proteinSetCountByPepInstanceId = new HashMap[Long,Int]
    val peptideSetIdByClusterId = new HashMap[Long,Long]
    
    // Generate id for each cluster and map it by the provided cluster id
    for( cluster <- clusters ) {
      // Generate new peptide set id
      val peptideSetId = PeptideSet.generateNewId()
      peptideSetIdByClusterId += ( cluster.id -> peptideSetId )
    }
      
    // Iterate over protein match clusters to build peptide sets
    for( cluster <- clusters ) {
      
      val peptideSetId = peptideSetIdByClusterId(cluster.id)
      val isSubset = if( keepSubsummableSubsets == false ) cluster.isSubset
      // Else check it is as strict subset to keep subsummable subsets
      else cluster.isSubset && cluster.oversetId.isDefined
      
      // Retrieve the protein match ids matching the same set of peptides
      val clusterProtMatchIds = cluster.samesetsKeys
      // Retrieve the corresponding set of peptides
      val clusterPepIds = peptideIdByProtMatchId( clusterProtMatchIds(0) )
      
      // Retrieve peptide instances corresponding to this set
      val samesetPeptideInstanceIds = new ArrayBuffer[Long]
      for( clusterPepId <- clusterPepIds ) {
        val tmpPepInstanceIds = pepInstanceByPepId(clusterPepId).id
        samesetPeptideInstanceIds += tmpPepInstanceIds
      }
      val samesetPeptideInstances = samesetPeptideInstanceIds.map { peptideInstanceById(_) } 
                   
      // Build peptide set items
      var peptideMatchesCount = 0
      val pepSetItems = new ArrayBuffer[PeptideSetItem]
      for( tmpPepInstance <- samesetPeptideInstances ) {
        
        // Increment peptide matches count
        peptideMatchesCount += tmpPepInstance.getPeptideMatchIds.length
        
        // Increment protein set count for each matching peptide instance
        if( !isSubset ) {
          val id = tmpPepInstance.id
          // Initialize counter
          proteinSetCountByPepInstanceId.getOrElseUpdate(id, 0)
          // Update counter
          proteinSetCountByPepInstanceId(id) += 1
        }
        
        pepSetItems += new PeptideSetItem(
          peptideInstance = tmpPepInstance,
          peptideSetId = peptideSetId,
          selectionLevel = 2,
          resultSummaryId = resultSummaryId
        )
        
      }
      
      // Build peptide set
      def buildPeptideSet( proteinSetId: Long ): PeptideSet = {
        val strictSubsetIdsOpt = cluster.strictSubsetsIds.map { strictSubsetsIds =>
          strictSubsetsIds.map(peptideSetIdByClusterId(_)).toArray
        }
        
        val subsumableSubsetIdsOpt = cluster.subsumableSubsetsIds.map { subsumableSubsetsIds =>
          subsumableSubsetsIds.map(peptideSetIdByClusterId(_)).toArray
        }
        
        new PeptideSet(
          id = peptideSetId,
          items = pepSetItems.toArray,
          isSubset = isSubset,
          peptideMatchesCount = peptideMatchesCount,
          proteinMatchIds = clusterProtMatchIds.toArray,
          strictSubsetIds = strictSubsetIdsOpt.orNull,
          subsumableSubsetIds = subsumableSubsetIdsOpt.orNull,
          proteinSetId = proteinSetId,
          resultSummaryId = resultSummaryId
        )
      }
      
      // If peptide set is a subset
      val peptideSet = if( isSubset ) buildPeptideSet( 0 )
      // Else we create a new protein set
      else {
        val proteinSetId = ProteinSet.generateNewId()
        val newPeptideSet = buildPeptideSet( proteinSetId )
        val sameSetPmIds = newPeptideSet.proteinMatchIds

        // Choose Typical using arbitrary alphabetical order
        var typicalProtMatch: ProteinMatch = null
        for (sameSetId <- sameSetPmIds) {
          val samesetProt = resultSet.proteinMatchById(sameSetId)
          if (typicalProtMatch == null || typicalProtMatch.accession.compareTo(samesetProt.accession) > 0) {
            typicalProtMatch = samesetProt
          }
        }
        val typicalProtMatchOpt = Option(typicalProtMatch)

        val proteinSet = ProteinSet(
          id = 1L,
          isDecoy = resultSet.isDecoy,
          peptideSet = newPeptideSet,
          hasPeptideSubset = newPeptideSet.hasSubset,
          typicalProteinMatchId = typicalProtMatchOpt.map(_.id).getOrElse(0),
          typicalProteinMatch = typicalProtMatchOpt,
          samesetProteinMatchIds = sameSetPmIds,
          subsetProteinMatchIds = cluster.subsetsKeys.toArray,
          resultSummaryId = resultSummaryId
        )
        
        // Add protein set to the list
        proteinSets += proteinSet
        
        newPeptideSet
      }
      
      // Add peptide set to the list
      peptideSets += peptideSet
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
    for( pepInstance <- peptideInstanceById.values ) {
      
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
      //isDecoy = resultSet.isDecoy,
      //isNative = resultSet.isNative
    )
    
  }
  
}