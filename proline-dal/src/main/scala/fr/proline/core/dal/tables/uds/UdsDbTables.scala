package fr.proline.core.dal.tables.uds

import fr.proline.core.dal.tables._

object UdsDbActivationColumns extends ColumnEnumeration {
  val $tableName = UdsDbActivationTable.name
  val TYPE = Column("type")
}

abstract class UdsDbActivationTable extends TableDefinition[UdsDbActivationColumns.type]

object UdsDbActivationTable extends UdsDbActivationTable {
  val name = "activation"
  val columns = UdsDbActivationColumns
}

object UdsDbAggregationColumns extends ColumnEnumeration {
  val $tableName = UdsDbAggregationTable.name
  val ID = Column("id")
  val CHILD_NATURE = Column("child_nature")
}

abstract class UdsDbAggregationTable extends TableDefinition[UdsDbAggregationColumns.type]

object UdsDbAggregationTable extends UdsDbAggregationTable {
  val name = "aggregation"
  val columns = UdsDbAggregationColumns
}

object UdsDbBiologicalGroupColumns extends ColumnEnumeration {
  val $tableName = UdsDbBiologicalGroupTable.name
  val ID = Column("id")
  val NUMBER = Column("number")
  val NAME = Column("name")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val QUANTITATION_ID = Column("quantitation_id")
}

abstract class UdsDbBiologicalGroupTable extends TableDefinition[UdsDbBiologicalGroupColumns.type]

object UdsDbBiologicalGroupTable extends UdsDbBiologicalGroupTable {
  val name = "biological_group"
  val columns = UdsDbBiologicalGroupColumns
}

object UdsDbBiologicalGroupBiologicalSampleItemColumns extends ColumnEnumeration {
  val $tableName = UdsDbBiologicalGroupBiologicalSampleItemTable.name
  val BIOLOGICAL_GROUP_ID = Column("biological_group_id")
  val BIOLOGICAL_SAMPLE_ID = Column("biological_sample_id")
}

abstract class UdsDbBiologicalGroupBiologicalSampleItemTable extends TableDefinition[UdsDbBiologicalGroupBiologicalSampleItemColumns.type]

object UdsDbBiologicalGroupBiologicalSampleItemTable extends UdsDbBiologicalGroupBiologicalSampleItemTable {
  val name = "biological_group_biological_sample_item"
  val columns = UdsDbBiologicalGroupBiologicalSampleItemColumns
}

object UdsDbBiologicalSampleColumns extends ColumnEnumeration {
  val $tableName = UdsDbBiologicalSampleTable.name
  val ID = Column("id")
  val NUMBER = Column("number")
  val NAME = Column("name")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val QUANTITATION_ID = Column("quantitation_id")
}

abstract class UdsDbBiologicalSampleTable extends TableDefinition[UdsDbBiologicalSampleColumns.type]

object UdsDbBiologicalSampleTable extends UdsDbBiologicalSampleTable {
  val name = "biological_sample"
  val columns = UdsDbBiologicalSampleColumns
}

object UdsDbBiologicalSampleSampleAnalysisMapColumns extends ColumnEnumeration {
  val $tableName = UdsDbBiologicalSampleSampleAnalysisMapTable.name
  val BIOLOGICAL_SAMPLE_ID = Column("biological_sample_id")
  val SAMPLE_ANALYSIS_ID = Column("sample_analysis_id")
  val SAMPLE_ANALYSIS_NUMBER = Column("sample_analysis_number")
}

abstract class UdsDbBiologicalSampleSampleAnalysisMapTable extends TableDefinition[UdsDbBiologicalSampleSampleAnalysisMapColumns.type]

object UdsDbBiologicalSampleSampleAnalysisMapTable extends UdsDbBiologicalSampleSampleAnalysisMapTable {
  val name = "biological_sample_sample_analysis_map"
  val columns = UdsDbBiologicalSampleSampleAnalysisMapColumns
}

