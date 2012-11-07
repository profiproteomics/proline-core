package fr.proline.core.dal

import fr.proline.core.utils.sql.TableDefinition

object UdsDbActivationTable extends TableDefinition {

  val tableName = "activation"

  object columns extends Enumeration {

    val `type` = Value("type")
  }

  def getColumnsAsStrList( f: UdsDbActivationTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[UdsDbActivationTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: UdsDbActivationTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[UdsDbActivationTable.columns.type]( f )
  }

}

object UdsDbAdminInfosTable extends TableDefinition {

  val tableName = "admin_infos"

  object columns extends Enumeration {

    val modelVersion = Value("model_version")
    val dbCreationDate = Value("db_creation_date")
    val modelUpdateDate = Value("model_update_date")
    val configuration = Value("configuration")
  }

  def getColumnsAsStrList( f: UdsDbAdminInfosTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[UdsDbAdminInfosTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: UdsDbAdminInfosTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[UdsDbAdminInfosTable.columns.type]( f )
  }

}

object UdsDbBiologicalGroupTable extends TableDefinition {

  val tableName = "biological_group"

  object columns extends Enumeration {

    val id = Value("id")
    val number = Value("number")
    val name = Value("name")
    val serializedProperties = Value("serialized_properties")
    val groupSetupId = Value("group_setup_id")
  }

  def getColumnsAsStrList( f: UdsDbBiologicalGroupTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[UdsDbBiologicalGroupTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: UdsDbBiologicalGroupTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[UdsDbBiologicalGroupTable.columns.type]( f )
  }

}

object UdsDbBiologicalGroupBiologicalSampleItemTable extends TableDefinition {

  val tableName = "biological_group_biological_sample_item"

  object columns extends Enumeration {

    val biologicalGroupId = Value("biological_group_id")
    val biologicalSampleId = Value("biological_sample_id")
  }

  def getColumnsAsStrList( f: UdsDbBiologicalGroupBiologicalSampleItemTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[UdsDbBiologicalGroupBiologicalSampleItemTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: UdsDbBiologicalGroupBiologicalSampleItemTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[UdsDbBiologicalGroupBiologicalSampleItemTable.columns.type]( f )
  }

}

object UdsDbBiologicalSampleTable extends TableDefinition {

  val tableName = "biological_sample"

  object columns extends Enumeration {

    val id = Value("id")
    val number = Value("number")
    val name = Value("name")
    val serializedProperties = Value("serialized_properties")
    val quantitationId = Value("quantitation_id")
  }

  def getColumnsAsStrList( f: UdsDbBiologicalSampleTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[UdsDbBiologicalSampleTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: UdsDbBiologicalSampleTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[UdsDbBiologicalSampleTable.columns.type]( f )
  }

}

object UdsDbDocumentTable extends TableDefinition {

  val tableName = "document"

  object columns extends Enumeration {

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

  def getColumnsAsStrList( f: UdsDbDocumentTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[UdsDbDocumentTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: UdsDbDocumentTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[UdsDbDocumentTable.columns.type]( f )
  }

}

object UdsDbEnzymeTable extends TableDefinition {

  val tableName = "enzyme"

  object columns extends Enumeration {

    val id = Value("id")
    val name = Value("name")
    val cleavageRegexp = Value("cleavage_regexp")
    val isIndependant = Value("is_independant")
    val isSemiSpecific = Value("is_semi_specific")
  }

  def getColumnsAsStrList( f: UdsDbEnzymeTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[UdsDbEnzymeTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: UdsDbEnzymeTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[UdsDbEnzymeTable.columns.type]( f )
  }

}

object UdsDbEnzymeCleavageTable extends TableDefinition {

  val tableName = "enzyme_cleavage"

  object columns extends Enumeration {

    val id = Value("id")
    val site = Value("site")
    val residues = Value("residues")
    val restrictiveResidues = Value("restrictive_residues")
    val enzymeId = Value("enzyme_id")
  }

