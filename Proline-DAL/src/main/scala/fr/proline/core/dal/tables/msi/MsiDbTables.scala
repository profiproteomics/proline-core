package fr.proline.core.dal.tables.msi

import fr.proline.core.dal.tables._

object MsiDbAdminInfosColumns extends ColumnEnumeration {
  val modelVersion = Value("model_version")
  val dbCreationDate = Value("db_creation_date")
  val modelUpdateDate = Value("model_update_date")
}

abstract class MsiDbAdminInfosTable extends TableDefinition[MsiDbAdminInfosColumns.type]

object MsiDbAdminInfosTable extends MsiDbAdminInfosTable {
  val tableName = "admin_infos"
  val columns = MsiDbAdminInfosColumns
}

object MsiDbBioSequenceColumns extends ColumnEnumeration {
  val id = Value("id")
  val alphabet = Value("alphabet")
  val sequence = Value("sequence")
  val length = Value("length")
  val mass = Value("mass")
  val pi = Value("pi")
  val crc64 = Value("crc64")
  val serializedProperties = Value("serialized_properties")
}

abstract class MsiDbBioSequenceTable extends TableDefinition[MsiDbBioSequenceColumns.type]

object MsiDbBioSequenceTable extends MsiDbBioSequenceTable {
  val tableName = "bio_sequence"
  val columns = MsiDbBioSequenceColumns
}

object MsiDbCacheColumns extends ColumnEnumeration {
  val scope = Value("scope")
  val id = Value("id")
  val format = Value("format")
  val byteOrder = Value("byte_order")
  val data = Value("data")
  val compression = Value("compression")
  val timestamp = Value("timestamp")
  val serializedProperties = Value("serialized_properties")
}

abstract class MsiDbCacheTable extends TableDefinition[MsiDbCacheColumns.type]

object MsiDbCacheTable extends MsiDbCacheTable {
  val tableName = "cache"
  val columns = MsiDbCacheColumns
}

object MsiDbConsensusSpectrumColumns extends ColumnEnumeration {
  val id = Value("id")
  val precursorCharge = Value("precursor_charge")
  val precursorCalculatedMoz = Value("precursor_calculated_moz")
  val normalizedElutionTime = Value("normalized_elution_time")
  val isArtificial = Value("is_artificial")
  val creationMode = Value("creation_mode")
  val serializedProperties = Value("serialized_properties")
  val spectrumId = Value("spectrum_id")
  val peptideId = Value("peptide_id")
}

abstract class MsiDbConsensusSpectrumTable extends TableDefinition[MsiDbConsensusSpectrumColumns.type]

object MsiDbConsensusSpectrumTable extends MsiDbConsensusSpectrumTable {
  val tableName = "consensus_spectrum"
  val columns = MsiDbConsensusSpectrumColumns
}

object MsiDbEnzymeColumns extends ColumnEnumeration {
  val id = Value("id")
  val name = Value("name")
  val cleavageRegexp = Value("cleavage_regexp")
  val isIndependant = Value("is_independant")
  val isSemiSpecific = Value("is_semi_specific")
}

abstract class MsiDbEnzymeTable extends TableDefinition[MsiDbEnzymeColumns.type]

object MsiDbEnzymeTable extends MsiDbEnzymeTable {
  val tableName = "enzyme"
  val columns = MsiDbEnzymeColumns
}

object MsiDbInstrumentConfigColumns extends ColumnEnumeration {
  val id = Value("id")
  val name = Value("name")
  val ms1_analyzer = Value("ms1_analyzer")
  val msnAnalyzer = Value("msn_analyzer")
  val serializedProperties = Value("serialized_properties")
}

abstract class MsiDbInstrumentConfigTable extends TableDefinition[MsiDbInstrumentConfigColumns.type]

object MsiDbInstrumentConfigTable extends MsiDbInstrumentConfigTable {
  val tableName = "instrument_config"
  val columns = MsiDbInstrumentConfigColumns
}

object MsiDbIonSearchColumns extends ColumnEnumeration {
  val id = Value("id")
  val maxProteinMass = Value("max_protein_mass")
  val minProteinMass = Value("min_protein_mass")
  val proteinPi = Value("protein_pi")
}

abstract class MsiDbIonSearchTable extends TableDefinition[MsiDbIonSearchColumns.type]

