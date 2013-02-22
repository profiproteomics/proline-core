package fr.proline.core.om.provider.msi.impl

import fr.proline.core.om.provider.msi.IPeptideMatchProvider
import fr.proline.core.om.model.msi.PeptideMatch


class FakePeptideMatchProvider extends IPeptideMatchProvider {

  val msiDbCtx = null
  val psDbCtx = null

  def getPeptideMatchesAsOptions(pepMatchIds: Seq[Int]): Array[Option[PeptideMatch]] = { Array.empty[Option[PeptideMatch]] }

  def getPeptideMatches(pepMatchIds: Seq[Int]): Array[PeptideMatch] = { Array.empty[PeptideMatch] }

  def getResultSetsPeptideMatches(resultSetIds: Seq[Int]): Array[PeptideMatch] = { Array.empty[PeptideMatch] }

  def getResultSummariesPeptideMatches(rsmIds: Seq[Int]): Array[PeptideMatch] = { Array.empty[PeptideMatch] }

}