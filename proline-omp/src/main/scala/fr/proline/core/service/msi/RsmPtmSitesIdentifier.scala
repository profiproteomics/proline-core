package fr.proline.core.service.msi

import java.util.concurrent.atomic.AtomicLong
import com.typesafe.scalalogging.LazyLogging
import fr.profi.util.primitives._
import fr.profi.util.serialization.ProfiJson
import fr.profi.api.service.IService
import fr.profi.util.misc.InMemoryIdGen
import fr.proline.context.{DatabaseConnectionContext, IExecutionContext, MsiDbConnectionContext}
import fr.proline.core.algo.msi.{PtmSiteClusterer, PtmSitesIdentifier, PtmSitesIdentifierV2}
import fr.proline.core.dal._
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.msi.MsiDbObjectTreeTable
import fr.proline.core.dal.tables.msi.MsiDbResultSummaryObjectTreeMapTable
import fr.proline.core.dal.tables.msi.MsiDbResultSummaryRelationTable
import fr.proline.core.om.model.msi.{PeptideMatch, PtmCluster, PtmDataSet, PtmDefinition, PtmSite, PtmSite2, ResultSummary}
import fr.proline.core.om.provider.PeptideCacheExecutionContext
import fr.proline.core.om.provider.msi.impl.{SQLPTMProvider, SQLPeptideInstanceProvider, SQLPeptideMatchProvider, SQLPeptideProvider, SQLProteinMatchProvider, SQLResultSummaryProvider}
import fr.proline.core.om.storer.msi.RsmStorer
import fr.proline.core.orm.msi.ObjectTreeSchema.SchemaName

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

case class PtmResult (
   resultSummary: ResultSummary,
   leafResultSummaryIds: Array[Long],
   sites: Iterable[PtmSite]
)

case class PtmResultV2 (
   resultSummary: ResultSummary,
   leafResultSummaryIds: Array[Long],
   sites: Iterable[PtmSite2]
 )

object RsmPtmSitesIdentifier extends LazyLogging {

  def loadResultSummary(rsmId: Long, execContext: IExecutionContext): ResultSummary = {

    val rsmProvider = new SQLResultSummaryProvider(PeptideCacheExecutionContext(execContext) )

    val rsm = rsmProvider.getResultSummary(rsmId, loadResultSet = true)
    require(rsm.isDefined, "Unknown ResultSummary Id: " + rsmId)

    rsm.get
  }

  def getPtmSitesObjectTreeID(rsmId: Long, schemaName: SchemaName, dbConnectionContext: DatabaseConnectionContext): Option[Long] = {
    DoJDBCReturningWork.withEzDBC(dbConnectionContext) { msiEzDBC =>
      val ptmSiteQuery = new SelectQueryBuilder1(MsiDbResultSummaryObjectTreeMapTable).mkSelectQuery((t, c) =>
        List(t.*) -> "WHERE " ~ t.RESULT_SUMMARY_ID ~ s" = '$rsmId'" ~ " AND " ~ t.SCHEMA_NAME ~ s" = '${schemaName.toString}'"
      )
      msiEzDBC.select(ptmSiteQuery) { r =>
        toLong(r.getAny(MsiDbResultSummaryObjectTreeMapTable.columns.OBJECT_TREE_ID))
      }
    }.headOption
  }

  def deleteRsmObjectTree(objectTreeID: Long, dbConnectionContext: DatabaseConnectionContext): Unit = {
    DoJDBCWork.withEzDBC(dbConnectionContext) { msiEzDBC =>
      msiEzDBC.execute(s"DELETE FROM ${MsiDbResultSummaryObjectTreeMapTable.name} WHERE ${MsiDbResultSummaryObjectTreeMapTable.columns.OBJECT_TREE_ID} = $objectTreeID")
      msiEzDBC.execute(s"DELETE FROM ${MsiDbObjectTreeTable.name} WHERE ${MsiDbObjectTreeTable.columns.ID} = $objectTreeID")
    }
  }

  def getChildResultSummaryIds(rsmId: Long, dbConnectionContext: DatabaseConnectionContext): Array[Long] = {
    DoJDBCReturningWork.withEzDBC(dbConnectionContext) { msiEzDBC =>
      val sqlQuery = new SelectQueryBuilder1(MsiDbResultSummaryRelationTable).mkSelectQuery((t, c) =>
        List(t.CHILD_RESULT_SUMMARY_ID) -> "WHERE " ~ t.PARENT_RESULT_SUMMARY_ID ~ s" = '$rsmId'")

      msiEzDBC.selectLongs(sqlQuery)
    }
  }


}