object MsiDbIonSearchTable extends MsiDbIonSearchTable {
  val tableName = "ion_search"
  val columns = MsiDbIonSearchColumns
}

object MsiDbMasterQuantComponentColumns extends ColumnEnumeration {
  val id = Value("id")
  val selectionLevel = Value("selection_level")
  val serializedProperties = Value("serialized_properties")
  val objectTreeId = Value("object_tree_id")
  val schemaName = Value("schema_name")
  val resultSummaryId = Value("result_summary_id")
}

abstract class MsiDbMasterQuantComponentTable extends TableDefinition[MsiDbMasterQuantComponentColumns.type]

object MsiDbMasterQuantComponentTable extends MsiDbMasterQuantComponentTable {
  val tableName = "master_quant_component"
  val columns = MsiDbMasterQuantComponentColumns
}

object MsiDbMasterQuantPeptideIonColumns extends ColumnEnumeration {
  val id = Value("id")
  val charge = Value("charge")
  val moz = Value("moz")
  val elutionTime = Value("elution_time")
  val scanNumber = Value("scan_number")
  val serializedProperties = Value("serialized_properties")
  val lcmsFeatureId = Value("lcms_feature_id")
  val peptideId = Value("peptide_id")
  val peptideInstanceId = Value("peptide_instance_id")
  val masterQuantComponentId = Value("master_quant_component_id")
  val bestPeptideMatchId = Value("best_peptide_match_id")
  val unmodifiedPeptideIonId = Value("unmodified_peptide_ion_id")
  val resultSummaryId = Value("result_summary_id")
}

abstract class MsiDbMasterQuantPeptideIonTable extends TableDefinition[MsiDbMasterQuantPeptideIonColumns.type]

object MsiDbMasterQuantPeptideIonTable extends MsiDbMasterQuantPeptideIonTable {
  val tableName = "master_quant_peptide_ion"
  val columns = MsiDbMasterQuantPeptideIonColumns
}

object MsiDbMasterQuantReporterIonColumns extends ColumnEnumeration {
  val id = Value("id")
  val serializedProperties = Value("serialized_properties")
  val masterQuantComponentId = Value("master_quant_component_id")
  val msQueryId = Value("ms_query_id")
  val masterQuantPeptideIonId = Value("master_quant_peptide_ion_id")
  val resultSummaryId = Value("result_summary_id")
}

abstract class MsiDbMasterQuantReporterIonTable extends TableDefinition[MsiDbMasterQuantReporterIonColumns.type]

object MsiDbMasterQuantReporterIonTable extends MsiDbMasterQuantReporterIonTable {
  val tableName = "master_quant_reporter_ion"
  val columns = MsiDbMasterQuantReporterIonColumns
}

object MsiDbMsQueryColumns extends ColumnEnumeration {
  val id = Value("id")
  val initialId = Value("initial_id")
  val charge = Value("charge")
  val moz = Value("moz")
  val serializedProperties = Value("serialized_properties")
  val spectrumId = Value("spectrum_id")
  val msiSearchId = Value("msi_search_id")
}

abstract class MsiDbMsQueryTable extends TableDefinition[MsiDbMsQueryColumns.type]

object MsiDbMsQueryTable extends MsiDbMsQueryTable {
  val tableName = "ms_query"
  val columns = MsiDbMsQueryColumns
}

object MsiDbMsiSearchColumns extends ColumnEnumeration {
  val id = Value("id")
  val title = Value("title")
  val date = Value("date")
  val resultFileName = Value("result_file_name")
  val resultFileDirectory = Value("result_file_directory")
  val jobNumber = Value("job_number")
  val userName = Value("user_name")
  val userEmail = Value("user_email")
  val queriesCount = Value("queries_count")
  val submittedQueriesCount = Value("submitted_queries_count")
  val searchedSequencesCount = Value("searched_sequences_count")
  val serializedProperties = Value("serialized_properties")
  val searchSettingsId = Value("search_settings_id")
  val peaklistId = Value("peaklist_id")
}

abstract class MsiDbMsiSearchTable extends TableDefinition[MsiDbMsiSearchColumns.type]

object MsiDbMsiSearchTable extends MsiDbMsiSearchTable {
  val tableName = "msi_search"
  val columns = MsiDbMsiSearchColumns
}

object MsiDbMsiSearchObjectTreeMapColumns extends ColumnEnumeration {
  val msiSearchId = Value("msi_search_id")
  val objectTreeId = Value("object_tree_id")
  val schemaName = Value("schema_name")
}

