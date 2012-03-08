package fr.proline.core.om.provider
import fr.proline.core.om.model.msi.ProteinMatch



trait IProteinMatchProvider {

  def getProteinMatches( protMatchIds: Seq[Int] ): Array[Option[ProteinMatch]]
  
  def getProteinMatch( protMatchId:Int ): Option[ProteinMatch] = {
    getProteinMatches( Array(protMatchId) )(0)
  }
  
  def getResultSetsProteinMatches( resultSetIds: Seq[Int] ): Array[Option[ProteinMatch]]
  
  def getResultSetProteinMatches( resultSetId: Int ): Array[Option[ProteinMatch]] = {
    getResultSetsProteinMatches( Array(resultSetId) )
  }
}