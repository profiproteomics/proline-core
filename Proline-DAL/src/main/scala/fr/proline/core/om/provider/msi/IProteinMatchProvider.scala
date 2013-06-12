package fr.proline.core.om.provider.msi

import fr.proline.core.om.model.msi.ProteinMatch
import fr.proline.context.DatabaseConnectionContext

trait IProteinMatchProvider {
  
  def getProteinMatchesAsOptions( protMatchIds: Seq[Long] ): Array[Option[ProteinMatch]]

  def getProteinMatches( protMatchIds: Seq[Long] ): Array[ProteinMatch]
  
  def getResultSetsProteinMatches( resultSetIds: Seq[Long] ): Array[ProteinMatch]
  
  def getResultSummariesProteinMatches( rsmIds: Seq[Long] ): Array[ProteinMatch]
  
  
  def getProteinMatch( protMatchId:Long ): Option[ProteinMatch] = {
    getProteinMatchesAsOptions( Array(protMatchId) )(0)
  }
  
  def getResultSetProteinMatches( resultSetId: Long ): Array[ProteinMatch] = {
    getResultSetsProteinMatches( Array(resultSetId) )
  }
  
  def getResultSummaryProteinMatches( rsmId: Long ): Array[ProteinMatch] = {
    getResultSummariesProteinMatches( Array(rsmId) )
  }
  
}