package fr.proline.core.dal.tables.uds

import fr.proline.core.dal.tables._

object UdsDbActivationColumns extends ColumnEnumeration {
  val `type` = Value("type")
}

abstract class UdsDbActivationTable extends TableDefinition[UdsDbActivationColumns.type]

object UdsDbActivationTable extends UdsDbActivationTable {
  val tableName = "activation"
  val columns = UdsDbActivationColumns
}

object UdsDbAdminInfosColumns extends ColumnEnumeration {
  val modelVersion = Value("model_version")
  val dbCreationDate = Value("db_creation_date")
  val modelUpdateDate = Value("model_update_date")
  val configuration = Value("configuration")
}

abstract class UdsDbAdminInfosTable extends TableDefinition[UdsDbAdminInfosColumns.type]

object UdsDbAdminInfosTable extends UdsDbAdminInfosTable {
  val tableName = "admin_infos"
  val columns = UdsDbAdminInfosColumns
}

object UdsDbBiologicalGroupColumns extends ColumnEnumeration {
  val id = Value("id")
  val number = Value("number")
  val name = Value("name")
  val serializedProperties = Value("serialized_properties")
  val groupSetupId = Value("group_setup_id")
}

abstract class UdsDbBiologicalGroupTable extends TableDefinition[UdsDbBiologicalGroupColumns.type]

object UdsDbBiologicalGroupTable extends UdsDbBiologicalGroupTable {
  val tableName = "biological_group"
  val columns = UdsDbBiologicalGroupColumns
}

object UdsDbBiologicalGroupBiologicalSampleItemColumns extends ColumnEnumeration {
  val biologicalGroupId = Value("biological_group_id")
  val biologicalSampleId = Value("biological_sample_id")
}

abstract class UdsDbBiologicalGroupBiologicalSampleItemTable extends TableDefinition[UdsDbBiologicalGroupBiologicalSampleItemColumns.type]

object UdsDbBiologicalGroupBiologicalSampleItemTable extends UdsDbBiologicalGroupBiologicalSampleItemTable {
  val tableName = "biological_group_biological_sample_item"
  val columns = UdsDbBiologicalGroupBiologicalSampleItemColumns
}

object UdsDbBiologicalSampleColumns extends ColumnEnumeration {
  val id = Value("id")
  val number = Value("number")
  val name = Value("name")
  val serializedProperties = Value("serialized_properties")
  val quantitationId = Value("quantitation_id")
}

abstract class UdsDbBiologicalSampleTable extends TableDefinition[UdsDbBiologicalSampleColumns.type]

object UdsDbBiologicalSampleTable extends UdsDbBiologicalSampleTable {
  val tableName = "biological_sample"
  val columns = UdsDbBiologicalSampleColumns
}

object UdsDbDocumentColumns extends ColumnEnumeration {
  val id = Value("id")
  val name = Value("name")
  val description = Value("description")
  val keywords = Value("keywords")
  val creationTimestamp = Value("creation_timestamp")
  val modificationTimestamp = Value("modification_timestamp")
  val creationLog = Value("creation_log")
  val modificationLog = Value("modification_log")
  val serializedProperties = Value("serialized_properties")
  val objectTreeId = Value("object_tree_id")
  val virtualFolderId = Value("virtual_folder_id")
  val projectId = Value("project_id")
  val schemaName = Value("schema_name")
}

abstract class UdsDbDocumentTable extends TableDefinition[UdsDbDocumentColumns.type]

object UdsDbDocumentTable extends UdsDbDocumentTable {
  val tableName = "document"
  val columns = UdsDbDocumentColumns
}

object UdsDbEnzymeColumns extends ColumnEnumeration {
  val id = Value("id")
  val name = Value("name")
  val cleavageRegexp = Value("cleavage_regexp")
  val isIndependant = Value("is_independant")
  val isSemiSpecific = Value("is_semi_specific")
}

abstract class UdsDbEnzymeTable extends TableDefinition[UdsDbEnzymeColumns.type]