object UdsDbDataSetColumns extends ColumnEnumeration {
  val $tableName = UdsDbDataSetTable.name
  val ID = Column("id")
  val NUMBER = Column("number")
  val NAME = Column("name")
  val DESCRIPTION = Column("description")
  val TYPE = Column("type")
  val KEYWORDS = Column("keywords")
  val CREATION_TIMESTAMP = Column("creation_timestamp")
  val MODIFICATION_LOG = Column("modification_log")
  val CHILD_COUNT = Column("child_count")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val RESULT_SET_ID = Column("result_set_id")
  val RESULT_SUMMARY_ID = Column("result_summary_id")
  val AGGREGATION_ID = Column("aggregation_id")
  val FRACTIONATION_ID = Column("fractionation_id")
  val QUANT_METHOD_ID = Column("quant_method_id")
  val PARENT_DATASET_ID = Column("parent_dataset_id")
  val PROJECT_ID = Column("project_id")
}

abstract class UdsDbDataSetTable extends TableDefinition[UdsDbDataSetColumns.type]

object UdsDbDataSetTable extends UdsDbDataSetTable {
  val name = "data_set"
  val columns = UdsDbDataSetColumns
}

object UdsDbDataSetObjectTreeMapColumns extends ColumnEnumeration {
  val $tableName = UdsDbDataSetObjectTreeMapTable.name
  val DATA_SET_ID = Column("data_set_id")
  val SCHEMA_NAME = Column("schema_name")
  val OBJECT_TREE_ID = Column("object_tree_id")
}

abstract class UdsDbDataSetObjectTreeMapTable extends TableDefinition[UdsDbDataSetObjectTreeMapColumns.type]

object UdsDbDataSetObjectTreeMapTable extends UdsDbDataSetObjectTreeMapTable {
  val name = "data_set_object_tree_map"
  val columns = UdsDbDataSetObjectTreeMapColumns
}

object UdsDbDocumentColumns extends ColumnEnumeration {
  val $tableName = UdsDbDocumentTable.name
  val ID = Column("id")
  val NAME = Column("name")
  val DESCRIPTION = Column("description")
  val KEYWORDS = Column("keywords")
  val CREATION_TIMESTAMP = Column("creation_timestamp")
  val MODIFICATION_TIMESTAMP = Column("modification_timestamp")
  val CREATION_LOG = Column("creation_log")
  val MODIFICATION_LOG = Column("modification_log")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val OBJECT_TREE_ID = Column("object_tree_id")
  val VIRTUAL_FOLDER_ID = Column("virtual_folder_id")
  val PROJECT_ID = Column("project_id")
  val SCHEMA_NAME = Column("schema_name")
}

abstract class UdsDbDocumentTable extends TableDefinition[UdsDbDocumentColumns.type]

object UdsDbDocumentTable extends UdsDbDocumentTable {
  val name = "document"
  val columns = UdsDbDocumentColumns
}

object UdsDbEnzymeColumns extends ColumnEnumeration {
  val $tableName = UdsDbEnzymeTable.name
  val ID = Column("id")
  val NAME = Column("name")
  val CLEAVAGE_REGEXP = Column("cleavage_regexp")
  val IS_INDEPENDANT = Column("is_independant")
  val IS_SEMI_SPECIFIC = Column("is_semi_specific")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
}

abstract class UdsDbEnzymeTable extends TableDefinition[UdsDbEnzymeColumns.type]

object UdsDbEnzymeTable extends UdsDbEnzymeTable {
  val name = "enzyme"
  val columns = UdsDbEnzymeColumns
}

object UdsDbEnzymeCleavageColumns extends ColumnEnumeration {
  val $tableName = UdsDbEnzymeCleavageTable.name
  val ID = Column("id")
  val SITE = Column("site")
  val RESIDUES = Column("residues")
  val RESTRICTIVE_RESIDUES = Column("restrictive_residues")
  val ENZYME_ID = Column("enzyme_id")
}

abstract class UdsDbEnzymeCleavageTable extends TableDefinition[UdsDbEnzymeCleavageColumns.type]

object UdsDbEnzymeCleavageTable extends UdsDbEnzymeCleavageTable {
  val name = "enzyme_cleavage"
  val columns = UdsDbEnzymeCleavageColumns
}

object UdsDbExternalDbColumns extends ColumnEnumeration {
  val $tableName = UdsDbExternalDbTable.name
  val ID = Column("id")
  val NAME = Column("name")
  val CONNECTION_MODE = Column("connection_mode")
  val HOST = Column("host")
  val PORT = Column("port")
  val TYPE = Column("type")
  val VERSION = Column("version")
  val IS_BUSY = Column("is_busy")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
}

abstract class UdsDbExternalDbTable extends TableDefinition[UdsDbExternalDbColumns.type]

