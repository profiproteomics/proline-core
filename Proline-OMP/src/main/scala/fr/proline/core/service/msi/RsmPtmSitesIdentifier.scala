package fr.proline.core.service.msi

import com.typesafe.scalalogging.LazyLogging
import fr.profi.util.primitives._
import fr.profi.util.serialization.ProfiJson
import fr.proline.api.service.IService
import fr.proline.context.IExecutionContext
import fr.proline.core.algo.msi.PtmSitesIdentifier
import fr.proline.core.dal._
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.msi.MsiDbObjectTreeTable
import fr.proline.core.dal.tables.msi.MsiDbPeptideMatchTable
import fr.proline.core.dal.tables.msi.MsiDbResultSummaryObjectTreeMapTable
import fr.proline.core.dal.tables.msi.MsiDbResultSummaryRelationTable
import fr.proline.core.om.model.msi.PtmSite
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.om.provider.PeptideCacheExecutionContext
import fr.proline.core.om.provider.msi.impl.SQLProteinMatchProvider
import fr.proline.core.om.provider.msi.impl.SQLResultSummaryProvider
import fr.proline.core.om.storer.msi.RsmStorer
import fr.proline.core.orm.msi.ObjectTreeSchema.SchemaName

import scala.collection.mutable.ArrayBuffer

object RsmPtmSitesIdentifier extends LazyLogging {

  def loadResultSummary(rsmId: Long, execContext: IExecutionContext): ResultSummary = {

    val rsmProvider = new SQLResultSummaryProvider(PeptideCacheExecutionContext(execContext) )

    val rsm = rsmProvider.getResultSummary(rsmId, loadResultSet = true)
    require(rsm.isDefined, "Unknown ResultSummary Id: " + rsmId)

    rsm.get
  }

  def getOrIdentifyPtmSites(resultSummary: ResultSummary, execContext: IExecutionContext): Iterable[PtmSite] = {

    null
  }

}

