package fr.proline.core.om.provider.msi

import fr.proline.core.om.model.msi.PeptideMatch
import fr.proline.repository.DatabaseContext

trait IPeptideMatchProvider {
  
  val msiDbCtx: DatabaseContext
  val psDbCtx: DatabaseContext
 
  def getPeptideMatchesAsOptions( pepMatchIds: Seq[Int] ): Array[Option[PeptideMatch]]
  
  def getPeptideMatches( pepMatchIds: Seq[Int] ): Array[PeptideMatch]
  
  def getResultSetsPeptideMatches( resultSetIds: Seq[Int] ): Array[PeptideMatch]
  
  def getResultSummariesPeptideMatches( rsmIds: Seq[Int] ): Array[PeptideMatch]
  
  
  def getPeptideMatch( pepMatchId:Int ): Option[PeptideMatch] = {
    getPeptideMatchesAsOptions( Array(pepMatchId) )(0)
  }
  
  def getResultSetPeptideMatches( resultSetId: Int ): Array[PeptideMatch] = {
    getResultSetsPeptideMatches( Array(resultSetId) )
  }
  
  def getResultSummaryPeptideMatches( rsmId: Int ): Array[PeptideMatch] = {
    getResultSummariesPeptideMatches( Array(rsmId) )
  }
  
}