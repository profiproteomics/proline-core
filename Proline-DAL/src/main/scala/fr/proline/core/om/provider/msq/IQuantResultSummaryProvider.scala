package fr.proline.core.om.provider.msq

import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.om.model.msq.QuantResultSummary

trait IQuantResultSummaryProvider {
  
  def getQuantResultSummariesAsOptions( quantRsmIds: Seq[Long], quantChannelIds: Seq[Long], loadResultSet: Boolean ): Array[Option[QuantResultSummary]]
  
  def getQuantResultSummaries( quantRsmIds: Seq[Long],  quantChannelIds: Seq[Long], loadResultSet: Boolean ): Array[QuantResultSummary]
  
  
  def getQuantResultSummary( quantRsmId:Long, quantChannelIds: Seq[Long], loadResultSet: Boolean ): Option[QuantResultSummary] = {
    getQuantResultSummariesAsOptions( Array(quantRsmId), quantChannelIds, loadResultSet )(0)
  }
  
}