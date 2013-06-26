package fr.proline.core.algo.msi

import scala.collection.Iterable
import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.om.model.msi.PeptideMatch
import fr.proline.core.om.model.msi.ProteinMatch
import fr.proline.core.om.model.msi.SequenceMatch
import fr.proline.core.om.model.msi.ResultSummary
import scala.collection.mutable.HashSet

class ResultSummarySelector(val rsm: ResultSummary) extends IResultSetSelector {

    // Retrieve the ID of valid peptide matches (having a corresponding peptide instance)
    var validPepMatchIdSetBuilder = Set.newBuilder[Long]
    val peptideIds = new HashSet[Long]
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
    
  def getPeptideMatches(rs: ResultSet): Iterable[PeptideMatch] = {
    rs.peptideMatches.filter{ pm => validPepMatchIdSet.contains(pm.id) }
  }

  def getProteinMatches(rs: ResultSet): Iterable[ProteinMatch] = {
    // TODO : alternative method : in init method, retrieve all proteinMatches from proteinSet and use this list
    rs.proteinMatches.filter{ pm => getSequenceMatches(pm).nonEmpty }
  }

  def getSequenceMatches(proteinMatch: ProteinMatch): Iterable[SequenceMatch] = {
    proteinMatch.sequenceMatches.filter{sm => peptideIds.contains(sm.getPeptideId) }
  }

}