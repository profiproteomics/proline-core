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
import fr.proline.util.primitives._

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

  def getPeptideSetsAsOptions(pepSetIds: Seq[Long]): Array[Option[PeptideSet]] = {

    val pepSets = this.getPeptideSets(pepSetIds)
    val pepSetById = pepSets.map { p => p.id -> p } toMap

    pepSetIds.map { pepSetById.get(_) } toArray

  }

  def getPeptideSets(pepSetIds: Seq[Long]): Array[PeptideSet] = {
    
    DoJDBCReturningWork.withEzDBC(msiDbCtx, { msiEzDBC =>
    
      val pepSetItemRecords = this._getPepSetItemRecords(msiEzDBC,pepSetIds)
      val pepInstIds = pepSetItemRecords.map { v => toLong(v(PepSetItemCols.PEPTIDE_INSTANCE_ID)) } distinct
  
      this._buildPeptideSets(
        this._getPepSetRecords(msiEzDBC,pepSetIds),
        this._getPepSetRelationRecords(msiEzDBC,pepSetIds),
        pepSetItemRecords,
        this.peptideInstanceProvider.getPeptideInstances(pepInstIds),
        this._getPepSetProtMatchMapRecords(msiEzDBC,pepSetIds)
      )
    
    })
  }

  def getResultSummariesPeptideSets(rsmIds: Seq[Long]): Array[PeptideSet] = {
    
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

  private def _getRSMsPepSetRecords(msiEzDBC: EasyDBC, rsmIds: Seq[Long]): Array[Map[String, Any]] = {
    msiEzDBC.selectAllRecordsAsMaps( new SelectQueryBuilder1(MsiDbPeptideSetTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.RESULT_SUMMARY_ID ~" IN("~ rsmIds.mkString(",") ~")"
    ) )
  }

  private def _getPepSetRecords(msiEzDBC: EasyDBC, pepSetIds: Seq[Long]): Array[Map[String, Any]] = {
    // TODO: use max nb iterations
    msiEzDBC.selectAllRecordsAsMaps( new SelectQueryBuilder1(MsiDbPeptideSetTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.ID ~" IN("~ pepSetIds.mkString(",") ~")"
    ) )
  }

  private def _getRSMsPepSetRelationRecords(msiEzDBC: EasyDBC, rsmIds: Seq[Long]): Array[Map[String, Any]] = {
    msiEzDBC.selectAllRecordsAsMaps( new SelectQueryBuilder1(MsiDbPeptideSetRelationTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.RESULT_SUMMARY_ID ~" IN("~ rsmIds.mkString(",") ~")"
    ) )
  }

  private def _getPepSetRelationRecords(msiEzDBC: EasyDBC, pepSetIds: Seq[Long]): Array[Map[String, Any]] = {
    // TODO: use max nb iterations
    msiEzDBC.selectAllRecordsAsMaps( new SelectQueryBuilder1(MsiDbPeptideSetRelationTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.PEPTIDE_OVERSET_ID ~" IN("~ pepSetIds.mkString(",") ~")"
    ) )
  }

  private def _getRSMsPepSetItemRecords(msiEzDBC: EasyDBC, rsmIds: Seq[Long]): Array[Map[String, Any]] = {
    msiEzDBC.selectAllRecordsAsMaps( new SelectQueryBuilder1(MsiDbPeptideSetPeptideInstanceItemTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.RESULT_SUMMARY_ID ~" IN("~ rsmIds.mkString(",") ~")"
    ) )
  }

  private def _getPepSetItemRecords(msiEzDBC: EasyDBC, pepSetIds: Seq[Long]): Array[Map[String, Any]] = {
    // TODO: use max nb iterations
    msiEzDBC.selectAllRecordsAsMaps( new SelectQueryBuilder1(MsiDbPeptideSetPeptideInstanceItemTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.PEPTIDE_SET_ID ~" IN("~ pepSetIds.mkString(",") ~")"
    ) )
  }

  private def _getRSMsPepSetProtMatchMapRecords(msiEzDBC: EasyDBC, rsmIds: Seq[Long]): Array[Map[String, Any]] = {
    msiEzDBC.selectAllRecordsAsMaps( new SelectQueryBuilder1(MsiDbPeptideSetProteinMatchMapTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.RESULT_SUMMARY_ID ~" IN("~ rsmIds.mkString(",") ~")"
    ) )
  }

  private def _getPepSetProtMatchMapRecords(msiEzDBC: EasyDBC, pepSetIds: Seq[Long]): Array[Map[String, Any]] = {
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
      v => toLong(v(PepSetRelationCols.PEPTIDE_OVERSET_ID))
    }

    // Group peptide set items mapping by peptide set id
    val pepSetItemRecordsByPepSetId = pepSetItemRecords.groupBy {
      v => toLong(v(PepSetItemCols.PEPTIDE_SET_ID))
    }
    // Group protein matches mapping by peptide set id
    val protMatchMappingByPepSetId = pepSetProtMatchMapRecords.groupBy {
      v => toLong(v(PepSetItemCols.PEPTIDE_SET_ID))
    }

    // Build peptide sets
    val pepSets = new Array[PeptideSet](pepSetRecords.length)

    for (pepSetIdx <- 0 until pepSetRecords.length) {

      // Retrieve peptide instance record
      val pepSetRecord = pepSetRecords(pepSetIdx)
      val pepSetId: Long = toLong(pepSetRecord(PepSetCols.ID))

      // Retrieve peptide set relations
      var strictSubsetIdsBuilder = Array.newBuilder[Long]
      var subsumableSubsetIdsBuilder = Array.newBuilder[Long]

      if (pepSetRelRecordsByOversetId.contains(pepSetId)) {
        pepSetRelRecordsByOversetId(pepSetId).foreach { pepSetRelationRecord =>

          val peptideSubsetId = toLong(pepSetRelationRecord(PepSetRelationCols.PEPTIDE_SUBSET_ID))
          val isStrictSubset: Boolean = pepSetRelationRecord(PepSetRelationCols.IS_STRICT_SUBSET)

          if (isStrictSubset) strictSubsetIdsBuilder += peptideSubsetId
          else subsumableSubsetIdsBuilder += peptideSubsetId
        }
      }

      // Retrieve peptide set items      
      val pepSetItems = Array.newBuilder[PeptideSetItem]

      pepSetItemRecordsByPepSetId(pepSetId).foreach { pepSetItemRecord =>
        val pepInstId = toLong(pepSetItemRecord(PepSetItemCols.PEPTIDE_INSTANCE_ID))
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
          resultSummaryId = toLong(pepSetItemRecord(PepSetItemCols.RESULT_SUMMARY_ID)),
          properties = properties
        )

        pepSetItems += pepSetItem

      }

      var protMatchIds = protMatchMappingByPepSetId(pepSetId).map {
        v => toLong(v(ProtMatchMappingCols.PROTEIN_MATCH_ID))
      } toArray

      // Decode JSON properties
      val propertiesAsJSON = pepSetRecord(PepSetCols.SERIALIZED_PROPERTIES).asInstanceOf[String]
      val properties = if (propertiesAsJSON != null) Some(parse[PeptideSetProperties](propertiesAsJSON)) else None
      
      val pepSet = new PeptideSet(
        id = pepSetId,
        items = pepSetItems.result(),
        isSubset = pepSetRecord(PepSetCols.IS_SUBSET),
        score = toFloat(pepSetRecord(PepSetCols.SCORE)),
        scoreType = scoreTypeById(toLong(pepSetRecord(PepSetCols.SCORING_ID))),
        peptideMatchesCount = pepSetRecord(PepSetCols.PEPTIDE_MATCH_COUNT).asInstanceOf[Int],
        proteinMatchIds = protMatchIds,
        proteinSetId = if(pepSetRecord(PepSetCols.PROTEIN_SET_ID) == null) 0L else  toLong(pepSetRecord(PepSetCols.PROTEIN_SET_ID)),
        strictSubsetIds = strictSubsetIdsBuilder.result(),
        subsumableSubsetIds = subsumableSubsetIdsBuilder.result(),
        resultSummaryId = toLong(pepSetRecord(PepSetCols.RESULT_SUMMARY_ID)),
        properties = properties
      )

      pepSets(pepSetIdx) = pepSet

    }

    pepSets

  }

}