package fr.proline.core.om.provider.msq

import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.om.model.msq.QuantResultSummary

trait IQuantResultSummaryProvider {
  
  def getQuantResultSummariesAsOptions( quantRsmIds: Seq[Int], loadResultSet: Boolean ): Array[Option[QuantResultSummary]]
  
  def getQuantResultSummaries( quantRsmIds: Seq[Int], loadResultSet: Boolean ): Array[QuantResultSummary]
  
  
  def getQuantResultSummary( quantRsmId:Int, loadResultSet: Boolean ): Option[QuantResultSummary] = {
    getQuantResultSummariesAsOptions( Array(quantRsmId), loadResultSet )(0)
  }
  
}