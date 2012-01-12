package fr.proline.core.om.provider
import fr.proline.core.om.msi.ResultSetClasses.ResultSummary

trait IResultSummaryProvider {
  
  def getResultSummaries( resultSummaryIds: Seq[Int] ): Array[ResultSummary]
  
  def getResultSummary( resultSummaryId:Int ): ResultSummary = { getResultSummaries( Array(0) )(0) }
  
  def getResultSetsResultSummaries( resultSetIds: Seq[Int] ): Array[ResultSummary]
  
  def getResultSetResultSummaries( resultSetId: Int ): Array[ResultSummary] = {
    getResultSetsResultSummaries( Array(resultSetId) )
  }
}