  def getColumnsAsStrList( f: UdsDbEnzymeCleavageTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[UdsDbEnzymeCleavageTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: UdsDbEnzymeCleavageTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[UdsDbEnzymeCleavageTable.columns.type]( f )
  }

}

object UdsDbExternalDbTable extends TableDefinition {

  val tableName = "external_db"

  object columns extends Enumeration {

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

  def getColumnsAsStrList( f: UdsDbExternalDbTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[UdsDbExternalDbTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: UdsDbExternalDbTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[UdsDbExternalDbTable.columns.type]( f )
  }

}

object UdsDbFragmentationRuleTable extends TableDefinition {

  val tableName = "fragmentation_rule"

  object columns extends Enumeration {

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

  def getColumnsAsStrList( f: UdsDbFragmentationRuleTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[UdsDbFragmentationRuleTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: UdsDbFragmentationRuleTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[UdsDbFragmentationRuleTable.columns.type]( f )
  }

}

object UdsDbGroupSetupTable extends TableDefinition {

  val tableName = "group_setup"

  object columns extends Enumeration {

    val id = Value("id")
    val name = Value("name")
    val serializedProperties = Value("serialized_properties")
    val quantitationId = Value("quantitation_id")
  }

  def getColumnsAsStrList( f: UdsDbGroupSetupTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[UdsDbGroupSetupTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: UdsDbGroupSetupTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[UdsDbGroupSetupTable.columns.type]( f )
  }

}

object UdsDbIdentificationTable extends TableDefinition {

  val tableName = "identification"

  object columns extends Enumeration {

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

  def getColumnsAsStrList( f: UdsDbIdentificationTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[UdsDbIdentificationTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: UdsDbIdentificationTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[UdsDbIdentificationTable.columns.type]( f )
  }

}

object UdsDbIdentificationFractionTable extends TableDefinition {

  val tableName = "identification_fraction"

  object columns extends Enumeration {

    val id = Value("id")
    val number = Value("number")
    val serializedProperties = Value("serialized_properties")
    val resultSetId = Value("result_set_id")
    val identificationId = Value("identification_id")
    val runId = Value("run_id")
    val rawFileName = Value("raw_file_name")
  }

  def getColumnsAsStrList( f: UdsDbIdentificationFractionTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[UdsDbIdentificationFractionTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: UdsDbIdentificationFractionTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[UdsDbIdentificationFractionTable.columns.type]( f )
  }

}

object UdsDbIdentificationFractionSummaryTable extends TableDefinition {

  val tableName = "identification_fraction_summary"

  object columns extends Enumeration {

    val id = Value("id")
    val serializedProperties = Value("serialized_properties")
    val resultSummaryId = Value("result_summary_id")
    val identificationFractionId = Value("identification_fraction_id")
    val identificationSummaryId = Value("identification_summary_id")
  }

  def getColumnsAsStrList( f: UdsDbIdentificationFractionSummaryTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[UdsDbIdentificationFractionSummaryTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: UdsDbIdentificationFractionSummaryTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[UdsDbIdentificationFractionSummaryTable.columns.type]( f )
  }

}

object UdsDbIdentificationSummaryTable extends TableDefinition {

  val tableName = "identification_summary"

  object columns extends Enumeration {

    val id = Value("id")
    val number = Value("number")
    val serializedProperties = Value("serialized_properties")
    val resultSummaryId = Value("result_summary_id")
    val identificationId = Value("identification_id")
  }

  def getColumnsAsStrList( f: UdsDbIdentificationSummaryTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[UdsDbIdentificationSummaryTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: UdsDbIdentificationSummaryTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[UdsDbIdentificationSummaryTable.columns.type]( f )
  }

}

object UdsDbInstrumentTable extends TableDefinition {

  val tableName = "instrument"

  object columns extends Enumeration {

    val id = Value("id")
    val name = Value("name")
    val source = Value("source")
    val serializedProperties = Value("serialized_properties")
  }

