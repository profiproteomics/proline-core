package fr.proline.core.algo.msi

import scala.collection.Iterable
import scala.collection.mutable.HashSet
import fr.proline.core.om.model.msi._

trait IResultSetSelector {

  def getPeptideMatches(rs: ResultSet): Iterable[PeptideMatch]

  def getProteinMatches(rs: ResultSet): Iterable[ProteinMatch]

  def getSequenceMatches(proteinMatch: ProteinMatch): Iterable[SequenceMatch]

}

object ResultSetSelector extends IResultSetSelector {

  def getPeptideMatches(rs: ResultSet): Iterable[PeptideMatch] = {
    rs.peptideMatches
  }

  def getProteinMatches(rs: ResultSet): Iterable[ProteinMatch] = {
    rs.proteinMatches
  }

  def getSequenceMatches(proteinMatch: ProteinMatch): Iterable[SequenceMatch] = {
    proteinMatch.sequenceMatches
  }

}

abstract class AbstractResultSetSelector extends IResultSetSelector {

  val validPepIdSet: Set[Long]
  val validPepMatchIdSet: Set[Long]
  val validProtMatchIdSet: Set[Long]

  def getPeptideMatches(rs: ResultSet): Iterable[PeptideMatch] = {
    rs.peptideMatches.filter { pm => validPepMatchIdSet.contains(pm.id) }
  }

  def getProteinMatches(rs: ResultSet): Iterable[ProteinMatch] = {
    rs.proteinMatches.filter { pm => validProtMatchIdSet.contains(pm.id) }
  }

  def getSequenceMatches(proteinMatch: ProteinMatch): Iterable[SequenceMatch] = {
    proteinMatch.sequenceMatches.filter {sm => validPepIdSet.contains(sm.getPeptideId) }
  }
  
  def getFilteredResultSet(rs: ResultSet, rsId: Long = ResultSet.generateNewId ): ResultSet = {
    
    val filteredProtMatches = this.getProteinMatches(rs).toArray
    val filteredPepMatches = this.getPeptideMatches(rs).toArray
    val pepMatchesByPepId = filteredPepMatches.groupBy(_.peptide.id)
    val filteredPeptides = filteredPepMatches.map(_.peptide).distinct
    
    val newProtMatches = for (proteinMatch <- filteredProtMatches) yield {
      
      val newSeqMatches = for (
        seqMatch <- proteinMatch.sequenceMatches;
        if pepMatchesByPepId.contains(seqMatch.getPeptideId)
      ) yield {
        seqMatch.copy(
          // TODO: do the maxBy operation in the result set adder
          bestPeptideMatchId = pepMatchesByPepId(seqMatch.getPeptideId).maxBy(_.score).id,
          resultSetId = rsId
        )
      }
      
      proteinMatch.copy(
        sequenceMatches = newSeqMatches,
        resultSetId = rsId
      )
    }
    
    rs.copy(
      id = rsId,
      peptides = filteredPeptides,
      peptideMatches = filteredPepMatches,
      proteinMatches = newProtMatches
    ) 
  }

}

/*
class RankSelector(val rs: ResultSet, val rank: Int = 1) extends AbstractResultSetSelector {
      // Retrieve the ID of valid peptide matches (having a corresponding peptide instance)
    var validPepMatchIdSetBuilder = Set.newBuilder[Long]
    var peptideIds = new HashSet[Long]
    for (peptideMatch <- rs.peptideMatches) {
      if (peptideMatch.rank <= rank) {
      	 validPepMatchIdSetBuilder += peptideMatch.id
          peptideIds.add(peptideMatch.peptideId)
        }
    }
    // Build the set of unique valid peptide match ids
    val validPepMatchIdSet = validPepMatchIdSetBuilder.result()
    validPepMatchIdSetBuilder = null
    val validPeptideIds = peptideIds
  
}*/

class ResultSummarySelector(val rsm: ResultSummary) extends AbstractResultSetSelector {

  val( validPepIdSet, validPepMatchIdSet, validProtMatchIdSet ) = {
    
    // Build the set of unique valid entities ids
    val validPepIdSetBuilder = Set.newBuilder[Long]
    val validPepMatchIdSetBuilder = Set.newBuilder[Long]
    val validProtMatchIdSetBuilder = Set.newBuilder[Long]
    
    // Retrieve the ID of valid peptide matches (having a corresponding peptide instance)
    for (proteinSet <- rsm.proteinSets) {
      if (proteinSet.isValidated) {
        val peptideSet = proteinSet.peptideSet
        
        for (pepInstance <- peptideSet.getPeptideInstances) {
          validPepIdSetBuilder += pepInstance.peptide.id
          validPepMatchIdSetBuilder ++= pepInstance.getPeptideMatchIds
        }
        
        validProtMatchIdSetBuilder ++= proteinSet.getProteinMatchIds
      }
    }
    
    (
      validPepIdSetBuilder.result(),
      validPepMatchIdSetBuilder.result(),
      validProtMatchIdSetBuilder.result()
    )
  }
  
  def toResultSet( rsId: Long = ResultSet.generateNewId ): ResultSet = {
    require( rsm.resultSet.isDefined, "a resultSet must be defined")
    this.getFilteredResultSet(rsm.resultSet.get, rsId)
  }
  
}