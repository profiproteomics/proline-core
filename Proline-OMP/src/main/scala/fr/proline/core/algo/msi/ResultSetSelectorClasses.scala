package fr.proline.core.algo.msi

import scala.collection.Iterable
import scala.collection.mutable.HashSet
import fr.proline.core.om.model.msi._

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

  // Retrieve the ID of valid peptide matches (having a corresponding peptide instance)
  var validPepIdSetBuilder = Set.newBuilder[Long]
  var validPepMatchIdSetBuilder = Set.newBuilder[Long]
  var validProtMatchIdSetBuilder = Set.newBuilder[Long]
  
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
  
  // Build the set of unique valid peptide match ids
  val validPepIdSet = validPepIdSetBuilder.result()
  val validPepMatchIdSet = validPepMatchIdSetBuilder.result()
  val validProtMatchIdSet = validProtMatchIdSetBuilder.result()
  
  validPepIdSetBuilder = null
  validPepMatchIdSetBuilder = null
  validProtMatchIdSetBuilder = null
}