object UdsDbExternalDbTable extends UdsDbExternalDbTable {
  val name = "external_db"
  val columns = UdsDbExternalDbColumns
}

object UdsDbFractionationColumns extends ColumnEnumeration {
  val $tableName = UdsDbFractionationTable.name
  val ID = Column("id")
  val TYPE = Column("type")
}

abstract class UdsDbFractionationTable extends TableDefinition[UdsDbFractionationColumns.type]

object UdsDbFractionationTable extends UdsDbFractionationTable {
  val name = "fractionation"
  val columns = UdsDbFractionationColumns
}

object UdsDbFragmentationRuleColumns extends ColumnEnumeration {
  val $tableName = UdsDbFragmentationRuleTable.name
  val ID = Column("id")
  val DESCRIPTION = Column("description")
  val PRECURSOR_MIN_CHARGE = Column("precursor_min_charge")
  val FRAGMENT_CHARGE = Column("fragment_charge")
  val FRAGMENT_MAX_MOZ = Column("fragment_max_moz")
  val FRAGMENT_RESIDUE_CONSTRAINT = Column("fragment_residue_constraint")
  val REQUIRED_SERIES_QUALITY_LEVEL = Column("required_series_quality_level")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val FRAGMENT_SERIES_ID = Column("fragment_series_id")
  val REQUIRED_SERIES_ID = Column("required_series_id")
}

abstract class UdsDbFragmentationRuleTable extends TableDefinition[UdsDbFragmentationRuleColumns.type]

object UdsDbFragmentationRuleTable extends UdsDbFragmentationRuleTable {
  val name = "fragmentation_rule"
  val columns = UdsDbFragmentationRuleColumns
}

object UdsDbFragmentationSeriesColumns extends ColumnEnumeration {
  val $tableName = UdsDbFragmentationSeriesTable.name
  val ID = Column("id")
  val NAME = Column("name")
  val NEUTRAL_LOSS = Column("neutral_loss")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
}

abstract class UdsDbFragmentationSeriesTable extends TableDefinition[UdsDbFragmentationSeriesColumns.type]

object UdsDbFragmentationSeriesTable extends UdsDbFragmentationSeriesTable {
  val name = "fragmentation_series"
  val columns = UdsDbFragmentationSeriesColumns
}

object UdsDbGroupSetupColumns extends ColumnEnumeration {
  val $tableName = UdsDbGroupSetupTable.name
  val ID = Column("id")
  val NUMBER = Column("number")
  val NAME = Column("name")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val QUANTITATION_ID = Column("quantitation_id")
}

abstract class UdsDbGroupSetupTable extends TableDefinition[UdsDbGroupSetupColumns.type]

object UdsDbGroupSetupTable extends UdsDbGroupSetupTable {
  val name = "group_setup"
  val columns = UdsDbGroupSetupColumns
}

object UdsDbGroupSetupBiologicalGroupMapColumns extends ColumnEnumeration {
  val $tableName = UdsDbGroupSetupBiologicalGroupMapTable.name
  val GROUP_SETUP_ID = Column("group_setup_id")
  val BIOLOGICAL_GROUP_ID = Column("biological_group_id")
}

abstract class UdsDbGroupSetupBiologicalGroupMapTable extends TableDefinition[UdsDbGroupSetupBiologicalGroupMapColumns.type]

object UdsDbGroupSetupBiologicalGroupMapTable extends UdsDbGroupSetupBiologicalGroupMapTable {
  val name = "group_setup_biological_group_map"
  val columns = UdsDbGroupSetupBiologicalGroupMapColumns
}

object UdsDbInstrumentColumns extends ColumnEnumeration {
  val $tableName = UdsDbInstrumentTable.name
  val ID = Column("id")
  val NAME = Column("name")
  val SOURCE = Column("source")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
}

abstract class UdsDbInstrumentTable extends TableDefinition[UdsDbInstrumentColumns.type]

object UdsDbInstrumentTable extends UdsDbInstrumentTable {
  val name = "instrument"
  val columns = UdsDbInstrumentColumns
}

object UdsDbInstrumentConfigColumns extends ColumnEnumeration {
  val $tableName = UdsDbInstrumentConfigTable.name
  val ID = Column("id")
  val NAME = Column("name")
  val MS1_ANALYZER = Column("ms1_analyzer")
  val MSN_ANALYZER = Column("msn_analyzer")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val INSTRUMENT_ID = Column("instrument_id")
  val ACTIVATION_TYPE = Column("activation_type")
}

