package fr.proline.core.algo.msi

import scala.collection.mutable.{ArrayBuffer,HashMap,HashSet}
import com.weiglewilczek.slf4s.Logging
import fr.proline.util.StringUtils.{isEmpty => isEmptyStr}
import fr.proline.core.om.model.msi._

object ResultSetMerger {
  
  def mergePeptideMatches( peptideMatches: Seq[PeptideMatch],
                           proteinMatches: Seq[ProteinMatch] ):
                           Tuple2[Seq[PeptideMatch],Seq[ProteinMatch]] = {
    
    // TODO: check if this code can be abstracted (shared with ResultSummaryMerger)
    // Group peptide matches by peptide id
    val pepMatchByPepId = peptideMatches.groupBy { _.peptide.id }
    
    // Merge peptide matches
    val parentPepMatchIdByPepId = new HashMap[Int,Int]()
    val scoreTypeSet = new HashSet[String]()
    val mergedPeptideMatches = new ArrayBuffer[PeptideMatch]( pepMatchByPepId.size )
    
    for( (peptideId, peptideMatchGroup) <- pepMatchByPepId ) {
      
      // Map merged peptide match by the peptide id
      val newPepMatchId = PeptideMatch.generateNewId()
      parentPepMatchIdByPepId(peptideId) = newPepMatchId
      
      // Retrieve a non-redundant list of children ids
      val nrPepMatchChildIds = peptideMatchGroup.map { _.id } distinct
      
      // Retrieve the best child
      val bestChild = peptideMatchGroup.reduce { (a,b) => if( a.score > b.score ) a else b }
      
      // Update score type map
      scoreTypeSet += bestChild.scoreType
      
      // Create a peptide match which correspond to the best peptide match of this group
      val parentPepMatch = bestChild.copy( id = newPepMatchId, childrenIds= nrPepMatchChildIds.toArray)
      
      mergedPeptideMatches += parentPepMatch
     
    }
    
    // Check if all peptide matches have the same type of score
    if( scoreTypeSet.size > 1 ) {
      throw new Exception( "can't merge peptide matches from different search engines yet" )
    }
    
    val mergedProteinMatches = new ArrayBuffer[ProteinMatch](proteinMatches.length)
    // Retrieve sequence matches corresponding to best child peptide matches
    for( proteinMatch <- proteinMatches ) {
      
      // Map sequence match by its peptide id to remove potential redundancy from merged data
      val seqMatchByBestPepMatchId = proteinMatch.sequenceMatches.map { s => s.getBestPeptideMatchId -> s } toMap
      
      val mergedSeqMatches = new ArrayBuffer[SequenceMatch]()
      for( seqMatch <- seqMatchByBestPepMatchId.values ) {
        
        val pepId = seqMatch.getPeptideId
        val parentPepMatchId = parentPepMatchIdByPepId.get(pepId)
        if( parentPepMatchId != None ) {
          
          // Build new sequence match corresponding to the merged peptide match
          val parentSeqMatch = seqMatch.copy( bestPeptideMatchId = parentPepMatchId.get )
          
          mergedSeqMatches += parentSeqMatch
        }
      }
      
      mergedProteinMatches += proteinMatch.copy( sequenceMatches = mergedSeqMatches.toArray )
    }
    
    (mergedPeptideMatches,mergedProteinMatches)
  }
  
}

class ResultSetMerger extends Logging {