  def getColumnsAsStrList( f: UdsDbInstrumentTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[UdsDbInstrumentTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: UdsDbInstrumentTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[UdsDbInstrumentTable.columns.type]( f )
  }

}

object UdsDbInstrumentConfigTable extends TableDefinition {

  val tableName = "instrument_config"

  object columns extends Enumeration {

    val id = Value("id")
    val name = Value("name")
    val ms1_analyzer = Value("ms1_analyzer")
    val msnAnalyzer = Value("msn_analyzer")
    val serializedProperties = Value("serialized_properties")
    val instrumentId = Value("instrument_id")
    val activationType = Value("activation_type")
  }

  def getColumnsAsStrList( f: UdsDbInstrumentConfigTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[UdsDbInstrumentConfigTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: UdsDbInstrumentConfigTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[UdsDbInstrumentConfigTable.columns.type]( f )
  }

}

object UdsDbInstrumentConfigFragmentationRuleMapTable extends TableDefinition {

  val tableName = "instrument_config_fragmentation_rule_map"

  object columns extends Enumeration {

    val instrumentConfigId = Value("instrument_config_id")
    val fragmentationRuleId = Value("fragmentation_rule_id")
  }

  def getColumnsAsStrList( f: UdsDbInstrumentConfigFragmentationRuleMapTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[UdsDbInstrumentConfigFragmentationRuleMapTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: UdsDbInstrumentConfigFragmentationRuleMapTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[UdsDbInstrumentConfigFragmentationRuleMapTable.columns.type]( f )
  }

}

object UdsDbObjectTreeTable extends TableDefinition {

  val tableName = "object_tree"

  object columns extends Enumeration {

    val id = Value("id")
    val serializedData = Value("serialized_data")
    val serializedProperties = Value("serialized_properties")
    val schemaName = Value("schema_name")
  }

  def getColumnsAsStrList( f: UdsDbObjectTreeTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[UdsDbObjectTreeTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: UdsDbObjectTreeTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[UdsDbObjectTreeTable.columns.type]( f )
  }

}

object UdsDbObjectTreeSchemaTable extends TableDefinition {

  val tableName = "object_tree_schema"

  object columns extends Enumeration {

    val name = Value("name")
    val `type` = Value("type")
    val version = Value("version")
    val schema = Value("schema")
    val description = Value("description")
    val serializedProperties = Value("serialized_properties")
  }

  def getColumnsAsStrList( f: UdsDbObjectTreeSchemaTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[UdsDbObjectTreeSchemaTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: UdsDbObjectTreeSchemaTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[UdsDbObjectTreeSchemaTable.columns.type]( f )
  }

}

object UdsDbPeaklistSoftwareTable extends TableDefinition {

  val tableName = "peaklist_software"

  object columns extends Enumeration {

    val id = Value("id")
    val name = Value("name")
    val version = Value("version")
    val serializedProperties = Value("serialized_properties")
    val specTitleParsingRuleId = Value("spec_title_parsing_rule_id")
  }

  def getColumnsAsStrList( f: UdsDbPeaklistSoftwareTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[UdsDbPeaklistSoftwareTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: UdsDbPeaklistSoftwareTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[UdsDbPeaklistSoftwareTable.columns.type]( f )
  }

}

object UdsDbProjectTable extends TableDefinition {

  val tableName = "project"

  object columns extends Enumeration {

    val id = Value("id")
    val name = Value("name")
    val description = Value("description")
    val creationTimestamp = Value("creation_timestamp")
    val serializedProperties = Value("serialized_properties")
    val ownerId = Value("owner_id")
  }

  def getColumnsAsStrList( f: UdsDbProjectTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[UdsDbProjectTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: UdsDbProjectTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[UdsDbProjectTable.columns.type]( f )
  }

}

object UdsDbProjectDbMapTable extends TableDefinition {

  val tableName = "project_db_map"

  object columns extends Enumeration {

    val externalDbId = Value("external_db_id")
    val projectId = Value("project_id")
  }

