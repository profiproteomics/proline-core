package fr.proline.core.om.provider

import fr.proline.core.om.msi.PeptideClasses.PeptideMatch

trait IPeptideMatchProvider {
 
  def getPeptideMatches( pepMatchIds: Seq[Int] ): Array[PeptideMatch]
  
  def getPeptideMatch( pepMatchId:Int ): PeptideMatch = {
    getPeptideMatches( Array(pepMatchId) )(0)
  }
  
  def getResultSetsPeptideMatches( resultSetIds: Seq[Int] ): Array[PeptideMatch]
  
  def getResultSetPeptideMatches( resultSetId: Int ): Array[PeptideMatch] = {
    getResultSetsPeptideMatches( Array(resultSetId) )
  }
  
}