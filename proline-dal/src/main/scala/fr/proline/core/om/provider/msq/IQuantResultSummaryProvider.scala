package fr.proline.core.om.provider.msq

import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.om.model.msq.QuantResultSummary

trait IQuantResultSummaryProvider {
  
  def getQuantResultSummaries( quantRsmIds: collection.Seq[Long],  quantChannelIds: collection.Seq[Long], loadResultSet: Boolean, loadProteinMatches: Option[Boolean] = None, loadReporterIons: Option[Boolean]  = None ): Array[QuantResultSummary]
  
  def getQuantResultSummary( quantRsmId:Long, quantChannelIds: collection.Seq[Long], loadResultSet: Boolean, loadProteinMatches: Option[Boolean] = None, loadReporterIons: Option[Boolean]  = None ): Option[QuantResultSummary] = {
    getQuantResultSummaries( Array(quantRsmId), quantChannelIds, loadResultSet, loadProteinMatches,loadReporterIons ).headOption
  }

}