abstract class UdsDbInstrumentConfigTable extends TableDefinition[UdsDbInstrumentConfigColumns.type]

object UdsDbInstrumentConfigTable extends UdsDbInstrumentConfigTable {
  val name = "instrument_config"
  val columns = UdsDbInstrumentConfigColumns
}


object UdsDbFragmentationRuleSetColumns extends ColumnEnumeration {
  val $tableName = UdsDbFragmentationRuleSetTable.name
  val ID = Column("id")
  val NAME = Column("name")
}

abstract class UdsDbFragmentationRuleSetTable extends TableDefinition[UdsDbFragmentationRuleSetColumns.type]

object UdsDbFragmentationRuleSetTable extends UdsDbFragmentationRuleSetTable {
  val name = "fragmentation_rule_set"
  val columns = UdsDbFragmentationRuleSetColumns
}


object UdsDbFragmentationRuleSetMapColumns extends ColumnEnumeration {
  val $tableName = UdsDbFragmentationRuleSetMapTable.name
  val FRAGMENTATION_RULE_SET_ID = Column("fragmentation_rule_set_id")
  val FRAGMENTATION_RULE_ID = Column("fragmentation_rule_id")
}

abstract class UdsDbFragmentationRuleSetMapTable extends TableDefinition[UdsDbFragmentationRuleSetMapColumns.type]

object UdsDbFragmentationRuleSetMapTable extends UdsDbFragmentationRuleSetMapTable {
  val name = "fragmentation_rule_set_map"
  val columns = UdsDbFragmentationRuleSetMapColumns
}

object UdsDbMasterQuantChannelColumns extends ColumnEnumeration {
  val $tableName = UdsDbMasterQuantChannelTable.name
  val ID = Column("id")
  val NUMBER = Column("number")
  val NAME = Column("name")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val LCMS_MAP_SET_ID = Column("lcms_map_set_id")
  val IDENT_RESULT_SUMMARY_ID = Column("ident_result_summary_id")
  val QUANT_RESULT_SUMMARY_ID = Column("quant_result_summary_id")
  val IDENT_DATA_SET_ID = Column("ident_data_set_id")
  val QUANTITATION_ID = Column("quantitation_id")
}

abstract class UdsDbMasterQuantChannelTable extends TableDefinition[UdsDbMasterQuantChannelColumns.type]

object UdsDbMasterQuantChannelTable extends UdsDbMasterQuantChannelTable {
  val name = "master_quant_channel"
  val columns = UdsDbMasterQuantChannelColumns
}

object UdsDbObjectTreeColumns extends ColumnEnumeration {
  val $tableName = UdsDbObjectTreeTable.name
  val ID = Column("id")
  val BLOB_DATA = Column("blob_data")
  val CLOB_DATA = Column("clob_data")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val SCHEMA_NAME = Column("schema_name")
}

abstract class UdsDbObjectTreeTable extends TableDefinition[UdsDbObjectTreeColumns.type]

object UdsDbObjectTreeTable extends UdsDbObjectTreeTable {
  val name = "object_tree"
  val columns = UdsDbObjectTreeColumns
}

object UdsDbObjectTreeSchemaColumns extends ColumnEnumeration {
  val $tableName = UdsDbObjectTreeSchemaTable.name
  val NAME = Column("name")
  val TYPE = Column("type")
  val IS_BINARY_MODE = Column("is_binary_mode")
  val VERSION = Column("version")
  val SCHEMA = Column("schema")
  val DESCRIPTION = Column("description")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
}

abstract class UdsDbObjectTreeSchemaTable extends TableDefinition[UdsDbObjectTreeSchemaColumns.type]

object UdsDbObjectTreeSchemaTable extends UdsDbObjectTreeSchemaTable {
  val name = "object_tree_schema"
  val columns = UdsDbObjectTreeSchemaColumns
}

object UdsDbPeaklistSoftwareColumns extends ColumnEnumeration {
  val $tableName = UdsDbPeaklistSoftwareTable.name
  val ID = Column("id")
  val NAME = Column("name")
  val VERSION = Column("version")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val SPEC_TITLE_PARSING_RULE_ID = Column("spec_title_parsing_rule_id")
}

abstract class UdsDbPeaklistSoftwareTable extends TableDefinition[UdsDbPeaklistSoftwareColumns.type]

