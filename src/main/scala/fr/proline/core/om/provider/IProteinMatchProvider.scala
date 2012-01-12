package fr.proline.core.om.provider

import fr.proline.core.om.msi.ProteinClasses.ProteinMatch

trait ProteinMatchProvider {

  def getProteinMatches( protMatchIds: Seq[Int] ): Array[ProteinMatch]
  
  def getProteinMatch( protMatchId:Int ): ProteinMatch = {
    getProteinMatches( Array(protMatchId) )(0)
  }
  
  def getResultSetsProteinMatches( resultSetIds: Seq[Int] ): Array[ProteinMatch]
  
  def getResultSetProteinMatches( resultSetId: Int ): Array[ProteinMatch] = {
    getResultSetsProteinMatches( Array(resultSetId) )
  }
}