abstract class MsiDbMsiSearchObjectTreeMapTable extends TableDefinition[MsiDbMsiSearchObjectTreeMapColumns.type]

object MsiDbMsiSearchObjectTreeMapTable extends MsiDbMsiSearchObjectTreeMapTable {
  val tableName = "msi_search_object_tree_map"
  val columns = MsiDbMsiSearchObjectTreeMapColumns
}

object MsiDbMsmsSearchColumns extends ColumnEnumeration {
  val id = Value("id")
  val fragmentChargeStates = Value("fragment_charge_states")
  val fragmentMassErrorTolerance = Value("fragment_mass_error_tolerance")
  val fragmentMassErrorToleranceUnit = Value("fragment_mass_error_tolerance_unit")
}

abstract class MsiDbMsmsSearchTable extends TableDefinition[MsiDbMsmsSearchColumns.type]

object MsiDbMsmsSearchTable extends MsiDbMsmsSearchTable {
  val tableName = "msms_search"
  val columns = MsiDbMsmsSearchColumns
}

object MsiDbObjectTreeColumns extends ColumnEnumeration {
  val id = Value("id")
  val serializedData = Value("serialized_data")
  val serializedProperties = Value("serialized_properties")
  val schemaName = Value("schema_name")
}

abstract class MsiDbObjectTreeTable extends TableDefinition[MsiDbObjectTreeColumns.type]

object MsiDbObjectTreeTable extends MsiDbObjectTreeTable {
  val tableName = "object_tree"
  val columns = MsiDbObjectTreeColumns
}

object MsiDbObjectTreeSchemaColumns extends ColumnEnumeration {
  val name = Value("name")
  val `type` = Value("type")
  val version = Value("version")
  val schema = Value("schema")
  val description = Value("description")
  val serializedProperties = Value("serialized_properties")
}

abstract class MsiDbObjectTreeSchemaTable extends TableDefinition[MsiDbObjectTreeSchemaColumns.type]

object MsiDbObjectTreeSchemaTable extends MsiDbObjectTreeSchemaTable {
  val tableName = "object_tree_schema"
  val columns = MsiDbObjectTreeSchemaColumns
}

object MsiDbPeaklistColumns extends ColumnEnumeration {
  val id = Value("id")
  val `type` = Value("type")
  val path = Value("path")
  val rawFileName = Value("raw_file_name")
  val msLevel = Value("ms_level")
  val spectrumDataCompression = Value("spectrum_data_compression")
  val serializedProperties = Value("serialized_properties")
  val peaklistSoftwareId = Value("peaklist_software_id")
}

abstract class MsiDbPeaklistTable extends TableDefinition[MsiDbPeaklistColumns.type]

object MsiDbPeaklistTable extends MsiDbPeaklistTable {
  val tableName = "peaklist"
  val columns = MsiDbPeaklistColumns
}

object MsiDbPeaklistRelationColumns extends ColumnEnumeration {
  val parentPeaklistId = Value("parent_peaklist_id")
  val childPeaklistId = Value("child_peaklist_id")
}

abstract class MsiDbPeaklistRelationTable extends TableDefinition[MsiDbPeaklistRelationColumns.type]

object MsiDbPeaklistRelationTable extends MsiDbPeaklistRelationTable {
  val tableName = "peaklist_relation"
  val columns = MsiDbPeaklistRelationColumns
}

object MsiDbPeaklistSoftwareColumns extends ColumnEnumeration {
  val id = Value("id")
  val name = Value("name")
  val version = Value("version")
  val serializedProperties = Value("serialized_properties")
}

abstract class MsiDbPeaklistSoftwareTable extends TableDefinition[MsiDbPeaklistSoftwareColumns.type]

object MsiDbPeaklistSoftwareTable extends MsiDbPeaklistSoftwareTable {
  val tableName = "peaklist_software"
  val columns = MsiDbPeaklistSoftwareColumns
}

object MsiDbPeptideColumns extends ColumnEnumeration {
  val id = Value("id")
  val sequence = Value("sequence")
  val ptmString = Value("ptm_string")
  val calculatedMass = Value("calculated_mass")
  val serializedProperties = Value("serialized_properties")
}

abstract class MsiDbPeptideTable extends TableDefinition[MsiDbPeptideColumns.type]

