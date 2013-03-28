package fr.proline.core.om.provider.msi.impl

import com.codahale.jerkson.Json.parse

import fr.profi.jdbc.easy.EasyDBC
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.tables.msi.{ MsiDbProteinSetTable, MsiDbProteinSetProteinMatchItemTable, MsiDbPeptideSetTable }
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.helper.MsiDbHelper
import fr.proline.core.om.model.msi.{ PeptideSet, ProteinSet, ProteinSetProperties, ProteinMatchResultSummaryProperties }
import fr.proline.core.om.provider.msi.{ IPeptideSetProvider, IProteinSetProvider }
import fr.proline.context.DatabaseConnectionContext

class SQLProteinSetProvider(
  val msiDbCtx: DatabaseConnectionContext,
  val peptideSetProvider: IPeptideSetProvider
) {
  
  def this(msiDbCtx: DatabaseConnectionContext, psDbCtx: DatabaseConnectionContext) = {
    this(msiDbCtx, new SQLPeptideSetProvider(msiDbCtx,psDbCtx) )
  }

  val ProtSetCols = MsiDbProteinSetTable.columns
  val ProtSetItemCols = MsiDbProteinSetProteinMatchItemTable.columns

  // Instantiate a MSIdb helper
  val msiDbHelper = new MsiDbHelper(msiDbCtx)

  // Retrieve score type map
  val scoreTypeById = msiDbHelper.getScoringTypeById

  def getProteinSetsAsOptions(protSetIds: Seq[Int]): Array[Option[ProteinSet]] = {

    val protSetById = this.getProteinSets(protSetIds).map { p => p.id -> p } toMap;
    protSetIds.map { protSetById.get(_) } toArray

  }

  def getProteinSets(protSetIds: Seq[Int]): Array[ProteinSet] = {
    
    DoJDBCReturningWork.withEzDBC(msiDbCtx, { msiEzDBC =>
    
      val pepSetIdQuery = new SelectQueryBuilder1(MsiDbPeptideSetTable).mkSelectQuery( (t,c) =>
        List(t.ID) -> "WHERE "~ t.PROTEIN_SET_ID ~" IN("~ protSetIds.mkString(",") ~")"
      )
  
      val peptideSetIds = msiEzDBC.select(pepSetIdQuery) { _.nextInt }
      val peptideSets = this.peptideSetProvider.getPeptideSets(peptideSetIds)
  
      this._buildProteinSets(
        this._getProtSetRecords(msiEzDBC,protSetIds),
        this._getProtSetItemRecords(msiEzDBC,protSetIds),
        peptideSets
      )
      
    })
  }

  def getResultSummariesProteinSets(rsmIds: Seq[Int]): Array[ProteinSet] = {
    DoJDBCReturningWork.withEzDBC(msiDbCtx, { msiEzDBC =>
      this._buildProteinSets(
        this._getRSMsProtSetRecords(msiEzDBC,rsmIds),
        this._getRSMsProtSetItemRecords(msiEzDBC,rsmIds),
        this.peptideSetProvider.getResultSummariesPeptideSets(rsmIds)
      )
    })
  }

  private def _getRSMsProtSetRecords(msiEzDBC: EasyDBC, rsmIds: Seq[Int]): Array[Map[String, Any]] = {
    val protSetQuery = new SelectQueryBuilder1(MsiDbProteinSetTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.RESULT_SUMMARY_ID ~" IN("~ rsmIds.mkString(",") ~")"
    )
    msiEzDBC.selectAllRecordsAsMaps(protSetQuery)
  }

  private def _getProtSetRecords(msiEzDBC: EasyDBC, protSetIds: Seq[Int]): Array[Map[String, Any]] = {
    // TODO: use max nb iterations
    val protSetQuery = new SelectQueryBuilder1(MsiDbProteinSetTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.ID ~" IN("~ protSetIds.mkString(",") ~")"
    )
    msiEzDBC.selectAllRecordsAsMaps(protSetQuery)
  }

  private def _getRSMsProtSetItemRecords(msiEzDBC: EasyDBC, rsmIds: Seq[Int]): Array[Map[String, Any]] = {    
    val protSetItemQuery = new SelectQueryBuilder1(MsiDbProteinSetProteinMatchItemTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.RESULT_SUMMARY_ID ~" IN("~ rsmIds.mkString(",") ~")"
    )
    msiEzDBC.selectAllRecordsAsMaps(protSetItemQuery)
  }

  private def _getProtSetItemRecords(msiEzDBC: EasyDBC, protSetIds: Seq[Int]): Array[Map[String, Any]] = {
    // TODO: use max nb iterations
    val protSetItemQuery = new SelectQueryBuilder1(MsiDbProteinSetProteinMatchItemTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.PROTEIN_SET_ID ~" IN("~ protSetIds.mkString(",") ~")"
    )
    msiEzDBC.selectAllRecordsAsMaps(protSetItemQuery)
  }

  private def _buildProteinSets(
    protSetRecords: Seq[Map[String, Any]],
    protSetItemRecords: Seq[Map[String, Any]],
    peptideSets: Seq[PeptideSet]
  ): Array[ProteinSet] = {

    import fr.proline.util.primitives._
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
      val protSetId: Int = toInt(protSetRecord(ProtSetCols.ID))

      // Retrieve corresponding peptide set
      val pepSet = pepSetByProtSetId(protSetId)

      // Retrieve protein match ids and properties
      val protMatchIdsBuilder = Array.newBuilder[Int]
      val protMatchPropertiesById = Map.newBuilder[Int, ProteinMatchResultSummaryProperties]

      protSetItemRecordsByProtSetId(protSetId).foreach { protSetItem =>
        
        val protMatchId = protSetItem(ProtSetItemCols.PROTEIN_MATCH_ID).asInstanceOf[Int]
        protMatchIdsBuilder += protMatchId
        
        val propertiesAsJSON = protSetItem(ProtSetItemCols.SERIALIZED_PROPERTIES).asInstanceOf[String]
        if (propertiesAsJSON != null) {
          protMatchPropertiesById += protMatchId -> parse[ProteinMatchResultSummaryProperties](propertiesAsJSON)
        }
      }

      // Decode JSON properties
      val propertiesAsJSON = protSetRecord(ProtSetCols.SERIALIZED_PROPERTIES).asInstanceOf[String]
      val properties = if (propertiesAsJSON != null) Some(parse[ProteinSetProperties](propertiesAsJSON)) else None
      
      val protSet = new ProteinSet(
        id = protSetId,
        peptideSet = pepSet,
        hasPeptideSubset = pepSet.hasSubset,
        score = toFloat(protSetRecord(ProtSetCols.SCORE)),
        scoreType = scoreTypeById(protSetRecord(ProtSetCols.SCORING_ID).asInstanceOf[Int]),
        isValidated = protSetRecord(ProtSetCols.IS_VALIDATED),
        selectionLevel = protSetRecord(ProtSetCols.SELECTION_LEVEL).asInstanceOf[Int],
        proteinMatchIds = protMatchIdsBuilder.result(),
        typicalProteinMatchId = protSetRecord(ProtSetCols.TYPICAL_PROTEIN_MATCH_ID).asInstanceOf[Int],
        resultSummaryId = protSetRecord(ProtSetCols.RESULT_SUMMARY_ID).asInstanceOf[Int],
        properties = properties,
        proteinMatchPropertiesById = protMatchPropertiesById.result
      )

      protSets(protSetIds) = protSet

    }

    protSets

  }

}