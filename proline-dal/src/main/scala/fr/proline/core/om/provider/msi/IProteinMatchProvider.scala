package fr.proline.core.om.provider.msi

import fr.proline.core.om.model.msi.ProteinMatch
import fr.proline.context.DatabaseConnectionContext

trait IProteinMatchProvider {
  
  def getProteinMatchesAsOptions( protMatchIds: collection.Seq[Long] ): Array[Option[ProteinMatch]]

  def getProteinMatches( protMatchIds: collection.Seq[Long] ): Array[ProteinMatch]
  
  def getResultSetsProteinMatches( resultSetIds: collection.Seq[Long] ): Array[ProteinMatch]
  
  def getResultSummariesProteinMatches( rsmIds: collection.Seq[Long] ): Array[ProteinMatch]
  
  
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