object MsiDbPeptideTable extends MsiDbPeptideTable {
  val tableName = "peptide"
  val columns = MsiDbPeptideColumns
}

object MsiDbPeptideInstanceColumns extends ColumnEnumeration {
  val id = Value("id")
  val peptideMatchCount = Value("peptide_match_count")
  val proteinMatchCount = Value("protein_match_count")
  val proteinSetCount = Value("protein_set_count")
  val selectionLevel = Value("selection_level")
  val elutionTime = Value("elution_time")
  val serializedProperties = Value("serialized_properties")
  val bestPeptideMatchId = Value("best_peptide_match_id")
  val peptideId = Value("peptide_id")
  val unmodifiedPeptideId = Value("unmodified_peptide_id")
  val masterQuantComponentId = Value("master_quant_component_id")
  val resultSummaryId = Value("result_summary_id")
}

abstract class MsiDbPeptideInstanceTable extends TableDefinition[MsiDbPeptideInstanceColumns.type]

object MsiDbPeptideInstanceTable extends MsiDbPeptideInstanceTable {
  val tableName = "peptide_instance"
  val columns = MsiDbPeptideInstanceColumns
}

object MsiDbPeptideInstancePeptideMatchMapColumns extends ColumnEnumeration {
  val peptideInstanceId = Value("peptide_instance_id")
  val peptideMatchId = Value("peptide_match_id")
  val serializedProperties = Value("serialized_properties")
  val resultSummaryId = Value("result_summary_id")
}

abstract class MsiDbPeptideInstancePeptideMatchMapTable extends TableDefinition[MsiDbPeptideInstancePeptideMatchMapColumns.type]

object MsiDbPeptideInstancePeptideMatchMapTable extends MsiDbPeptideInstancePeptideMatchMapTable {
  val tableName = "peptide_instance_peptide_match_map"
  val columns = MsiDbPeptideInstancePeptideMatchMapColumns
}

object MsiDbPeptideMatchColumns extends ColumnEnumeration {
  val id = Value("id")
  val charge = Value("charge")
  val experimentalMoz = Value("experimental_moz")
  val score = Value("score")
  val rank = Value("rank")
  val deltaMoz = Value("delta_moz")
  val missedCleavage = Value("missed_cleavage")
  val fragmentMatchCount = Value("fragment_match_count")
  val isDecoy = Value("is_decoy")
  val serializedProperties = Value("serialized_properties")
  val peptideId = Value("peptide_id")
  val msQueryId = Value("ms_query_id")
  val bestChildId = Value("best_child_id")
  val scoringId = Value("scoring_id")
  val resultSetId = Value("result_set_id")
}

abstract class MsiDbPeptideMatchTable extends TableDefinition[MsiDbPeptideMatchColumns.type]

object MsiDbPeptideMatchTable extends MsiDbPeptideMatchTable {
  val tableName = "peptide_match"
  val columns = MsiDbPeptideMatchColumns
}

object MsiDbPeptideMatchObjectTreeMapColumns extends ColumnEnumeration {
  val peptideMatchId = Value("peptide_match_id")
  val objectTreeId = Value("object_tree_id")
  val schemaName = Value("schema_name")
}

abstract class MsiDbPeptideMatchObjectTreeMapTable extends TableDefinition[MsiDbPeptideMatchObjectTreeMapColumns.type]

object MsiDbPeptideMatchObjectTreeMapTable extends MsiDbPeptideMatchObjectTreeMapTable {
  val tableName = "peptide_match_object_tree_map"
  val columns = MsiDbPeptideMatchObjectTreeMapColumns
}

object MsiDbPeptideMatchRelationColumns extends ColumnEnumeration {
  val parentPeptideMatchId = Value("parent_peptide_match_id")
  val childPeptideMatchId = Value("child_peptide_match_id")
  val parentResultSetId = Value("parent_result_set_id")
}

abstract class MsiDbPeptideMatchRelationTable extends TableDefinition[MsiDbPeptideMatchRelationColumns.type]

object MsiDbPeptideMatchRelationTable extends MsiDbPeptideMatchRelationTable {
  val tableName = "peptide_match_relation"
  val columns = MsiDbPeptideMatchRelationColumns
}

object MsiDbPeptideSetColumns extends ColumnEnumeration {
  val id = Value("id")
  val isSubset = Value("is_subset")
  val peptideCount = Value("peptide_count")
  val peptideMatchCount = Value("peptide_match_count")
  val serializedProperties = Value("serialized_properties")
  val proteinSetId = Value("protein_set_id")
  val resultSummaryId = Value("result_summary_id")
}

