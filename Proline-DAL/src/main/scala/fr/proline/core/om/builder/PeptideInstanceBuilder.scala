package fr.proline.core.om.builder

import scala.collection.mutable.ArrayBuffer
import fr.profi.util.primitives._
import fr.profi.util.serialization._
import fr.proline.core.dal.tables.msi._
import fr.proline.core.om.model.msi._
import fr.proline.core.om.provider.msi.IPeptideProvider

/**
 * @author David Bouyssie
 *
 */
object PeptideInstanceBuilder {
  
  protected val PepInstCols = MsiDbPeptideInstanceColumns
  protected val PepMatchMappingCols = MsiDbPeptideInstancePeptideMatchMapColumns
  
  def buildPeptideInstances(
    pepInstRecords: Seq[IValueContainer],
    pepInstPepMatchMapRecords: Seq[IValueContainer],
    peptideProvider: IPeptideProvider
  ): Array[PeptideInstance] = {
    
    // Load peptides
    val uniqPepIds = pepInstRecords.map( _.getLong(PepInstCols.PEPTIDE_ID) ).distinct
    val peptides = peptideProvider.getPeptides(uniqPepIds)

    // Map peptides by their id
    val peptideById = Map() ++ peptides.map { pep => (pep.id -> pep) }

    // Group peptide matches mapping by peptide instance id
    val pepMatchesMappingsByPepInstId = pepInstPepMatchMapRecords.groupBy { r =>
      r.getLong(PepMatchMappingCols.PEPTIDE_INSTANCE_ID)
    }

    // Build peptide instances
    val pepInsts = new Array[PeptideInstance](pepInstRecords.length)

    for (pepInstIdx <- 0 until pepInstRecords.length) {

      // Retrieve peptide instance record
      val pepInstRecord = pepInstRecords(pepInstIdx)

      // Retrieve the corresponding peptide
      val pepId = pepInstRecord.getLong(PepInstCols.PEPTIDE_ID)
      require(peptideById.contains(pepId), "undefined peptide with id ='" + pepId + "'")
      val peptide = peptideById(pepId)

      // Retrieve peptide match ids and properties
      val pepInstId = pepInstRecord.getLong(PepInstCols.ID)
      val pepMatchIds = new ArrayBuffer[Long]()
      val pepMatchPropertyMapBuilder = Map.newBuilder[Long, PeptideMatchResultSummaryProperties]

      pepMatchesMappingsByPepInstId(pepInstId).foreach { pepMatchMapping =>
        val pepMatchId = pepMatchMapping.getLong(PepMatchMappingCols.PEPTIDE_MATCH_ID)
        pepMatchIds += pepMatchId

        val propertiesAsJSON = pepMatchMapping.getString(PepMatchMappingCols.SERIALIZED_PROPERTIES)
        if (propertiesAsJSON != null) {
          pepMatchPropertyMapBuilder += pepMatchId -> ProfiJson.deserialize[PeptideMatchResultSummaryProperties](propertiesAsJSON)
        }
      }

      // Decode JSON properties
      val propertiesAsJSONOpt = pepInstRecord.getStringOption(PepInstCols.SERIALIZED_PROPERTIES)
      
      val pepInst = new PeptideInstance(
        id = pepInstId,
        peptide = peptide,
        proteinMatchesCount = pepInstRecord.getInt(PepInstCols.PROTEIN_MATCH_COUNT),
        proteinSetsCount = pepInstRecord.getInt(PepInstCols.PROTEIN_SET_COUNT),
        validatedProteinSetsCount = pepInstRecord.getInt(PepInstCols.VALIDATED_PROTEIN_SET_COUNT),
        totalLeavesMatchCount = pepInstRecord.getInt(PepInstCols.TOTAL_LEAVES_MATCH_COUNT),
        selectionLevel = pepInstRecord.getInt(PepInstCols.SELECTION_LEVEL),
        elutionTime = pepInstRecord.getFloatOrElse(PepInstCols.ELUTION_TIME,0f),
        peptideMatchIds = pepMatchIds.toArray,
        bestPeptideMatchId = pepInstRecord.getLong(PepInstCols.BEST_PEPTIDE_MATCH_ID),
        unmodifiedPeptideId = pepInstRecord.getLongOrElse(PepInstCols.UNMODIFIED_PEPTIDE_ID, 0L),
        resultSummaryId = pepInstRecord.getLong(PepInstCols.RESULT_SUMMARY_ID),
        properties = propertiesAsJSONOpt.map(ProfiJson.deserialize[PeptideInstanceProperties](_)),
        peptideMatchPropertiesById = pepMatchPropertyMapBuilder.result()
      )

      pepInsts(pepInstIdx) = pepInst

    }

    pepInsts

  }
  
}