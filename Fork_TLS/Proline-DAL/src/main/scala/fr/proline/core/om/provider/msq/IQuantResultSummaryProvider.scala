package fr.proline.core.om.provider.msq

import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.om.model.msq.QuantResultSummary

trait IQuantResultSummaryProvider {
  
  def getQuantResultSummaries( quantRsmIds: Seq[Long],  quantChannelIds: Seq[Long], loadResultSet: Boolean ): Array[QuantResultSummary]
  
  def getQuantResultSummary( quantRsmId:Long, quantChannelIds: Seq[Long], loadResultSet: Boolean ): Option[QuantResultSummary] = {
    getQuantResultSummaries( Array(quantRsmId), quantChannelIds, loadResultSet ).headOption
  }
  
}