package fr.proline.core.om.provider.msi

import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.context.DatabaseConnectionContext

trait IResultSummaryProvider {
  
  def getResultSummariesAsOptions( rsmIds: Seq[Long], loadResultSet: Boolean, loadProteinMatches: Option[Boolean] = None ): Array[Option[ResultSummary]]
  
  def getResultSummaries( rsmIds: Seq[Long], loadResultSet: Boolean, loadProteinMatches: Option[Boolean] = None ): Array[ResultSummary]
  
  //def getResultSetsResultSummaries( rsIds: Seq[Long], loadResultSet: Boolean ): Array[ResultSummary]
  
  
  def getResultSummary( rsmId:Long, loadResultSet: Boolean, loadProteinMatches: Option[Boolean] = None ): Option[ResultSummary] = {
    getResultSummariesAsOptions( Array(rsmId), loadResultSet )(0)
  }
  
  /*def getResultSetResultSummaries( rsId: Long, loadResultSet: Boolean ): Array[ResultSummary] = {
    getResultSetsResultSummaries( Array(rsId), loadResultSet )
  }*/
  
}