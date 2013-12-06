package fr.proline.core.dal.tables.msi

import fr.proline.core.dal.tables._

object MsiDbAdminInfosColumns extends ColumnEnumeration {
  val $tableName = MsiDbAdminInfosTable.name
  val MODEL_VERSION = Column("model_version")
  val DB_CREATION_DATE = Column("db_creation_date")
  val MODEL_UPDATE_DATE = Column("model_update_date")
}

abstract class MsiDbAdminInfosTable extends TableDefinition[MsiDbAdminInfosColumns.type]

object MsiDbAdminInfosTable extends MsiDbAdminInfosTable {
  val name = "admin_infos"
  val columns = MsiDbAdminInfosColumns
}

object MsiDbBioSequenceColumns extends ColumnEnumeration {
  val $tableName = MsiDbBioSequenceTable.name
  val ID = Column("id")
  val ALPHABET = Column("alphabet")
  val SEQUENCE = Column("sequence")
  val LENGTH = Column("length")
  val MASS = Column("mass")
  val PI = Column("pi")
  val CRC64 = Column("crc64")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
}

abstract class MsiDbBioSequenceTable extends TableDefinition[MsiDbBioSequenceColumns.type]

object MsiDbBioSequenceTable extends MsiDbBioSequenceTable {
  val name = "bio_sequence"
  val columns = MsiDbBioSequenceColumns
}

object MsiDbCacheColumns extends ColumnEnumeration {
  val $tableName = MsiDbCacheTable.name
  val SCOPE = Column("scope")
  val ID = Column("id")
  val FORMAT = Column("format")
  val BYTE_ORDER = Column("byte_order")
  val DATA = Column("data")
  val COMPRESSION = Column("compression")
  val TIMESTAMP = Column("timestamp")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
}

abstract class MsiDbCacheTable extends TableDefinition[MsiDbCacheColumns.type]

object MsiDbCacheTable extends MsiDbCacheTable {
  val name = "cache"
  val columns = MsiDbCacheColumns
}

object MsiDbConsensusSpectrumColumns extends ColumnEnumeration {
  val $tableName = MsiDbConsensusSpectrumTable.name
  val ID = Column("id")
  val PRECURSOR_CHARGE = Column("precursor_charge")
  val PRECURSOR_CALCULATED_MOZ = Column("precursor_calculated_moz")
  val NORMALIZED_ELUTION_TIME = Column("normalized_elution_time")
  val IS_ARTIFICIAL = Column("is_artificial")
  val CREATION_MODE = Column("creation_mode")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val SPECTRUM_ID = Column("spectrum_id")
  val PEPTIDE_ID = Column("peptide_id")
}

abstract class MsiDbConsensusSpectrumTable extends TableDefinition[MsiDbConsensusSpectrumColumns.type]

object MsiDbConsensusSpectrumTable extends MsiDbConsensusSpectrumTable {
  val name = "consensus_spectrum"
  val columns = MsiDbConsensusSpectrumColumns
}

object MsiDbEnzymeColumns extends ColumnEnumeration {
  val $tableName = MsiDbEnzymeTable.name
  val ID = Column("id")
  val NAME = Column("name")
  val CLEAVAGE_REGEXP = Column("cleavage_regexp")
  val IS_INDEPENDANT = Column("is_independant")
  val IS_SEMI_SPECIFIC = Column("is_semi_specific")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
}

abstract class MsiDbEnzymeTable extends TableDefinition[MsiDbEnzymeColumns.type]

object MsiDbEnzymeTable extends MsiDbEnzymeTable {
  val name = "enzyme"
  val columns = MsiDbEnzymeColumns
}

object MsiDbInstrumentConfigColumns extends ColumnEnumeration {
  val $tableName = MsiDbInstrumentConfigTable.name
  val ID = Column("id")
  val NAME = Column("name")
  val MS1_ANALYZER = Column("ms1_analyzer")
  val MSN_ANALYZER = Column("msn_analyzer")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
}

abstract class MsiDbInstrumentConfigTable extends TableDefinition[MsiDbInstrumentConfigColumns.type]

object MsiDbInstrumentConfigTable extends MsiDbInstrumentConfigTable {
  val name = "instrument_config"
  val columns = MsiDbInstrumentConfigColumns
}

object MsiDbIonSearchColumns extends ColumnEnumeration {
  val $tableName = MsiDbIonSearchTable.name
  val ID = Column("id")
  val MAX_PROTEIN_MASS = Column("max_protein_mass")
  val MIN_PROTEIN_MASS = Column("min_protein_mass")
  val PROTEIN_PI = Column("protein_pi")
}

abstract class MsiDbIonSearchTable extends TableDefinition[MsiDbIonSearchColumns.type]

object MsiDbIonSearchTable extends MsiDbIonSearchTable {
  val name = "ion_search"
  val columns = MsiDbIonSearchColumns
}