object UdsDbPeaklistSoftwareTable extends UdsDbPeaklistSoftwareTable {
  val name = "peaklist_software"
  val columns = UdsDbPeaklistSoftwareColumns
}

object UdsDbProjectColumns extends ColumnEnumeration {
  val $tableName = UdsDbProjectTable.name
  val ID = Column("id")
  val NAME = Column("name")
  val DESCRIPTION = Column("description")
  val CREATION_TIMESTAMP = Column("creation_timestamp")
  val LOCK_EXPIRATION_TIMESTAMP = Column("lock_expiration_timestamp")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val LOCK_USER_ID = Column("lock_user_id")
  val OWNER_ID = Column("owner_id")
}

abstract class UdsDbProjectTable extends TableDefinition[UdsDbProjectColumns.type]

object UdsDbProjectTable extends UdsDbProjectTable {
  val name = "project"
  val columns = UdsDbProjectColumns
}

object UdsDbProjectDbMapColumns extends ColumnEnumeration {
  val $tableName = UdsDbProjectDbMapTable.name
  val PROJECT_ID = Column("project_id")
  val EXTERNAL_DB_ID = Column("external_db_id")
}

abstract class UdsDbProjectDbMapTable extends TableDefinition[UdsDbProjectDbMapColumns.type]

object UdsDbProjectDbMapTable extends UdsDbProjectDbMapTable {
  val name = "project_db_map"
  val columns = UdsDbProjectDbMapColumns
}

object UdsDbProjectUserAccountMapColumns extends ColumnEnumeration {
  val $tableName = UdsDbProjectUserAccountMapTable.name
  val PROJECT_ID = Column("project_id")
  val USER_ACCOUNT_ID = Column("user_account_id")
  val WRITE_PERMISSION = Column("write_permission")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
}

abstract class UdsDbProjectUserAccountMapTable extends TableDefinition[UdsDbProjectUserAccountMapColumns.type]

object UdsDbProjectUserAccountMapTable extends UdsDbProjectUserAccountMapTable {
  val name = "project_user_account_map"
  val columns = UdsDbProjectUserAccountMapColumns
}

object UdsDbProteinMatchDecoyRuleColumns extends ColumnEnumeration {
  val $tableName = UdsDbProteinMatchDecoyRuleTable.name
  val ID = Column("id")
  val NAME = Column("name")
  val AC_DECOY_TAG = Column("ac_decoy_tag")
}

abstract class UdsDbProteinMatchDecoyRuleTable extends TableDefinition[UdsDbProteinMatchDecoyRuleColumns.type]

object UdsDbProteinMatchDecoyRuleTable extends UdsDbProteinMatchDecoyRuleTable {
  val name = "protein_match_decoy_rule"
  val columns = UdsDbProteinMatchDecoyRuleColumns
}

object UdsDbQuantChannelColumns extends ColumnEnumeration {
  val $tableName = UdsDbQuantChannelTable.name
  val ID = Column("id")
  val NUMBER = Column("number")
  val NAME = Column("name")
  val CONTEXT_KEY = Column("context_key")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val LCMS_MAP_ID = Column("lcms_map_id")
  val IDENT_RESULT_SUMMARY_ID = Column("ident_result_summary_id")
  val RUN_ID = Column("run_id")
  val QUANT_LABEL_ID = Column("quant_label_id")
  val SAMPLE_ANALYSIS_ID = Column("sample_analysis_id")
  val BIOLOGICAL_SAMPLE_ID = Column("biological_sample_id")
  val MASTER_QUANT_CHANNEL_ID = Column("master_quant_channel_id")
  val QUANTITATION_ID = Column("quantitation_id")
}

abstract class UdsDbQuantChannelTable extends TableDefinition[UdsDbQuantChannelColumns.type]

object UdsDbQuantChannelTable extends UdsDbQuantChannelTable {
  val name = "quant_channel"
  val columns = UdsDbQuantChannelColumns
}

object UdsDbQuantLabelColumns extends ColumnEnumeration {
  val $tableName = UdsDbQuantLabelTable.name
  val ID = Column("id")
  val NUMBER = Column("number")
  val TYPE = Column("type")
  val NAME = Column("name")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val QUANT_METHOD_ID = Column("quant_method_id")
}

abstract class UdsDbQuantLabelTable extends TableDefinition[UdsDbQuantLabelColumns.type]

