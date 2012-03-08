package fr.proline.core.om.provider
import fr.proline.core.om.model.msi.ResultSummary

trait IResultSummaryProvider {
  
  def getResultSummaries( resultSummaryIds: Seq[Int] ): Array[Option[ResultSummary]]
  
  def getResultSummary( resultSummaryId:Int ): Option[ResultSummary] = { getResultSummaries( Array(0) )(0) }
  
  def getResultSetsResultSummaries( resultSetIds: Seq[Int] ): Array[Option[ResultSummary]]
  
  def getResultSetResultSummaries( resultSetId: Int ): Array[Option[ResultSummary]] = {
    getResultSetsResultSummaries( Array(resultSetId) )
  }
}