object MsiDbMasterQuantComponentColumns extends ColumnEnumeration {
  val $tableName = MsiDbMasterQuantComponentTable.name
  val ID = Column("id")
  val SELECTION_LEVEL = Column("selection_level")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val OBJECT_TREE_ID = Column("object_tree_id")
  val SCHEMA_NAME = Column("schema_name")
  val RESULT_SUMMARY_ID = Column("result_summary_id")
}

abstract class MsiDbMasterQuantComponentTable extends TableDefinition[MsiDbMasterQuantComponentColumns.type]

object MsiDbMasterQuantComponentTable extends MsiDbMasterQuantComponentTable {
  val name = "master_quant_component"
  val columns = MsiDbMasterQuantComponentColumns
}

object MsiDbMasterQuantPeptideIonColumns extends ColumnEnumeration {
  val $tableName = MsiDbMasterQuantPeptideIonTable.name
  val ID = Column("id")
  val CHARGE = Column("charge")
  val MOZ = Column("moz")
  val ELUTION_TIME = Column("elution_time")
  val SCAN_NUMBER = Column("scan_number")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val LCMS_FEATURE_ID = Column("lcms_feature_id")
  val PEPTIDE_ID = Column("peptide_id")
  val PEPTIDE_INSTANCE_ID = Column("peptide_instance_id")
  val MASTER_QUANT_PEPTIDE_ID = Column("master_quant_peptide_id")
  val MASTER_QUANT_COMPONENT_ID = Column("master_quant_component_id")
  val BEST_PEPTIDE_MATCH_ID = Column("best_peptide_match_id")
  val UNMODIFIED_PEPTIDE_ION_ID = Column("unmodified_peptide_ion_id")
  val RESULT_SUMMARY_ID = Column("result_summary_id")
}

abstract class MsiDbMasterQuantPeptideIonTable extends TableDefinition[MsiDbMasterQuantPeptideIonColumns.type]

object MsiDbMasterQuantPeptideIonTable extends MsiDbMasterQuantPeptideIonTable {
  val name = "master_quant_peptide_ion"
  val columns = MsiDbMasterQuantPeptideIonColumns
}

object MsiDbMasterQuantReporterIonColumns extends ColumnEnumeration {
  val $tableName = MsiDbMasterQuantReporterIonTable.name
  val ID = Column("id")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val MASTER_QUANT_COMPONENT_ID = Column("master_quant_component_id")
  val MS_QUERY_ID = Column("ms_query_id")
  val MASTER_QUANT_PEPTIDE_ION_ID = Column("master_quant_peptide_ion_id")
  val RESULT_SUMMARY_ID = Column("result_summary_id")
}

abstract class MsiDbMasterQuantReporterIonTable extends TableDefinition[MsiDbMasterQuantReporterIonColumns.type]

object MsiDbMasterQuantReporterIonTable extends MsiDbMasterQuantReporterIonTable {
  val name = "master_quant_reporter_ion"
  val columns = MsiDbMasterQuantReporterIonColumns
}

object MsiDbMsQueryColumns extends ColumnEnumeration {
  val $tableName = MsiDbMsQueryTable.name
  val ID = Column("id")
  val INITIAL_ID = Column("initial_id")
  val CHARGE = Column("charge")
  val MOZ = Column("moz")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val SPECTRUM_ID = Column("spectrum_id")
  val MSI_SEARCH_ID = Column("msi_search_id")
}

abstract class MsiDbMsQueryTable extends TableDefinition[MsiDbMsQueryColumns.type]

object MsiDbMsQueryTable extends MsiDbMsQueryTable {
  val name = "ms_query"
  val columns = MsiDbMsQueryColumns
}

object MsiDbMsiSearchColumns extends ColumnEnumeration {
  val $tableName = MsiDbMsiSearchTable.name
  val ID = Column("id")
  val TITLE = Column("title")
  val DATE = Column("date")
  val RESULT_FILE_NAME = Column("result_file_name")
  val RESULT_FILE_DIRECTORY = Column("result_file_directory")
  val JOB_NUMBER = Column("job_number")
  val USER_NAME = Column("user_name")
  val USER_EMAIL = Column("user_email")
  val QUERIES_COUNT = Column("queries_count")
  val SUBMITTED_QUERIES_COUNT = Column("submitted_queries_count")
  val SEARCHED_SEQUENCES_COUNT = Column("searched_sequences_count")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val SEARCH_SETTINGS_ID = Column("search_settings_id")
  val PEAKLIST_ID = Column("peaklist_id")
}

abstract class MsiDbMsiSearchTable extends TableDefinition[MsiDbMsiSearchColumns.type]

object MsiDbMsiSearchTable extends MsiDbMsiSearchTable {
  val name = "msi_search"
  val columns = MsiDbMsiSearchColumns
}

object MsiDbMsiSearchObjectTreeMapColumns extends ColumnEnumeration {
  val $tableName = MsiDbMsiSearchObjectTreeMapTable.name
  val MSI_SEARCH_ID = Column("msi_search_id")
  val OBJECT_TREE_ID = Column("object_tree_id")
  val SCHEMA_NAME = Column("schema_name")
}

