package fr.proline.core.om.provider.msi

import fr.proline.core.om.model.msi.ProteinMatch

trait IProteinMatchProvider {
  
  def getProteinMatchesAsOptions( protMatchIds: Seq[Int] ): Array[Option[ProteinMatch]]

  def getProteinMatches( protMatchIds: Seq[Int] ): Array[ProteinMatch]
  
  def getResultSetsProteinMatches( resultSetIds: Seq[Int] ): Array[ProteinMatch]
  
  def getResultSummariesProteinMatches( rsmIds: Seq[Int] ): Array[ProteinMatch]
  
  
  def getProteinMatch( protMatchId:Int ): Option[ProteinMatch] = {
    getProteinMatchesAsOptions( Array(protMatchId) )(0)
  }
  
  def getResultSetProteinMatches( resultSetId: Int ): Array[ProteinMatch] = {
    getResultSetsProteinMatches( Array(resultSetId) )
  }
  
  def getResultSummaryProteinMatches( rsmId: Int ): Array[ProteinMatch] = {
    getResultSummariesProteinMatches( Array(rsmId) )
  }
  
}