abstract class MsiDbPeptideSetTable extends TableDefinition[MsiDbPeptideSetColumns.type]

object MsiDbPeptideSetTable extends MsiDbPeptideSetTable {
  val tableName = "peptide_set"
  val columns = MsiDbPeptideSetColumns
}

object MsiDbPeptideSetPeptideInstanceItemColumns extends ColumnEnumeration {
  val peptideSetId = Value("peptide_set_id")
  val peptideInstanceId = Value("peptide_instance_id")
  val isBestPeptideSet = Value("is_best_peptide_set")
  val selectionLevel = Value("selection_level")
  val serializedProperties = Value("serialized_properties")
  val resultSummaryId = Value("result_summary_id")
}

abstract class MsiDbPeptideSetPeptideInstanceItemTable extends TableDefinition[MsiDbPeptideSetPeptideInstanceItemColumns.type]

object MsiDbPeptideSetPeptideInstanceItemTable extends MsiDbPeptideSetPeptideInstanceItemTable {
  val tableName = "peptide_set_peptide_instance_item"
  val columns = MsiDbPeptideSetPeptideInstanceItemColumns
}

object MsiDbPeptideSetProteinMatchMapColumns extends ColumnEnumeration {
  val peptideSetId = Value("peptide_set_id")
  val proteinMatchId = Value("protein_match_id")
  val resultSummaryId = Value("result_summary_id")
}

abstract class MsiDbPeptideSetProteinMatchMapTable extends TableDefinition[MsiDbPeptideSetProteinMatchMapColumns.type]

object MsiDbPeptideSetProteinMatchMapTable extends MsiDbPeptideSetProteinMatchMapTable {
  val tableName = "peptide_set_protein_match_map"
  val columns = MsiDbPeptideSetProteinMatchMapColumns
}

object MsiDbPeptideSetRelationColumns extends ColumnEnumeration {
  val peptideOversetId = Value("peptide_overset_id")
  val peptideSubsetId = Value("peptide_subset_id")
  val isStrictSubset = Value("is_strict_subset")
  val resultSummaryId = Value("result_summary_id")
}

abstract class MsiDbPeptideSetRelationTable extends TableDefinition[MsiDbPeptideSetRelationColumns.type]

object MsiDbPeptideSetRelationTable extends MsiDbPeptideSetRelationTable {
  val tableName = "peptide_set_relation"
  val columns = MsiDbPeptideSetRelationColumns
}

object MsiDbProteinMatchColumns extends ColumnEnumeration {
  val id = Value("id")
  val accession = Value("accession")
  val description = Value("description")
  val geneName = Value("gene_name")
  val score = Value("score")
  val coverage = Value("coverage")
  val peptideCount = Value("peptide_count")
  val peptideMatchCount = Value("peptide_match_count")
  val isDecoy = Value("is_decoy")
  val isLastBioSequence = Value("is_last_bio_sequence")
  val serializedProperties = Value("serialized_properties")
  val taxonId = Value("taxon_id")
  val bioSequenceId = Value("bio_sequence_id")
  val scoringId = Value("scoring_id")
  val resultSetId = Value("result_set_id")
}

abstract class MsiDbProteinMatchTable extends TableDefinition[MsiDbProteinMatchColumns.type]

object MsiDbProteinMatchTable extends MsiDbProteinMatchTable {
  val tableName = "protein_match"
  val columns = MsiDbProteinMatchColumns
}

object MsiDbProteinMatchSeqDatabaseMapColumns extends ColumnEnumeration {
  val proteinMatchId = Value("protein_match_id")
  val seqDatabaseId = Value("seq_database_id")
  val resultSetId = Value("result_set_id")
}

abstract class MsiDbProteinMatchSeqDatabaseMapTable extends TableDefinition[MsiDbProteinMatchSeqDatabaseMapColumns.type]

object MsiDbProteinMatchSeqDatabaseMapTable extends MsiDbProteinMatchSeqDatabaseMapTable {
  val tableName = "protein_match_seq_database_map"
  val columns = MsiDbProteinMatchSeqDatabaseMapColumns
}