abstract class MsiDbMsiSearchObjectTreeMapTable extends TableDefinition[MsiDbMsiSearchObjectTreeMapColumns.type]

object MsiDbMsiSearchObjectTreeMapTable extends MsiDbMsiSearchObjectTreeMapTable {
  val name = "msi_search_object_tree_map"
  val columns = MsiDbMsiSearchObjectTreeMapColumns
}

object MsiDbMsmsSearchColumns extends ColumnEnumeration {
  val $tableName = MsiDbMsmsSearchTable.name
  val ID = Column("id")
  val FRAGMENT_CHARGE_STATES = Column("fragment_charge_states")
  val FRAGMENT_MASS_ERROR_TOLERANCE = Column("fragment_mass_error_tolerance")
  val FRAGMENT_MASS_ERROR_TOLERANCE_UNIT = Column("fragment_mass_error_tolerance_unit")
}

abstract class MsiDbMsmsSearchTable extends TableDefinition[MsiDbMsmsSearchColumns.type]

object MsiDbMsmsSearchTable extends MsiDbMsmsSearchTable {
  val name = "msms_search"
  val columns = MsiDbMsmsSearchColumns
}

object MsiDbObjectTreeColumns extends ColumnEnumeration {
  val $tableName = MsiDbObjectTreeTable.name
  val ID = Column("id")
  val BLOB_DATA = Column("blob_data")
  val CLOB_DATA = Column("clob_data")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val SCHEMA_NAME = Column("schema_name")
}

abstract class MsiDbObjectTreeTable extends TableDefinition[MsiDbObjectTreeColumns.type]

object MsiDbObjectTreeTable extends MsiDbObjectTreeTable {
  val name = "object_tree"
  val columns = MsiDbObjectTreeColumns
}

object MsiDbObjectTreeSchemaColumns extends ColumnEnumeration {
  val $tableName = MsiDbObjectTreeSchemaTable.name
  val NAME = Column("name")
  val TYPE = Column("type")
  val IS_BINARY_MODE = Column("is_binary_mode")
  val VERSION = Column("version")
  val SCHEMA = Column("schema")
  val DESCRIPTION = Column("description")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
}

abstract class MsiDbObjectTreeSchemaTable extends TableDefinition[MsiDbObjectTreeSchemaColumns.type]

object MsiDbObjectTreeSchemaTable extends MsiDbObjectTreeSchemaTable {
  val name = "object_tree_schema"
  val columns = MsiDbObjectTreeSchemaColumns
}

object MsiDbPeaklistColumns extends ColumnEnumeration {
  val $tableName = MsiDbPeaklistTable.name
  val ID = Column("id")
  val TYPE = Column("type")
  val PATH = Column("path")
  val RAW_FILE_NAME = Column("raw_file_name")
  val MS_LEVEL = Column("ms_level")
  val SPECTRUM_DATA_COMPRESSION = Column("spectrum_data_compression")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val PEAKLIST_SOFTWARE_ID = Column("peaklist_software_id")
}

abstract class MsiDbPeaklistTable extends TableDefinition[MsiDbPeaklistColumns.type]

object MsiDbPeaklistTable extends MsiDbPeaklistTable {
  val name = "peaklist"
  val columns = MsiDbPeaklistColumns
}

object MsiDbPeaklistRelationColumns extends ColumnEnumeration {
  val $tableName = MsiDbPeaklistRelationTable.name
  val PARENT_PEAKLIST_ID = Column("parent_peaklist_id")
  val CHILD_PEAKLIST_ID = Column("child_peaklist_id")
}

abstract class MsiDbPeaklistRelationTable extends TableDefinition[MsiDbPeaklistRelationColumns.type]

object MsiDbPeaklistRelationTable extends MsiDbPeaklistRelationTable {
  val name = "peaklist_relation"
  val columns = MsiDbPeaklistRelationColumns
}

object MsiDbPeaklistSoftwareColumns extends ColumnEnumeration {
  val $tableName = MsiDbPeaklistSoftwareTable.name
  val ID = Column("id")
  val NAME = Column("name")
  val VERSION = Column("version")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
}

abstract class MsiDbPeaklistSoftwareTable extends TableDefinition[MsiDbPeaklistSoftwareColumns.type]

object MsiDbPeaklistSoftwareTable extends MsiDbPeaklistSoftwareTable {
  val name = "peaklist_software"
  val columns = MsiDbPeaklistSoftwareColumns
}

object MsiDbPeptideColumns extends ColumnEnumeration {
  val $tableName = MsiDbPeptideTable.name
  val ID = Column("id")
  val SEQUENCE = Column("sequence")
  val PTM_STRING = Column("ptm_string")
  val CALCULATED_MASS = Column("calculated_mass")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
}

abstract class MsiDbPeptideTable extends TableDefinition[MsiDbPeptideColumns.type]