object UdsDbEnzymeTable extends UdsDbEnzymeTable {
  val tableName = "enzyme"
  val columns = UdsDbEnzymeColumns
}

object UdsDbEnzymeCleavageColumns extends ColumnEnumeration {
  val id = Value("id")
  val site = Value("site")
  val residues = Value("residues")
  val restrictiveResidues = Value("restrictive_residues")
  val enzymeId = Value("enzyme_id")
}

abstract class UdsDbEnzymeCleavageTable extends TableDefinition[UdsDbEnzymeCleavageColumns.type]

object UdsDbEnzymeCleavageTable extends UdsDbEnzymeCleavageTable {
  val tableName = "enzyme_cleavage"
  val columns = UdsDbEnzymeCleavageColumns
}

object UdsDbExternalDbColumns extends ColumnEnumeration {
  val id = Value("id")
  val name = Value("name")
  val connectionMode = Value("connection_mode")
  val username = Value("username")
  val password = Value("password")
  val host = Value("host")
  val port = Value("port")
  val `type` = Value("type")
  val version = Value("version")
  val isBusy = Value("is_busy")
  val serializedProperties = Value("serialized_properties")
}

abstract class UdsDbExternalDbTable extends TableDefinition[UdsDbExternalDbColumns.type]

object UdsDbExternalDbTable extends UdsDbExternalDbTable {
  val tableName = "external_db"
  val columns = UdsDbExternalDbColumns
}

object UdsDbFragmentationRuleColumns extends ColumnEnumeration {
  val id = Value("id")
  val description = Value("description")
  val precursorMinCharge = Value("precursor_min_charge")
  val fragmentCharge = Value("fragment_charge")
  val fragmentMaxMoz = Value("fragment_max_moz")
  val fragmentResidueConstraint = Value("fragment_residue_constraint")
  val requiredSerieQualityLevel = Value("required_serie_quality_level")
  val serializedProperties = Value("serialized_properties")
  val theoreticalFragmentId = Value("theoretical_fragment_id")
  val requiredSerieId = Value("required_serie_id")
}

abstract class UdsDbFragmentationRuleTable extends TableDefinition[UdsDbFragmentationRuleColumns.type]

object UdsDbFragmentationRuleTable extends UdsDbFragmentationRuleTable {
  val tableName = "fragmentation_rule"
  val columns = UdsDbFragmentationRuleColumns
}

object UdsDbGroupSetupColumns extends ColumnEnumeration {
  val id = Value("id")
  val name = Value("name")
  val serializedProperties = Value("serialized_properties")
  val quantitationId = Value("quantitation_id")
}

abstract class UdsDbGroupSetupTable extends TableDefinition[UdsDbGroupSetupColumns.type]

object UdsDbGroupSetupTable extends UdsDbGroupSetupTable {
  val tableName = "group_setup"
  val columns = UdsDbGroupSetupColumns
}

object UdsDbIdentificationColumns extends ColumnEnumeration {
  val id = Value("id")
  val number = Value("number")
  val name = Value("name")
  val description = Value("description")
  val keywords = Value("keywords")
  val creationTimestamp = Value("creation_timestamp")
  val modificationLog = Value("modification_log")
  val fractionationType = Value("fractionation_type")
  val fractionCount = Value("fraction_count")
  val serializedProperties = Value("serialized_properties")
  val activeSummaryId = Value("active_summary_id")
  val projectId = Value("project_id")
}

abstract class UdsDbIdentificationTable extends TableDefinition[UdsDbIdentificationColumns.type]

object UdsDbIdentificationTable extends UdsDbIdentificationTable {
  val tableName = "identification"
  val columns = UdsDbIdentificationColumns
}

object UdsDbIdentificationFractionColumns extends ColumnEnumeration {
  val id = Value("id")
  val number = Value("number")
  val serializedProperties = Value("serialized_properties")
  val resultSetId = Value("result_set_id")
  val identificationId = Value("identification_id")
  val runId = Value("run_id")
  val rawFileName = Value("raw_file_name")
}

