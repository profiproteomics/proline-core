package fr.proline.core.om.provider.msi

import fr.proline.core.om.model.msi.PeptideMatch
import fr.proline.context.DatabaseConnectionContext

case class PeptideMatchFilter(
  val maxPrettyRank: Option[Int] = None,
  val minScore: Option[Float] = None
)

trait IPeptideMatchProvider {
 
  def getPeptideMatchesAsOptions( pepMatchIds: Seq[Long] ): Array[Option[PeptideMatch]]
  
  def getPeptideMatches( pepMatchIds: Seq[Long] ): Array[PeptideMatch]
  
  def getResultSetsPeptideMatches( resultSetIds: Seq[Long], pepMatchFilter: Option[PeptideMatchFilter] = None ): Array[PeptideMatch]
  
  def getResultSummariesPeptideMatches( rsmIds: Seq[Long] ): Array[PeptideMatch]
  
  
  def getPeptideMatch( pepMatchId: Long ): Option[PeptideMatch] = {
    getPeptideMatchesAsOptions( Array(pepMatchId) )(0)
  }
  
  def getResultSetPeptideMatches( resultSetId: Long, pepMatchFilter: Option[PeptideMatchFilter] = None ): Array[PeptideMatch] = {
    getResultSetsPeptideMatches( Array(resultSetId), pepMatchFilter )
  }
  
  def getResultSummaryPeptideMatches( rsmId: Long ): Array[PeptideMatch] = {
    getResultSummariesPeptideMatches( Array(rsmId) )
  }
  
}