  def mergeResultSets( resultSets: Seq[ResultSet], seqLengthByProtId: Option[Map[Int,Int]] = None ): ResultSet = {
    
    // TODO: check that all result sets come from the same search engine
    
    // Iterate over result sets
    //val msiSearchIds = new ArrayBuffer[Int](resultSets.length)
    val allPeptideMatches = new ArrayBuffer[PeptideMatch](0)
    val proteinMatchesByKey = new HashMap[String,ArrayBuffer[ProteinMatch]]
    val peptideById = new HashMap[Int,Peptide]
    
    for( resultSet <- resultSets ) {
      
      // Retrieve some vars
      val proteinMatches = resultSet.proteinMatches
      val peptideMatches = resultSet.peptideMatches
      val peptides = resultSet.peptides
      //msiSearchIds += resultSet.getMSISearchId
      
      // Append peptide matches to the total list
      allPeptideMatches ++= peptideMatches
      
      // Iterate over protein matches to merge them by a unique key
      for( proteinMatch <- proteinMatches ) {
        //next if !defined proteinMatch.taxonId
        
        var protMatchKey = ""
        if( proteinMatch.getProteinId != 0 ) {
          // Build key using protein id and taxon id if they are defined
          protMatchKey = proteinMatch.getProteinId + "%" + proteinMatch.taxonId
        } else {
          // Else the key in the accession number
          protMatchKey = proteinMatch.accession
        }
        
        proteinMatchesByKey.getOrElseUpdate(protMatchKey, new ArrayBuffer[ProteinMatch](1) ) += proteinMatch
      }
      
      // Merge peptides by id
      for( p <- peptides ) peptideById( p.id ) = p
      
    }
    
    // Re-map the non-redundant list of peptides to peptide matches
    // Peptide matches related to the same peptide will use the same object
    val pepMatchesByPepId = new HashMap[Int,ArrayBuffer[PeptideMatch]]
    for( peptideMatch <- allPeptideMatches ) {
      val peptideId = peptideMatch.peptide.id
      //val peptideMatch.peptide = peptideById(peptideId)
      
      pepMatchesByPepId.getOrElseUpdate(peptideId, new ArrayBuffer[PeptideMatch](1) ) += peptideMatch
    }
    
    // Iterate over grouped protein matches to build a list of non-redundant protein matches
    val nrProteinMatches = new ArrayBuffer[ProteinMatch]
    
    for( protMatchGroup <- proteinMatchesByKey.values ) {
      
      var firstDescribedProtMatch: ProteinMatch = null
      for( proteinMatch <- protMatchGroup ) {
        if( firstDescribedProtMatch != null && ! isEmptyStr(proteinMatch.description) ) {
          firstDescribedProtMatch = proteinMatch
        }
      }
      if( firstDescribedProtMatch == null ) firstDescribedProtMatch = protMatchGroup(0)
      
      // Retrieve all sequence matches of this protein match group
      val seqMatchByPepId = new HashMap[Int,SequenceMatch]
      val seqDatabaseIdSet = new HashSet[Int]
      
      for( proteinMatch <- protMatchGroup ) {
        for( seqMatch <- proteinMatch.sequenceMatches ) seqMatchByPepId(seqMatch.getPeptideId) = seqMatch 
        
        if( proteinMatch.seqDatabaseIds != null ) {
          for( seqDbId <- proteinMatch.seqDatabaseIds ) seqDatabaseIdSet += seqDbId
        }
      }
      
      // Keep only sequence matches corresponding to best peptide matches
      val bestSeqMatches = new ArrayBuffer[SequenceMatch]()
      
      for( ( pepId, seqMatch ) <- seqMatchByPepId ) {
        
        val tmpPeptideMatches = pepMatchesByPepId.get(pepId)
        
        // Check if peptides matches corresponding to this sequence match are loaded in the result set
        // Note that peptide matches with a rank greater than 1 may not be loaded
        if( tmpPeptideMatches != None ) {
          
          var bestPepMatchScore = 0f
          var bestPepMatch: PeptideMatch = null
          var bestSeqMatch: SequenceMatch = null
          
          for( pepMatch <- tmpPeptideMatches.get ) {
            if( pepMatch.score >= bestPepMatchScore ) {              
              bestPepMatchScore = pepMatch.score
              bestPepMatch = pepMatch
              bestSeqMatch = seqMatch
            }
          }
          
          // Build new sequence match corresponding to the best peptide match
          val newSeqMatch = bestSeqMatch.copy( 
                              peptide = Some(bestPepMatch.peptide),
                              bestPeptideMatchId = bestPepMatch.id
                            )
          
          bestSeqMatches += newSeqMatch
          
        }
      }
      
      // Retrieve protein id
      val proteinId = firstDescribedProtMatch.getProteinId
      
      // Compute protein match sequence coverage  
      var coverage = 0f
      if( proteinId != 0 && seqLengthByProtId.isDefined) {
        val seqLength = seqLengthByProtId.get.get(proteinId)
        if( seqLength == None ) {
          throw new Exception( "can't find a sequence length for the protein with id='"+proteinId+"'" )
        }
        
        val seqPositions = bestSeqMatches.map { s => ( s.start, s.end ) }        
        coverage = Protein.calcSequenceCoverage( seqLength.get, seqPositions )
      }
      
      val newProteinMatch = new ProteinMatch(
                                  id = ProteinMatch.generateNewId,
                                  accession = firstDescribedProtMatch.accession,
                                  description = firstDescribedProtMatch.description,
                                  coverage = coverage,
                                  peptideMatchesCount = bestSeqMatches.length,
                                  isDecoy = firstDescribedProtMatch.isDecoy,
                                  sequenceMatches = bestSeqMatches.toArray,
                                  seqDatabaseIds = seqDatabaseIdSet.toArray,
                                  proteinId = proteinId,
                                  taxonId = firstDescribedProtMatch.taxonId,
                                  scoreType = firstDescribedProtMatch.scoreType
                                 )
      
      nrProteinMatches += newProteinMatch
    }
    
    // Merge peptide matches and related protein matches
    val( mergedPeptideMatches, mergedProteinMatches) = ResultSetMerger.mergePeptideMatches( allPeptideMatches, nrProteinMatches )

    // Create merged result set    
    val mergedResultSet = new ResultSet(
                                id = ResultSet.generateNewId,
                                proteinMatches = mergedProteinMatches.toArray,
                                peptideMatches = mergedPeptideMatches.toArray,
                                peptides = peptideById.values.toArray,
                                isDecoy = resultSets(0).isDecoy,
                                isNative = false,
                                // FIXME: is this the best solution ???
                                msiSearch = resultSets(0).msiSearch
                              )
    this.logger.info( "result sets have been merged:")
    this.logger.info( "- nb merged protein matches = " + mergedResultSet.proteinMatches.length )
    this.logger.info( "- nb merged peptide matches = " + mergedResultSet.peptideMatches.length )
    this.logger.info( "- nb merged peptides = " + mergedResultSet.peptides.length )
    
    // TODO: Make some updates of result set objects
    //mergedResultSet.updateScoresOfProteinMatches( search_engine = 'mascot' )
    
    mergedResultSet
  }

}