abstract class UdsDbIdentificationFractionTable extends TableDefinition[UdsDbIdentificationFractionColumns.type]

object UdsDbIdentificationFractionTable extends UdsDbIdentificationFractionTable {
  val tableName = "identification_fraction"
  val columns = UdsDbIdentificationFractionColumns
}

object UdsDbIdentificationFractionSummaryColumns extends ColumnEnumeration {
  val id = Value("id")
  val serializedProperties = Value("serialized_properties")
  val resultSummaryId = Value("result_summary_id")
  val identificationFractionId = Value("identification_fraction_id")
  val identificationSummaryId = Value("identification_summary_id")
}

abstract class UdsDbIdentificationFractionSummaryTable extends TableDefinition[UdsDbIdentificationFractionSummaryColumns.type]

object UdsDbIdentificationFractionSummaryTable extends UdsDbIdentificationFractionSummaryTable {
  val tableName = "identification_fraction_summary"
  val columns = UdsDbIdentificationFractionSummaryColumns
}

object UdsDbIdentificationSummaryColumns extends ColumnEnumeration {
  val id = Value("id")
  val number = Value("number")
  val serializedProperties = Value("serialized_properties")
  val resultSummaryId = Value("result_summary_id")
  val identificationId = Value("identification_id")
}

abstract class UdsDbIdentificationSummaryTable extends TableDefinition[UdsDbIdentificationSummaryColumns.type]

object UdsDbIdentificationSummaryTable extends UdsDbIdentificationSummaryTable {
  val tableName = "identification_summary"
  val columns = UdsDbIdentificationSummaryColumns
}

object UdsDbInstrumentColumns extends ColumnEnumeration {
  val id = Value("id")
  val name = Value("name")
  val source = Value("source")
  val serializedProperties = Value("serialized_properties")
}

abstract class UdsDbInstrumentTable extends TableDefinition[UdsDbInstrumentColumns.type]

object UdsDbInstrumentTable extends UdsDbInstrumentTable {
  val tableName = "instrument"
  val columns = UdsDbInstrumentColumns
}

object UdsDbInstrumentConfigColumns extends ColumnEnumeration {
  val id = Value("id")
  val name = Value("name")
  val ms1_analyzer = Value("ms1_analyzer")
  val msnAnalyzer = Value("msn_analyzer")
  val serializedProperties = Value("serialized_properties")
  val instrumentId = Value("instrument_id")
  val activationType = Value("activation_type")
}

abstract class UdsDbInstrumentConfigTable extends TableDefinition[UdsDbInstrumentConfigColumns.type]

object UdsDbInstrumentConfigTable extends UdsDbInstrumentConfigTable {
  val tableName = "instrument_config"
  val columns = UdsDbInstrumentConfigColumns
}

object UdsDbInstrumentConfigFragmentationRuleMapColumns extends ColumnEnumeration {
  val instrumentConfigId = Value("instrument_config_id")
  val fragmentationRuleId = Value("fragmentation_rule_id")
}

abstract class UdsDbInstrumentConfigFragmentationRuleMapTable extends TableDefinition[UdsDbInstrumentConfigFragmentationRuleMapColumns.type]

object UdsDbInstrumentConfigFragmentationRuleMapTable extends UdsDbInstrumentConfigFragmentationRuleMapTable {
  val tableName = "instrument_config_fragmentation_rule_map"
  val columns = UdsDbInstrumentConfigFragmentationRuleMapColumns
}

object UdsDbObjectTreeColumns extends ColumnEnumeration {
  val id = Value("id")
  val serializedData = Value("serialized_data")
  val serializedProperties = Value("serialized_properties")
  val schemaName = Value("schema_name")
}

abstract class UdsDbObjectTreeTable extends TableDefinition[UdsDbObjectTreeColumns.type]

object UdsDbObjectTreeTable extends UdsDbObjectTreeTable {
  val tableName = "object_tree"
  val columns = UdsDbObjectTreeColumns
}