class RsmPtmSitesIdentifier(
  val execContext: IExecutionContext,
  resultSummaryId: Long,
  force: Boolean) extends IService with LazyLogging {

  val msiDbContext: MsiDbConnectionContext = execContext.getMSIDbConnectionContext
  val rsmStorer: RsmStorer = RsmStorer(msiDbContext)
  val proteinMatchProvider = new SQLProteinMatchProvider(msiDbContext)
  val peptideProvider = new SQLPeptideProvider(PeptideCacheExecutionContext(execContext))
  val peptideInstanceProvider = new SQLPeptideInstanceProvider(msiDbContext, peptideProvider)
  val peptideMatchProvider = new SQLPeptideMatchProvider(msiDbContext, peptideProvider)

  val schemaName = SchemaName.PTM_SITES
  val annotatedSchemaName: SchemaName = null

  def runService(): Boolean = {

    logger.info("PtmSites identifier service starts")

    val startTime = System.currentTimeMillis() / 1000

   val existingObjectTreeId = RsmPtmSitesIdentifier.getPtmSitesObjectTreeID(resultSummaryId, schemaName, msiDbContext )

    // Check if a transaction is already initiated
    val wasInTransaction = msiDbContext.isInTransaction
    if (!wasInTransaction) msiDbContext.beginTransaction()

    // Avoid loadResultSummary if PTMSites already defined and option force is not set
    if (existingObjectTreeId.isDefined && !force) {
      logger.info("PtmSites already defined for this ResultSummary. Please use force parameter to force re-identification")
      false
    } else {
      if (existingObjectTreeId.isDefined) {
        logger.info("already defined Ptm sites data for this ResultSummary will be deleted")
        RsmPtmSitesIdentifier.deleteRsmObjectTree(existingObjectTreeId.get, msiDbContext)
        val annotatedObjTreeId = RsmPtmSitesIdentifier.getPtmSitesObjectTreeID(resultSummaryId, annotatedSchemaName, msiDbContext)
        if(annotatedObjTreeId.isDefined) {
          logger.info("already defined annotated Ptm sites data for this ResultSummary will be deleted")
          RsmPtmSitesIdentifier.deleteRsmObjectTree(annotatedObjTreeId.get, msiDbContext)
        }
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

  def onIdentifyPtmSites(identifier: PtmSitesIdentifier, result: PtmResult): Unit = {
    rsmStorer.storePtmSites(identifier.resultSummary.id, result.sites, execContext)
  }

  private def _getOrIdentifyPtmSites(resultSummary: ResultSummary, onPtmSites: (PtmSitesIdentifier, PtmResult) => Unit): PtmResult = {

    val existingObjectTreeId = RsmPtmSitesIdentifier.getPtmSitesObjectTreeID(resultSummary.id, schemaName, msiDbContext)
    var ptmSites = Array.empty[PtmSite].toIterable

    if (existingObjectTreeId.isDefined && !force) {
      logger.info(s"Read already defined PtmSites for ResultSummary #${resultSummary.id} ")
      PtmResult(resultSummary, Array(resultSummary.id), _getPtmSites(existingObjectTreeId.get))
    } else {
      if (existingObjectTreeId.isDefined) {
        logger.info(s"already defined Ptm sites data for ResultSummary #${resultSummary.id} will be deleted")
        RsmPtmSitesIdentifier.deleteRsmObjectTree(existingObjectTreeId.get, msiDbContext)
        val annotatedObjTreeId = RsmPtmSitesIdentifier.getPtmSitesObjectTreeID(resultSummary.id, annotatedSchemaName, msiDbContext)
        if(annotatedObjTreeId.isDefined) {
          logger.info(s"already defined annotated Ptm sites data for for ResultSummary #${resultSummary.id} will be deleted")
          RsmPtmSitesIdentifier.deleteRsmObjectTree(annotatedObjTreeId.get, msiDbContext)
        }
      }

      // this resultSummary is a merged RSM
      val sites = new ArrayBuffer[Iterable[PtmSite]]
      val leafResultSummaryIds = new ArrayBuffer[Long]

      val childRSMIds = RsmPtmSitesIdentifier.getChildResultSummaryIds(resultSummary.id, msiDbContext)

      //TODO: proteinMatchProvider.getResultSummariesProteinMatches(List(resultSummary.id)) must be replaced to filter validated proteinMatches from RSM
      val ptmSitesIdentifier = new PtmSitesIdentifier(resultSummary, proteinMatchProvider.getResultSummariesProteinMatches(List(resultSummary.id)))

      if (!childRSMIds.isEmpty) {
        logger.debug(s"get or identify PtmSite for children of ResultSummary #${resultSummary.id}")
        for (childRSMId <- childRSMIds) {
          val rsm = RsmPtmSitesIdentifier.loadResultSummary(childRSMId, execContext)
          val result = _getOrIdentifyPtmSites(rsm, onPtmSites)
          sites += result.sites
          leafResultSummaryIds ++= result.leafResultSummaryIds
        }

        //TODO: proteinMatchProvider.getResultSummariesProteinMatches(List(resultSummary.id)) must be replaced to filter validated proteinMatches from child RSM
        val proteinMatches = proteinMatchProvider.getResultSummariesProteinMatches(childRSMIds)
        // then aggregate those sites
        logger.debug(s"Aggregation of PtmSite ResultSummary #${resultSummary.id}")
        ptmSites = ptmSitesIdentifier.aggregatePtmSites(sites.toArray, proteinMatches, _getPeptideMatches)
      } else {
        // this ResultSummary is a leaf RSM 
        logger.info(s"Identifying Ptm sites for ResultSummary #${resultSummary.id}")
        ptmSites = ptmSitesIdentifier.identifyPtmSites()
        leafResultSummaryIds += resultSummary.id
      }

      val result = PtmResult(resultSummary, leafResultSummaryIds.toArray, ptmSites)
      onPtmSites(ptmSitesIdentifier, result)
      result
    }
  }


  def _getPtmSites(objectTreeId: Long): Iterable[PtmSite] = {
    DoJDBCReturningWork.withEzDBC(execContext.getMSIDbConnectionContext) { msiEzDBC =>

      val ptmSiteQuery = new SelectQueryBuilder1(MsiDbObjectTreeTable).mkSelectQuery((t, c) =>
        List(t.*) -> "WHERE " ~ t.ID ~ s" = '$objectTreeId'"
      )

      msiEzDBC.select(ptmSiteQuery) { r =>
        val clob = r.getString(MsiDbObjectTreeTable.columns.CLOB_DATA)
        val ptmSites = ProfiJson.deserialize[Iterable[PtmSite]](clob)
        ptmSites
      }.head
    }
  }
  
    private def _getPeptideMatches(peptideMatchIds: Array[Long]): Map[Long, PeptideMatch] = {
    //TODO: allow PeptideMatch pre-loading
      peptideMatchProvider.getPeptideMatches(peptideMatchIds).map( pm => pm.id -> pm).toMap
  }

}

object RsmPtmSitesIdentifierV2 {

  def toPtmSites2(ptmSites: Iterable[PtmSite]) : Array[PtmSite2] = {
    //TODO : need to convert isomericPeptideInstanceIds to isomericPeptideIds
    ptmSites.zipWithIndex.map{ case(site, index) =>
      PtmSite2(
        id = index,
        proteinMatchId = site.proteinMatchId,
        ptmDefinitionId = site.ptmDefinitionId,
        seqPosition = site.seqPosition,
        bestPeptideMatchId = site.bestPeptideMatchId,
        localizationConfidence = site.localizationConfidence,
        peptideIdsByPtmPosition = site.peptideIdsByPtmPosition,
        isomericPeptideIds = site.isomericPeptideInstanceIds) }.toArray
  }

}

@deprecated (message = "User RsmPtmSitesIdentifierV3 instead", since = "Version 2.3")
class RsmPtmSitesIdentifierV2(
    execContext: IExecutionContext,
    resultSummaryId: Long,
    ptmIds: Array[Long],
    clusteringConfigAsMap: Map[String, Any],
    force: Boolean) extends RsmPtmSitesIdentifier(execContext, resultSummaryId, force) with InMemoryIdGen {

  override val schemaName = SchemaName.PTM_DATASET
  override val annotatedSchemaName = SchemaName.PTM_DATASET_ANNOTATED
  val ptmDefinitionById: mutable.LongMap[PtmDefinition] = new SQLPTMProvider(msiDbContext).ptmDefinitionById

  override def onIdentifyPtmSites(identifier: PtmSitesIdentifier, result: PtmResult): Unit = {

    val ptmSitesAsArray = RsmPtmSitesIdentifierV2.toPtmSites2(result.sites)
    val ptmDataSet = PtmDataSet(
                            ptmIds = ptmIds,
                            leafResultSummaryIds = result.leafResultSummaryIds,
                            ptmSites = ptmSitesAsArray,
                            ptmClusters = _clusterize(ptmSitesAsArray, result))

    DoJDBCWork.withEzDBC(execContext.getMSIDbConnectionContext) { msiEzDBC =>
      rsmStorer.storeObjectTree(msiEzDBC, identifier.resultSummary.id, ptmDataSet, SchemaName.PTM_DATASET.toString)
      logger.info("ptmDataSet has been stored")
    }

  }

  override def _getPtmSites(objectTreeId: Long): Iterable[PtmSite] = {
    DoJDBCReturningWork.withEzDBC(execContext.getMSIDbConnectionContext) { msiEzDBC =>

      val ptmSiteQuery = new SelectQueryBuilder1(MsiDbObjectTreeTable).mkSelectQuery((t, c) =>
        List(t.*) -> "WHERE " ~ t.ID ~ s" = '$objectTreeId'"
      )

      // TODO the following transformation from PtmSite V1 to V2 is made for compatibility only, it must be removed
      msiEzDBC.select(ptmSiteQuery) { r =>
        val clob = r.getString(MsiDbObjectTreeTable.columns.CLOB_DATA)
        val ptmDataset = ProfiJson.deserialize[PtmDataSet](clob)
        ptmDataset
      }.head.ptmSites.map( s =>
        PtmSite(
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


  def _clusterize(sites: Array[PtmSite2], result: PtmResult): Array[PtmCluster] = {

    val pepInstances = peptideInstanceProvider.getResultSummariesPeptideInstances(result.leafResultSummaryIds)
    val peptideMatchIds = pepInstances.flatMap(pi => pi.peptideMatchIds)
    val peptideMatches = peptideMatchProvider.getPeptideMatches(peptideMatchIds)

    val peptideMatchesByPeptideIds = peptideMatches.groupBy(_.peptideId)

    def _getPeptideMatchesByPeptideIds(peptideIds: Array[Long]): Map[Long, PeptideMatch] = {
      val matches = peptideIds.map(peptideMatchesByPeptideIds(_)).flatten.map(pm => pm.id -> pm).toMap
      matches
    }

    val proteinMatches = proteinMatchProvider.getResultSummariesProteinMatches(List(result.resultSummary.id))
    val clusterizer = PtmSiteClusterer(clusteringConfigAsMap("method_name").toString,result.resultSummary, proteinMatches)
    val sitesByProteinMatchIds = sites.filter { s => ptmIds.contains(ptmDefinitionById(s.ptmDefinitionId).ptmId) }.groupBy(_.proteinMatchId)

    val clusters = sitesByProteinMatchIds.flatMap{ case (protMatchId, sites) => clusterizer.clusterize(protMatchId, sites, _getPeptideMatchesByPeptideIds, this) }
    clusters.toArray
  }

  private val inMemoryIdSequence = new AtomicLong(0)
  override def generateNewId(): Long = { inMemoryIdSequence.incrementAndGet() }
}

class RsmPtmSitesIdentifierV3( val execContext: IExecutionContext,
                               resultSummaryId: Long,
                               ptmIds: Array[Long],
                               clusteringConfigAsMap: Map[String, Any],
                               force: Boolean) extends IService with LazyLogging with InMemoryIdGen {


  val msiDbContext: MsiDbConnectionContext = execContext.getMSIDbConnectionContext
  val rsmStorer: RsmStorer = RsmStorer(msiDbContext)
  val proteinMatchProvider = new SQLProteinMatchProvider(msiDbContext)
  val peptideProvider = new SQLPeptideProvider(PeptideCacheExecutionContext(execContext))
  val peptideInstanceProvider = new SQLPeptideInstanceProvider(msiDbContext, peptideProvider)
  val peptideMatchProvider = new SQLPeptideMatchProvider(msiDbContext, peptideProvider)

  val schemaName = SchemaName.PTM_DATASET
  val annotatedSchemaName: SchemaName = SchemaName.PTM_DATASET_ANNOTATED
  val ptmDefinitionById: mutable.LongMap[PtmDefinition] = new SQLPTMProvider(msiDbContext).ptmDefinitionById

  def runService(): Boolean = {

    logger.info("PtmSites identifier V3 service starts")

    val startTime = System.currentTimeMillis() / 1000

    //    val existingObjectTreeId = _getPtmSitesObjectTreeID(resultSummaryId)
    val existingObjectTreeId = RsmPtmSitesIdentifier.getPtmSitesObjectTreeID(resultSummaryId, schemaName, msiDbContext )

    // Check if a transaction is already initiated
    val wasInTransaction = msiDbContext.isInTransaction
    if (!wasInTransaction) msiDbContext.beginTransaction()

    // Avoid loadResultSummary if PTMSites already defined and option force is not set
    if (existingObjectTreeId.isDefined && !force) {
      logger.info("PtmSites already defined for this ResultSummary. Please use force parameter to force re-identification")
      false
    } else {
      if (existingObjectTreeId.isDefined) {
        logger.info("already defined Ptm sites data for this ResultSummary will be deleted")
        RsmPtmSitesIdentifier.deleteRsmObjectTree(existingObjectTreeId.get, msiDbContext)
        val annotatedObjTreeId = RsmPtmSitesIdentifier.getPtmSitesObjectTreeID(resultSummaryId, annotatedSchemaName, msiDbContext)
        if(annotatedObjTreeId.isDefined) {
          logger.info("already defined annotated Ptm sites data for this ResultSummary will be deleted")
          RsmPtmSitesIdentifier.deleteRsmObjectTree(annotatedObjTreeId.get, msiDbContext)
        }
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

  def onIdentifyPtmSites(identifier: PtmSitesIdentifierV2, result: PtmResultV2): Unit = {
    val ptmDataSet = PtmDataSet(
      version  ="2.0",
      ptmIds = ptmIds,
      leafResultSummaryIds = result.leafResultSummaryIds,
      ptmSites = result.sites.toArray,
      ptmClusters = _clusterize(result.sites.toArray, result))

    DoJDBCWork.withEzDBC(execContext.getMSIDbConnectionContext) { msiEzDBC =>
      rsmStorer.storeObjectTree(msiEzDBC, identifier.resultSummary.id, ptmDataSet, SchemaName.PTM_DATASET.toString)
      logger.info("ptmDataSet has been stored")
    }
  }

  def _clusterize(sites: Array[PtmSite2], result: PtmResultV2): Array[PtmCluster] = {

    val pepInstances = peptideInstanceProvider.getResultSummariesPeptideInstances(result.leafResultSummaryIds)
    val peptideMatchIds = pepInstances.flatMap(pi => pi.peptideMatchIds)
    val peptideMatches = peptideMatchProvider.getPeptideMatches(peptideMatchIds)

    val peptideMatchesByPeptideIds = peptideMatches.groupBy(_.peptideId)

    def _getPeptideMatchesByPeptideIds(peptideIds: Array[Long]): Map[Long, PeptideMatch] = {
      val matches = peptideIds.map(peptideMatchesByPeptideIds(_)).flatten.map(pm => pm.id -> pm).toMap
      matches
    }

    val proteinMatches = proteinMatchProvider.getResultSummariesProteinMatches(List(result.resultSummary.id))
    val clusterizer = PtmSiteClusterer(clusteringConfigAsMap("method_name").toString,result.resultSummary, proteinMatches)
    val sitesByProteinMatchIds = sites.filter { s => ptmIds.contains(ptmDefinitionById(s.ptmDefinitionId).ptmId) }.groupBy(_.proteinMatchId)

    val clusters = sitesByProteinMatchIds.flatMap{ case (protMatchId, sites) => clusterizer.clusterize(protMatchId, sites, _getPeptideMatchesByPeptideIds, this) }
    clusters.toArray
  }

  private def _getOrIdentifyPtmSites(resultSummary: ResultSummary, onPtmSites: (PtmSitesIdentifierV2, PtmResultV2) => Unit): PtmResultV2 = {

    val existingObjectTreeId = RsmPtmSitesIdentifier.getPtmSitesObjectTreeID(resultSummary.id, schemaName, msiDbContext)
    var ptmSites = Array.empty[PtmSite2].toIterable

    if (existingObjectTreeId.isDefined && !force) {
      logger.info(s"Read already defined PtmSites for ResultSummary #${resultSummary.id} ")
      PtmResultV2(resultSummary, Array(resultSummary.id), _getPtmSites(existingObjectTreeId.get))
    } else {
      if (existingObjectTreeId.isDefined) {
        logger.info(s"already defined Ptm sites data for ResultSummary #${resultSummary.id} will be deleted")
        RsmPtmSitesIdentifier.deleteRsmObjectTree(existingObjectTreeId.get, msiDbContext)
        val annotatedObjTreeId = RsmPtmSitesIdentifier.getPtmSitesObjectTreeID(resultSummary.id, annotatedSchemaName, msiDbContext)
        if(annotatedObjTreeId.isDefined) {
          logger.info(s"already defined annotated Ptm sites data for for ResultSummary #${resultSummary.id} will be deleted")
          RsmPtmSitesIdentifier.deleteRsmObjectTree(annotatedObjTreeId.get, msiDbContext)
        }
      }

      // this resultSummary is a merged RSM
      val sites = new ArrayBuffer[Iterable[PtmSite2]]
      val leafResultSummaryIds = new ArrayBuffer[Long]

      val childRSMIds = RsmPtmSitesIdentifier.getChildResultSummaryIds(resultSummary.id, msiDbContext)

      //TODO: proteinMatchProvider.getResultSummariesProteinMatches(List(resultSummary.id)) must be replaced to filter validated proteinMatches from RSM
      val ptmSitesIdentifier = new PtmSitesIdentifierV2(resultSummary, proteinMatchProvider.getResultSummariesProteinMatches(List(resultSummary.id)))

      if (!childRSMIds.isEmpty) {
        logger.debug(s"get or identify PtmSite for children of ResultSummary #${resultSummary.id}")
        for (childRSMId <- childRSMIds) {
          val rsm = RsmPtmSitesIdentifier.loadResultSummary(childRSMId, execContext)
          val result = _getOrIdentifyPtmSites(rsm, onPtmSites)
          sites += result.sites
          leafResultSummaryIds ++= result.leafResultSummaryIds
        }

        //TODO: proteinMatchProvider.getResultSummariesProteinMatches(List(resultSummary.id)) must be replaced to filter validated proteinMatches from child RSM
        val proteinMatches = proteinMatchProvider.getResultSummariesProteinMatches(childRSMIds)
        // then aggregate those sites
        logger.debug(s"Aggregation of PtmSite ResultSummary #${resultSummary.id}")
        ptmSites = ptmSitesIdentifier.aggregatePtmSite2s(sites.toArray, proteinMatches, _getPeptideMatches)
      } else {
        // this ResultSummary is a leaf RSM
        logger.info(s"Identifying Ptm sites for ResultSummary #${resultSummary.id}")
        ptmSites = ptmSitesIdentifier.identifyPtmSite2s()
        leafResultSummaryIds += resultSummary.id
      }

      val result = PtmResultV2(resultSummary, leafResultSummaryIds.toArray, ptmSites)
      onPtmSites(ptmSitesIdentifier, result)
      result
    }
  }

  def _getPtmSites(objectTreeId: Long): Iterable[PtmSite2] = {
    DoJDBCReturningWork.withEzDBC(execContext.getMSIDbConnectionContext) { msiEzDBC =>

      val ptmSiteQuery = new SelectQueryBuilder1(MsiDbObjectTreeTable).mkSelectQuery((t, c) =>
        List(t.*) -> "WHERE " ~ t.ID ~ s" = '$objectTreeId'"
      )

      msiEzDBC.select(ptmSiteQuery) { r =>
        val clob = r.getString(MsiDbObjectTreeTable.columns.CLOB_DATA)
        val ptmDataset = ProfiJson.deserialize[PtmDataSet](clob)
        ptmDataset
      }.head.ptmSites
    }
  }

  private def _getPeptideMatches(peptideMatchIds: Array[Long]): Map[Long, PeptideMatch] = {
    //TODO: allow PeptideMatch pre-loading
    peptideMatchProvider.getPeptideMatches(peptideMatchIds).map( pm => pm.id -> pm).toMap
  }

  private val inMemoryIdSequence = new AtomicLong(0)
  override def generateNewId(): Long = { inMemoryIdSequence.incrementAndGet() }

}