object MsiDbPeptideTable extends MsiDbPeptideTable {
  val name = "peptide"
  val columns = MsiDbPeptideColumns
}

object MsiDbPeptideInstanceColumns extends ColumnEnumeration {
  val $tableName = MsiDbPeptideInstanceTable.name
  val ID = Column("id")
  val PEPTIDE_MATCH_COUNT = Column("peptide_match_count")
  val PROTEIN_MATCH_COUNT = Column("protein_match_count")
  val PROTEIN_SET_COUNT = Column("protein_set_count")
  val VALIDATED_PROTEIN_SET_COUNT = Column("validated_protein_set_count")
  val TOTAL_LEAVES_MATCH_COUNT = Column("total_leaves_match_count")
  val SELECTION_LEVEL = Column("selection_level")
  val ELUTION_TIME = Column("elution_time")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val BEST_PEPTIDE_MATCH_ID = Column("best_peptide_match_id")
  val PEPTIDE_ID = Column("peptide_id")
  val UNMODIFIED_PEPTIDE_ID = Column("unmodified_peptide_id")
  val MASTER_QUANT_COMPONENT_ID = Column("master_quant_component_id")
  val RESULT_SUMMARY_ID = Column("result_summary_id")
}

abstract class MsiDbPeptideInstanceTable extends TableDefinition[MsiDbPeptideInstanceColumns.type]

object MsiDbPeptideInstanceTable extends MsiDbPeptideInstanceTable {
  val name = "peptide_instance"
  val columns = MsiDbPeptideInstanceColumns
}

object MsiDbPeptideInstancePeptideMatchMapColumns extends ColumnEnumeration {
  val $tableName = MsiDbPeptideInstancePeptideMatchMapTable.name
  val PEPTIDE_INSTANCE_ID = Column("peptide_instance_id")
  val PEPTIDE_MATCH_ID = Column("peptide_match_id")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val RESULT_SUMMARY_ID = Column("result_summary_id")
}

abstract class MsiDbPeptideInstancePeptideMatchMapTable extends TableDefinition[MsiDbPeptideInstancePeptideMatchMapColumns.type]

object MsiDbPeptideInstancePeptideMatchMapTable extends MsiDbPeptideInstancePeptideMatchMapTable {
  val name = "peptide_instance_peptide_match_map"
  val columns = MsiDbPeptideInstancePeptideMatchMapColumns
}

object MsiDbPeptideMatchColumns extends ColumnEnumeration {
  val $tableName = MsiDbPeptideMatchTable.name
  val ID = Column("id")
  val CHARGE = Column("charge")
  val EXPERIMENTAL_MOZ = Column("experimental_moz")
  val SCORE = Column("score")
  val RANK = Column("rank")
  val DELTA_MOZ = Column("delta_moz")
  val MISSED_CLEAVAGE = Column("missed_cleavage")
  val FRAGMENT_MATCH_COUNT = Column("fragment_match_count")
  val IS_DECOY = Column("is_decoy")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val PEPTIDE_ID = Column("peptide_id")
  val MS_QUERY_ID = Column("ms_query_id")
  val BEST_CHILD_ID = Column("best_child_id")
  val SCORING_ID = Column("scoring_id")
  val RESULT_SET_ID = Column("result_set_id")
}

abstract class MsiDbPeptideMatchTable extends TableDefinition[MsiDbPeptideMatchColumns.type]

object MsiDbPeptideMatchTable extends MsiDbPeptideMatchTable {
  val name = "peptide_match"
  val columns = MsiDbPeptideMatchColumns
}

object MsiDbPeptideMatchObjectTreeMapColumns extends ColumnEnumeration {
  val $tableName = MsiDbPeptideMatchObjectTreeMapTable.name
  val PEPTIDE_MATCH_ID = Column("peptide_match_id")
  val OBJECT_TREE_ID = Column("object_tree_id")
  val SCHEMA_NAME = Column("schema_name")
}

abstract class MsiDbPeptideMatchObjectTreeMapTable extends TableDefinition[MsiDbPeptideMatchObjectTreeMapColumns.type]

object MsiDbPeptideMatchObjectTreeMapTable extends MsiDbPeptideMatchObjectTreeMapTable {
  val name = "peptide_match_object_tree_map"
  val columns = MsiDbPeptideMatchObjectTreeMapColumns
}

object MsiDbPeptideMatchRelationColumns extends ColumnEnumeration {
  val $tableName = MsiDbPeptideMatchRelationTable.name
  val PARENT_PEPTIDE_MATCH_ID = Column("parent_peptide_match_id")
  val CHILD_PEPTIDE_MATCH_ID = Column("child_peptide_match_id")
  val PARENT_RESULT_SET_ID = Column("parent_result_set_id")
}

abstract class MsiDbPeptideMatchRelationTable extends TableDefinition[MsiDbPeptideMatchRelationColumns.type]

object MsiDbPeptideMatchRelationTable extends MsiDbPeptideMatchRelationTable {
  val name = "peptide_match_relation"
  val columns = MsiDbPeptideMatchRelationColumns
}