object UdsDbObjectTreeSchemaColumns extends ColumnEnumeration {
  val name = Value("name")
  val `type` = Value("type")
  val version = Value("version")
  val schema = Value("schema")
  val description = Value("description")
  val serializedProperties = Value("serialized_properties")
}

abstract class UdsDbObjectTreeSchemaTable extends TableDefinition[UdsDbObjectTreeSchemaColumns.type]

object UdsDbObjectTreeSchemaTable extends UdsDbObjectTreeSchemaTable {
  val tableName = "object_tree_schema"
  val columns = UdsDbObjectTreeSchemaColumns
}

object UdsDbPeaklistSoftwareColumns extends ColumnEnumeration {
  val id = Value("id")
  val name = Value("name")
  val version = Value("version")
  val serializedProperties = Value("serialized_properties")
  val specTitleParsingRuleId = Value("spec_title_parsing_rule_id")
}

abstract class UdsDbPeaklistSoftwareTable extends TableDefinition[UdsDbPeaklistSoftwareColumns.type]

object UdsDbPeaklistSoftwareTable extends UdsDbPeaklistSoftwareTable {
  val tableName = "peaklist_software"
  val columns = UdsDbPeaklistSoftwareColumns
}

object UdsDbProjectColumns extends ColumnEnumeration {
  val id = Value("id")
  val name = Value("name")
  val description = Value("description")
  val creationTimestamp = Value("creation_timestamp")
  val serializedProperties = Value("serialized_properties")
  val ownerId = Value("owner_id")
}

abstract class UdsDbProjectTable extends TableDefinition[UdsDbProjectColumns.type]

object UdsDbProjectTable extends UdsDbProjectTable {
  val tableName = "project"
  val columns = UdsDbProjectColumns
}

object UdsDbProjectDbMapColumns extends ColumnEnumeration {
  val externalDbId = Value("external_db_id")
  val projectId = Value("project_id")
}

abstract class UdsDbProjectDbMapTable extends TableDefinition[UdsDbProjectDbMapColumns.type]

object UdsDbProjectDbMapTable extends UdsDbProjectDbMapTable {
  val tableName = "project_db_map"
  val columns = UdsDbProjectDbMapColumns
}

object UdsDbProjectUserAccountMapColumns extends ColumnEnumeration {
  val projectId = Value("project_id")
  val userAccountId = Value("user_account_id")
  val serializedProperties = Value("serialized_properties")
}

abstract class UdsDbProjectUserAccountMapTable extends TableDefinition[UdsDbProjectUserAccountMapColumns.type]

object UdsDbProjectUserAccountMapTable extends UdsDbProjectUserAccountMapTable {
  val tableName = "project_user_account_map"
  val columns = UdsDbProjectUserAccountMapColumns
}

object UdsDbProteinMatchDecoyRuleColumns extends ColumnEnumeration {
  val id = Value("id")
  val name = Value("name")
  val acDecoyTag = Value("ac_decoy_tag")
}

abstract class UdsDbProteinMatchDecoyRuleTable extends TableDefinition[UdsDbProteinMatchDecoyRuleColumns.type]

object UdsDbProteinMatchDecoyRuleTable extends UdsDbProteinMatchDecoyRuleTable {
  val tableName = "protein_match_decoy_rule"
  val columns = UdsDbProteinMatchDecoyRuleColumns
}

object UdsDbQuantChannelColumns extends ColumnEnumeration {
  val id = Value("id")
  val number = Value("number")
  val name = Value("name")
  val contextKey = Value("context_key")
  val serializedProperties = Value("serialized_properties")
  val lcmsMapId = Value("lcms_map_id")
  val identResultSummaryId = Value("ident_result_summary_id")
  val quantResultSummaryId = Value("quant_result_summary_id")
  val quantLabelId = Value("quant_label_id")
  val sampleAnalysisReplicateId = Value("sample_analysis_replicate_id")
  val biologicalSampleId = Value("biological_sample_id")
  val quantitationFractionId = Value("quantitation_fraction_id")
  val quantitationId = Value("quantitation_id")
}