object MsiDbProteinSetColumns extends ColumnEnumeration {
  val id = Value("id")
  val score = Value("score")
  val isValidated = Value("is_validated")
  val selectionLevel = Value("selection_level")
  val serializedProperties = Value("serialized_properties")
  val typicalProteinMatchId = Value("typical_protein_match_id")
  val scoringId = Value("scoring_id")
  val masterQuantComponentId = Value("master_quant_component_id")
  val resultSummaryId = Value("result_summary_id")
}

abstract class MsiDbProteinSetTable extends TableDefinition[MsiDbProteinSetColumns.type]

object MsiDbProteinSetTable extends MsiDbProteinSetTable {
  val tableName = "protein_set"
  val columns = MsiDbProteinSetColumns
}

object MsiDbProteinSetClusterColumns extends ColumnEnumeration {
  val id = Value("id")
  val serializedProperties = Value("serialized_properties")
  val bestProteinSetId = Value("best_protein_set_id")
  val resultSummaryId = Value("result_summary_id")
}

abstract class MsiDbProteinSetClusterTable extends TableDefinition[MsiDbProteinSetClusterColumns.type]

object MsiDbProteinSetClusterTable extends MsiDbProteinSetClusterTable {
  val tableName = "protein_set_cluster"
  val columns = MsiDbProteinSetClusterColumns
}

object MsiDbProteinSetClusterItemColumns extends ColumnEnumeration {
  val proteinSetClusterId = Value("protein_set_cluster_id")
  val proteinSetId = Value("protein_set_id")
  val resultSummaryId = Value("result_summary_id")
}

abstract class MsiDbProteinSetClusterItemTable extends TableDefinition[MsiDbProteinSetClusterItemColumns.type]

object MsiDbProteinSetClusterItemTable extends MsiDbProteinSetClusterItemTable {
  val tableName = "protein_set_cluster_item"
  val columns = MsiDbProteinSetClusterItemColumns
}

object MsiDbProteinSetObjectTreeMapColumns extends ColumnEnumeration {
  val proteinSetId = Value("protein_set_id")
  val objectTreeId = Value("object_tree_id")
  val schemaName = Value("schema_name")
}

abstract class MsiDbProteinSetObjectTreeMapTable extends TableDefinition[MsiDbProteinSetObjectTreeMapColumns.type]

object MsiDbProteinSetObjectTreeMapTable extends MsiDbProteinSetObjectTreeMapTable {
  val tableName = "protein_set_object_tree_map"
  val columns = MsiDbProteinSetObjectTreeMapColumns
}

object MsiDbProteinSetProteinMatchItemColumns extends ColumnEnumeration {
  val proteinSetId = Value("protein_set_id")
  val proteinMatchId = Value("protein_match_id")
  val serializedProperties = Value("serialized_properties")
  val resultSummaryId = Value("result_summary_id")
}

abstract class MsiDbProteinSetProteinMatchItemTable extends TableDefinition[MsiDbProteinSetProteinMatchItemColumns.type]

object MsiDbProteinSetProteinMatchItemTable extends MsiDbProteinSetProteinMatchItemTable {
  val tableName = "protein_set_protein_match_item"
  val columns = MsiDbProteinSetProteinMatchItemColumns
}

object MsiDbPtmSpecificityColumns extends ColumnEnumeration {
  val id = Value("id")
  val location = Value("location")
  val residue = Value("residue")
  val serializedProperties = Value("serialized_properties")
}

abstract class MsiDbPtmSpecificityTable extends TableDefinition[MsiDbPtmSpecificityColumns.type]

object MsiDbPtmSpecificityTable extends MsiDbPtmSpecificityTable {
  val tableName = "ptm_specificity"
  val columns = MsiDbPtmSpecificityColumns
}

object MsiDbResultSetColumns extends ColumnEnumeration {
  val id = Value("id")
  val name = Value("name")
  val description = Value("description")
  val `type` = Value("type")
  val modificationTimestamp = Value("modification_timestamp")
  val serializedProperties = Value("serialized_properties")
  val decoyResultSetId = Value("decoy_result_set_id")
  val msiSearchId = Value("msi_search_id")
}

abstract class MsiDbResultSetTable extends TableDefinition[MsiDbResultSetColumns.type]

object MsiDbResultSetTable extends MsiDbResultSetTable {
  val tableName = "result_set"
  val columns = MsiDbResultSetColumns
}