object UdsDbQuantLabelTable extends UdsDbQuantLabelTable {
  val name = "quant_label"
  val columns = UdsDbQuantLabelColumns
}

object UdsDbQuantMethodColumns extends ColumnEnumeration {
  val $tableName = UdsDbQuantMethodTable.name
  val ID = Column("id")
  val NAME = Column("name")
  val TYPE = Column("type")
  val ABUNDANCE_UNIT = Column("abundance_unit")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
}

abstract class UdsDbQuantMethodTable extends TableDefinition[UdsDbQuantMethodColumns.type]

object UdsDbQuantMethodTable extends UdsDbQuantMethodTable {
  val name = "quant_method"
  val columns = UdsDbQuantMethodColumns
}

object UdsDbRatioDefinitionColumns extends ColumnEnumeration {
  val $tableName = UdsDbRatioDefinitionTable.name
  val ID = Column("id")
  val NUMBER = Column("number")
  val NUMERATOR_ID = Column("numerator_id")
  val DENOMINATOR_ID = Column("denominator_id")
  val GROUP_SETUP_ID = Column("group_setup_id")
}

abstract class UdsDbRatioDefinitionTable extends TableDefinition[UdsDbRatioDefinitionColumns.type]

object UdsDbRatioDefinitionTable extends UdsDbRatioDefinitionTable {
  val name = "ratio_definition"
  val columns = UdsDbRatioDefinitionColumns
}

object UdsDbRawFileColumns extends ColumnEnumeration {
  val $tableName = UdsDbRawFileTable.name
  val IDENTIFIER = Column("identifier")
  val RAW_FILE_NAME = Column("raw_file_name")
  val RAW_FILE_DIRECTORY = Column("raw_file_directory")
  val MZDB_FILE_NAME = Column("mzdb_file_name")
  val MZDB_FILE_DIRECTORY = Column("mzdb_file_directory")
  val SAMPLE_NAME = Column("sample_name")
  val CREATION_TIMESTAMP = Column("creation_timestamp")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val OWNER_ID = Column("owner_id")
}

abstract class UdsDbRawFileTable extends TableDefinition[UdsDbRawFileColumns.type]

object UdsDbRawFileTable extends UdsDbRawFileTable {
  val name = "raw_file"
  val columns = UdsDbRawFileColumns
}

object UdsDbRawFileProjectMapColumns extends ColumnEnumeration {
  val $tableName = UdsDbRawFileProjectMapTable.name
  val RAW_FILE_IDENTIFIER = Column("raw_file_identifier")
  val PROJECT_ID = Column("project_id")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
}

abstract class UdsDbRawFileProjectMapTable extends TableDefinition[UdsDbRawFileProjectMapColumns.type]

object UdsDbRawFileProjectMapTable extends UdsDbRawFileProjectMapTable {
  val name = "raw_file_project_map"
  val columns = UdsDbRawFileProjectMapColumns
}

object UdsDbRunColumns extends ColumnEnumeration {
  val $tableName = UdsDbRunTable.name
  val ID = Column("id")
  val NUMBER = Column("number")
  val RUN_START = Column("run_start")
  val RUN_STOP = Column("run_stop")
  val DURATION = Column("duration")
  val LC_METHOD = Column("lc_method")
  val MS_METHOD = Column("ms_method")
  val ANALYST = Column("analyst")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val RAW_FILE_IDENTIFIER = Column("raw_file_identifier")
}

abstract class UdsDbRunTable extends TableDefinition[UdsDbRunColumns.type]

object UdsDbRunTable extends UdsDbRunTable {
  val name = "run"
  val columns = UdsDbRunColumns
}

object UdsDbRunIdentificationColumns extends ColumnEnumeration {
  val $tableName = UdsDbRunIdentificationTable.name
  val ID = Column("id")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val RUN_ID = Column("run_id")
  val RAW_FILE_IDENTIFIER = Column("raw_file_identifier")
}

abstract class UdsDbRunIdentificationTable extends TableDefinition[UdsDbRunIdentificationColumns.type]

object UdsDbRunIdentificationTable extends UdsDbRunIdentificationTable {
  val name = "run_identification"
  val columns = UdsDbRunIdentificationColumns
}

object UdsDbSampleAnalysisColumns extends ColumnEnumeration {
  val $tableName = UdsDbSampleAnalysisTable.name
  val ID = Column("id")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val QUANTITATION_ID = Column("quantitation_id")
}