abstract class UdsDbQuantChannelTable extends TableDefinition[UdsDbQuantChannelColumns.type]

object UdsDbQuantChannelTable extends UdsDbQuantChannelTable {
  val tableName = "quant_channel"
  val columns = UdsDbQuantChannelColumns
}

object UdsDbQuantLabelColumns extends ColumnEnumeration {
  val id = Value("id")
  val `type` = Value("type")
  val name = Value("name")
  val serializedProperties = Value("serialized_properties")
  val quantMethodId = Value("quant_method_id")
}

abstract class UdsDbQuantLabelTable extends TableDefinition[UdsDbQuantLabelColumns.type]

object UdsDbQuantLabelTable extends UdsDbQuantLabelTable {
  val tableName = "quant_label"
  val columns = UdsDbQuantLabelColumns
}

object UdsDbQuantMethodColumns extends ColumnEnumeration {
  val id = Value("id")
  val name = Value("name")
  val `type` = Value("type")
  val abundanceUnit = Value("abundance_unit")
  val serializedProperties = Value("serialized_properties")
}

abstract class UdsDbQuantMethodTable extends TableDefinition[UdsDbQuantMethodColumns.type]

object UdsDbQuantMethodTable extends UdsDbQuantMethodTable {
  val tableName = "quant_method"
  val columns = UdsDbQuantMethodColumns
}

object UdsDbQuantitationColumns extends ColumnEnumeration {
  val id = Value("id")
  val number = Value("number")
  val name = Value("name")
  val description = Value("description")
  val keywords = Value("keywords")
  val creationTimestamp = Value("creation_timestamp")
  val modificationLog = Value("modification_log")
  val fractionCount = Value("fraction_count")
  val fractionationType = Value("fractionation_type")
  val serializedProperties = Value("serialized_properties")
  val quantMethodId = Value("quant_method_id")
  val projectId = Value("project_id")
}

abstract class UdsDbQuantitationTable extends TableDefinition[UdsDbQuantitationColumns.type]

object UdsDbQuantitationTable extends UdsDbQuantitationTable {
  val tableName = "quantitation"
  val columns = UdsDbQuantitationColumns
}

object UdsDbQuantitationFractionColumns extends ColumnEnumeration {
  val id = Value("id")
  val number = Value("number")
  val name = Value("name")
  val serializedProperties = Value("serialized_properties")
  val lcmsMapSetId = Value("lcms_map_set_id")
  val quantResultSummaryId = Value("quant_result_summary_id")
  val quantitationId = Value("quantitation_id")
}

abstract class UdsDbQuantitationFractionTable extends TableDefinition[UdsDbQuantitationFractionColumns.type]

object UdsDbQuantitationFractionTable extends UdsDbQuantitationFractionTable {
  val tableName = "quantitation_fraction"
  val columns = UdsDbQuantitationFractionColumns
}

object UdsDbRatioDefinitionColumns extends ColumnEnumeration {
  val id = Value("id")
  val number = Value("number")
  val numeratorId = Value("numerator_id")
  val denominatorId = Value("denominator_id")
  val groupSetupId = Value("group_setup_id")
}

abstract class UdsDbRatioDefinitionTable extends TableDefinition[UdsDbRatioDefinitionColumns.type]

object UdsDbRatioDefinitionTable extends UdsDbRatioDefinitionTable {
  val tableName = "ratio_definition"
  val columns = UdsDbRatioDefinitionColumns
}

object UdsDbRawFileColumns extends ColumnEnumeration {
  val name = Value("name")
  val extension = Value("extension")
  val directory = Value("directory")
  val creationTimestamp = Value("creation_timestamp")
  val instrumentId = Value("instrument_id")
  val ownerId = Value("owner_id")
}

abstract class UdsDbRawFileTable extends TableDefinition[UdsDbRawFileColumns.type]

