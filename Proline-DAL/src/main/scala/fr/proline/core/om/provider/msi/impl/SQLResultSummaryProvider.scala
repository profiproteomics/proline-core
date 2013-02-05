package fr.proline.core.om.provider.msi.impl

import com.codahale.jerkson.Json.parse

import fr.proline.core.dal.tables.msi.MsiDbResultSummaryTable
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.helper.MsiDbHelper
import fr.proline.core.om.model.msi.ProteinSet
import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.om.model.msi.ResultSummaryProperties
import fr.proline.core.om.provider.msi.IResultSummaryProvider
import fr.proline.context.DatabaseConnectionContext
import fr.profi.jdbc.easy.EasyDBC
import fr.proline.core.dal.SQLConnectionContext

class SQLResultSummaryProvider(
  val msiDbCtx: SQLConnectionContext,
  val psDbCtx: SQLConnectionContext = null,
  val udsDbCtx: SQLConnectionContext = null
) extends SQLResultSetLoader with IResultSummaryProvider {

  // Instantiate a MSIdb helper
  val msiDbHelper = new MsiDbHelper(msiDbCtx.ezDBC)

  val RSMCols = MsiDbResultSummaryTable.columns

  def getResultSummaries(rsmIds: Seq[Int], loadResultSet: Boolean): Array[ResultSummary] = {

    import fr.proline.util.primitives.LongOrIntAsInt._
    import fr.proline.util.sql.StringOrBoolAsBool._

    // Load peptide sets
    val pepSetProvider = new SQLPeptideSetProvider(msiDbCtx, psDbCtx)
    val pepSets = pepSetProvider.getResultSummariesPeptideSets(rsmIds)
    val inMemPepSetProvider = new InMemoryPeptideSetProvider(pepSets)

    // Load protein sets
    val protSetProvider = new SQLProteinSetProvider(msiDbCtx, psDbCtx, Some(pepSetProvider))
    val protSets = protSetProvider.getResultSummariesProteinSets(rsmIds)
    val protSetsByRsmId = protSets.groupBy(_.resultSummaryId)

    // Execute SQL query to load result sets
    val rsmQuery = new SelectQueryBuilder1(MsiDbResultSummaryTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.ID ~" IN("~ rsmIds.mkString(",") ~")"
    )

    val rsms = msiDbCtx.ezDBC.select(rsmQuery) { r =>

      // Retrieve some vars
      val rsmId: Int = r.getAnyVal(RSMCols.ID)
      val rsmPepSets = inMemPepSetProvider.getResultSummaryPeptideSets(rsmId)
      val rsmPepInsts = rsmPepSets.flatMap(_.getPeptideInstances).distinct
      val rsmProtSets = protSetsByRsmId.getOrElse(rsmId, Array.empty[ProteinSet])

      val decoyRsmId = r.getIntOrElse(RSMCols.DECOY_RESULT_SUMMARY_ID, 0)
      //val decoyRsmId = if( decoyRsmIdField != null ) decoyRsmIdField.asInstanceOf[Int] else 0
      
      val isQuantified = r.getBooleanOrElse(RSMCols.IS_QUANTIFIED,false)
      
      // Decode JSON properties
      val propertiesAsJSON = r.getString(RSMCols.SERIALIZED_PROPERTIES)
      val properties = if (propertiesAsJSON != null) Some(parse[ResultSummaryProperties](propertiesAsJSON)) else None
      
      val rsId = r.getInt(RSMCols.RESULT_SET_ID)
      
      var rsAsOpt = Option.empty[ResultSet]
      if (loadResultSet) {

        val pepMatchProvider = new SQLPeptideMatchProvider(msiDbCtx, psDbCtx)
        val protMatchProvider = new SQLProteinMatchProvider(msiDbCtx)

        val pepMatches = pepMatchProvider.getResultSummaryPeptideMatches(rsmId)
        val protMatches = protMatchProvider.getResultSummariesProteinMatches(Array(rsmId))

        // Remove objects which are not linked to result summary
        // TODO: remove when the provider is fully implemented
        val protMatchIdSet = rsmPepSets.flatMap(_.proteinMatchIds) toSet
        val rsmProtMatches = protMatches.filter { p => protMatchIdSet.contains(p.id) }

        rsAsOpt = Some(this.getResultSet(rsId, pepMatches, rsmProtMatches))
      }

      new ResultSummary(
        id = rsmId,
        description = r.getString(RSMCols.DESCRIPTION),
        isQuantified = isQuantified,
        modificationTimestamp = r.getDate(RSMCols.MODIFICATION_TIMESTAMP),
        peptideInstances = rsmPepInsts,
        peptideSets = rsmPepSets,
        proteinSets = rsmProtSets,
        resultSetId = rsId,
        resultSet = rsAsOpt,
        decoyResultSummaryId = decoyRsmId,
        properties = properties
      )

    }

    rsms.toArray

  }

  def getResultSummariesAsOptions(rsmIds: Seq[Int], loadResultSet: Boolean): Array[Option[ResultSummary]] = {
    val rsms = this.getResultSummaries(rsmIds, loadResultSet)
    val rsmById = rsms.map { rsm => rsm.id -> rsm } toMap;
    rsmIds.map { rsmById.get(_) } toArray
  }

  def getResultSetsResultSummaries(rsIds: Seq[Int], loadResultSet: Boolean): Array[ResultSummary] = {
    throw new Exception("NYI")
    null
  }

}