object MsiDbPeptideReadablePtmStringColumns extends ColumnEnumeration {
  val $tableName = MsiDbPeptideReadablePtmStringTable.name
  val ID = Column("id")
  val READABLE_PTM_STRING = Column("readable_ptm_string")
  val PEPTIDE_ID = Column("peptide_id")
  val RESULT_SET_ID = Column("result_set_id")
}

abstract class MsiDbPeptideReadablePtmStringTable extends TableDefinition[MsiDbPeptideReadablePtmStringColumns.type]

object MsiDbPeptideReadablePtmStringTable extends MsiDbPeptideReadablePtmStringTable {
  val name = "peptide_readable_ptm_string"
  val columns = MsiDbPeptideReadablePtmStringColumns
}

object MsiDbPeptideSetColumns extends ColumnEnumeration {
  val $tableName = MsiDbPeptideSetTable.name
  val ID = Column("id")
  val IS_SUBSET = Column("is_subset")
  val SCORE = Column("score")
  val PEPTIDE_COUNT = Column("peptide_count")
  val PEPTIDE_MATCH_COUNT = Column("peptide_match_count")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val PROTEIN_SET_ID = Column("protein_set_id")
  val SCORING_ID = Column("scoring_id")
  val RESULT_SUMMARY_ID = Column("result_summary_id")
}

abstract class MsiDbPeptideSetTable extends TableDefinition[MsiDbPeptideSetColumns.type]

object MsiDbPeptideSetTable extends MsiDbPeptideSetTable {
  val name = "peptide_set"
  val columns = MsiDbPeptideSetColumns
}

object MsiDbPeptideSetPeptideInstanceItemColumns extends ColumnEnumeration {
  val $tableName = MsiDbPeptideSetPeptideInstanceItemTable.name
  val PEPTIDE_SET_ID = Column("peptide_set_id")
  val PEPTIDE_INSTANCE_ID = Column("peptide_instance_id")
  val IS_BEST_PEPTIDE_SET = Column("is_best_peptide_set")
  val SELECTION_LEVEL = Column("selection_level")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val RESULT_SUMMARY_ID = Column("result_summary_id")
}

abstract class MsiDbPeptideSetPeptideInstanceItemTable extends TableDefinition[MsiDbPeptideSetPeptideInstanceItemColumns.type]

object MsiDbPeptideSetPeptideInstanceItemTable extends MsiDbPeptideSetPeptideInstanceItemTable {
  val name = "peptide_set_peptide_instance_item"
  val columns = MsiDbPeptideSetPeptideInstanceItemColumns
}

object MsiDbPeptideSetProteinMatchMapColumns extends ColumnEnumeration {
  val $tableName = MsiDbPeptideSetProteinMatchMapTable.name
  val PEPTIDE_SET_ID = Column("peptide_set_id")
  val PROTEIN_MATCH_ID = Column("protein_match_id")
  val RESULT_SUMMARY_ID = Column("result_summary_id")
}

abstract class MsiDbPeptideSetProteinMatchMapTable extends TableDefinition[MsiDbPeptideSetProteinMatchMapColumns.type]

object MsiDbPeptideSetProteinMatchMapTable extends MsiDbPeptideSetProteinMatchMapTable {
  val name = "peptide_set_protein_match_map"
  val columns = MsiDbPeptideSetProteinMatchMapColumns
}

object MsiDbPeptideSetRelationColumns extends ColumnEnumeration {
  val $tableName = MsiDbPeptideSetRelationTable.name
  val PEPTIDE_OVERSET_ID = Column("peptide_overset_id")
  val PEPTIDE_SUBSET_ID = Column("peptide_subset_id")
  val IS_STRICT_SUBSET = Column("is_strict_subset")
  val RESULT_SUMMARY_ID = Column("result_summary_id")
}

abstract class MsiDbPeptideSetRelationTable extends TableDefinition[MsiDbPeptideSetRelationColumns.type]

object MsiDbPeptideSetRelationTable extends MsiDbPeptideSetRelationTable {
  val name = "peptide_set_relation"
  val columns = MsiDbPeptideSetRelationColumns
}

object MsiDbProteinMatchColumns extends ColumnEnumeration {
  val $tableName = MsiDbProteinMatchTable.name
  val ID = Column("id")
  val ACCESSION = Column("accession")
  val DESCRIPTION = Column("description")
  val GENE_NAME = Column("gene_name")
  val SCORE = Column("score")
  val COVERAGE = Column("coverage")
  val PEPTIDE_COUNT = Column("peptide_count")
  val PEPTIDE_MATCH_COUNT = Column("peptide_match_count")
  val IS_DECOY = Column("is_decoy")
  val IS_LAST_BIO_SEQUENCE = Column("is_last_bio_sequence")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val TAXON_ID = Column("taxon_id")
  val BIO_SEQUENCE_ID = Column("bio_sequence_id")
  val SCORING_ID = Column("scoring_id")
  val RESULT_SET_ID = Column("result_set_id")
}