object UdsDbRawFileTable extends UdsDbRawFileTable {
  val tableName = "raw_file"
  val columns = UdsDbRawFileColumns
}

object UdsDbRunColumns extends ColumnEnumeration {
  val id = Value("id")
  val number = Value("number")
  val runStart = Value("run_start")
  val runStop = Value("run_stop")
  val duration = Value("duration")
  val lcMethod = Value("lc_method")
  val msMethod = Value("ms_method")
  val analyst = Value("analyst")
  val serializedProperties = Value("serialized_properties")
  val rawFileName = Value("raw_file_name")
}

abstract class UdsDbRunTable extends TableDefinition[UdsDbRunColumns.type]

object UdsDbRunTable extends UdsDbRunTable {
  val tableName = "run"
  val columns = UdsDbRunColumns
}

object UdsDbSampleAnalysisReplicateColumns extends ColumnEnumeration {
  val id = Value("id")
  val number = Value("number")
  val serializedProperties = Value("serialized_properties")
  val biologicalSampleId = Value("biological_sample_id")
  val quantitationId = Value("quantitation_id")
}

abstract class UdsDbSampleAnalysisReplicateTable extends TableDefinition[UdsDbSampleAnalysisReplicateColumns.type]

object UdsDbSampleAnalysisReplicateTable extends UdsDbSampleAnalysisReplicateTable {
  val tableName = "sample_analysis_replicate"
  val columns = UdsDbSampleAnalysisReplicateColumns
}

object UdsDbSpecTitleParsingRuleColumns extends ColumnEnumeration {
  val id = Value("id")
  val rawFileName = Value("raw_file_name")
  val firstCycle = Value("first_cycle")
  val lastCycle = Value("last_cycle")
  val firstScan = Value("first_scan")
  val lastScan = Value("last_scan")
  val firstTime = Value("first_time")
  val lastTime = Value("last_time")
  val name = Value("name")
}

abstract class UdsDbSpecTitleParsingRuleTable extends TableDefinition[UdsDbSpecTitleParsingRuleColumns.type]

object UdsDbSpecTitleParsingRuleTable extends UdsDbSpecTitleParsingRuleTable {
  val tableName = "spec_title_parsing_rule"
  val columns = UdsDbSpecTitleParsingRuleColumns
}

object UdsDbTheoreticalFragmentColumns extends ColumnEnumeration {
  val id = Value("id")
  val `type` = Value("type")
  val neutralLoss = Value("neutral_loss")
  val serializedProperties = Value("serialized_properties")
}

abstract class UdsDbTheoreticalFragmentTable extends TableDefinition[UdsDbTheoreticalFragmentColumns.type]

object UdsDbTheoreticalFragmentTable extends UdsDbTheoreticalFragmentTable {
  val tableName = "theoretical_fragment"
  val columns = UdsDbTheoreticalFragmentColumns
}

object UdsDbUserAccountColumns extends ColumnEnumeration {
  val id = Value("id")
  val login = Value("login")
  val creationMode = Value("creation_mode")
  val serializedProperties = Value("serialized_properties")
}

abstract class UdsDbUserAccountTable extends TableDefinition[UdsDbUserAccountColumns.type]

object UdsDbUserAccountTable extends UdsDbUserAccountTable {
  val tableName = "user_account"
  val columns = UdsDbUserAccountColumns
}

object UdsDbVirtualFolderColumns extends ColumnEnumeration {
  val id = Value("id")
  val name = Value("name")
  val path = Value("path")
  val serializedProperties = Value("serialized_properties")
  val parentVirtualFolderId = Value("parent_virtual_folder_id")
  val projectId = Value("project_id")
}

abstract class UdsDbVirtualFolderTable extends TableDefinition[UdsDbVirtualFolderColumns.type]

object UdsDbVirtualFolderTable extends UdsDbVirtualFolderTable {
  val tableName = "virtual_folder"
  val columns = UdsDbVirtualFolderColumns
}
