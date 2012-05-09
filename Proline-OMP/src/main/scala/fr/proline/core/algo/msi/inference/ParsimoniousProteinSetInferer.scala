package fr.proline.core.algo.msi.inference

import fr.proline.core.om.model.msi._
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.collection.mutable.HashSet

class ParsimoniousProteinSetInferer extends IProteinSetInferer {

  def computeResultSummary( resultSet: ResultSet ): ResultSummary = {
    
    // Retrieve some vars
    val proteinMatches = resultSet.proteinMatches
    val peptideMatches = resultSet.peptideMatches
    
    // Group peptide matches into peptide instances and map instance by peptide match id
    val peptideMatchesByPepId = peptideMatches.groupBy( _.peptide.id )
    
    // Define some vars
    val resultSummaryId = ResultSummary.generateNewId()
    val peptideInstances = new ArrayBuffer[PeptideInstance](peptideMatchesByPepId.size)
    val peptideInstanceById = new HashMap[Int,PeptideInstance]()
    val pepInstanceByPepId = new HashMap[Int,PeptideInstance]()
    val nrPepKeyByPepInstanceId = new HashMap[Int,String]()
    val pepInstanceIdsByNrPepKey = new HashMap[String,ArrayBuffer[Int]]()
    
    // Build peptide instances and map them
    for( (peptideId, pepMatchGroup) <- (peptideMatchesByPepId) ) {
      
      val pepMatchIds = pepMatchGroup.map( _.id )
      val peptideMatchPropertiesById = pepMatchGroup.filter { _.properties != None }
                                                    .map { pepMatch => pepMatch.id -> pepMatch.properties.get } toMap
      
      // Build peptide instance
      var bestPepMatch: PeptideMatch = null
      if( pepMatchGroup.length == 1 ) { bestPepMatch = pepMatchGroup(0) }
      else { bestPepMatch = pepMatchGroup.toList.reduce { (a,b) => if( a.score > b.score ) a else b }  }
      
      val pepInstProps = new HashMap[String,Any]
      pepInstProps += ( "best_peptide_match_id" -> bestPepMatch.id )
      
      val peptideInstance = new PeptideInstance(
                                  id = PeptideInstance.generateNewId(),
                                  peptide = bestPepMatch.peptide,
                                  peptideMatchIds = pepMatchIds,
                                  properties = pepInstProps,
                                  peptideMatchPropertiesById = peptideMatchPropertiesById,
                                  resultSummaryId = resultSummaryId
                                )
      
      peptideInstanceById += ( peptideInstance.id -> peptideInstance )
      pepInstanceByPepId += ( peptideInstance.peptide.id -> peptideInstance )
      peptideInstances += peptideInstance
      
      val nrPepKey = "q"+ bestPepMatch.msQuery.id +"_r"+ bestPepMatch.rank
      nrPepKeyByPepInstanceId += ( peptideInstance.id -> nrPepKey )
      
      pepInstanceIdsByNrPepKey.getOrElseUpdate( nrPepKey, new ArrayBuffer[Int](1) ) += peptideInstance.id
      
    }
    
    // Map peptide instance ids by protein match id
    val nrPepKeysByProtMatchIdBuilder = collection.immutable.Map.newBuilder[Int,Set[String]]
    val proteinCountByPepId = new HashMap[Int,Int]()
    
    for( protMatch <- proteinMatches ) {
      val seqMatches = protMatch.sequenceMatches
      
      // Retrieve only validated peptide matches (i.e. present in peptide matches)
      val pepInstanceIdSet = new HashSet[Int]()
      val nrPepKeySet = new HashSet[String]()
      val nrPepIdSet = new HashSet[Int]()
      for( seqMatch <- seqMatches ) {
        
        val pepId = seqMatch.getPeptideId
        nrPepIdSet += pepId
        
        val peptideInstance = pepInstanceByPepId.getOrElse(pepId,null)
        
        if( peptideInstance != null ) {
          pepInstanceIdSet += peptideInstance.id
          nrPepKeySet += nrPepKeyByPepInstanceId(peptideInstance.id)
        }
      }
      
      // Increment protein match count for each matching peptide instance
      nrPepIdSet.foreach( key => {        
        // Initialize counter
        proteinCountByPepId.getOrElseUpdate(key, 0)
        // Update counter
        proteinCountByPepId( key ) += 1
      } )
      
      if( pepInstanceIdSet.size > 0 ) {

        // Map provided peptide instance ids by protein match id
        if( protMatch.id == 0 ) { throw new Exception( "protein match id must be defined" ); }
        nrPepKeysByProtMatchIdBuilder += ( protMatch.id -> nrPepKeySet.toSet )
      }
      
    }
    
    // Clusterize peptides
    val nrPepKeysByProtMatchId = nrPepKeysByProtMatchIdBuilder.result()
    val clusters = SetClusterer.clusterizeMappedSets[Int,String]( nrPepKeysByProtMatchId )
    
    // Define some vars
    val proteinSets = new ArrayBuffer[ProteinSet]
    val peptideSets = new ArrayBuffer[PeptideSet]
    val proteinSetCountByPepInstanceId = new HashMap[Int,Int]
    val peptideSetIdByClusterId = new HashMap[Int,Int]
      
    // Iterate over protein match clusters to build peptide sets
    for( cluster <- clusters ) {
      val clusterId = cluster.id
      val isSubset = cluster.isSubset
      
      // Retrieve the protein match ids matching the same set of peptides
      val clusterProtMatchIds = cluster.samesetsKeys
      // Retrieve the corresponding set of peptides
      val clusterNrPepKeys = nrPepKeysByProtMatchId( clusterProtMatchIds(0) )
      
      // Retrieve peptide instances corresponding to this set
      val samesetPeptideInstanceIds = new ArrayBuffer[Int]
      for( nrPepKey <- clusterNrPepKeys ) {
        val tmpPepInstanceIds = pepInstanceIdsByNrPepKey(nrPepKey)
        samesetPeptideInstanceIds ++= tmpPepInstanceIds
      }
      val samesetPeptideInstances = samesetPeptideInstanceIds.map { peptideInstanceById(_) } 
      
      // Generate new peptide set id
      val peptideSetId = PeptideSet.generateNewId()
      peptideSetIdByClusterId += ( clusterId -> peptideSetId )
                                
      // Build peptide set items
      var peptideMatchesCount = 0
      val pepSetItems = new ArrayBuffer[PeptideSetItem]
      for( tmpPepInstance <- samesetPeptideInstances ) {
        
        // Increment peptide matches count
        peptideMatchesCount += tmpPepInstance.getPeptideMatchIds.length
        
        // Increment protein set count for each matching peptide instance
        if( ! isSubset )  {
          val id = tmpPepInstance.id
          val count = proteinSetCountByPepInstanceId.getOrElseUpdate(id, 0)
          proteinSetCountByPepInstanceId(id) += 1
        }
        
        val pepSetItem = new PeptideSetItem(
                                id = PeptideSetItem.generateNewId,
                                peptideInstance = tmpPepInstance,
                                peptideSetId = peptideSetId,
                                selectionLevel = 2,
                                resultSummaryId = resultSummaryId
                              )
        pepSetItems += pepSetItem
        
      }
      
      // Build peptide set
      val buildPeptideSet = new Function[Int,PeptideSet] {
        def apply( proteinSetId: Int ): PeptideSet = {
          
          var strictSubsetIds: Array[Int] = null
          var subsumableSubsetIds: Array[Int] = null
          if( cluster.strictSubsetsIds != None ) {
            strictSubsetIds = cluster.strictSubsetsIds.get.toArray
          }
          if( cluster.subsumableSubsetsIds != None ) {
            subsumableSubsetIds = cluster.subsumableSubsetsIds.get.toArray
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
      if( ! isSubset ) {
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
      
    }
        
    // Create result summary
    val resultSummary = new ResultSummary(
                                id = resultSummaryId,
                                peptideInstances = peptideInstances.toArray,
                                peptideSets = peptideSets.toArray,
                                proteinSets = proteinSets.toArray,
                                resultSet = Some(resultSet),
                                isDecoy = resultSet.isDecoy,
                                isNative = resultSet.isNative
                              )
    
    return resultSummary
    
    null

  }
}