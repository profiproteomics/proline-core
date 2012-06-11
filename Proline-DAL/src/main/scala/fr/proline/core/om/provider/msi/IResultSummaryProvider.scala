package fr.proline.core.om.provider.msi
import fr.proline.core.om.model.msi.ResultSummary

trait IResultSummaryProvider {
  
  def getResultSummariesAsOptions( rsmIds: Seq[Int] ): Array[Option[ResultSummary]]
  
  def getResultSummaries( rsmIds: Seq[Int] ): Array[Option[ResultSummary]]
  
  def getResultSetsResultSummaries( rsIds: Seq[Int] ): Array[ResultSummary]
  
  
  def getResultSummary( rsmId:Int ): Option[ResultSummary] = { getResultSummariesAsOptions( Array(rsmId) )(0) }
  
  def getResultSetResultSummaries( rsId: Int ): Array[ResultSummary] = {
    getResultSetsResultSummaries( Array(rsId) )
  }
  
}