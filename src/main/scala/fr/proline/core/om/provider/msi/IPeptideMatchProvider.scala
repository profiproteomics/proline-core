package fr.proline.core.om.provider
import fr.proline.core.om.model.msi.PeptideMatch


trait IPeptideMatchProvider {
 
  def getPeptideMatches( pepMatchIds: Seq[Int] ): Array[Option[PeptideMatch]]
  
  def getPeptideMatch( pepMatchId:Int ): Option[PeptideMatch] = {
    getPeptideMatches( Array(pepMatchId) )(0)
  }
  
  def getResultSetsPeptideMatches( resultSetIds: Seq[Int] ): Array[Option[PeptideMatch]]
  
  def getResultSetPeptideMatches( resultSetId: Int ): Array[Option[PeptideMatch]] = {
    getResultSetsPeptideMatches( Array(resultSetId) )
  }
  
}