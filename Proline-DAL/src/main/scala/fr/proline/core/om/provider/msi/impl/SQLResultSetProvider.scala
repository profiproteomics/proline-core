package fr.proline.core.om.provider.msi.impl

import scala.collection.mutable.ArrayBuffer

import fr.proline.core.dal.tables.msi.MsiDbResultSetTable
import fr.proline.core.om.model.msi.{ ProteinMatch, PeptideMatch, ResultSet }
import fr.proline.core.om.provider.msi.IResultSetProvider
import fr.proline.context.DatabaseConnectionContext
import fr.profi.jdbc.easy.EasyDBC
import fr.proline.core.dal.SQLConnectionContext

trait SQLResultSetLoader {

  import fr.proline.core.dal.helper.MsiDbHelper

  val msiDbCtx: SQLConnectionContext
  val psDbCtx: SQLConnectionContext
  val udsDbCtx: SQLConnectionContext

  val RSCols = MsiDbResultSetTable.columns

  protected def getResultSet(rsId: Int,
    pepMatches: Array[PeptideMatch],
    protMatches: Array[ProteinMatch]): ResultSet = {
    this.getResultSets(Array(rsId), pepMatches, protMatches)(0)
  }

  protected def getResultSets(rsIds: Seq[Int],
    pepMatches: Array[PeptideMatch],
    protMatches: Array[ProteinMatch]): Array[ResultSet] = {

    import fr.proline.util.primitives.LongOrIntAsInt._

    // Build some maps
    val pepMatchesByRsId = pepMatches.groupBy(_.resultSetId)
    val protMatchesByRsId = protMatches.groupBy(_.resultSetId)

    // Instantiate a MSIdb helper
    val msiDbHelper = new MsiDbHelper(msiDbCtx.ezDBC)
    val msiSearchIdsByRsId = msiDbHelper.getMsiSearchIdsByParentResultSetId(rsIds)
    val msiSearchIds = msiSearchIdsByRsId.flatMap(_._2).toArray.distinct

    var msiSearchById: Map[Int, fr.proline.core.om.model.msi.MSISearch] = Map()
    if (udsDbCtx != null) {
      val msiSearches = new SQLMsiSearchProvider(udsDbCtx, msiDbCtx, psDbCtx).getMSISearches(msiSearchIds)
      msiSearchById = Map() ++ msiSearches.map(ms => ms.id -> ms)
    }

    // Execute SQL query to load result sets
    var rsColNames: Seq[String] = null
    val resultSets = msiDbCtx.ezDBC.select("SELECT * FROM result_set WHERE id IN (" + rsIds.mkString(",") + ")") { r =>

      if (rsColNames == null) { rsColNames = r.columnNames }
      val resultSetRecord = rsColNames.map(colName => (colName -> r.nextAnyRefOrElse(null))).toMap

      // Retrieve some vars
      val rsId: Int = resultSetRecord(RSCols.ID).asInstanceOf[AnyVal]
      val rsProtMatches = protMatchesByRsId(rsId)
      val rsPepMatches = pepMatchesByRsId(rsId)
      val rsPeptides = rsPepMatches map { _.peptide } distinct
      val rsType = resultSetRecord(RSCols.TYPE).asInstanceOf[String]
      val isDecoy = rsType matches "DECOY_SEARCH"
      val isNative = rsType matches "SEARCH"

      val rsMsiSearchId = if (isNative) {
        resultSetRecord.getOrElse(RSCols.MSI_SEARCH_ID, 0).asInstanceOf[Int]
      } else if (msiSearchIdsByRsId.contains(rsId)) {
        // FIXME: we should attach all MSI searches to the result set ???
        msiSearchIdsByRsId(rsId)(0)
      } else 0

      val msiSearch = msiSearchById.getOrElse(rsMsiSearchId, null)

      var decoyRsId: Int = 0
      if (resultSetRecord(RSCols.DECOY_RESULT_SET_ID) != null)
        decoyRsId = resultSetRecord(RSCols.DECOY_RESULT_SET_ID).asInstanceOf[Int]

      // TODO: parse properties
      new ResultSet(
        id = rsId,
        name = resultSetRecord(RSCols.NAME).asInstanceOf[String],
        description = resultSetRecord(RSCols.DESCRIPTION).asInstanceOf[String],
        peptides = rsPeptides,
        peptideMatches = rsPepMatches,
        proteinMatches = rsProtMatches,
        isDecoy = isDecoy,
        isNative = isNative,
        msiSearch = msiSearch,
        decoyResultSetId = decoyRsId)
    }

    resultSets.toArray

  }

}

class SQLResultSetProvider(val msiDbCtx: SQLConnectionContext,
  val psDbCtx: SQLConnectionContext,
  val udsDbCtx: SQLConnectionContext) extends SQLResultSetLoader with IResultSetProvider {

  val pdiDbCtx = null

  def getResultSets(rsIds: Seq[Int]): Array[ResultSet] = {

    // Load peptide matches
    val pepMatchProvider = new SQLPeptideMatchProvider(msiDbCtx, psDbCtx)
    val pepMatches = pepMatchProvider.getResultSetsPeptideMatches(rsIds)

    // Load protein matches
    val protMatchProvider = new SQLProteinMatchProvider(msiDbCtx)
    val protMatches = protMatchProvider.getResultSetsProteinMatches(rsIds)

    this.getResultSets(rsIds, pepMatches, protMatches)

  }

  def getResultSetsAsOptions(resultSetIds: Seq[Int]): Array[Option[ResultSet]] = {

    val resultSets = this.getResultSets(resultSetIds)
    val resultSetById = resultSets.map { rs => rs.id -> rs } toMap

    resultSetIds.map { resultSetById.get(_) } toArray

  }

}