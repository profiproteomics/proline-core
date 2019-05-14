package fr.proline.core.service.msi

import com.typesafe.scalalogging.LazyLogging
import fr.profi.util.primitives._
import fr.profi.util.serialization.ProfiJson
import fr.profi.api.service.IService
import fr.proline.context.IExecutionContext
import fr.proline.core.algo.msi.PtmSitesIdentifier
import fr.proline.core.dal._
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.msi.MsiDbObjectTreeTable
import fr.proline.core.dal.tables.msi.MsiDbPeptideMatchTable
import fr.proline.core.dal.tables.msi.MsiDbResultSummaryObjectTreeMapTable
import fr.proline.core.dal.tables.msi.MsiDbResultSummaryRelationTable
import fr.proline.core.om.model.msi.{PtmCluster, PtmDataSet, PtmSite, PtmSite2, ResultSummary}
import fr.proline.core.om.provider.PeptideCacheExecutionContext
import fr.proline.core.om.provider.msi.impl.{SQLPTMProvider, SQLProteinMatchProvider, SQLResultSummaryProvider}
import fr.proline.core.om.storer.msi.RsmStorer
import fr.proline.core.orm.msi.ObjectTreeSchema.SchemaName

import scala.collection.mutable.ArrayBuffer

case class PtmResult (
    val leafResultSummaryIds: Array[Long],
    val sites: Iterable[PtmSite]
)

object RsmPtmSitesIdentifier extends LazyLogging {

  def loadResultSummary(rsmId: Long, execContext: IExecutionContext): ResultSummary = {

    val rsmProvider = new SQLResultSummaryProvider(PeptideCacheExecutionContext(execContext) )

    val rsm = rsmProvider.getResultSummary(rsmId, loadResultSet = true)
    require(rsm.isDefined, "Unknown ResultSummary Id: " + rsmId)

    rsm.get
  }

}

