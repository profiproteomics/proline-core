package fr.proline.core.algo.msi

import scala.collection.Iterable
import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.om.model.msi.PeptideMatch
import fr.proline.core.om.model.msi.ProteinMatch
import fr.proline.core.om.model.msi.SequenceMatch
import fr.proline.core.om.model.msi.ResultSummary
import scala.collection.mutable.HashSet

abstract class AbstractResultSetSelector extends IResultSetSelector {

   val validPepMatchIdSet:Set[Long] //= getPeptideMatchIds
   val validPeptideIds:HashSet[Long]  //= getPeptideIds
  
//  def getPeptideMatchIds: Set[Long]
//  def getPeptideIds: HashSet[Long]
  
  def getPeptideMatches(rs: ResultSet): Iterable[PeptideMatch] = {
    rs.peptideMatches.filter{ pm => validPepMatchIdSet.contains(pm.id) }
  }

  def getProteinMatches(rs: ResultSet): Iterable[ProteinMatch] = {
    // TODO : alternative method : in init method, retrieve all proteinMatches from proteinSet and use this list
    rs.proteinMatches.filter{ pm => getSequenceMatches(pm).nonEmpty }
  }

  def getSequenceMatches(proteinMatch: ProteinMatch): Iterable[SequenceMatch] = {
    proteinMatch.sequenceMatches.filter{sm => validPeptideIds.contains(sm.getPeptideId) }
  }

}

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
}

class ResultSummarySelector(val rsm: ResultSummary) extends AbstractResultSetSelector {

    // Retrieve the ID of valid peptide matches (having a corresponding peptide instance)
    var validPepMatchIdSetBuilder = Set.newBuilder[Long]
    var peptideIds = new HashSet[Long]
    for (proteinSet <- rsm.proteinSets) {
      if (proteinSet.isValidated) {
        val peptideInstances = proteinSet.peptideSet.getPeptideInstances
        for (pepInstance <- peptideInstances) {
          validPepMatchIdSetBuilder ++= pepInstance.getPeptideMatchIds
          peptideIds.add(pepInstance.peptideId)
        }
      }
    }
    // Build the set of unique valid peptide match ids
    val validPepMatchIdSet = validPepMatchIdSetBuilder.result()
    validPepMatchIdSetBuilder = null
    val validPeptideIds = peptideIds
  

   
}