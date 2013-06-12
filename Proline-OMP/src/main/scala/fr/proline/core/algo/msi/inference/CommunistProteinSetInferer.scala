package fr.proline.core.algo.msi.inference

import fr.proline.core.om.model.msi._
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.collection.mutable.HashSet
import com.weiglewilczek.slf4s.Logging
import fr.proline.context.IExecutionContext
import fr.proline.util.primitives._

class CommunistProteinSetInferer extends IProteinSetInferer with Logging {

  def computeResultSummary( resultSet: ResultSet ): ResultSummary = {
    

    // Retrieve some vars
    val proteinMatches = resultSet.proteinMatches
    val peptideMatches = resultSet.peptideMatches.filter { _.isValidated }
    
    // Group peptide matches into peptide instances and map instance by peptide match id
    val peptideMatchesByPepId = peptideMatches.groupBy( _.peptide.id )
    
    // Define some vars
    val resultSummaryId = ResultSummary.generateNewId()
    val peptideInstances = new ArrayBuffer[PeptideInstance](peptideMatchesByPepId.size)
    val peptideInstanceById = new HashMap[Long,PeptideInstance]()
    val pepInstanceByPepId = new HashMap[Long,PeptideInstance]()
    
    // Build peptide instances and map them
    for( (peptideId, pepMatchGroup) <- (peptideMatchesByPepId) ) {
      
      val pepMatchIds = pepMatchGroup.map( _.id )
      /*val peptideMatchPropertiesById = pepMatchGroup.filter { _.properties != None }
                                                    .map { pepMatch => pepMatch.id -> pepMatch.properties.get } toMap*/
      
      // Build peptide instance
      var bestPepMatch: PeptideMatch = null
      if( pepMatchGroup.length == 1 ) { bestPepMatch = pepMatchGroup(0) }
      else { bestPepMatch = pepMatchGroup.toList.reduce { (a,b) => if( a.score > b.score ) a else b }  }
      
      //val pepInstProps = new HashMap[String,Any]
      //pepInstProps += ( "best_peptide_match_id" -> bestPepMatch.id )
      //val pepInstProps = new PeptideInstanceProperties()
      //pepInstProps.setBestPeptideMatchId( Some(bestPepMatch.id) )


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
    val proteinSets = new ArrayBuffer[ProteinSet]
    val peptideSets = new ArrayBuffer[PeptideSet]
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
      val isSubset = cluster.isSubset
      
      // Retrieve the protein match ids matching the same set of peptides
      val clusterProtMatchIds = cluster.samesetsKeys
      // Retrieve the corresponding set of peptides
      val clusterPepIds = peptideIdByProtMatchId( clusterProtMatchIds(0) )
      
      // Retrieve peptide instances corresponding to this set
      val samesetPeptideInstanceIds = new ArrayBuffer[Long]
      for( nrPepKey <- clusterPepIds ) {
        val tmpPepInstanceIds = pepInstanceByPepId(nrPepKey).id
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
        if( ! isSubset || ! cluster.oversetId.isDefined)  {
          val id = tmpPepInstance.id
          val count = proteinSetCountByPepInstanceId.getOrElseUpdate(id, 0)
          proteinSetCountByPepInstanceId(id) += 1
        }
        
        val pepSetItem = new PeptideSetItem(                                
                                peptideInstance = tmpPepInstance,
                                peptideSetId = peptideSetId,
                                selectionLevel = 2,
                                resultSummaryId = resultSummaryId
                              )
        pepSetItems += pepSetItem
        
      }
      
      // Build peptide set
      val buildPeptideSet = new Function[Long,PeptideSet] {
        def apply( proteinSetId: Long ): PeptideSet = {
          
          var strictSubsetIds: Array[Long] = null
          var subsumableSubsetIds: Array[Long] = null
          if( cluster.strictSubsetsIds != None ) {
            strictSubsetIds = cluster.strictSubsetsIds.get.map { peptideSetIdByClusterId(_) } toArray
          }
          if( cluster.subsumableSubsetsIds != None ) {
            subsumableSubsetIds = cluster.subsumableSubsetsIds.get.map { peptideSetIdByClusterId(_) } toArray
          }
          
          val peptideSet = new PeptideSet(
                                id = peptideSetId,
                                items = pepSetItems.toArray,
                                isSubset = isSubset,
                                peptideMatchesCount = peptideMatchesCount,
                                proteinMatchIds = clusterProtMatchIds.toArray,
                                strictSubsetIds = strictSubsetIds,
                                subsumableSubsetIds = subsumableSubsetIds,
                                proteinSetId = proteinSetId,
                                resultSummaryId = resultSummaryId
                              )
          peptideSet
          
        }
      }
      
      var peptideSet: PeptideSet = null
      
      // Create protein set if peptide set isn't a subset
      if( ! isSubset || ! cluster.oversetId.isDefined) {
        val proteinSetId = ProteinSet.generateNewId()
        peptideSet = buildPeptideSet( proteinSetId )
        
        val proteinSet = new ProteinSet(
                                  id = proteinSetId,
                                  peptideSet = peptideSet,
                                  hasPeptideSubset = peptideSet.hasSubset,
                                  proteinMatchIds = peptideSet.proteinMatchIds,                                  
                                  resultSummaryId = resultSummaryId
                                )
        
        // Add protein set to the list
        proteinSets += proteinSet
        
      } else { peptideSet = buildPeptideSet( 0 ) }
      
      // Add peptide set to the list
      peptideSets += peptideSet
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
    val resultSummary = new ResultSummary(
                                id = resultSummaryId,
                                peptideInstances = peptideInstances.toArray,
                                peptideSets = peptideSets.toArray,
                                proteinSets = proteinSets.toArray,
                                resultSet = Some(resultSet)
                                //isDecoy = resultSet.isDecoy,
                                //isNative = resultSet.isNative
                              )
    
    return resultSummary
    
    null

  }
}