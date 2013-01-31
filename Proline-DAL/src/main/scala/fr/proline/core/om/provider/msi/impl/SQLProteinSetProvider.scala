package fr.proline.core.om.provider.msi.impl

import com.codahale.jerkson.Json.parse

import fr.profi.jdbc.SQLQueryExecution
import fr.proline.core.dal.tables.msi.{ MsiDbProteinSetTable, MsiDbProteinSetProteinMatchItemTable }
import fr.proline.core.dal.helper.MsiDbHelper
import fr.proline.core.om.model.msi.{ PeptideSet, ProteinSet }
import fr.proline.core.om.provider.msi.{ IPeptideSetProvider, IProteinSetProvider }
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.SQLConnectionContext

class SQLProteinSetProvider(val msiDbCtx: SQLConnectionContext,
  val psDbCtx: SQLConnectionContext,

  val peptideSetProvider: Option[IPeptideSetProvider] = None) {

  val ProtSetCols = MsiDbProteinSetTable.columns
  val ProtSetItemCols = MsiDbProteinSetProteinMatchItemTable.columns

  // Instantiate a MSIdb helper
  val msiDbHelper = new MsiDbHelper(msiDbCtx.ezDBC)

  // Retrieve score type map
  val scoreTypeById = msiDbHelper.getScoringTypeById

  private def _getPeptideSetProvider(): IPeptideSetProvider = {
    if (this.peptideSetProvider != None) this.peptideSetProvider.get
    else new SQLPeptideSetProvider(msiDbCtx, psDbCtx)
  }

  def getProteinSetsAsOptions(protSetIds: Seq[Int]): Array[Option[ProteinSet]] = {

    val protSetById = this.getProteinSets(protSetIds).map { p => p.id -> p } toMap;
    protSetIds.map { protSetById.get(_) } toArray

  }

  def getProteinSets(protSetIds: Seq[Int]): Array[ProteinSet] = {

    val peptideSetIds = msiDbCtx.ezDBC.select("SELECT id FROM peptide_set WHERE protein_set_id IN (" + protSetIds.mkString(",") + ")") { _.nextInt }
    val peptideSets = this._getPeptideSetProvider.getPeptideSets(peptideSetIds)

    this._buildProteinSets(this._getProtSetRecords(protSetIds),
      this._getProtSetItemRecords(protSetIds),
      peptideSets)
  }

  def getResultSummariesProteinSets(rsmIds: Seq[Int]): Array[ProteinSet] = {
    this._buildProteinSets(this._getRSMsProtSetRecords(rsmIds),
      this._getRSMsProtSetItemRecords(rsmIds),
      this._getPeptideSetProvider.getResultSummariesPeptideSets(rsmIds))
  }

  private def _getRSMsProtSetRecords(rsmIds: Seq[Int]): Array[Map[String, Any]] = {
    msiDbCtx.ezDBC.selectAllRecordsAsMaps("SELECT * FROM protein_set WHERE result_summary_id IN (" + rsmIds.mkString(",") + ")")
  }

  private def _getProtSetRecords(protSetIds: Seq[Int]): Array[Map[String, Any]] = {
    // TODO: use max nb iterations
    msiDbCtx.ezDBC.selectAllRecordsAsMaps("SELECT * FROM protein_set WHERE id IN (" + protSetIds.mkString(",") + ")")
  }

  private def _getRSMsProtSetItemRecords(rsmIds: Seq[Int]): Array[Map[String, Any]] = {
    msiDbCtx.ezDBC.selectAllRecordsAsMaps("SELECT * FROM protein_set_protein_match_item WHERE result_summary_id IN (" + rsmIds.mkString(",") + ")")
  }

  private def _getProtSetItemRecords(protSetIds: Seq[Int]): Array[Map[String, Any]] = {
    // TODO: use max nb iterations
    msiDbCtx.ezDBC.selectAllRecordsAsMaps("SELECT * FROM protein_set_protein_match_item WHERE protein_set_id IN (" + protSetIds.mkString(",") + ")")
  }

  private def _buildProteinSets(protSetRecords: Seq[Map[String, Any]],
    protSetItemRecords: Seq[Map[String, Any]],
    peptideSets: Seq[PeptideSet]): Array[ProteinSet] = {

    import fr.proline.util.primitives.LongOrIntAsInt._
    import fr.proline.util.primitives.DoubleOrFloatAsFloat._
    import fr.proline.util.sql.StringOrBoolAsBool._

    // Map peptide set by protein set id
    val pepSetByProtSetId = Map() ++ peptideSets.map { pepSet => pepSet.getProteinSetId -> pepSet }

    // Group protein set items by protein set id
    val protSetItemRecordsByProtSetId = protSetItemRecords.groupBy {
      _(ProtSetItemCols.PROTEIN_SET_ID).asInstanceOf[Int]
    }

    // Build protein sets
    val protSets = new Array[ProteinSet](protSetRecords.length)

    for (protSetIds <- 0 until protSetRecords.length) {

      // Retrieve peptide instance record
      val protSetRecord = protSetRecords(protSetIds)
      val protSetId: Int = protSetRecord(ProtSetCols.ID).asInstanceOf[AnyVal]

      // Retrieve corresponding peptide set
      val pepSet = pepSetByProtSetId(protSetId)

      // Retrieve protein match ids and properties
      val protMatchIdsBuilder = Array.newBuilder[Int]

      protSetItemRecordsByProtSetId(protSetId).foreach { protSetItems =>
        protMatchIdsBuilder += protSetItems(ProtSetItemCols.PROTEIN_MATCH_ID).asInstanceOf[Int]

        /*val propertiesAsJSON = pepMatchMapping(PepMatchMappingCols.serializedProperties).asInstanceOf[String]        
        if( propertiesAsJSON != null ) {
          pepMatchPropertyMapBuilder += pepMatchId -> parse[ProteinSetPeptideMatchMapProperties](propertiesAsJSON)
        }*/
      }

      // Decode JSON properties
      /*val propertiesAsJSON = protSetRecord(ProtSetCols.serializedProperties).asInstanceOf[String]
      var properties = Option.empty[PeptideMatchProperties]
      if( propertiesAsJSON != null ) {
        properties = Some( parse[PeptideMatchProperties](propertiesAsJSON) )
      }*/

      // TODO: load properties
      val protSet = new ProteinSet(
        id = protSetId,
        peptideSet = pepSet,
        hasPeptideSubset = pepSet.hasSubset,
        score = protSetRecord(ProtSetCols.SCORE).asInstanceOf[AnyVal],
        scoreType = scoreTypeById(protSetRecord(ProtSetCols.SCORING_ID).asInstanceOf[Int]),
        isValidated = protSetRecord(ProtSetCols.IS_VALIDATED),
        selectionLevel = protSetRecord(ProtSetCols.SELECTION_LEVEL).asInstanceOf[Int],
        proteinMatchIds = protMatchIdsBuilder.result(),
        typicalProteinMatchId = protSetRecord(ProtSetCols.TYPICAL_PROTEIN_MATCH_ID).asInstanceOf[Int],
        resultSummaryId = protSetRecord(ProtSetCols.RESULT_SUMMARY_ID).asInstanceOf[Int])

      protSets(protSetIds) = protSet

    }

    protSets

  }

}