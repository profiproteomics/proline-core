package fr.proline.core.om.builder

import fr.profi.util.primitives._
import fr.profi.util.serialization._
import fr.proline.core.dal.tables.msi._
import fr.proline.core.om.model.msi._

/**
 * @author David Bouyssie
 *
 */
object ProteinSetBuilder {
  
  protected val ProtSetCols = MsiDbProteinSetColumns
  protected val ProtSetItemCols = MsiDbProteinSetProteinMatchItemColumns
  
  def buildProteinSets(
    protSetRecords: Seq[IValueContainer],
    protSetItemRecords: Seq[IValueContainer],
    peptideSets: Seq[PeptideSet]
  ): Array[ProteinSet] = {

    // Map peptide set by protein set id
    val pepSetByProtSetId = Map() ++ peptideSets.map { pepSet => pepSet.getProteinSetId -> pepSet }

    // Group protein set items by protein set id
    val protSetItemRecordsByProtSetId = protSetItemRecords.groupBy(
      _.getLong(ProtSetItemCols.PROTEIN_SET_ID)
    )

    // Build protein sets
    val protSets = new Array[ProteinSet](protSetRecords.length)

    for (protSetIds <- 0 until protSetRecords.length) {

      // Retrieve peptide instance record
      val protSetRecord = protSetRecords(protSetIds)
      val protSetId = protSetRecord.getLong(ProtSetCols.ID)
      
      // Retrieve corresponding protein set items
      val protSetItemRecords = protSetItemRecordsByProtSetId(protSetId)
 
      // Retrieve corresponding peptide set
      val pepSet = pepSetByProtSetId(protSetId)

      protSets(protSetIds) = this.buildProteinSet(protSetRecord,protSetItemRecords,pepSet)
    }

    protSets

  }
  
  def buildProteinSet(
    protSetRecord: IValueContainer,
    protSetItemRecords: Seq[IValueContainer],
    pepSet: PeptideSet
    ): ProteinSet = {
    
    // Retrieve protein match ids and properties
    val sameSetProtMatchIdsBuilder = Array.newBuilder[Long]
    val subSetProtMatchIdsBuilder = Array.newBuilder[Long]
    val protMatchPropertiesByIdBuilder = Map.newBuilder[Long, ProteinMatchResultSummaryProperties]
    val protMatchCoverageByIdBuilder = Map.newBuilder[Long, Float]

    for( protSetItemRecord <- protSetItemRecords ) {
      
      // Link only protein matches which do not belong to a subset
      val protMatchId = protSetItemRecord.getLong(ProtSetItemCols.PROTEIN_MATCH_ID)
      val isInSubset = protSetItemRecord.getBoolean(ProtSetItemCols.IS_IN_SUBSET)      
      val coverage = protSetItemRecord.getFloat(ProtSetItemCols.COVERAGE)
      protMatchCoverageByIdBuilder += protMatchId -> coverage
      
      val propertiesAsJsonOpt = protSetItemRecord.getStringOption(ProtSetItemCols.SERIALIZED_PROPERTIES)
      if (propertiesAsJsonOpt.isDefined) {
        protMatchPropertiesByIdBuilder += protMatchId -> ProfiJson.deserialize[ProteinMatchResultSummaryProperties](propertiesAsJsonOpt.get)
      }
      
      if ( isInSubset == false ) {
        sameSetProtMatchIdsBuilder += protMatchId
      } else {
        subSetProtMatchIdsBuilder += protMatchId
      }
      
    }
    
    // Decode JSON properties
    val propertiesAsJsonOpt = protSetRecord.getStringOption(ProtSetCols.SERIALIZED_PROPERTIES)
    val properties = propertiesAsJsonOpt.map( ProfiJson.deserialize[ProteinSetProperties](_) )
    
    new ProteinSet(
      id = protSetRecord.getLong(ProtSetCols.ID),
      isDecoy = protSetRecord.getBoolean(ProtSetCols.IS_DECOY),
      peptideSet = pepSet,
      hasPeptideSubset = pepSet.hasSubset,
      isValidated = protSetRecord.getBoolean(ProtSetCols.IS_VALIDATED),
      selectionLevel = protSetRecord.getInt(ProtSetCols.SELECTION_LEVEL),
      samesetProteinMatchIds = sameSetProtMatchIdsBuilder.result(),
      subsetProteinMatchIds = subSetProtMatchIdsBuilder.result(),
      representativeProteinMatchId = protSetRecord.getLong(ProtSetCols.REPRESENTATIVE_PROTEIN_MATCH_ID),
      proteinMatchCoverageById = protMatchCoverageByIdBuilder.result,
      masterQuantComponentId = protSetRecord.getLongOrElse(ProtSetCols.MASTER_QUANT_COMPONENT_ID,0L),
      resultSummaryId = protSetRecord.getLong(ProtSetCols.RESULT_SUMMARY_ID),
      properties = properties,
      proteinMatchPropertiesById = protMatchPropertiesByIdBuilder.result
    )
    
  }

}