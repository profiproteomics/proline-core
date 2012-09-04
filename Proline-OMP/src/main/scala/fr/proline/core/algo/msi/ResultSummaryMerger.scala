package fr.proline.core.algo.msi

import collection.mutable.ArrayBuffer
import collection.mutable.HashMap
import org.apache.commons.lang3.StringUtils.{isNotEmpty => isStrNotEmpty}
import com.weiglewilczek.slf4s.Logging
import fr.proline.core.om.model.msi._

class ResultSummaryMerger extends Logging {

  def mergeResultSummaries( resultSummaries: Seq[ResultSummary], seqLengthByProtId: Map[Int,Int] ): ResultSummary = {
    
    // Define some vars
    //val msiSearchIds = new ArrayBuffer[Int]()
    val allValidPeptideMatches = new ArrayBuffer[PeptideMatch]()
    val proteinMatchesByKey = new HashMap[String,ArrayBuffer[ProteinMatch]]()
    val peptideById = new HashMap[Int,Peptide]()
    
    // Iterate over result summaries
    for( resultSummary <- resultSummaries ) {
      
      val resultSetAsOpt = resultSummary.resultSet
      assert( resultSetAsOpt != None, "the result summary must contain a result set" )
      
      val resultSet = resultSetAsOpt.get
      
      // Retrieve some vars
      val proteinMatches = resultSet.proteinMatches
      val peptideMatches = resultSet.peptideMatches
      val peptides = resultSet.peptides
      val proteinSets = resultSummary.proteinSets
      //if( resultSet.isNative ) msiSearchIds += resultSet.getMSISearchId
      
      // Retrieve the ID of valid peptide matches (having a corresponding peptide instance)
      val validPepMatchIdSetBuilder = Set.newBuilder[Int]
      for( proteinSet <- proteinSets ) {
        if( proteinSet.isValidated ) {
          val peptideInstances = proteinSet.peptideSet.getPeptideInstances
          
          for( pepInstance <- peptideInstances ) {
            validPepMatchIdSetBuilder ++= pepInstance.getPeptideMatchIds
          }
        }
      }
      
      // Build the set of unique valid peptide match ids
      val validPepMatchIdSet = validPepMatchIdSetBuilder.result()
      
      // Keep only peptide matches which belong to valid protein sets
      val validPeptideMatches = peptideMatches.filter { p => validPepMatchIdSet.contains( p.id ) }
      allValidPeptideMatches ++= validPeptideMatches
      
      // Iterate over protein matches to merge them by a unique key
      for( proteinMatch <- proteinMatches ) {
        //next if !defined proteinMatch.taxonId
        
        var protMatchKey = if( proteinMatch.getProteinId != 0 ) {
          // Build key using protein id and taxon id if they are defined
          "" + proteinMatch.getProteinId + '%' + proteinMatch.taxonId
        } else {
          // Else the key in the accession number
          proteinMatch.accession
        }
        
        proteinMatchesByKey.getOrElseUpdate( protMatchKey, new ArrayBuffer[ProteinMatch] ) += proteinMatch

      }
      
      // Merge peptides by id
      peptides.foreach { p => peptideById( p.id ) = p }
    
    }
    
    // Retrieve non-redundant list of peptides
    val nrPeptides = peptideById.values
    
    // Re-map the non-redundant list of peptides to peptide matches
    // Peptide matches related to the same peptide will use the same object
    val validPeptideById = new HashMap[Int,Peptide]()
    val validPepMatchesByPepId = new HashMap[Int,ArrayBuffer[PeptideMatch]]()
    
    for( peptideMatch <- allValidPeptideMatches ) {      
      val peptideId = peptideMatch.peptide.id
      val peptide = peptideById(peptideId)
      //TODO: find an other way to reduce the redundancy (use a cache in the provider ?)
      //peptideMatch.peptide = peptide
      
      validPeptideById(peptideId) = peptide
      validPepMatchesByPepId.getOrElseUpdate(peptideId, new ArrayBuffer[PeptideMatch] ) += peptideMatch
    }
    
    // Define some vars
    val nrProteinMatches = new ArrayBuffer[ProteinMatch]    
    var protMatchNum = 1
    
    // Iterate over grouped protein matches to build a list of non-redundant protein matches
    for( protMatchGroup <- proteinMatchesByKey.values) {
      
      var firstDescribedProtMatch: ProteinMatch = null
      var protMatchIdx = 0
      while( firstDescribedProtMatch == null && protMatchIdx < protMatchGroup.length ) {
        val proteinMatch = protMatchGroup(protMatchIdx)
        if( isStrNotEmpty(proteinMatch.description) ) {
          firstDescribedProtMatch = proteinMatch
        }
        protMatchIdx = protMatchIdx + 1
      }
      if( firstDescribedProtMatch == null ) firstDescribedProtMatch = protMatchGroup(0)
      
      // Retrieve all sequence matches of this protein match group
      val seqMatchByPepId = new HashMap[Int,SequenceMatch]()
      val seqDbIdSetBuilder = Set.newBuilder[Int]
      
      for( proteinMatch <- protMatchGroup ) {
        proteinMatch.sequenceMatches.foreach { s => seqMatchByPepId(s.getPeptideId) = s }
        
        if( proteinMatch.seqDatabaseIds != null ) {
         proteinMatch.seqDatabaseIds.foreach { seqDbIdSetBuilder += _ }
        }
      }
      
      // Keep only sequence matches corresponding to best validated peptide matches
      val bestSeqMatches = new ArrayBuffer[SequenceMatch]
      for( ( pepId, seqMatch ) <- seqMatchByPepId ) {        
        
        if( validPepMatchesByPepId.contains(pepId) ) {
          val peptideValidMatches = validPepMatchesByPepId(pepId)
        
          var bestPepMatchScore = 0f
          var bestPepMatch: PeptideMatch = null
          var bestSeqMatch: SequenceMatch = null
          
          for( validPepMatch <- peptideValidMatches ) {
            if( validPepMatch.score > bestPepMatchScore ) {              
              bestPepMatchScore = validPepMatch.score
              bestPepMatch = validPepMatch
              bestSeqMatch = seqMatch
            }
          }
          
          // Build new sequence match corresponding to the merged peptide match
          bestSeqMatches += bestSeqMatch.copy(
                              peptide = Some(bestPepMatch.peptide),
                              bestPeptideMatchId = bestPepMatch.id
                              )
          
        }
      }
      
      // Retrieve protein id
      val proteinId = firstDescribedProtMatch.getProteinId
      
      // Compute protein match sequence coverage  
      var coverage = 0f
      if( proteinId > 0 && seqLengthByProtId.contains(proteinId) ) {
        /*if( !seqLengthByProtId.contains(proteinId) ) {
          throw new Exception( "can't find a sequence for the protein with id='"+proteinId+"'" )
        }*/
        val seqLength = seqLengthByProtId(proteinId)
        val seqPositions = bestSeqMatches.map { s => ( s.start, s.end ) } 
        
        coverage = Protein.calcSequenceCoverage( seqLength, seqPositions )
      }
      
      nrProteinMatches += new ProteinMatch(
                                  id = protMatchNum,
                                  accession = firstDescribedProtMatch.accession,
                                  description = firstDescribedProtMatch.description,
                                  geneName = firstDescribedProtMatch.geneName,
                                  coverage = coverage,
                                  peptideMatchesCount = bestSeqMatches.length,
                                  isDecoy = firstDescribedProtMatch.isDecoy,
                                  sequenceMatches = bestSeqMatches.toArray,
                                  seqDatabaseIds = seqDbIdSetBuilder.result().toArray,
                                  proteinId = proteinId,
                                  taxonId = firstDescribedProtMatch.taxonId,
                                  scoreType = firstDescribedProtMatch.scoreType
                                 )
      
      protMatchNum += 1
    }
    
    // Create a non redundant list of MSI search ids
    //val nrMsiSearchIds = msiSearchIds.distinct
    
    // Merge peptide matches and related protein matches
    val mergedPeptideMatches = ResultSetMerger.mergePeptideMatches( allValidPeptideMatches, nrProteinMatches )
    
    // Create a merged result set
    val mergedResultSet = new ResultSet(
                                id = ResultSet.generateNewId(),
                                proteinMatches = nrProteinMatches.toArray,
                                peptideMatches = mergedPeptideMatches.toArray,
                                peptides =  validPeptideById.values.toArray,
                                isDecoy = resultSummaries(0).resultSet.get.isDecoy,
                                isNative = false
                                //msiSearchId = nrMsiSearchIds
                                )
    
    this.logger.info( "result summaries have been merged:")
    this.logger.debug( "- nb merged protein matches = " + mergedResultSet.proteinMatches.length )
    this.logger.debug( "- nb merged peptide matches = " + mergedResultSet.peptideMatches.length )
    this.logger.debug( "- nb merged peptides = " + mergedResultSet.peptides.length )
    
    // Instantiate a protein inference algo and build the merged result summary
    val protInferenceAlgo = ProteinSetInferer( InferenceMethods.parsimonious )
    val mergedRsm = protInferenceAlgo.computeResultSummary( mergedResultSet )
    
    // FIXME: retrieve the right score type
    for( proteinSet <- mergedRsm.proteinSets ) {
      proteinSet.scoreType = "mascot:mudpit score"
    }
    
    // TODO: Make some updates of result set and result summary objects
    //mergedResultSet.updateScoresOfProteinMatches( search_engine = 'mascot' )
    //mergedResultSummary.updateScoresOfProteinSets( search_engine = 'mascot' )
    //mergedResultSummary.updatePeptideRelations()
    
    mergedRsm
  }

}