object MsiDbResultSetObjectTreeMapColumns extends ColumnEnumeration {
  val resultSetId = Value("result_set_id")
  val objectTreeId = Value("object_tree_id")
  val schemaName = Value("schema_name")
}

abstract class MsiDbResultSetObjectTreeMapTable extends TableDefinition[MsiDbResultSetObjectTreeMapColumns.type]

object MsiDbResultSetObjectTreeMapTable extends MsiDbResultSetObjectTreeMapTable {
  val tableName = "result_set_object_tree_map"
  val columns = MsiDbResultSetObjectTreeMapColumns
}

object MsiDbResultSetRelationColumns extends ColumnEnumeration {
  val parentResultSetId = Value("parent_result_set_id")
  val childResultSetId = Value("child_result_set_id")
}

abstract class MsiDbResultSetRelationTable extends TableDefinition[MsiDbResultSetRelationColumns.type]

object MsiDbResultSetRelationTable extends MsiDbResultSetRelationTable {
  val tableName = "result_set_relation"
  val columns = MsiDbResultSetRelationColumns
}

object MsiDbResultSummaryColumns extends ColumnEnumeration {
  val id = Value("id")
  val description = Value("description")
  val modificationTimestamp = Value("modification_timestamp")
  val isQuantified = Value("is_quantified")
  val serializedProperties = Value("serialized_properties")
  val decoyResultSummaryId = Value("decoy_result_summary_id")
  val resultSetId = Value("result_set_id")
}

abstract class MsiDbResultSummaryTable extends TableDefinition[MsiDbResultSummaryColumns.type]

object MsiDbResultSummaryTable extends MsiDbResultSummaryTable {
  val tableName = "result_summary"
  val columns = MsiDbResultSummaryColumns
}

object MsiDbResultSummaryObjectTreeMapColumns extends ColumnEnumeration {
  val resultSummaryId = Value("result_summary_id")
  val objectTreeId = Value("object_tree_id")
  val schemaName = Value("schema_name")
}

abstract class MsiDbResultSummaryObjectTreeMapTable extends TableDefinition[MsiDbResultSummaryObjectTreeMapColumns.type]

object MsiDbResultSummaryObjectTreeMapTable extends MsiDbResultSummaryObjectTreeMapTable {
  val tableName = "result_summary_object_tree_map"
  val columns = MsiDbResultSummaryObjectTreeMapColumns
}

object MsiDbResultSummaryRelationColumns extends ColumnEnumeration {
  val parentResultSummaryId = Value("parent_result_summary_id")
  val childResultSummaryId = Value("child_result_summary_id")
}

abstract class MsiDbResultSummaryRelationTable extends TableDefinition[MsiDbResultSummaryRelationColumns.type]

object MsiDbResultSummaryRelationTable extends MsiDbResultSummaryRelationTable {
  val tableName = "result_summary_relation"
  val columns = MsiDbResultSummaryRelationColumns
}

object MsiDbScoringColumns extends ColumnEnumeration {
  val id = Value("id")
  val searchEngine = Value("search_engine")
  val name = Value("name")
  val description = Value("description")
  val serializedProperties = Value("serialized_properties")
}

abstract class MsiDbScoringTable extends TableDefinition[MsiDbScoringColumns.type]

object MsiDbScoringTable extends MsiDbScoringTable {
  val tableName = "scoring"
  val columns = MsiDbScoringColumns
}

object MsiDbSearchSettingsColumns extends ColumnEnumeration {
  val id = Value("id")
  val softwareName = Value("software_name")
  val softwareVersion = Value("software_version")
  val taxonomy = Value("taxonomy")
  val maxMissedCleavages = Value("max_missed_cleavages")
  val peptideChargeStates = Value("peptide_charge_states")
  val peptideMassErrorTolerance = Value("peptide_mass_error_tolerance")
  val peptideMassErrorToleranceUnit = Value("peptide_mass_error_tolerance_unit")
  val quantitation = Value("quantitation")
  val isDecoy = Value("is_decoy")
  val serializedProperties = Value("serialized_properties")
  val instrumentConfigId = Value("instrument_config_id")
}

abstract class MsiDbSearchSettingsTable extends TableDefinition[MsiDbSearchSettingsColumns.type]

object MsiDbSearchSettingsTable extends MsiDbSearchSettingsTable {
  val tableName = "search_settings"
  val columns = MsiDbSearchSettingsColumns
}