  def getColumnsAsStrList( f: UdsDbProjectDbMapTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[UdsDbProjectDbMapTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: UdsDbProjectDbMapTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[UdsDbProjectDbMapTable.columns.type]( f )
  }

}

object UdsDbProjectUserAccountMapTable extends TableDefinition {

  val tableName = "project_user_account_map"

  object columns extends Enumeration {

    val projectId = Value("project_id")
    val userAccountId = Value("user_account_id")
    val serializedProperties = Value("serialized_properties")
  }

  def getColumnsAsStrList( f: UdsDbProjectUserAccountMapTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[UdsDbProjectUserAccountMapTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: UdsDbProjectUserAccountMapTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[UdsDbProjectUserAccountMapTable.columns.type]( f )
  }

}

object UdsDbProteinMatchDecoyRuleTable extends TableDefinition {

  val tableName = "protein_match_decoy_rule"

  object columns extends Enumeration {

    val id = Value("id")
    val name = Value("name")
    val acDecoyTag = Value("ac_decoy_tag")
  }

  def getColumnsAsStrList( f: UdsDbProteinMatchDecoyRuleTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[UdsDbProteinMatchDecoyRuleTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: UdsDbProteinMatchDecoyRuleTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[UdsDbProteinMatchDecoyRuleTable.columns.type]( f )
  }

}

object UdsDbQuantChannelTable extends TableDefinition {

  val tableName = "quant_channel"

  object columns extends Enumeration {

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

  def getColumnsAsStrList( f: UdsDbQuantChannelTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[UdsDbQuantChannelTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: UdsDbQuantChannelTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[UdsDbQuantChannelTable.columns.type]( f )
  }

}

object UdsDbQuantLabelTable extends TableDefinition {

  val tableName = "quant_label"

  object columns extends Enumeration {

    val id = Value("id")
    val `type` = Value("type")
    val name = Value("name")
    val serializedProperties = Value("serialized_properties")
    val quantMethodId = Value("quant_method_id")
  }

  def getColumnsAsStrList( f: UdsDbQuantLabelTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[UdsDbQuantLabelTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: UdsDbQuantLabelTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[UdsDbQuantLabelTable.columns.type]( f )
  }

}

object UdsDbQuantMethodTable extends TableDefinition {

  val tableName = "quant_method"

  object columns extends Enumeration {

    val id = Value("id")
    val name = Value("name")
    val `type` = Value("type")
    val abundanceUnit = Value("abundance_unit")
    val serializedProperties = Value("serialized_properties")
  }

  def getColumnsAsStrList( f: UdsDbQuantMethodTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[UdsDbQuantMethodTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: UdsDbQuantMethodTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[UdsDbQuantMethodTable.columns.type]( f )
  }

}

object UdsDbQuantitationTable extends TableDefinition {

  val tableName = "quantitation"

  object columns extends Enumeration {

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

  def getColumnsAsStrList( f: UdsDbQuantitationTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[UdsDbQuantitationTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: UdsDbQuantitationTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[UdsDbQuantitationTable.columns.type]( f )
  }

}

object UdsDbQuantitationFractionTable extends TableDefinition {

  val tableName = "quantitation_fraction"

  object columns extends Enumeration {

    val id = Value("id")
    val number = Value("number")
    val name = Value("name")
    val serializedProperties = Value("serialized_properties")
    val lcmsMapSetId = Value("lcms_map_set_id")
    val quantResultSummaryId = Value("quant_result_summary_id")
    val quantitationId = Value("quantitation_id")
  }

  def getColumnsAsStrList( f: UdsDbQuantitationFractionTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[UdsDbQuantitationFractionTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: UdsDbQuantitationFractionTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[UdsDbQuantitationFractionTable.columns.type]( f )
  }

}

object UdsDbRatioDefinitionTable extends TableDefinition {

  val tableName = "ratio_definition"

  object columns extends Enumeration {

