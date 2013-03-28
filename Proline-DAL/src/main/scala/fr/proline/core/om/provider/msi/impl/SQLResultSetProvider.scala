package fr.proline.core.om.provider.msi.impl

import scala.collection.mutable.ArrayBuffer
import com.codahale.jerkson.Json.parse

import fr.proline.core.dal.tables.msi.MsiDbResultSetTable
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.om.model.msi.{ ProteinMatch, PeptideMatch, ResultSet, ResultSetProperties }
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

  protected def getResultSets(
    rsIds: Seq[Int],
    pepMatches: Array[PeptideMatch],
    protMatches: Array[ProteinMatch]
  ): Array[ResultSet] = {

    import fr.proline.util.primitives._

    // Build some maps
    val pepMatchesByRsId = pepMatches.groupBy(_.resultSetId)
    val protMatchesByRsId = protMatches.groupBy(_.resultSetId)

    // Instantiate a MSIdb helper
    val msiDbHelper = new MsiDbHelper(msiDbCtx)
    val msiSearchIdsByRsId = msiDbHelper.getMsiSearchIdsByParentResultSetId(rsIds)
    val msiSearchIds = msiSearchIdsByRsId.flatMap(_._2).toArray.distinct
    
    var msiSearchById = Map.empty[Int, fr.proline.core.om.model.msi.MSISearch]
    if (udsDbCtx != null) {
      val msiSearches = new SQLMsiSearchProvider(udsDbCtx, msiDbCtx, psDbCtx).getMSISearches(msiSearchIds)
      msiSearchById = Map() ++ msiSearches.map(ms => ms.id -> ms)
    }

    // Execute SQL query to load result sets
    val rsQuery = new SelectQueryBuilder1(MsiDbResultSetTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.ID ~" IN("~ rsIds.mkString(",") ~")"
    )
    
    val resultSets = msiDbCtx.ezDBC.select(rsQuery) { r =>      

      // Retrieve some vars
      val rsId: Int = toInt(r.getAnyVal(RSCols.ID))
      /*if( !protMatchesByRsId.contains(rsId) ) {
        throw new Exception("this result set doesn't have any protein match and can't be loaded")
      }*/
      
      val rsProtMatches = protMatchesByRsId.getOrElse(rsId,Array.empty[ProteinMatch])
      val rsPepMatches = pepMatchesByRsId.getOrElse(rsId,Array.empty[PeptideMatch])
      val rsPeptides = rsPepMatches map { _.peptide } distinct
      val rsType = r.getString(RSCols.TYPE)
      val isDecoy = rsType matches "DECOY_SEARCH"
      val isNative = rsType matches "SEARCH"
      val decoyRsId = r.getIntOrElse(RSCols.DECOY_RESULT_SET_ID, 0)

      val rsMsiSearchId = if (isNative) {
        r.getIntOrElse(RSCols.MSI_SEARCH_ID, 0)
      } else if (msiSearchIdsByRsId.contains(rsId)) {
        // FIXME: we should attach all MSI searches to the result set ???
        msiSearchIdsByRsId(rsId).head
      } else 0

      val msiSearch = msiSearchById.getOrElse(rsMsiSearchId, null)      

      // Decode JSON properties
      val propertiesAsJSON = r.getString(RSCols.SERIALIZED_PROPERTIES)
      val properties = if (propertiesAsJSON != null) Some(parse[ResultSetProperties](propertiesAsJSON)) else None
      
      new ResultSet(
        id = rsId,
        name = r.getString(RSCols.NAME),
        description = r.getString(RSCols.DESCRIPTION),
        peptides = rsPeptides,
        peptideMatches = rsPepMatches,
        proteinMatches = rsProtMatches,
        isDecoy = isDecoy,
        isNative = isNative,
        msiSearch = msiSearch,
        decoyResultSetId = decoyRsId,
        properties = properties
      )
    }

    resultSets.toArray

  }

}

class SQLResultSetProvider(
  val msiDbCtx: SQLConnectionContext,
  val psDbCtx: SQLConnectionContext,
  val udsDbCtx: SQLConnectionContext
) extends SQLResultSetLoader with IResultSetProvider {

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