abstract class MsiDbProteinMatchTable extends TableDefinition[MsiDbProteinMatchColumns.type]

object MsiDbProteinMatchTable extends MsiDbProteinMatchTable {
  val name = "protein_match"
  val columns = MsiDbProteinMatchColumns
}

object MsiDbProteinMatchSeqDatabaseMapColumns extends ColumnEnumeration {
  val $tableName = MsiDbProteinMatchSeqDatabaseMapTable.name
  val PROTEIN_MATCH_ID = Column("protein_match_id")
  val SEQ_DATABASE_ID = Column("seq_database_id")
  val RESULT_SET_ID = Column("result_set_id")
}

abstract class MsiDbProteinMatchSeqDatabaseMapTable extends TableDefinition[MsiDbProteinMatchSeqDatabaseMapColumns.type]

object MsiDbProteinMatchSeqDatabaseMapTable extends MsiDbProteinMatchSeqDatabaseMapTable {
  val name = "protein_match_seq_database_map"
  val columns = MsiDbProteinMatchSeqDatabaseMapColumns
}

object MsiDbProteinSetColumns extends ColumnEnumeration {
  val $tableName = MsiDbProteinSetTable.name
  val ID = Column("id")
  val IS_VALIDATED = Column("is_validated")
  val SELECTION_LEVEL = Column("selection_level")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val TYPICAL_PROTEIN_MATCH_ID = Column("typical_protein_match_id")
  val MASTER_QUANT_COMPONENT_ID = Column("master_quant_component_id")
  val RESULT_SUMMARY_ID = Column("result_summary_id")
}

abstract class MsiDbProteinSetTable extends TableDefinition[MsiDbProteinSetColumns.type]

object MsiDbProteinSetTable extends MsiDbProteinSetTable {
  val name = "protein_set"
  val columns = MsiDbProteinSetColumns
}

object MsiDbProteinSetObjectTreeMapColumns extends ColumnEnumeration {
  val $tableName = MsiDbProteinSetObjectTreeMapTable.name
  val PROTEIN_SET_ID = Column("protein_set_id")
  val OBJECT_TREE_ID = Column("object_tree_id")
  val SCHEMA_NAME = Column("schema_name")
}

abstract class MsiDbProteinSetObjectTreeMapTable extends TableDefinition[MsiDbProteinSetObjectTreeMapColumns.type]

object MsiDbProteinSetObjectTreeMapTable extends MsiDbProteinSetObjectTreeMapTable {
  val name = "protein_set_object_tree_map"
  val columns = MsiDbProteinSetObjectTreeMapColumns
}

object MsiDbProteinSetProteinMatchItemColumns extends ColumnEnumeration {
  val $tableName = MsiDbProteinSetProteinMatchItemTable.name
  val PROTEIN_SET_ID = Column("protein_set_id")
  val PROTEIN_MATCH_ID = Column("protein_match_id")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val RESULT_SUMMARY_ID = Column("result_summary_id")
}

abstract class MsiDbProteinSetProteinMatchItemTable extends TableDefinition[MsiDbProteinSetProteinMatchItemColumns.type]

object MsiDbProteinSetProteinMatchItemTable extends MsiDbProteinSetProteinMatchItemTable {
  val name = "protein_set_protein_match_item"
  val columns = MsiDbProteinSetProteinMatchItemColumns
}

object MsiDbPtmSpecificityColumns extends ColumnEnumeration {
  val $tableName = MsiDbPtmSpecificityTable.name
  val ID = Column("id")
  val LOCATION = Column("location")
  val RESIDUE = Column("residue")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
}

abstract class MsiDbPtmSpecificityTable extends TableDefinition[MsiDbPtmSpecificityColumns.type]

object MsiDbPtmSpecificityTable extends MsiDbPtmSpecificityTable {
  val name = "ptm_specificity"
  val columns = MsiDbPtmSpecificityColumns
}

object MsiDbResultSetColumns extends ColumnEnumeration {
  val $tableName = MsiDbResultSetTable.name
  val ID = Column("id")
  val NAME = Column("name")
  val DESCRIPTION = Column("description")
  val TYPE = Column("type")
  val CREATION_LOG = Column("creation_log")
  val MODIFICATION_TIMESTAMP = Column("modification_timestamp")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val DECOY_RESULT_SET_ID = Column("decoy_result_set_id")
  val MSI_SEARCH_ID = Column("msi_search_id")
}

abstract class MsiDbResultSetTable extends TableDefinition[MsiDbResultSetColumns.type]

object MsiDbResultSetTable extends MsiDbResultSetTable {
  val name = "result_set"
  val columns = MsiDbResultSetColumns
}

object MsiDbResultSetObjectTreeMapColumns extends ColumnEnumeration {
  val $tableName = MsiDbResultSetObjectTreeMapTable.name
  val RESULT_SET_ID = Column("result_set_id")
  val OBJECT_TREE_ID = Column("object_tree_id")
  val SCHEMA_NAME = Column("schema_name")
}