object MsiDbSearchSettingsSeqDatabaseMapColumns extends ColumnEnumeration {
  val searchSettingsId = Value("search_settings_id")
  val seqDatabaseId = Value("seq_database_id")
  val searchedSequencesCount = Value("searched_sequences_count")
  val serializedProperties = Value("serialized_properties")
}

abstract class MsiDbSearchSettingsSeqDatabaseMapTable extends TableDefinition[MsiDbSearchSettingsSeqDatabaseMapColumns.type]

object MsiDbSearchSettingsSeqDatabaseMapTable extends MsiDbSearchSettingsSeqDatabaseMapTable {
  val tableName = "search_settings_seq_database_map"
  val columns = MsiDbSearchSettingsSeqDatabaseMapColumns
}

object MsiDbSeqDatabaseColumns extends ColumnEnumeration {
  val id = Value("id")
  val name = Value("name")
  val fastaFilePath = Value("fasta_file_path")
  val version = Value("version")
  val releaseDate = Value("release_date")
  val sequenceCount = Value("sequence_count")
  val serializedProperties = Value("serialized_properties")
}

abstract class MsiDbSeqDatabaseTable extends TableDefinition[MsiDbSeqDatabaseColumns.type]

object MsiDbSeqDatabaseTable extends MsiDbSeqDatabaseTable {
  val tableName = "seq_database"
  val columns = MsiDbSeqDatabaseColumns
}

object MsiDbSequenceMatchColumns extends ColumnEnumeration {
  val proteinMatchId = Value("protein_match_id")
  val peptideId = Value("peptide_id")
  val start = Value("start")
  val stop = Value("stop")
  val residueBefore = Value("residue_before")
  val residueAfter = Value("residue_after")
  val isDecoy = Value("is_decoy")
  val serializedProperties = Value("serialized_properties")
  val bestPeptideMatchId = Value("best_peptide_match_id")
  val resultSetId = Value("result_set_id")
}

abstract class MsiDbSequenceMatchTable extends TableDefinition[MsiDbSequenceMatchColumns.type]

object MsiDbSequenceMatchTable extends MsiDbSequenceMatchTable {
  val tableName = "sequence_match"
  val columns = MsiDbSequenceMatchColumns
}

object MsiDbSpectrumColumns extends ColumnEnumeration {
  val id = Value("id")
  val title = Value("title")
  val precursorMoz = Value("precursor_moz")
  val precursorIntensity = Value("precursor_intensity")
  val precursorCharge = Value("precursor_charge")
  val isSummed = Value("is_summed")
  val firstCycle = Value("first_cycle")
  val lastCycle = Value("last_cycle")
  val firstScan = Value("first_scan")
  val lastScan = Value("last_scan")
  val firstTime = Value("first_time")
  val lastTime = Value("last_time")
  val mozList = Value("moz_list")
  val intensityList = Value("intensity_list")
  val peakCount = Value("peak_count")
  val serializedProperties = Value("serialized_properties")
  val peaklistId = Value("peaklist_id")
  val instrumentConfigId = Value("instrument_config_id")
}

abstract class MsiDbSpectrumTable extends TableDefinition[MsiDbSpectrumColumns.type]

object MsiDbSpectrumTable extends MsiDbSpectrumTable {
  val tableName = "spectrum"
  val columns = MsiDbSpectrumColumns
}

object MsiDbUsedEnzymeColumns extends ColumnEnumeration {
  val searchSettingsId = Value("search_settings_id")
  val enzymeId = Value("enzyme_id")
}

abstract class MsiDbUsedEnzymeTable extends TableDefinition[MsiDbUsedEnzymeColumns.type]

object MsiDbUsedEnzymeTable extends MsiDbUsedEnzymeTable {
  val tableName = "used_enzyme"
  val columns = MsiDbUsedEnzymeColumns
}

object MsiDbUsedPtmColumns extends ColumnEnumeration {
  val searchSettingsId = Value("search_settings_id")
  val ptmSpecificityId = Value("ptm_specificity_id")
  val shortName = Value("short_name")
  val isFixed = Value("is_fixed")
  val `type` = Value("type")
}

abstract class MsiDbUsedPtmTable extends TableDefinition[MsiDbUsedPtmColumns.type]

object MsiDbUsedPtmTable extends MsiDbUsedPtmTable {
  val tableName = "used_ptm"
  val columns = MsiDbUsedPtmColumns
}