    val id = Value("id")
    val number = Value("number")
    val numeratorId = Value("numerator_id")
    val denominatorId = Value("denominator_id")
    val groupSetupId = Value("group_setup_id")
  }

  def getColumnsAsStrList( f: UdsDbRatioDefinitionTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[UdsDbRatioDefinitionTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: UdsDbRatioDefinitionTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[UdsDbRatioDefinitionTable.columns.type]( f )
  }

}

object UdsDbRawFileTable extends TableDefinition {

  val tableName = "raw_file"

  object columns extends Enumeration {

    val name = Value("name")
    val extension = Value("extension")
    val directory = Value("directory")
    val creationTimestamp = Value("creation_timestamp")
    val instrumentId = Value("instrument_id")
    val ownerId = Value("owner_id")
  }

  def getColumnsAsStrList( f: UdsDbRawFileTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[UdsDbRawFileTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: UdsDbRawFileTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[UdsDbRawFileTable.columns.type]( f )
  }

}

object UdsDbRunTable extends TableDefinition {

  val tableName = "run"

  object columns extends Enumeration {

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

  def getColumnsAsStrList( f: UdsDbRunTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[UdsDbRunTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: UdsDbRunTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[UdsDbRunTable.columns.type]( f )
  }

}

object UdsDbSampleAnalysisReplicateTable extends TableDefinition {

  val tableName = "sample_analysis_replicate"

  object columns extends Enumeration {

    val id = Value("id")
    val number = Value("number")
    val serializedProperties = Value("serialized_properties")
    val biologicalSampleId = Value("biological_sample_id")
    val quantitationId = Value("quantitation_id")
  }

  def getColumnsAsStrList( f: UdsDbSampleAnalysisReplicateTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[UdsDbSampleAnalysisReplicateTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: UdsDbSampleAnalysisReplicateTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[UdsDbSampleAnalysisReplicateTable.columns.type]( f )
  }

}

object UdsDbSpecTitleParsingRuleTable extends TableDefinition {

  val tableName = "spec_title_parsing_rule"

  object columns extends Enumeration {

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

  def getColumnsAsStrList( f: UdsDbSpecTitleParsingRuleTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[UdsDbSpecTitleParsingRuleTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: UdsDbSpecTitleParsingRuleTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[UdsDbSpecTitleParsingRuleTable.columns.type]( f )
  }

}

object UdsDbTheoreticalFragmentTable extends TableDefinition {

  val tableName = "theoretical_fragment"

  object columns extends Enumeration {

    val id = Value("id")
    val `type` = Value("type")
    val neutralLoss = Value("neutral_loss")
    val serializedProperties = Value("serialized_properties")
  }

  def getColumnsAsStrList( f: UdsDbTheoreticalFragmentTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[UdsDbTheoreticalFragmentTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: UdsDbTheoreticalFragmentTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[UdsDbTheoreticalFragmentTable.columns.type]( f )
  }

}

object UdsDbUserAccountTable extends TableDefinition {

  val tableName = "user_account"

  object columns extends Enumeration {

    val id = Value("id")
    val login = Value("login")
    val creationMode = Value("creation_mode")
    val serializedProperties = Value("serialized_properties")
  }

  def getColumnsAsStrList( f: UdsDbUserAccountTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[UdsDbUserAccountTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: UdsDbUserAccountTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[UdsDbUserAccountTable.columns.type]( f )
  }

}

object UdsDbVirtualFolderTable extends TableDefinition {

  val tableName = "virtual_folder"

  object columns extends Enumeration {

    val id = Value("id")
    val name = Value("name")
    val path = Value("path")
    val serializedProperties = Value("serialized_properties")
    val parentVirtualFolderId = Value("parent_virtual_folder_id")
    val projectId = Value("project_id")
  }

  def getColumnsAsStrList( f: UdsDbVirtualFolderTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[UdsDbVirtualFolderTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: UdsDbVirtualFolderTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[UdsDbVirtualFolderTable.columns.type]( f )
  }

}


