package fr.proline.core.om.provider.msi.impl

import com.codahale.jerkson.Json.parse

import fr.profi.jdbc.easy.EasyDBC
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.tables.msi._
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.helper.MsiDbHelper
import fr.proline.core.om.model.msi.PeptideInstance
import fr.proline.core.om.model.msi.PeptideSet
import fr.proline.core.om.model.msi.PeptideSetItem
import fr.proline.core.om.model.msi.PeptideSetItemProperties
import fr.proline.core.om.model.msi.PeptideSetProperties
import fr.proline.core.om.provider.msi.{ IPeptideSetProvider, IPeptideInstanceProvider }

class SQLPeptideSetProvider(
  val msiDbCtx: DatabaseConnectionContext,
  val peptideInstanceProvider: IPeptideInstanceProvider
) extends IPeptideSetProvider {
  
  def this(msiDbCtx: DatabaseConnectionContext, psDbCtx: DatabaseConnectionContext) {
    this(msiDbCtx, new SQLPeptideInstanceProvider(msiDbCtx,psDbCtx) )
  }

  val PepSetCols = MsiDbPeptideSetTable.columns
  val PepSetRelationCols = MsiDbPeptideSetRelationTable.columns
  val PepSetItemCols = MsiDbPeptideSetPeptideInstanceItemTable.columns
  val ProtMatchMappingCols = MsiDbPeptideSetProteinMatchMapTable.columns

  // Instantiate a MSIdb helper
  val msiDbHelper = new MsiDbHelper(msiDbCtx)

  // Retrieve score type map
  val scoreTypeById = msiDbHelper.getScoringTypeById

  def getPeptideSetsAsOptions(pepSetIds: Seq[Int]): Array[Option[PeptideSet]] = {

    val pepSets = this.getPeptideSets(pepSetIds)
    val pepSetById = pepSets.map { p => p.id -> p } toMap

    pepSetIds.map { pepSetById.get(_) } toArray

  }

  def getPeptideSets(pepSetIds: Seq[Int]): Array[PeptideSet] = {
    
    DoJDBCReturningWork.withEzDBC(msiDbCtx, { msiEzDBC =>
    
      val pepSetItemRecords = this._getPepSetItemRecords(msiEzDBC,pepSetIds)
      val pepInstIds = pepSetItemRecords.map { _(PepSetItemCols.PEPTIDE_INSTANCE_ID).asInstanceOf[Int] } distinct
  
      this._buildPeptideSets(
        this._getPepSetRecords(msiEzDBC,pepSetIds),
        this._getPepSetRelationRecords(msiEzDBC,pepSetIds),
        pepSetItemRecords,
        this.peptideInstanceProvider.getPeptideInstances(pepInstIds),
        this._getPepSetProtMatchMapRecords(msiEzDBC,pepSetIds)
      )
    
    })
  }

  def getResultSummariesPeptideSets(rsmIds: Seq[Int]): Array[PeptideSet] = {
    
    DoJDBCReturningWork.withEzDBC(msiDbCtx, { msiEzDBC =>
    
      this._buildPeptideSets(
        this._getRSMsPepSetRecords(msiEzDBC,rsmIds),
        this._getRSMsPepSetRelationRecords(msiEzDBC,rsmIds),
        this._getRSMsPepSetItemRecords(msiEzDBC,rsmIds),
        this.peptideInstanceProvider.getResultSummariesPeptideInstances(rsmIds),
        this._getRSMsPepSetProtMatchMapRecords(msiEzDBC,rsmIds)
      )
      
    })
  }

  private def _getRSMsPepSetRecords(msiEzDBC: EasyDBC, rsmIds: Seq[Int]): Array[Map[String, Any]] = {
    msiEzDBC.selectAllRecordsAsMaps( new SelectQueryBuilder1(MsiDbPeptideSetTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.RESULT_SUMMARY_ID ~" IN("~ rsmIds.mkString(",") ~")"
    ) )
  }

  private def _getPepSetRecords(msiEzDBC: EasyDBC, pepSetIds: Seq[Int]): Array[Map[String, Any]] = {
    // TODO: use max nb iterations
    msiEzDBC.selectAllRecordsAsMaps( new SelectQueryBuilder1(MsiDbPeptideSetTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.ID ~" IN("~ pepSetIds.mkString(",") ~")"
    ) )
  }

  private def _getRSMsPepSetRelationRecords(msiEzDBC: EasyDBC, rsmIds: Seq[Int]): Array[Map[String, Any]] = {
    msiEzDBC.selectAllRecordsAsMaps( new SelectQueryBuilder1(MsiDbPeptideSetRelationTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.RESULT_SUMMARY_ID ~" IN("~ rsmIds.mkString(",") ~")"
    ) )
  }

  private def _getPepSetRelationRecords(msiEzDBC: EasyDBC, pepSetIds: Seq[Int]): Array[Map[String, Any]] = {
    // TODO: use max nb iterations
    msiEzDBC.selectAllRecordsAsMaps( new SelectQueryBuilder1(MsiDbPeptideSetRelationTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.PEPTIDE_OVERSET_ID ~" IN("~ pepSetIds.mkString(",") ~")"
    ) )
  }

  private def _getRSMsPepSetItemRecords(msiEzDBC: EasyDBC, rsmIds: Seq[Int]): Array[Map[String, Any]] = {
    msiEzDBC.selectAllRecordsAsMaps( new SelectQueryBuilder1(MsiDbPeptideSetPeptideInstanceItemTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.RESULT_SUMMARY_ID ~" IN("~ rsmIds.mkString(",") ~")"
    ) )
  }

  private def _getPepSetItemRecords(msiEzDBC: EasyDBC, pepSetIds: Seq[Int]): Array[Map[String, Any]] = {
    // TODO: use max nb iterations
    msiEzDBC.selectAllRecordsAsMaps( new SelectQueryBuilder1(MsiDbPeptideSetPeptideInstanceItemTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.PEPTIDE_SET_ID ~" IN("~ pepSetIds.mkString(",") ~")"
    ) )
  }

  private def _getRSMsPepSetProtMatchMapRecords(msiEzDBC: EasyDBC, rsmIds: Seq[Int]): Array[Map[String, Any]] = {
    msiEzDBC.selectAllRecordsAsMaps( new SelectQueryBuilder1(MsiDbPeptideSetProteinMatchMapTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.RESULT_SUMMARY_ID ~" IN("~ rsmIds.mkString(",") ~")"
    ) )
  }

  private def _getPepSetProtMatchMapRecords(msiEzDBC: EasyDBC, pepSetIds: Seq[Int]): Array[Map[String, Any]] = {
    // TODO: use max nb iterations
    msiEzDBC.selectAllRecordsAsMaps( new SelectQueryBuilder1(MsiDbPeptideSetProteinMatchMapTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.PEPTIDE_SET_ID ~" IN("~ pepSetIds.mkString(",") ~")"
    ) )
  }

  private def _buildPeptideSets(
    pepSetRecords: Seq[Map[String, Any]],
    pepSetRelationRecords: Seq[Map[String, Any]],
    pepSetItemRecords: Seq[Map[String, Any]],
    pepInstances: Seq[PeptideInstance],
    pepSetProtMatchMapRecords: Seq[Map[String, Any]]
  ): Array[PeptideSet] = {

    import fr.proline.util.primitives._
    import fr.proline.util.sql.StringOrBoolAsBool._

    // Load peptides
    //val uniqPepSetIds = pepSetRecords map { _(PepSetCols.peptideId).asInstanceOf[Int] } distinct
    //val peptides = this._getPeptideProvider().getPeptides(uniqPepIds)

    // Map peptide instance by their id
    val pepInstById = Map() ++ pepInstances.map { p => (p.id -> p) }

    // Group peptide set relations by peptide overset ids
    val pepSetRelRecordsByOversetId = pepSetRelationRecords.groupBy {
      _(PepSetRelationCols.PEPTIDE_OVERSET_ID).asInstanceOf[Int]
    }

    // Group peptide set items mapping by peptide set id
    val pepSetItemRecordsByPepSetId = pepSetItemRecords.groupBy {
      _(PepSetItemCols.PEPTIDE_SET_ID).asInstanceOf[Int]
    }
    // Group protein matches mapping by peptide set id
    val protMatchMappingByPepSetId = pepSetProtMatchMapRecords.groupBy {
      _(PepSetItemCols.PEPTIDE_SET_ID).asInstanceOf[Int]
    }

    // Build peptide sets
    val pepSets = new Array[PeptideSet](pepSetRecords.length)

    for (pepSetIdx <- 0 until pepSetRecords.length) {

      // Retrieve peptide instance record
      val pepSetRecord = pepSetRecords(pepSetIdx)
      val pepSetId: Int = toInt(pepSetRecord(PepSetCols.ID))

      // Retrieve peptide set relations
      var strictSubsetIdsBuilder = Array.newBuilder[Int]
      var subsumableSubsetIdsBuilder = Array.newBuilder[Int]

      if (pepSetRelRecordsByOversetId.contains(pepSetId)) {
        pepSetRelRecordsByOversetId(pepSetId).foreach { pepSetRelationRecord =>

          val peptideSubsetId = pepSetRelationRecord(PepSetRelationCols.PEPTIDE_SUBSET_ID).asInstanceOf[Int]
          val isStrictSubset: Boolean = pepSetRelationRecord(PepSetRelationCols.IS_STRICT_SUBSET)

          if (isStrictSubset) strictSubsetIdsBuilder += peptideSubsetId
          else subsumableSubsetIdsBuilder += peptideSubsetId
        }
      }

      // Retrieve peptide set items      
      val pepSetItems = Array.newBuilder[PeptideSetItem]

      pepSetItemRecordsByPepSetId(pepSetId).foreach { pepSetItemRecord =>
        val pepInstId = pepSetItemRecord(PepSetItemCols.PEPTIDE_INSTANCE_ID).asInstanceOf[Int]
        val pepInst = pepInstById(pepInstId)
        val isBestPepSetField = pepSetItemRecord(PepSetItemCols.IS_BEST_PEPTIDE_SET)
        val isBestPepSet: Option[Boolean] = if (isBestPepSetField != null) Some(isBestPepSetField) else None
        val propertiesAsJSON = pepSetItemRecord(PepSetItemCols.SERIALIZED_PROPERTIES).asInstanceOf[String]
        val properties = if (propertiesAsJSON != null) Some(parse[PeptideSetItemProperties](propertiesAsJSON)) else None
      
        val pepSetItem = new PeptideSetItem(
          selectionLevel = pepSetItemRecord(PepSetItemCols.SELECTION_LEVEL).asInstanceOf[Int],
          peptideInstance = pepInst,
          peptideSetId = pepSetId,
          isBestPeptideSet = isBestPepSet,
          resultSummaryId = pepSetItemRecord(PepSetItemCols.RESULT_SUMMARY_ID).asInstanceOf[Int],
          properties = properties
        )

        pepSetItems += pepSetItem

      }

      var protMatchIds = protMatchMappingByPepSetId(pepSetId).map {
        _(ProtMatchMappingCols.PROTEIN_MATCH_ID).asInstanceOf[Int]
      } toArray

      // Decode JSON properties
      val propertiesAsJSON = pepSetRecord(PepSetCols.SERIALIZED_PROPERTIES).asInstanceOf[String]
      val properties = if (propertiesAsJSON != null) Some(parse[PeptideSetProperties](propertiesAsJSON)) else None
      
      val pepSet = new PeptideSet(
        id = pepSetId,
        items = pepSetItems.result(),
        isSubset = pepSetRecord(PepSetCols.IS_SUBSET),
        score = toFloat(pepSetRecord(PepSetCols.SCORE)),
        scoreType = scoreTypeById(pepSetRecord(PepSetCols.SCORING_ID).asInstanceOf[Int]),
        peptideMatchesCount = pepSetRecord(PepSetCols.PEPTIDE_MATCH_COUNT).asInstanceOf[Int],
        proteinMatchIds = protMatchIds,
        proteinSetId = pepSetRecord.getOrElse(PepSetCols.PROTEIN_SET_ID, 0).asInstanceOf[Int],
        strictSubsetIds = strictSubsetIdsBuilder.result(),
        subsumableSubsetIds = subsumableSubsetIdsBuilder.result(),
        resultSummaryId = pepSetRecord(PepSetCols.RESULT_SUMMARY_ID).asInstanceOf[Int],
        properties = properties
      )

      pepSets(pepSetIdx) = pepSet

    }

    pepSets

  }

}