package fr.proline.core.om.provider.msi.impl

import com.typesafe.scalalogging.slf4j.Logging

import scala.collection.mutable.ArrayBuffer

import fr.profi.util.serialization.ProfiJson
import fr.profi.util.misc.MapIfNotNull
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.tables.msi.MsiDbResultSetTable
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.om.model.msi.{ ProteinMatch, PeptideMatch, ResultSet, ResultSetProperties }
import fr.proline.core.om.provider.msi.{ IResultSetProvider, PeptideMatchFilter, ResultSetFilter }

trait SQLResultSetLoader extends Logging {

  import fr.proline.core.dal.helper.MsiDbHelper

  val msiDbCtx: DatabaseConnectionContext
  val psDbCtx: DatabaseConnectionContext
  val udsDbCtx: DatabaseConnectionContext

  val RSCols = MsiDbResultSetTable.columns

  protected def getResultSet(rsId: Long,
                             pepMatches: Array[PeptideMatch],
                             protMatches: Array[ProteinMatch]): ResultSet = {
    this.getResultSets(Array(rsId), pepMatches, protMatches)(0)
  }

  protected def getResultSets(
    rsIds: Seq[Long],
    pepMatches: Array[PeptideMatch],
    protMatches: Array[ProteinMatch]): Array[ResultSet] = {

    import fr.profi.util.primitives._

    // Build some maps
    val pepMatchesByRsId = pepMatches.groupBy(_.resultSetId)
    val protMatchesByRsId = protMatches.groupBy(_.resultSetId)

    // Instantiate a MSIdb helper
    val msiDbHelper = new MsiDbHelper(msiDbCtx)
    val msiSearchIdsByRsId = msiDbHelper.getMsiSearchIdsByParentResultSetId(rsIds)
    val msiSearchIds = msiSearchIdsByRsId.flatMap(_._2).toArray.distinct

    var msiSearchById = Map.empty[Long, fr.proline.core.om.model.msi.MSISearch]
    if (udsDbCtx != null && msiSearchIds != null && !msiSearchIds.isEmpty) {
      val msiSearches = new SQLMsiSearchProvider(udsDbCtx, msiDbCtx, psDbCtx).getMSISearches(msiSearchIds)
      msiSearchById = Map() ++ msiSearches.map(ms => ms.id -> ms)
    }

    // Execute SQL query to load result sets
    val rsQuery = new SelectQueryBuilder1(MsiDbResultSetTable).mkSelectQuery((t, c) =>
      List(t.*) -> "WHERE " ~ t.ID ~ " IN(" ~ rsIds.mkString(",") ~ ")"
    )

    DoJDBCReturningWork.withEzDBC(msiDbCtx, { msiEzDBC =>

      val start = System.currentTimeMillis()

      val resultSets = msiEzDBC.select(rsQuery) { r =>

        // Retrieve some vars
        val rsId: Long = toLong(r.getAny(RSCols.ID))
        logger.info("Start loading ResultSet #" + rsId)

        /*if( !protMatchesByRsId.contains(rsId) ) {
          throw new Exception("this result set doesn't have any protein match and can't be loaded")
        }*/

        val rsProtMatches = protMatchesByRsId.getOrElse(rsId, Array.empty[ProteinMatch])
        val rsPepMatches = pepMatchesByRsId.getOrElse(rsId, Array.empty[PeptideMatch])
        val rsPeptides = rsPepMatches map { _.peptide } distinct
        val rsType = r.getString(RSCols.TYPE)
        val isDecoy = rsType matches "DECOY_.*"
        val isNative = rsType matches ".*SEARCH"
        val isQuantified = rsType matches "QUANTITATION"
        val decoyRsId = r.getLongOrElse(RSCols.DECOY_RESULT_SET_ID, 0L)

        val rsMsiSearchId = if (isNative) {
          r.getLongOrElse(RSCols.MSI_SEARCH_ID, 0L)
        } else if (msiSearchIdsByRsId.contains(rsId)) {
          // FIXME: we should attach all MSI searches to the result set ???
          msiSearchIdsByRsId(rsId).head
        } else 0

        val msiSearch = msiSearchById.getOrElse(rsMsiSearchId, null)

        // Decode JSON properties
        val propertiesAsJSON = r.getString(RSCols.SERIALIZED_PROPERTIES)
        val properties = MapIfNotNull(propertiesAsJSON) { ProfiJson.deserialize[ResultSetProperties](_) }

        val rs = new ResultSet(
          id = rsId,
          name = r.getString(RSCols.NAME),
          description = r.getString(RSCols.DESCRIPTION),
          peptides = rsPeptides,
          peptideMatches = rsPepMatches,
          proteinMatches = rsProtMatches,
          isDecoy = isDecoy,
          isNative = isNative,
          isQuantified = isQuantified,
          msiSearch = Some(msiSearch),
          decoyResultSetId = decoyRsId,
          properties = properties
        )

        val nPeptides = if (rsPeptides == null) 0 else rsPeptides.length
        val nPeptMatches = if (rsPepMatches == null) 0 else rsPepMatches.length
        val nProtMatches = if (rsProtMatches == null) 0 else rsProtMatches.length

        val buff = new StringBuilder()
        buff.append("Loading")

        if (isDecoy) buff.append(" Decoy") else buff.append(" Target")

        buff.append(" ResultSet #").append(rsId)

        buff.append(" [")
        buff.append(nPeptides).append(" Peptides, ")
        buff.append(nPeptMatches).append(" PeptideMatches, ")
        buff.append(nProtMatches).append(" ProteinMatches] ")

        logger.info(buff.toString + " loaded in " + (System.currentTimeMillis() - start) + " ms")

        rs
      }

      resultSets.toArray

    })

  }

}

class SQLResultSetProvider(
  val msiDbCtx: DatabaseConnectionContext,
  val psDbCtx: DatabaseConnectionContext,
  val udsDbCtx: DatabaseConnectionContext) extends SQLResultSetLoader with IResultSetProvider {

  def getResultSets(rsIds: Seq[Long], resultSetFilter: Option[ResultSetFilter] = None): Array[ResultSet] = {

    val pepMatchFilter = resultSetFilter.map(rsf => new PeptideMatchFilter(maxRank = rsf.maxPeptideMatchRank))

    // Load peptide matches
    val pepMatchProvider = new SQLPeptideMatchProvider(msiDbCtx, psDbCtx)
    val pepMatches = pepMatchProvider.getResultSetsPeptideMatches(rsIds, pepMatchFilter)

    // Load protein matches
    val protMatchProvider = new SQLProteinMatchProvider(msiDbCtx)
    val protMatches = protMatchProvider.getResultSetsProteinMatches(rsIds)

    this.getResultSets(rsIds, pepMatches, protMatches)

  }

  def getResultSetsAsOptions(resultSetIds: Seq[Long], resultSetFilter: Option[ResultSetFilter] = None): Array[Option[ResultSet]] = {

    val resultSets = this.getResultSets(resultSetIds, resultSetFilter)
    val resultSetById = resultSets.map { rs => rs.id -> rs } toMap

    resultSetIds.map { resultSetById.get(_) } toArray

  }

}
