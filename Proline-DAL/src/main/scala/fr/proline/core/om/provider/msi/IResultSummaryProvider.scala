package fr.proline.core.om.provider.msi

import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.repository.DatabaseContext

trait IResultSummaryProvider {
  
  val msiDbCtx: DatabaseContext
  val psDbCtx: DatabaseContext
  
  def getResultSummariesAsOptions( rsmIds: Seq[Int], loadResultSet: Boolean ): Array[Option[ResultSummary]]
  
  def getResultSummaries( rsmIds: Seq[Int], loadResultSet: Boolean ): Array[ResultSummary]
  
  def getResultSetsResultSummaries( rsIds: Seq[Int], loadResultSet: Boolean ): Array[ResultSummary]
  
  
  def getResultSummary( rsmId:Int, loadResultSet: Boolean ): Option[ResultSummary] = {
    getResultSummariesAsOptions( Array(rsmId), loadResultSet )(0)
  }
  
  def getResultSetResultSummaries( rsId: Int, loadResultSet: Boolean ): Array[ResultSummary] = {
    getResultSetsResultSummaries( Array(rsId), loadResultSet )
  }
  
}