class RsmPtmSitesIdentifier(
  execContext: IExecutionContext,
  resultSummaryId: Long,
  force: Boolean) extends IService with LazyLogging {

  private val msiDbContext = execContext.getMSIDbConnectionContext
  private val rsmStorer = RsmStorer(msiDbContext)
  private val proteinMatchProvider = new SQLProteinMatchProvider(msiDbContext)
  private val ptmSitesIdentifier = new PtmSitesIdentifier()

  def runService(): Boolean = {

    logger.info("PtmSites identifier service starts")

    val startTime = System.currentTimeMillis() / 1000

    val existingObjectTreeId = _getPtmSitesObjectTreeID(resultSummaryId)

    // Check if a transaction is already initiated
    val wasInTransaction = msiDbContext.isInTransaction
    if (!wasInTransaction) msiDbContext.beginTransaction()

    // Avoid loadResultSummary if PTMSites already defined and option force is not set
    if (existingObjectTreeId.isDefined && !force) {
      logger.info("PtmSites already defined for this ResultSummary. Please use force parameter to force re-identification")
      false
    } else {
      if (existingObjectTreeId.isDefined) {
        logger.info("already defined Ptm sites for this ResultSummary will be deleted")
        _deletePtmSites(existingObjectTreeId.get)
      }

      logger.info("Start identifying Ptm sites for ResultSummary #"+resultSummaryId)

      val rsm = RsmPtmSitesIdentifier.loadResultSummary(resultSummaryId, execContext)
      _getOrIdentifyPtmSites(rsm)

      // Commit transaction if it was initiated locally
      if (!wasInTransaction) msiDbContext.commitTransaction()

      val took = System.currentTimeMillis() / 1000 - startTime
      this.logger.info("Ptm sites identifier service took " + took + " seconds")
      true
    }
  }

  private def _getOrIdentifyPtmSites(resultSummary: ResultSummary): Iterable[PtmSite] = {

    val existingObjectTreeId = _getPtmSitesObjectTreeID(resultSummary.id)
    var ptmSites = Array.empty[PtmSite].toIterable
    
    if (existingObjectTreeId.isDefined && !force) {
      logger.info(s"Read already defined PtmSites for ResultSummary #${resultSummary.id} ")
      _getPtmSites(existingObjectTreeId.get)
    } else {
      if (existingObjectTreeId.isDefined) {
        logger.info(s"already defined Ptm sites for ResultSummary #${resultSummary.id} will be deleted")
        _deletePtmSites(existingObjectTreeId.get)
      }

      // this resultSummary is a merged RSM
      val sites = new ArrayBuffer[Iterable[PtmSite]]
      
      val childRSMIds = _getChildResultSummaryIds(resultSummary.id)
      
      if (!childRSMIds.isEmpty) {
        logger.debug(s"get or identify PtmSite for children of ResultSummary #${resultSummary.id}")
        for (childRSMId <- childRSMIds) {
          val rsm = RsmPtmSitesIdentifier.loadResultSummary(childRSMId, execContext)
          sites += _getOrIdentifyPtmSites(rsm)
        }
        
        val proteinMatches = proteinMatchProvider.getResultSummariesProteinMatches(childRSMIds)
        // then aggregate those sites
        logger.debug(s"Aggregation of PtmSite ResultSummary #${resultSummary.id}")
        ptmSites = ptmSitesIdentifier.aggregatePtmSites(sites.toArray, proteinMatches, proteinMatchProvider.getResultSummariesProteinMatches(List(resultSummary.id)), _getPeptideMatchScores)
      } else {
        // this ResultSummary is a leaf RSM 
        logger.info(s"Identifying Ptm sites for ResultSummary #${resultSummary.id}")
        ptmSites = ptmSitesIdentifier.identifyPtmSites(resultSummary, proteinMatchProvider.getResultSummariesProteinMatches(List(resultSummary.id)))
      }
      
      rsmStorer.storePtmSites(resultSummary.id, ptmSites, execContext)
      ptmSites
      
    }
  }

  private def _getChildResultSummaryIds(rsmId: Long): Array[Long] = {
    DoJDBCReturningWork.withEzDBC(execContext.getMSIDbConnectionContext) { msiEzDBC =>
      val sqlQuery = new SelectQueryBuilder1(MsiDbResultSummaryRelationTable).mkSelectQuery((t, c) =>
        List(t.CHILD_RESULT_SUMMARY_ID) -> "WHERE " ~ t.PARENT_RESULT_SUMMARY_ID ~ s" = '${rsmId}'")

      msiEzDBC.selectLongs(sqlQuery)
    }
  }

  private def _getPtmSitesObjectTreeID(rsmId: Long): Option[Long] = {
    DoJDBCReturningWork.withEzDBC(execContext.getMSIDbConnectionContext) { msiEzDBC =>
      val ptmSiteQuery = new SelectQueryBuilder1(MsiDbResultSummaryObjectTreeMapTable).mkSelectQuery((t, c) =>
        List(t.*) -> "WHERE " ~ t.RESULT_SUMMARY_ID ~ s" = '${rsmId}'" ~ " AND " ~ t.SCHEMA_NAME ~ s" = '${SchemaName.PTM_SITES.toString}'"
      )
      msiEzDBC.select(ptmSiteQuery) { r =>
        toLong(r.getAny(MsiDbResultSummaryObjectTreeMapTable.columns.OBJECT_TREE_ID))
      }
    }.headOption
  }

  private def _getPtmSites(objectTreeId: Long): Iterable[PtmSite] = {
    DoJDBCReturningWork.withEzDBC(execContext.getMSIDbConnectionContext) { msiEzDBC =>

      val ptmSiteQuery = new SelectQueryBuilder1(MsiDbObjectTreeTable).mkSelectQuery((t, c) =>
        List(t.*) -> "WHERE " ~ t.ID ~ s" = '${objectTreeId}'"
      )

      msiEzDBC.select(ptmSiteQuery) { r =>
        val clob = r.getString(MsiDbObjectTreeTable.columns.CLOB_DATA)
        val ptmSites = ProfiJson.deserialize[Iterable[PtmSite]](clob)
        ptmSites
      }.head
    }
  }
  
    private def _getPeptideMatchScores(peptideMatchIds: Array[Long]): Map[Long, Double] = {
    DoJDBCReturningWork.withEzDBC(execContext.getMSIDbConnectionContext) { msiEzDBC =>

      val peptideMatchesQuery = new SelectQueryBuilder1(MsiDbPeptideMatchTable).mkSelectQuery((t, c) =>
        List(t.ID, t.SCORE) -> "WHERE " ~ t.ID ~ " IN(" ~ peptideMatchIds.mkString(",") ~ ")"
      )

      msiEzDBC.select(peptideMatchesQuery) { r =>
        (r.getLong(MsiDbPeptideMatchTable.columns.ID) -> r.getDouble(MsiDbPeptideMatchTable.columns.SCORE))
      }.toMap
      
    }
  }

  private def _deletePtmSites(objectTreeID: Long) {
    DoJDBCWork.withEzDBC(execContext.getMSIDbConnectionContext) { msiEzDBC =>
      msiEzDBC.execute(s"DELETE FROM ${MsiDbResultSummaryObjectTreeMapTable.name} WHERE ${MsiDbResultSummaryObjectTreeMapTable.columns.OBJECT_TREE_ID} = ${objectTreeID}")
      msiEzDBC.execute(s"DELETE FROM ${MsiDbObjectTreeTable.name} WHERE ${MsiDbObjectTreeTable.columns.ID} = ${objectTreeID}")
    }
  }
}