abstract class UdsDbSampleAnalysisTable extends TableDefinition[UdsDbSampleAnalysisColumns.type]

object UdsDbSampleAnalysisTable extends UdsDbSampleAnalysisTable {
  val name = "sample_analysis"
  val columns = UdsDbSampleAnalysisColumns
}

object UdsDbSpecTitleParsingRuleColumns extends ColumnEnumeration {
  val $tableName = UdsDbSpecTitleParsingRuleTable.name
  val ID = Column("id")
  val RAW_FILE_IDENTIFIER = Column("raw_file_identifier")
  val FIRST_CYCLE = Column("first_cycle")
  val LAST_CYCLE = Column("last_cycle")
  val FIRST_SCAN = Column("first_scan")
  val LAST_SCAN = Column("last_scan")
  val FIRST_TIME = Column("first_time")
  val LAST_TIME = Column("last_time")
}

abstract class UdsDbSpecTitleParsingRuleTable extends TableDefinition[UdsDbSpecTitleParsingRuleColumns.type]

object UdsDbSpecTitleParsingRuleTable extends UdsDbSpecTitleParsingRuleTable {
  val name = "spec_title_parsing_rule"
  val columns = UdsDbSpecTitleParsingRuleColumns
}

object UdsDbUserAccountColumns extends ColumnEnumeration {
  val $tableName = UdsDbUserAccountTable.name
  val ID = Column("id")
  val LOGIN = Column("login")
  val PASSWORD_HASH = Column("password_hash")
  val CREATION_MODE = Column("creation_mode")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
}

abstract class UdsDbUserAccountTable extends TableDefinition[UdsDbUserAccountColumns.type]

object UdsDbUserAccountTable extends UdsDbUserAccountTable {
  val name = "user_account"
  val columns = UdsDbUserAccountColumns
}

object UdsDbVirtualFolderColumns extends ColumnEnumeration {
  val $tableName = UdsDbVirtualFolderTable.name
  val ID = Column("id")
  val NAME = Column("name")
  val PATH = Column("path")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val PARENT_VIRTUAL_FOLDER_ID = Column("parent_virtual_folder_id")
  val PROJECT_ID = Column("project_id")
}

abstract class UdsDbVirtualFolderTable extends TableDefinition[UdsDbVirtualFolderColumns.type]

object UdsDbVirtualFolderTable extends UdsDbVirtualFolderTable {
  val name = "virtual_folder"
  val columns = UdsDbVirtualFolderColumns
}

object UdsDb {
  val tables = Array(
    UdsDbActivationTable,
    UdsDbAggregationTable,
    UdsDbBiologicalGroupTable,
    UdsDbBiologicalGroupBiologicalSampleItemTable,
    UdsDbBiologicalSampleTable,
    UdsDbBiologicalSampleSampleAnalysisMapTable,
    UdsDbDataSetTable,
    UdsDbDataSetObjectTreeMapTable,
    UdsDbDocumentTable,
    UdsDbEnzymeTable,
    UdsDbEnzymeCleavageTable,
    UdsDbExternalDbTable,
    UdsDbFractionationTable,
    UdsDbFragmentationRuleTable,
    UdsDbFragmentationSeriesTable,
    UdsDbGroupSetupTable,
    UdsDbGroupSetupBiologicalGroupMapTable,
    UdsDbInstrumentTable,
    UdsDbInstrumentConfigTable,
    UdsDbFragmentationRuleSetTable,
    UdsDbFragmentationRuleSetMapTable,
    UdsDbMasterQuantChannelTable,
    UdsDbObjectTreeTable,
    UdsDbObjectTreeSchemaTable,
    UdsDbPeaklistSoftwareTable,
    UdsDbProjectTable,
    UdsDbProjectDbMapTable,
    UdsDbProjectUserAccountMapTable,
    UdsDbProteinMatchDecoyRuleTable,
    UdsDbQuantChannelTable,
    UdsDbQuantLabelTable,
    UdsDbQuantMethodTable,
    UdsDbRatioDefinitionTable,
    UdsDbRawFileTable,
    UdsDbRawFileProjectMapTable,
    UdsDbRunTable,
    UdsDbRunIdentificationTable,
    UdsDbSampleAnalysisTable,
    UdsDbSpecTitleParsingRuleTable,
    UdsDbUserAccountTable,
    UdsDbVirtualFolderTable
  )
}


