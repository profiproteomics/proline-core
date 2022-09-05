package fr.proline.core.om.provider.msi.impl

import com.typesafe.scalalogging.LazyLogging
import fr.profi.util.collection._
import fr.proline.context._
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.msi.MsiDbResultSetTable
import fr.proline.core.om.builder.ResultSetBuilder
import fr.proline.core.om.model.msi._
import fr.proline.core.om.provider.PeptideCacheExecutionContext
import fr.proline.core.om.provider.msi.IResultSetProvider
import fr.proline.core.om.provider.msi.PeptideMatchFilter
import fr.proline.core.om.provider.msi.ResultSetFilter

import scala.collection.mutable.LongMap

trait SQLResultSetLoader extends LazyLogging {

  import fr.proline.core.dal.helper.MsiDbHelper

  protected val udsDbCtx: UdsDbConnectionContext
  protected val msiDbCtx: MsiDbConnectionContext

  val RSCols = MsiDbResultSetTable.columns

  protected def getResultSet(
    rsId: Long,
    isValidatedContent: Boolean,
    pepMatches: Array[PeptideMatch],
    protMatches: Array[ProteinMatch]
  ): ResultSet = {
    this.getResultSets(Array(rsId), isValidatedContent, pepMatches, protMatches)(0)
  }

  protected def getResultSets(
    rsIds: Seq[Long],
    isValidatedContent: Boolean,
    pepMatches: Array[PeptideMatch],
    protMatches: Array[ProteinMatch]
  ): Array[ResultSet] = {
    require(udsDbCtx != null, "udsDbCtx is null")


    // Build some maps
    val pepMatchesByRsId = pepMatches.groupByLong(_.resultSetId)
    val protMatchesByRsId = protMatches.groupByLong(_.resultSetId)

    // Instantiate a MSIdb helper
    val msiDbHelper = new MsiDbHelper(msiDbCtx)
    val msiSearchIdsByParentRsId = msiDbHelper.getMsiSearchIdsByParentResultSetId(rsIds)
    val msiSearchIds = msiSearchIdsByParentRsId.flatMap(_._2).toArray.distinct

    val msiSearchById = if (msiSearchIds.isEmpty) LongMap.empty[MSISearch]
    else {
      require(udsDbCtx != null, "An UDSdb context must be provided")
      val msiSearches = new SQLMsiSearchProvider(udsDbCtx, msiDbCtx).getMSISearches(msiSearchIds)
      msiSearches.mapByLong(_.id)
    }

    // Execute SQL query to load result sets
    val rsQuery = new SelectQueryBuilder1(MsiDbResultSetTable).mkSelectQuery((t, c) =>
      List(t.*) -> "WHERE " ~ t.ID ~ " IN(" ~ rsIds.mkString(",") ~ ")"
    )

    DoJDBCReturningWork.withEzDBC(msiDbCtx) { msiEzDBC =>

      val start = System.currentTimeMillis()

      val resultSets = msiEzDBC.select(rsQuery) { record =>
        
        val rsId = record.getLong(RSCols.ID)
        logger.info("Start building ResultSet #" + rsId)
        
        val rs = ResultSetBuilder.buildResultSet(
          record,
          isValidatedContent,
          msiSearchById,
          msiSearchIdsByParentRsId,
          protMatchesByRsId,
          pepMatchesByRsId
        )

        val nPeptides = if (rs.peptides == null) 0 else rs.peptides.length
        val nPeptMatches = if (rs.peptideMatches == null) 0 else rs.peptideMatches.length
        val nProtMatches = if (rs.proteinMatches == null) 0 else rs.proteinMatches.length

        val buff = new StringBuilder().append("Built")
        if (rs.isDecoy) buff.append(" Decoy") else buff.append(" Target")

        buff
          .append(s" ResultSet #${rsId} contains")
          .append("[")
          .append(s"${nPeptides} Peptides, ")
          .append(s"${nPeptMatches} PeptideMatches, ")
          .append(s"${nProtMatches} ProteinMatches")
          .append("] ")

        logger.info(s"${buff} built in ${ (System.currentTimeMillis() - start) } ms")

        rs
      }

      resultSets.toArray

    }

  }

}

class SQLResultSetProvider(
  val peptideCacheExecContext: PeptideCacheExecutionContext
) extends SQLResultSetLoader with IResultSetProvider {

  protected val udsDbCtx: UdsDbConnectionContext = peptideCacheExecContext.getUDSDbConnectionContext
  protected val msiDbCtx: MsiDbConnectionContext = peptideCacheExecContext.getMSIDbConnectionContext

  def getResultSets(
    rsIds: Seq[Long],
    resultSetFilter: Option[ResultSetFilter] = None
  ): Array[ResultSet] = {
    if (rsIds.isEmpty) return Array()
    
    val start = System.currentTimeMillis()
    logger.info(s"Start loading ${rsIds.length} result set(s)")

    val pepMatchFilter = resultSetFilter.map(rsf => 
      new PeptideMatchFilter(maxPrettyRank = rsf.maxPeptideMatchPrettyRank, minScore = rsf.minPeptideMatchScore)
    )

    // Load peptide matches
    val pepMatchProvider = new SQLPeptideMatchProvider(peptideCacheExecContext)
    val pepMatches = pepMatchProvider.getResultSetsPeptideMatches(rsIds, pepMatchFilter)

    // Load protein matches
    val protMatchProvider = new SQLProteinMatchProvider(msiDbCtx)
    val protMatches = protMatchProvider.getResultSetsProteinMatches(rsIds)

    val resultSets = this.getResultSets(rsIds, isValidatedContent = false, pepMatches, protMatches)
    
    logger.info(s"${rsIds.length} result sets loaded in ${ (System.currentTimeMillis() - start) / 1000 } s")

    resultSets
  }

  def getResultSetsAsOptions(
    resultSetIds: Seq[Long],
    resultSetFilter: Option[ResultSetFilter] = None
  ): Array[Option[ResultSet]] = {
    if (resultSetIds.isEmpty) return Array()
    
    val resultSets = this.getResultSets(resultSetIds, resultSetFilter)
    val resultSetById = resultSets.map { rs => rs.id -> rs }.toMap

    resultSetIds.map { resultSetById.get(_) }.toArray
  }

}