class RsmPtmSitesIdentifier(
  val execContext: IExecutionContext,
  resultSummaryId: Long,
  force: Boolean) extends IService with LazyLogging {

  val msiDbContext = execContext.getMSIDbConnectionContext
  val rsmStorer = RsmStorer(msiDbContext)
  val proteinMatchProvider = new SQLProteinMatchProvider(msiDbContext)
  val ptmSitesIdentifier = new PtmSitesIdentifier()
  val schemaName = SchemaName.PTM_SITES

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
      _getOrIdentifyPtmSites(rsm, onIdentifyPtmSites)

      // Commit transaction if it was initiated locally
      if (!wasInTransaction) msiDbContext.commitTransaction()

      val took = System.currentTimeMillis() / 1000 - startTime
      this.logger.info("Ptm sites identifier service took " + took + " seconds")
      true
    }
  }

  def onIdentifyPtmSites(resultSummaryId: Long, result: PtmResult): Unit = {
    rsmStorer.storePtmSites(resultSummaryId, result.sites, execContext)
  }

  private def _getOrIdentifyPtmSites(resultSummary: ResultSummary, onPtmSites: (Long, PtmResult) => Unit): PtmResult = {

    val existingObjectTreeId = _getPtmSitesObjectTreeID(resultSummary.id)
    var ptmSites = Array.empty[PtmSite].toIterable
    
    if (existingObjectTreeId.isDefined && !force) {
      logger.info(s"Read already defined PtmSites for ResultSummary #${resultSummary.id} ")
      PtmResult(Array(resultSummary.id), _getPtmSites(existingObjectTreeId.get))
    } else {
      if (existingObjectTreeId.isDefined) {
        logger.info(s"already defined Ptm sites for ResultSummary #${resultSummary.id} will be deleted")
        _deletePtmSites(existingObjectTreeId.get)
      }

      // this resultSummary is a merged RSM
      val sites = new ArrayBuffer[Iterable[PtmSite]]
      val leafResultSummaryIds = new ArrayBuffer[Long]

      val childRSMIds = _getChildResultSummaryIds(resultSummary.id)
      
      if (!childRSMIds.isEmpty) {
        logger.debug(s"get or identify PtmSite for children of ResultSummary #${resultSummary.id}")
        for (childRSMId <- childRSMIds) {
          val rsm = RsmPtmSitesIdentifier.loadResultSummary(childRSMId, execContext)
          val result = _getOrIdentifyPtmSites(rsm, onPtmSites)
          sites += result.sites
          leafResultSummaryIds ++= result.leafResultSummaryIds
        }
        
        val proteinMatches = proteinMatchProvider.getResultSummariesProteinMatches(childRSMIds)
        // then aggregate those sites
        logger.debug(s"Aggregation of PtmSite ResultSummary #${resultSummary.id}")
        ptmSites = ptmSitesIdentifier.aggregatePtmSites(sites.toArray, proteinMatches, proteinMatchProvider.getResultSummariesProteinMatches(List(resultSummary.id)), _getPeptideMatchScores)
      } else {
        // this ResultSummary is a leaf RSM 
        logger.info(s"Identifying Ptm sites for ResultSummary #${resultSummary.id}")
        ptmSites = ptmSitesIdentifier.identifyPtmSites(resultSummary, proteinMatchProvider.getResultSummariesProteinMatches(List(resultSummary.id)))
        leafResultSummaryIds += resultSummary.id
      }

      val result = PtmResult(leafResultSummaryIds.toArray, ptmSites)
      onPtmSites(resultSummary.id, result)
      result
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
        List(t.*) -> "WHERE " ~ t.RESULT_SUMMARY_ID ~ s" = '${rsmId}'" ~ " AND " ~ t.SCHEMA_NAME ~ s" = '${schemaName.toString}'"
      )
      msiEzDBC.select(ptmSiteQuery) { r =>
        toLong(r.getAny(MsiDbResultSummaryObjectTreeMapTable.columns.OBJECT_TREE_ID))
      }
    }.headOption
  }

  def _getPtmSites(objectTreeId: Long): Iterable[PtmSite] = {
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

class RsmPtmSitesIdentifierV2(
    execContext: IExecutionContext,
    resultSummaryId: Long,
    ptmIds: Array[Long],
    clusteringConfigAsMap: Map[String, Any],
    force: Boolean) extends RsmPtmSitesIdentifier(execContext, resultSummaryId, force) {

  override val schemaName = SchemaName.PTM_DATASET
  val ptmDefinitionById = new SQLPTMProvider(msiDbContext).ptmDefinitionById

  override def onIdentifyPtmSites(resultSummaryId: Long, result: PtmResult): Unit = {

    val ptmSitesAsArray = toPtmSites2(result.sites)
    val ptmDataSet = new PtmDataSet(
                            ptmIds = ptmIds,
                            leafResultSummaryIds = result.leafResultSummaryIds,
                            ptmSites = ptmSitesAsArray,
                            ptmClusters = _clusterize(ptmSitesAsArray))

    DoJDBCWork.withEzDBC(execContext.getMSIDbConnectionContext) { msiEzDBC =>
      rsmStorer.storeObjectTree(msiEzDBC, resultSummaryId, ptmDataSet, SchemaName.PTM_DATASET.toString)
      logger.info("ptmDataSet has been stored")
    }

  }


  override def _getPtmSites(objectTreeId: Long): Iterable[PtmSite] = {
    DoJDBCReturningWork.withEzDBC(execContext.getMSIDbConnectionContext) { msiEzDBC =>

      val ptmSiteQuery = new SelectQueryBuilder1(MsiDbObjectTreeTable).mkSelectQuery((t, c) =>
        List(t.*) -> "WHERE " ~ t.ID ~ s" = '${objectTreeId}'"
      )

      // TODO the following transformation from PtmSite V1 to V2 is made for compatibility only, it must be removed
      msiEzDBC.select(ptmSiteQuery) { r =>
        val clob = r.getString(MsiDbObjectTreeTable.columns.CLOB_DATA)
        val ptmDataset = ProfiJson.deserialize[PtmDataSet](clob)
        ptmDataset
      }.head.ptmSites.map( s =>
        new PtmSite(
          proteinMatchId = s.proteinMatchId,
          ptmDefinitionId = s.ptmDefinitionId,
          seqPosition = s.seqPosition,
          bestPeptideMatchId = s.bestPeptideMatchId,
          localizationConfidence = s.localizationConfidence,
          peptideIdsByPtmPosition = s.peptideIdsByPtmPosition,
          peptideInstanceIds = Array.empty[Long],
          isomericPeptideInstanceIds = s.isomericPeptideIds
        )
      )
    }
  }

  def toPtmSites2(ptmSites: Iterable[PtmSite]) : Array[PtmSite2] = {
  //TODO : need to convert isomericPeptideInstanceIds to isomericPeptideIds
    ptmSites.zipWithIndex.map{ case(site, index) =>
      new PtmSite2(
        id = index,
        proteinMatchId = site.proteinMatchId,
        ptmDefinitionId = site.ptmDefinitionId,
        seqPosition = site.seqPosition,
        bestPeptideMatchId = site.bestPeptideMatchId,
        localizationConfidence = site.localizationConfidence,
        peptideIdsByPtmPosition = site.peptideIdsByPtmPosition,
        isomericPeptideIds = site.isomericPeptideInstanceIds) }.toArray
  }

  def _clusterize(sites: Array[PtmSite2]): Array[PtmCluster] = {
    // TODO : dummy implementation for test only: 1 ptmsite = 1 cluster
    sites.filter{ s => ptmIds.contains(ptmDefinitionById(s.ptmDefinitionId).ptmId) }.map{ s =>
      new PtmCluster(
        ptmSiteLocations = Array(s.id),
        bestPeptideMatchId = s.bestPeptideMatchId,
        localizationConfidence = s.localizationConfidence,
        peptideIds = s.peptideIdsByPtmPosition.flatMap(_._2).toArray.distinct,
        isomericPeptideIds = Array.empty[Long]) }
  }
}
