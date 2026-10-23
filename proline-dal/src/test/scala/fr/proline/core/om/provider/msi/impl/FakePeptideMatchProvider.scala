package fr.proline.core.om.provider.msi.impl

import fr.proline.core.om.model.msi.PeptideMatch
import fr.proline.core.om.provider.msi.IPeptideMatchProvider
import fr.proline.core.om.provider.msi.PeptideMatchFilter

class FakePeptideMatchProvider extends IPeptideMatchProvider {

  val msiDbCtx = null
  val psDbCtx = null

  def getPeptideMatchesAsOptions(pepMatchIds: collection.Seq[Long]): Array[Option[PeptideMatch]] = { Array.empty[Option[PeptideMatch]] }

  def getPeptideMatches(pepMatchIds: collection.Seq[Long]): Array[PeptideMatch] = { Array.empty[PeptideMatch] }

  def getResultSetsPeptideMatches(resultSetIds: collection.Seq[Long], pepMatchFilter: Option[PeptideMatchFilter] = None): Array[PeptideMatch] = { Array.empty[PeptideMatch] }

  def getResultSummariesPeptideMatches(rsmIds: collection.Seq[Long]): Array[PeptideMatch] = { Array.empty[PeptideMatch] }

}