abstract class MsiDbResultSetObjectTreeMapTable extends TableDefinition[MsiDbResultSetObjectTreeMapColumns.type]

object MsiDbResultSetObjectTreeMapTable extends MsiDbResultSetObjectTreeMapTable {
  val name = "result_set_object_tree_map"
  val columns = MsiDbResultSetObjectTreeMapColumns
}

object MsiDbResultSetRelationColumns extends ColumnEnumeration {
  val $tableName = MsiDbResultSetRelationTable.name
  val PARENT_RESULT_SET_ID = Column("parent_result_set_id")
  val CHILD_RESULT_SET_ID = Column("child_result_set_id")
}

abstract class MsiDbResultSetRelationTable extends TableDefinition[MsiDbResultSetRelationColumns.type]

object MsiDbResultSetRelationTable extends MsiDbResultSetRelationTable {
  val name = "result_set_relation"
  val columns = MsiDbResultSetRelationColumns
}

object MsiDbResultSummaryColumns extends ColumnEnumeration {
  val $tableName = MsiDbResultSummaryTable.name
  val ID = Column("id")
  val DESCRIPTION = Column("description")
  val CREATION_LOG = Column("creation_log")
  val MODIFICATION_TIMESTAMP = Column("modification_timestamp")
  val IS_QUANTIFIED = Column("is_quantified")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val DECOY_RESULT_SUMMARY_ID = Column("decoy_result_summary_id")
  val RESULT_SET_ID = Column("result_set_id")
}

abstract class MsiDbResultSummaryTable extends TableDefinition[MsiDbResultSummaryColumns.type]

object MsiDbResultSummaryTable extends MsiDbResultSummaryTable {
  val name = "result_summary"
  val columns = MsiDbResultSummaryColumns
}

object MsiDbResultSummaryObjectTreeMapColumns extends ColumnEnumeration {
  val $tableName = MsiDbResultSummaryObjectTreeMapTable.name
  val RESULT_SUMMARY_ID = Column("result_summary_id")
  val OBJECT_TREE_ID = Column("object_tree_id")
  val SCHEMA_NAME = Column("schema_name")
}

abstract class MsiDbResultSummaryObjectTreeMapTable extends TableDefinition[MsiDbResultSummaryObjectTreeMapColumns.type]

object MsiDbResultSummaryObjectTreeMapTable extends MsiDbResultSummaryObjectTreeMapTable {
  val name = "result_summary_object_tree_map"
  val columns = MsiDbResultSummaryObjectTreeMapColumns
}

object MsiDbResultSummaryRelationColumns extends ColumnEnumeration {
  val $tableName = MsiDbResultSummaryRelationTable.name
  val PARENT_RESULT_SUMMARY_ID = Column("parent_result_summary_id")
  val CHILD_RESULT_SUMMARY_ID = Column("child_result_summary_id")
}

abstract class MsiDbResultSummaryRelationTable extends TableDefinition[MsiDbResultSummaryRelationColumns.type]

object MsiDbResultSummaryRelationTable extends MsiDbResultSummaryRelationTable {
  val name = "result_summary_relation"
  val columns = MsiDbResultSummaryRelationColumns
}

object MsiDbScoringColumns extends ColumnEnumeration {
  val $tableName = MsiDbScoringTable.name
  val ID = Column("id")
  val SEARCH_ENGINE = Column("search_engine")
  val NAME = Column("name")
  val DESCRIPTION = Column("description")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
}

abstract class MsiDbScoringTable extends TableDefinition[MsiDbScoringColumns.type]

object MsiDbScoringTable extends MsiDbScoringTable {
  val name = "scoring"
  val columns = MsiDbScoringColumns
}

object MsiDbSearchSettingsColumns extends ColumnEnumeration {
  val $tableName = MsiDbSearchSettingsTable.name
  val ID = Column("id")
  val SOFTWARE_NAME = Column("software_name")
  val SOFTWARE_VERSION = Column("software_version")
  val TAXONOMY = Column("taxonomy")
  val MAX_MISSED_CLEAVAGES = Column("max_missed_cleavages")
  val PEPTIDE_CHARGE_STATES = Column("peptide_charge_states")
  val PEPTIDE_MASS_ERROR_TOLERANCE = Column("peptide_mass_error_tolerance")
  val PEPTIDE_MASS_ERROR_TOLERANCE_UNIT = Column("peptide_mass_error_tolerance_unit")
  val QUANTITATION = Column("quantitation")
  val IS_DECOY = Column("is_decoy")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val INSTRUMENT_CONFIG_ID = Column("instrument_config_id")
}

abstract class MsiDbSearchSettingsTable extends TableDefinition[MsiDbSearchSettingsColumns.type]

object MsiDbSearchSettingsTable extends MsiDbSearchSettingsTable {
  val name = "search_settings"
  val columns = MsiDbSearchSettingsColumns
}

