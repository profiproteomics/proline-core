package fr.proline.core.om.factory

import fr.proline.core.om.msi.PeptideClasses.PeptideMatch

trait IPeptideMatchLoader {
 
  def getPeptideMatches( pepMatchIds: Seq[Int] ): Array[PeptideMatch]
  
  def getPeptideMatch( pepMatchId:Int ): PeptideMatch = {
    getResultSetsPeptideMatches( Array(pepMatchId) )(0)
  }
  
  def getResultSetsPeptideMatches( resultSetIds: Seq[Int] ): Array[PeptideMatch]
  
  def getResultSetPeptideMatches( resultSetId: Int ): Array[PeptideMatch] = {
    getResultSetsPeptideMatches( Array(resultSetId) )
  }
  
}