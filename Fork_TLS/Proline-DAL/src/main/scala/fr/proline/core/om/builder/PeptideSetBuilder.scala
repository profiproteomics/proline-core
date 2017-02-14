package fr.proline.core.om.builder

import scala.collection.mutable.LongMap

import fr.profi.util.primitives._
import fr.profi.util.serialization._
import fr.proline.core.dal.tables.msi._
import fr.proline.core.om.model.msi._

/**
 * @author David Bouyssie
 *
 */
object PeptideSetBuilder {
  
  protected val PepSetCols = MsiDbPeptideSetTable.columns
  protected val PepSetRelationCols = MsiDbPeptideSetRelationTable.columns
  protected val PepSetItemCols = MsiDbPeptideSetPeptideInstanceItemTable.columns
  protected val ProtMatchMappingCols = MsiDbPeptideSetProteinMatchMapTable.columns
  
  def buildPeptideSets(
    pepSetRecords: Seq[IValueContainer],
    pepSetRelationRecords: Seq[IValueContainer],
    pepSetItemRecords: Seq[IValueContainer],
    pepInstances: Seq[PeptideInstance],
    pepSetProtMatchMapRecords: Seq[IValueContainer],
    scoreTypeById: LongMap[String]
  ): Array[PeptideSet] = {
    
    // Map peptide instance by their id
    val pepInstById = Map() ++ pepInstances.map { p => (p.id -> p) }

    // Group peptide set relations by peptide overset ids
    val pepSetRelRecordsByOversetId = pepSetRelationRecords.groupBy(
      _.getLong(PepSetRelationCols.PEPTIDE_OVERSET_ID)
    )

    // Group peptide set items mapping by peptide set id
    val pepSetItemRecordsByPepSetId = pepSetItemRecords.groupBy(
      _.getLong(PepSetItemCols.PEPTIDE_SET_ID)
    )
    
    // Group protein matches mapping by peptide set id
    val protMatchMappingByPepSetId = pepSetProtMatchMapRecords.groupBy(
      _.getLong(PepSetItemCols.PEPTIDE_SET_ID)
    )

    // Build peptide sets
    val pepSets = new Array[PeptideSet](pepSetRecords.length)

    for (pepSetIdx <- 0 until pepSetRecords.length) {

      // Retrieve peptide instance record
      val pepSetRecord = pepSetRecords(pepSetIdx)
      val pepSetId = pepSetRecord.getLong(PepSetCols.ID)

      // Retrieve peptide set relations
      val strictSubsetIdsBuilder = Array.newBuilder[Long]
      val subsumableSubsetIdsBuilder = Array.newBuilder[Long]

      if (pepSetRelRecordsByOversetId.contains(pepSetId)) {
        for( pepSetRelationRecord <- pepSetRelRecordsByOversetId(pepSetId) ) {

          val peptideSubsetId = pepSetRelationRecord.getLong(PepSetRelationCols.PEPTIDE_SUBSET_ID)
          val isStrictSubset = pepSetRelationRecord.getBoolean(PepSetRelationCols.IS_STRICT_SUBSET)

          if (isStrictSubset) strictSubsetIdsBuilder += peptideSubsetId
          else subsumableSubsetIdsBuilder += peptideSubsetId
        }
      }

      // Retrieve peptide set items      
      val pepSetItems = Array.newBuilder[PeptideSetItem]

      pepSetItemRecordsByPepSetId(pepSetId).foreach { pepSetItemRecord =>
        val pepInstId = pepSetItemRecord.getLong(PepSetItemCols.PEPTIDE_INSTANCE_ID)
        val propertiesAsJsonOpt = pepSetItemRecord.getStringOption(PepSetItemCols.SERIALIZED_PROPERTIES)
        val properties = propertiesAsJsonOpt.map( ProfiJson.deserialize[PeptideSetItemProperties](_) )
        
        val pepSetItem = new PeptideSetItem(
          selectionLevel = pepSetItemRecord.getInt(PepSetItemCols.SELECTION_LEVEL),
          peptideInstance = pepInstById(pepInstId),
          peptideSetId = pepSetId,
          isBestPeptideSet = pepSetItemRecord.getBooleanOption(PepSetItemCols.IS_BEST_PEPTIDE_SET),
          resultSummaryId = pepSetItemRecord.getLong(PepSetItemCols.RESULT_SUMMARY_ID),
          properties = properties
        )

        pepSetItems += pepSetItem

      }

      val protMatchIds = protMatchMappingByPepSetId(pepSetId).map(
        _.getLong(ProtMatchMappingCols.PROTEIN_MATCH_ID)
      ).toArray

      // Decode JSON properties
      val propertiesAsJsonOpt = pepSetRecord.getStringOption(PepSetCols.SERIALIZED_PROPERTIES)
      val properties = propertiesAsJsonOpt.map { ProfiJson.deserialize[PeptideSetProperties](_) }
      
      pepSets(pepSetIdx) = new PeptideSet(
        id = pepSetId,
        items = pepSetItems.result(),
        isSubset = pepSetRecord.getBoolean(PepSetCols.IS_SUBSET),
        score = pepSetRecord.getFloat(PepSetCols.SCORE),
        scoreType = scoreTypeById( pepSetRecord.getLong(PepSetCols.SCORING_ID) ),
        sequencesCount = pepSetRecord.getInt(PepSetCols.SEQUENCE_COUNT),
        peptideMatchesCount = pepSetRecord.getInt(PepSetCols.PEPTIDE_MATCH_COUNT),
        proteinMatchIds = protMatchIds,
        proteinSetId = pepSetRecord.getLongOrElse(PepSetCols.PROTEIN_SET_ID, 0L),
        strictSubsetIds = strictSubsetIdsBuilder.result(),
        subsumableSubsetIds = subsumableSubsetIdsBuilder.result(),
        resultSummaryId = pepSetRecord.getLong(PepSetCols.RESULT_SUMMARY_ID),
        properties = properties
      )

    }

    pepSets

  }
  
}