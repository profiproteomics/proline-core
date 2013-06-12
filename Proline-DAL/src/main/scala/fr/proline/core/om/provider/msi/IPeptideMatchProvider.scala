package fr.proline.core.om.provider.msi

import fr.proline.core.om.model.msi.PeptideMatch
import fr.proline.context.DatabaseConnectionContext

trait IPeptideMatchProvider {
 
  def getPeptideMatchesAsOptions( pepMatchIds: Seq[Long] ): Array[Option[PeptideMatch]]
  
  def getPeptideMatches( pepMatchIds: Seq[Long] ): Array[PeptideMatch]
  
  def getResultSetsPeptideMatches( resultSetIds: Seq[Long] ): Array[PeptideMatch]
  
  def getResultSummariesPeptideMatches( rsmIds: Seq[Long] ): Array[PeptideMatch]
  
  
  def getPeptideMatch( pepMatchId:Long ): Option[PeptideMatch] = {
    getPeptideMatchesAsOptions( Array(pepMatchId) )(0)
  }
  
  def getResultSetPeptideMatches( resultSetId: Long ): Array[PeptideMatch] = {
    getResultSetsPeptideMatches( Array(resultSetId) )
  }
  
  def getResultSummaryPeptideMatches( rsmId: Long ): Array[PeptideMatch] = {
    getResultSummariesPeptideMatches( Array(rsmId) )
  }
  
}