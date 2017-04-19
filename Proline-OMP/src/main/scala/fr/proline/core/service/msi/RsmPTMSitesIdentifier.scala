package fr.proline.core.service.msi

import com.typesafe.scalalogging.LazyLogging

import fr.profi.jdbc.easy._
import fr.profi.util.regex.RegexUtils._
import fr.proline.api.service.IService
import fr.proline.context.IExecutionContext
import fr.proline.context.MsiDbConnectionContext
import fr.proline.core.algo.msi.PTMSitesIdentifier
import fr.proline.core.dal._
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.msi.MsiDbResultSummaryObjectTreeMapTable
import fr.proline.core.om.model.msi.PtmSite
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.om.model.msi.SpectrumTitleFields.RAW_FILE_IDENTIFIER
import fr.proline.core.om.provider.msi.impl.SQLProteinMatchProvider
import fr.proline.core.om.provider.msi.impl.SQLResultSummaryProvider
import fr.proline.core.om.storer.msi.RsmStorer
import fr.proline.core.orm.msi.ObjectTreeSchema.SchemaName
import fr.profi.util.primitives._
import fr.proline.core.dal.tables.msi.MsiDbObjectTreeTable

object RsmPTMSitesIdentifier extends LazyLogging {

  def loadResultSummary(rsmId: Long, execContext: IExecutionContext): ResultSummary = {

    val rsmProvider = new SQLResultSummaryProvider(
      execContext.getMSIDbConnectionContext,
      execContext.getPSDbConnectionContext,
      execContext.getUDSDbConnectionContext
    )

    val rsm = rsmProvider.getResultSummary(rsmId, true)
    require(rsm.isDefined, "Unknown ResultSummary Id: " + rsmId)

    rsm.get
  }

  def getOrIdentifyPtmSites(resultSummary: ResultSummary, execContext: IExecutionContext): Iterable[PtmSite] = {

    null
  }

}

class RsmPTMSitesIdentifier(
  execContext: IExecutionContext,
  resultSummaryId: Long,
  force: Boolean) extends IService with LazyLogging {

  private val msiDbContext = execContext.getMSIDbConnectionContext

  def runService(): Boolean = {

    logger.info("PtmSites identifier service starts")

    val startTime = System.currentTimeMillis() / 1000

    val existingObjectTreeId = _getPtmSites(resultSummaryId)

    if (existingObjectTreeId.isDefined && !force) {
      logger.info("PtmSites already defined for this ResultSummary. Please use force parameter to force re-identification")
      false
    } else { 
      if (existingObjectTreeId.isDefined) {
        logger.info("already defined Ptm sites for this ResultSummary will be deleted")
         _deletePtmSites(existingObjectTreeId.get)
      }
   
      val ptmSitesIdentifier = new PTMSitesIdentifier()
      val proteinMatchProvider = new SQLProteinMatchProvider(msiDbContext)
      val rsmStorer = RsmStorer(msiDbContext)

      // Check if a transaction is already initiated
      val wasInTransaction = msiDbContext.isInTransaction()
      if (!wasInTransaction) msiDbContext.beginTransaction()

      val rsm = RsmPTMSitesIdentifier.loadResultSummary(resultSummaryId, execContext)
      logger.info("Start identifying Ptm sites")
      val ptmSites = ptmSitesIdentifier.identifyPTMSites(rsm, proteinMatchProvider.getResultSummariesProteinMatches(List(resultSummaryId)))
      rsmStorer.storePtmSites(rsm.id, ptmSites, execContext)

      // Commit transaction if it was initiated locally
      if (!wasInTransaction) msiDbContext.commitTransaction()

      val took = System.currentTimeMillis() / 1000 - startTime
      this.logger.info("Ptm sites identifier service took " + took + " seconds")
      true
    }
  }

  private def _getPtmSites(rsmId: Long): Option[Long] = {
    DoJDBCReturningWork.withEzDBC(execContext.getMSIDbConnectionContext) { msiEzDBC =>
      val ptmSiteQuery = new SelectQueryBuilder1(MsiDbResultSummaryObjectTreeMapTable).mkSelectQuery((t, c) =>
        List(t.*) -> "WHERE " ~ t.RESULT_SUMMARY_ID ~ s" = '${rsmId}'" ~ " AND " ~ t.SCHEMA_NAME ~ s" = '${SchemaName.PTM_SITES.toString}'"
      )
      msiEzDBC.select(ptmSiteQuery) { r =>
        toLong(r.getAny(MsiDbResultSummaryObjectTreeMapTable.columns.OBJECT_TREE_ID))
      }
    }.headOption
  }
  
  private def _deletePtmSites(objectTreeID: Long) {
    DoJDBCWork.withEzDBC(execContext.getMSIDbConnectionContext) { msiEzDBC =>
      msiEzDBC.execute(s"DELETE FROM ${MsiDbResultSummaryObjectTreeMapTable.name} WHERE ${MsiDbResultSummaryObjectTreeMapTable.columns.OBJECT_TREE_ID} = ${objectTreeID}" ) 
      msiEzDBC.execute(s"DELETE FROM ${MsiDbObjectTreeTable.name} WHERE ${MsiDbObjectTreeTable.columns.ID} = ${objectTreeID}" ) 
    }
  }
}