object MsiDbSearchSettingsSeqDatabaseMapColumns extends ColumnEnumeration {
  val $tableName = MsiDbSearchSettingsSeqDatabaseMapTable.name
  val SEARCH_SETTINGS_ID = Column("search_settings_id")
  val SEQ_DATABASE_ID = Column("seq_database_id")
  val SEARCHED_SEQUENCES_COUNT = Column("searched_sequences_count")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
}

abstract class MsiDbSearchSettingsSeqDatabaseMapTable extends TableDefinition[MsiDbSearchSettingsSeqDatabaseMapColumns.type]

object MsiDbSearchSettingsSeqDatabaseMapTable extends MsiDbSearchSettingsSeqDatabaseMapTable {
  val name = "search_settings_seq_database_map"
  val columns = MsiDbSearchSettingsSeqDatabaseMapColumns
}

object MsiDbSeqDatabaseColumns extends ColumnEnumeration {
  val $tableName = MsiDbSeqDatabaseTable.name
  val ID = Column("id")
  val NAME = Column("name")
  val FASTA_FILE_PATH = Column("fasta_file_path")
  val VERSION = Column("version")
  val RELEASE_DATE = Column("release_date")
  val SEQUENCE_COUNT = Column("sequence_count")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
}

abstract class MsiDbSeqDatabaseTable extends TableDefinition[MsiDbSeqDatabaseColumns.type]

object MsiDbSeqDatabaseTable extends MsiDbSeqDatabaseTable {
  val name = "seq_database"
  val columns = MsiDbSeqDatabaseColumns
}

object MsiDbSequenceMatchColumns extends ColumnEnumeration {
  val $tableName = MsiDbSequenceMatchTable.name
  val PROTEIN_MATCH_ID = Column("protein_match_id")
  val PEPTIDE_ID = Column("peptide_id")
  val START = Column("start")
  val STOP = Column("stop")
  val RESIDUE_BEFORE = Column("residue_before")
  val RESIDUE_AFTER = Column("residue_after")
  val IS_DECOY = Column("is_decoy")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val BEST_PEPTIDE_MATCH_ID = Column("best_peptide_match_id")
  val RESULT_SET_ID = Column("result_set_id")
}

abstract class MsiDbSequenceMatchTable extends TableDefinition[MsiDbSequenceMatchColumns.type]

object MsiDbSequenceMatchTable extends MsiDbSequenceMatchTable {
  val name = "sequence_match"
  val columns = MsiDbSequenceMatchColumns
}

object MsiDbSpectrumColumns extends ColumnEnumeration {
  val $tableName = MsiDbSpectrumTable.name
  val ID = Column("id")
  val TITLE = Column("title")
  val PRECURSOR_MOZ = Column("precursor_moz")
  val PRECURSOR_INTENSITY = Column("precursor_intensity")
  val PRECURSOR_CHARGE = Column("precursor_charge")
  val IS_SUMMED = Column("is_summed")
  val FIRST_CYCLE = Column("first_cycle")
  val LAST_CYCLE = Column("last_cycle")
  val FIRST_SCAN = Column("first_scan")
  val LAST_SCAN = Column("last_scan")
  val FIRST_TIME = Column("first_time")
  val LAST_TIME = Column("last_time")
  val MOZ_LIST = Column("moz_list")
  val INTENSITY_LIST = Column("intensity_list")
  val PEAK_COUNT = Column("peak_count")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val PEAKLIST_ID = Column("peaklist_id")
  val INSTRUMENT_CONFIG_ID = Column("instrument_config_id")
}

abstract class MsiDbSpectrumTable extends TableDefinition[MsiDbSpectrumColumns.type]

object MsiDbSpectrumTable extends MsiDbSpectrumTable {
  val name = "spectrum"
  val columns = MsiDbSpectrumColumns
}

object MsiDbUsedEnzymeColumns extends ColumnEnumeration {
  val $tableName = MsiDbUsedEnzymeTable.name
  val SEARCH_SETTINGS_ID = Column("search_settings_id")
  val ENZYME_ID = Column("enzyme_id")
}

abstract class MsiDbUsedEnzymeTable extends TableDefinition[MsiDbUsedEnzymeColumns.type]

object MsiDbUsedEnzymeTable extends MsiDbUsedEnzymeTable {
  val name = "used_enzyme"
  val columns = MsiDbUsedEnzymeColumns
}

object MsiDbUsedPtmColumns extends ColumnEnumeration {
  val $tableName = MsiDbUsedPtmTable.name
  val SEARCH_SETTINGS_ID = Column("search_settings_id")
  val PTM_SPECIFICITY_ID = Column("ptm_specificity_id")
  val SHORT_NAME = Column("short_name")
  val IS_FIXED = Column("is_fixed")
}

abstract class MsiDbUsedPtmTable extends TableDefinition[MsiDbUsedPtmColumns.type]

object MsiDbUsedPtmTable extends MsiDbUsedPtmTable {
  val name = "used_ptm"
  val columns = MsiDbUsedPtmColumns
}



