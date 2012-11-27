package fr.proline.core.dal

object MsiDbAdminInfosTable extends TableDefinition {

  val tableName = "admin_infos"

  object columns extends Enumeration {

    val modelVersion = Value("model_version")
    val dbCreationDate = Value("db_creation_date")
    val modelUpdateDate = Value("model_update_date")
  }

  def getColumnsAsStrList( f: MsiDbAdminInfosTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[MsiDbAdminInfosTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: MsiDbAdminInfosTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[MsiDbAdminInfosTable.columns.type]( f )
  }

}

object MsiDbBioSequenceTable extends TableDefinition {

  val tableName = "bio_sequence"

  object columns extends Enumeration {

    val id = Value("id")
    val alphabet = Value("alphabet")
    val sequence = Value("sequence")
    val length = Value("length")
    val mass = Value("mass")
    val pi = Value("pi")
    val crc64 = Value("crc64")
    val serializedProperties = Value("serialized_properties")
  }

  def getColumnsAsStrList( f: MsiDbBioSequenceTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[MsiDbBioSequenceTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: MsiDbBioSequenceTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[MsiDbBioSequenceTable.columns.type]( f )
  }

}

object MsiDbCacheTable extends TableDefinition {

  val tableName = "cache"

  object columns extends Enumeration {

    val scope = Value("scope")
    val id = Value("id")
    val format = Value("format")
    val byteOrder = Value("byte_order")
    val data = Value("data")
    val compression = Value("compression")
    val timestamp = Value("timestamp")
    val serializedProperties = Value("serialized_properties")
  }

  def getColumnsAsStrList( f: MsiDbCacheTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[MsiDbCacheTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: MsiDbCacheTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[MsiDbCacheTable.columns.type]( f )
  }

}

object MsiDbConsensusSpectrumTable extends TableDefinition {

  val tableName = "consensus_spectrum"

  object columns extends Enumeration {

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

  def getColumnsAsStrList( f: MsiDbConsensusSpectrumTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[MsiDbConsensusSpectrumTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: MsiDbConsensusSpectrumTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[MsiDbConsensusSpectrumTable.columns.type]( f )
  }

}

object MsiDbEnzymeTable extends TableDefinition {

  val tableName = "enzyme"

  object columns extends Enumeration {

    val id = Value("id")
    val name = Value("name")
    val cleavageRegexp = Value("cleavage_regexp")
    val isIndependant = Value("is_independant")
    val isSemiSpecific = Value("is_semi_specific")
  }

  def getColumnsAsStrList( f: MsiDbEnzymeTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[MsiDbEnzymeTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: MsiDbEnzymeTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[MsiDbEnzymeTable.columns.type]( f )
  }

}

object MsiDbInstrumentConfigTable extends TableDefinition {

  val tableName = "instrument_config"

  object columns extends Enumeration {

    val id = Value("id")
    val name = Value("name")
    val ms1_analyzer = Value("ms1_analyzer")
    val msnAnalyzer = Value("msn_analyzer")
    val serializedProperties = Value("serialized_properties")
  }

  def getColumnsAsStrList( f: MsiDbInstrumentConfigTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[MsiDbInstrumentConfigTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: MsiDbInstrumentConfigTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[MsiDbInstrumentConfigTable.columns.type]( f )
  }

}

object MsiDbIonSearchTable extends TableDefinition {

  val tableName = "ion_search"

  object columns extends Enumeration {

    val id = Value("id")
    val maxProteinMass = Value("max_protein_mass")
    val minProteinMass = Value("min_protein_mass")
    val proteinPi = Value("protein_pi")
  }

  def getColumnsAsStrList( f: MsiDbIonSearchTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[MsiDbIonSearchTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: MsiDbIonSearchTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[MsiDbIonSearchTable.columns.type]( f )
  }

}

object MsiDbMasterQuantComponentTable extends TableDefinition {

  val tableName = "master_quant_component"

  object columns extends Enumeration {

    val id = Value("id")
    val selectionLevel = Value("selection_level")
    val serializedProperties = Value("serialized_properties")
    val objectTreeId = Value("object_tree_id")
    val schemaName = Value("schema_name")
    val resultSummaryId = Value("result_summary_id")
  }

  def getColumnsAsStrList( f: MsiDbMasterQuantComponentTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[MsiDbMasterQuantComponentTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: MsiDbMasterQuantComponentTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[MsiDbMasterQuantComponentTable.columns.type]( f )
  }

}

object MsiDbMasterQuantPeptideIonTable extends TableDefinition {

  val tableName = "master_quant_peptide_ion"

  object columns extends Enumeration {

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

  def getColumnsAsStrList( f: MsiDbMasterQuantPeptideIonTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[MsiDbMasterQuantPeptideIonTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: MsiDbMasterQuantPeptideIonTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[MsiDbMasterQuantPeptideIonTable.columns.type]( f )
  }

}

object MsiDbMasterQuantReporterIonTable extends TableDefinition {

  val tableName = "master_quant_reporter_ion"

  object columns extends Enumeration {

    val id = Value("id")
    val serializedProperties = Value("serialized_properties")
    val masterQuantComponentId = Value("master_quant_component_id")
    val msQueryId = Value("ms_query_id")
    val masterQuantPeptideIonId = Value("master_quant_peptide_ion_id")
    val resultSummaryId = Value("result_summary_id")
  }

  def getColumnsAsStrList( f: MsiDbMasterQuantReporterIonTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[MsiDbMasterQuantReporterIonTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: MsiDbMasterQuantReporterIonTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[MsiDbMasterQuantReporterIonTable.columns.type]( f )
  }

}

object MsiDbMsQueryTable extends TableDefinition {

  val tableName = "ms_query"

  object columns extends Enumeration {

    val id = Value("id")
    val initialId = Value("initial_id")
    val charge = Value("charge")
    val moz = Value("moz")
    val serializedProperties = Value("serialized_properties")
    val spectrumId = Value("spectrum_id")
    val msiSearchId = Value("msi_search_id")
  }

  def getColumnsAsStrList( f: MsiDbMsQueryTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[MsiDbMsQueryTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: MsiDbMsQueryTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[MsiDbMsQueryTable.columns.type]( f )
  }

}

object MsiDbMsiSearchTable extends TableDefinition {

  val tableName = "msi_search"

  object columns extends Enumeration {

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

  def getColumnsAsStrList( f: MsiDbMsiSearchTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[MsiDbMsiSearchTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: MsiDbMsiSearchTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[MsiDbMsiSearchTable.columns.type]( f )
  }

}

object MsiDbMsiSearchObjectTreeMapTable extends TableDefinition {

  val tableName = "msi_search_object_tree_map"

  object columns extends Enumeration {

    val msiSearchId = Value("msi_search_id")
    val objectTreeId = Value("object_tree_id")
    val schemaName = Value("schema_name")
  }

  def getColumnsAsStrList( f: MsiDbMsiSearchObjectTreeMapTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[MsiDbMsiSearchObjectTreeMapTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: MsiDbMsiSearchObjectTreeMapTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[MsiDbMsiSearchObjectTreeMapTable.columns.type]( f )
  }

}

object MsiDbMsmsSearchTable extends TableDefinition {

  val tableName = "msms_search"

  object columns extends Enumeration {

    val id = Value("id")
    val fragmentChargeStates = Value("fragment_charge_states")
    val fragmentMassErrorTolerance = Value("fragment_mass_error_tolerance")
    val fragmentMassErrorToleranceUnit = Value("fragment_mass_error_tolerance_unit")
  }

  def getColumnsAsStrList( f: MsiDbMsmsSearchTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[MsiDbMsmsSearchTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: MsiDbMsmsSearchTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[MsiDbMsmsSearchTable.columns.type]( f )
  }

}

object MsiDbObjectTreeTable extends TableDefinition {

  val tableName = "object_tree"

  object columns extends Enumeration {

    val id = Value("id")
    val serializedData = Value("serialized_data")
    val serializedProperties = Value("serialized_properties")
    val schemaName = Value("schema_name")
  }

  def getColumnsAsStrList( f: MsiDbObjectTreeTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[MsiDbObjectTreeTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: MsiDbObjectTreeTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[MsiDbObjectTreeTable.columns.type]( f )
  }

}

object MsiDbObjectTreeSchemaTable extends TableDefinition {

  val tableName = "object_tree_schema"

  object columns extends Enumeration {

    val name = Value("name")
    val `type` = Value("type")
    val version = Value("version")
    val schema = Value("schema")
    val description = Value("description")
    val serializedProperties = Value("serialized_properties")
  }

  def getColumnsAsStrList( f: MsiDbObjectTreeSchemaTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[MsiDbObjectTreeSchemaTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: MsiDbObjectTreeSchemaTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[MsiDbObjectTreeSchemaTable.columns.type]( f )
  }

}

object MsiDbPeaklistTable extends TableDefinition {

  val tableName = "peaklist"

  object columns extends Enumeration {

    val id = Value("id")
    val `type` = Value("type")
    val path = Value("path")
    val rawFileName = Value("raw_file_name")
    val msLevel = Value("ms_level")
    val spectrumDataCompression = Value("spectrum_data_compression")
    val serializedProperties = Value("serialized_properties")
    val peaklistSoftwareId = Value("peaklist_software_id")
  }

  def getColumnsAsStrList( f: MsiDbPeaklistTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[MsiDbPeaklistTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: MsiDbPeaklistTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[MsiDbPeaklistTable.columns.type]( f )
  }

}

object MsiDbPeaklistRelationTable extends TableDefinition {

  val tableName = "peaklist_relation"

  object columns extends Enumeration {

    val parentPeaklistId = Value("parent_peaklist_id")
    val childPeaklistId = Value("child_peaklist_id")
  }

  def getColumnsAsStrList( f: MsiDbPeaklistRelationTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[MsiDbPeaklistRelationTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: MsiDbPeaklistRelationTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[MsiDbPeaklistRelationTable.columns.type]( f )
  }

}

object MsiDbPeaklistSoftwareTable extends TableDefinition {

  val tableName = "peaklist_software"

  object columns extends Enumeration {

    val id = Value("id")
    val name = Value("name")
    val version = Value("version")
    val serializedProperties = Value("serialized_properties")
  }

  def getColumnsAsStrList( f: MsiDbPeaklistSoftwareTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[MsiDbPeaklistSoftwareTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: MsiDbPeaklistSoftwareTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[MsiDbPeaklistSoftwareTable.columns.type]( f )
  }

}

object MsiDbPeptideTable extends TableDefinition {

  val tableName = "peptide"

  object columns extends Enumeration {

    val id = Value("id")
    val sequence = Value("sequence")
    val ptmString = Value("ptm_string")
    val calculatedMass = Value("calculated_mass")
    val serializedProperties = Value("serialized_properties")
  }

  def getColumnsAsStrList( f: MsiDbPeptideTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[MsiDbPeptideTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: MsiDbPeptideTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[MsiDbPeptideTable.columns.type]( f )
  }

}

object MsiDbPeptideInstanceTable extends TableDefinition {

  val tableName = "peptide_instance"

  object columns extends Enumeration {

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

  def getColumnsAsStrList( f: MsiDbPeptideInstanceTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[MsiDbPeptideInstanceTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: MsiDbPeptideInstanceTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[MsiDbPeptideInstanceTable.columns.type]( f )
  }

}

object MsiDbPeptideInstancePeptideMatchMapTable extends TableDefinition {

  val tableName = "peptide_instance_peptide_match_map"

  object columns extends Enumeration {

    val peptideInstanceId = Value("peptide_instance_id")
    val peptideMatchId = Value("peptide_match_id")
    val serializedProperties = Value("serialized_properties")
    val resultSummaryId = Value("result_summary_id")
  }

  def getColumnsAsStrList( f: MsiDbPeptideInstancePeptideMatchMapTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[MsiDbPeptideInstancePeptideMatchMapTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: MsiDbPeptideInstancePeptideMatchMapTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[MsiDbPeptideInstancePeptideMatchMapTable.columns.type]( f )
  }

}

object MsiDbPeptideMatchTable extends TableDefinition {

  val tableName = "peptide_match"

  object columns extends Enumeration {

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

  def getColumnsAsStrList( f: MsiDbPeptideMatchTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[MsiDbPeptideMatchTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: MsiDbPeptideMatchTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[MsiDbPeptideMatchTable.columns.type]( f )
  }

}

object MsiDbPeptideMatchObjectTreeMapTable extends TableDefinition {

  val tableName = "peptide_match_object_tree_map"

  object columns extends Enumeration {

    val peptideMatchId = Value("peptide_match_id")
    val objectTreeId = Value("object_tree_id")
    val schemaName = Value("schema_name")
  }

  def getColumnsAsStrList( f: MsiDbPeptideMatchObjectTreeMapTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[MsiDbPeptideMatchObjectTreeMapTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: MsiDbPeptideMatchObjectTreeMapTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[MsiDbPeptideMatchObjectTreeMapTable.columns.type]( f )
  }

}

object MsiDbPeptideMatchRelationTable extends TableDefinition {

  val tableName = "peptide_match_relation"

  object columns extends Enumeration {

    val parentPeptideMatchId = Value("parent_peptide_match_id")
    val childPeptideMatchId = Value("child_peptide_match_id")
    val parentResultSetId = Value("parent_result_set_id")
  }

  def getColumnsAsStrList( f: MsiDbPeptideMatchRelationTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[MsiDbPeptideMatchRelationTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: MsiDbPeptideMatchRelationTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[MsiDbPeptideMatchRelationTable.columns.type]( f )
  }

}

object MsiDbPeptideSetTable extends TableDefinition {

  val tableName = "peptide_set"

  object columns extends Enumeration {

    val id = Value("id")
    val isSubset = Value("is_subset")
    val peptideCount = Value("peptide_count")
    val peptideMatchCount = Value("peptide_match_count")
    val serializedProperties = Value("serialized_properties")
    val proteinSetId = Value("protein_set_id")
    val resultSummaryId = Value("result_summary_id")
  }

  def getColumnsAsStrList( f: MsiDbPeptideSetTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[MsiDbPeptideSetTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: MsiDbPeptideSetTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[MsiDbPeptideSetTable.columns.type]( f )
  }

}

object MsiDbPeptideSetPeptideInstanceItemTable extends TableDefinition {

  val tableName = "peptide_set_peptide_instance_item"

  object columns extends Enumeration {

    val peptideSetId = Value("peptide_set_id")
    val peptideInstanceId = Value("peptide_instance_id")
    val isBestPeptideSet = Value("is_best_peptide_set")
    val selectionLevel = Value("selection_level")
    val serializedProperties = Value("serialized_properties")
    val resultSummaryId = Value("result_summary_id")
  }

  def getColumnsAsStrList( f: MsiDbPeptideSetPeptideInstanceItemTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[MsiDbPeptideSetPeptideInstanceItemTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: MsiDbPeptideSetPeptideInstanceItemTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[MsiDbPeptideSetPeptideInstanceItemTable.columns.type]( f )
  }

}

object MsiDbPeptideSetProteinMatchMapTable extends TableDefinition {

  val tableName = "peptide_set_protein_match_map"

  object columns extends Enumeration {

    val peptideSetId = Value("peptide_set_id")
    val proteinMatchId = Value("protein_match_id")
    val resultSummaryId = Value("result_summary_id")
  }

  def getColumnsAsStrList( f: MsiDbPeptideSetProteinMatchMapTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[MsiDbPeptideSetProteinMatchMapTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: MsiDbPeptideSetProteinMatchMapTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[MsiDbPeptideSetProteinMatchMapTable.columns.type]( f )
  }

}

object MsiDbPeptideSetRelationTable extends TableDefinition {

  val tableName = "peptide_set_relation"

  object columns extends Enumeration {

    val peptideOversetId = Value("peptide_overset_id")
    val peptideSubsetId = Value("peptide_subset_id")
    val isStrictSubset = Value("is_strict_subset")
    val resultSummaryId = Value("result_summary_id")
  }

  def getColumnsAsStrList( f: MsiDbPeptideSetRelationTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[MsiDbPeptideSetRelationTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: MsiDbPeptideSetRelationTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[MsiDbPeptideSetRelationTable.columns.type]( f )
  }

}

object MsiDbProteinMatchTable extends TableDefinition {

  val tableName = "protein_match"

  object columns extends Enumeration {

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

  def getColumnsAsStrList( f: MsiDbProteinMatchTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[MsiDbProteinMatchTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: MsiDbProteinMatchTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[MsiDbProteinMatchTable.columns.type]( f )
  }

}

object MsiDbProteinMatchSeqDatabaseMapTable extends TableDefinition {

  val tableName = "protein_match_seq_database_map"

  object columns extends Enumeration {

    val proteinMatchId = Value("protein_match_id")
    val seqDatabaseId = Value("seq_database_id")
    val resultSetId = Value("result_set_id")
  }

  def getColumnsAsStrList( f: MsiDbProteinMatchSeqDatabaseMapTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[MsiDbProteinMatchSeqDatabaseMapTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: MsiDbProteinMatchSeqDatabaseMapTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[MsiDbProteinMatchSeqDatabaseMapTable.columns.type]( f )
  }

}

object MsiDbProteinSetTable extends TableDefinition {

  val tableName = "protein_set"

  object columns extends Enumeration {

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

  def getColumnsAsStrList( f: MsiDbProteinSetTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[MsiDbProteinSetTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: MsiDbProteinSetTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[MsiDbProteinSetTable.columns.type]( f )
  }

}

object MsiDbProteinSetClusterTable extends TableDefinition {

  val tableName = "protein_set_cluster"

  object columns extends Enumeration {

    val id = Value("id")
    val serializedProperties = Value("serialized_properties")
    val bestProteinSetId = Value("best_protein_set_id")
    val resultSummaryId = Value("result_summary_id")
  }

  def getColumnsAsStrList( f: MsiDbProteinSetClusterTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[MsiDbProteinSetClusterTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: MsiDbProteinSetClusterTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[MsiDbProteinSetClusterTable.columns.type]( f )
  }

}

object MsiDbProteinSetClusterItemTable extends TableDefinition {

  val tableName = "protein_set_cluster_item"

  object columns extends Enumeration {

    val proteinSetClusterId = Value("protein_set_cluster_id")
    val proteinSetId = Value("protein_set_id")
    val resultSummaryId = Value("result_summary_id")
  }

  def getColumnsAsStrList( f: MsiDbProteinSetClusterItemTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[MsiDbProteinSetClusterItemTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: MsiDbProteinSetClusterItemTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[MsiDbProteinSetClusterItemTable.columns.type]( f )
  }

}

object MsiDbProteinSetObjectTreeMapTable extends TableDefinition {

  val tableName = "protein_set_object_tree_map"

  object columns extends Enumeration {

    val proteinSetId = Value("protein_set_id")
    val objectTreeId = Value("object_tree_id")
    val schemaName = Value("schema_name")
  }

  def getColumnsAsStrList( f: MsiDbProteinSetObjectTreeMapTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[MsiDbProteinSetObjectTreeMapTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: MsiDbProteinSetObjectTreeMapTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[MsiDbProteinSetObjectTreeMapTable.columns.type]( f )
  }

}

object MsiDbProteinSetProteinMatchItemTable extends TableDefinition {

  val tableName = "protein_set_protein_match_item"

  object columns extends Enumeration {

    val proteinSetId = Value("protein_set_id")
    val proteinMatchId = Value("protein_match_id")
    val serializedProperties = Value("serialized_properties")
    val resultSummaryId = Value("result_summary_id")
  }

  def getColumnsAsStrList( f: MsiDbProteinSetProteinMatchItemTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[MsiDbProteinSetProteinMatchItemTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: MsiDbProteinSetProteinMatchItemTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[MsiDbProteinSetProteinMatchItemTable.columns.type]( f )
  }

}

object MsiDbPtmSpecificityTable extends TableDefinition {

  val tableName = "ptm_specificity"

  object columns extends Enumeration {

    val id = Value("id")
    val location = Value("location")
    val residue = Value("residue")
    val serializedProperties = Value("serialized_properties")
  }

  def getColumnsAsStrList( f: MsiDbPtmSpecificityTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[MsiDbPtmSpecificityTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: MsiDbPtmSpecificityTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[MsiDbPtmSpecificityTable.columns.type]( f )
  }

}

object MsiDbResultSetTable extends TableDefinition {

  val tableName = "result_set"

  object columns extends Enumeration {

    val id = Value("id")
    val name = Value("name")
    val description = Value("description")
    val `type` = Value("type")
    val modificationTimestamp = Value("modification_timestamp")
    val serializedProperties = Value("serialized_properties")
    val decoyResultSetId = Value("decoy_result_set_id")
    val msiSearchId = Value("msi_search_id")
  }

  def getColumnsAsStrList( f: MsiDbResultSetTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[MsiDbResultSetTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: MsiDbResultSetTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[MsiDbResultSetTable.columns.type]( f )
  }

}

object MsiDbResultSetObjectTreeMapTable extends TableDefinition {

  val tableName = "result_set_object_tree_map"

  object columns extends Enumeration {

    val resultSetId = Value("result_set_id")
    val objectTreeId = Value("object_tree_id")
    val schemaName = Value("schema_name")
  }

  def getColumnsAsStrList( f: MsiDbResultSetObjectTreeMapTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[MsiDbResultSetObjectTreeMapTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: MsiDbResultSetObjectTreeMapTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[MsiDbResultSetObjectTreeMapTable.columns.type]( f )
  }

}

object MsiDbResultSetRelationTable extends TableDefinition {

  val tableName = "result_set_relation"

  object columns extends Enumeration {

    val parentResultSetId = Value("parent_result_set_id")
    val childResultSetId = Value("child_result_set_id")
  }

  def getColumnsAsStrList( f: MsiDbResultSetRelationTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[MsiDbResultSetRelationTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: MsiDbResultSetRelationTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[MsiDbResultSetRelationTable.columns.type]( f )
  }

}

object MsiDbResultSummaryTable extends TableDefinition {

  val tableName = "result_summary"

  object columns extends Enumeration {

    val id = Value("id")
    val description = Value("description")
    val modificationTimestamp = Value("modification_timestamp")
    val isQuantified = Value("is_quantified")
    val serializedProperties = Value("serialized_properties")
    val decoyResultSummaryId = Value("decoy_result_summary_id")
    val resultSetId = Value("result_set_id")
  }

  def getColumnsAsStrList( f: MsiDbResultSummaryTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[MsiDbResultSummaryTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: MsiDbResultSummaryTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[MsiDbResultSummaryTable.columns.type]( f )
  }

}

object MsiDbResultSummaryObjectTreeMapTable extends TableDefinition {

  val tableName = "result_summary_object_tree_map"

  object columns extends Enumeration {

    val resultSummaryId = Value("result_summary_id")
    val objectTreeId = Value("object_tree_id")
    val schemaName = Value("schema_name")
  }

  def getColumnsAsStrList( f: MsiDbResultSummaryObjectTreeMapTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[MsiDbResultSummaryObjectTreeMapTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: MsiDbResultSummaryObjectTreeMapTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[MsiDbResultSummaryObjectTreeMapTable.columns.type]( f )
  }

}

object MsiDbResultSummaryRelationTable extends TableDefinition {

  val tableName = "result_summary_relation"

  object columns extends Enumeration {

    val parentResultSummaryId = Value("parent_result_summary_id")
    val childResultSummaryId = Value("child_result_summary_id")
  }

  def getColumnsAsStrList( f: MsiDbResultSummaryRelationTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[MsiDbResultSummaryRelationTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: MsiDbResultSummaryRelationTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[MsiDbResultSummaryRelationTable.columns.type]( f )
  }

}

object MsiDbScoringTable extends TableDefinition {

  val tableName = "scoring"

  object columns extends Enumeration {

    val id = Value("id")
    val searchEngine = Value("search_engine")
    val name = Value("name")
    val description = Value("description")
    val serializedProperties = Value("serialized_properties")
  }

  def getColumnsAsStrList( f: MsiDbScoringTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[MsiDbScoringTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: MsiDbScoringTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[MsiDbScoringTable.columns.type]( f )
  }

}

object MsiDbSearchSettingsTable extends TableDefinition {

  val tableName = "search_settings"

  object columns extends Enumeration {

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

  def getColumnsAsStrList( f: MsiDbSearchSettingsTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[MsiDbSearchSettingsTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: MsiDbSearchSettingsTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[MsiDbSearchSettingsTable.columns.type]( f )
  }

}

object MsiDbSearchSettingsSeqDatabaseMapTable extends TableDefinition {

  val tableName = "search_settings_seq_database_map"

  object columns extends Enumeration {

    val searchSettingsId = Value("search_settings_id")
    val seqDatabaseId = Value("seq_database_id")
    val searchedSequencesCount = Value("searched_sequences_count")
    val serializedProperties = Value("serialized_properties")
  }

  def getColumnsAsStrList( f: MsiDbSearchSettingsSeqDatabaseMapTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[MsiDbSearchSettingsSeqDatabaseMapTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: MsiDbSearchSettingsSeqDatabaseMapTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[MsiDbSearchSettingsSeqDatabaseMapTable.columns.type]( f )
  }

}

object MsiDbSeqDatabaseTable extends TableDefinition {

  val tableName = "seq_database"

  object columns extends Enumeration {

    val id = Value("id")
    val name = Value("name")
    val fastaFilePath = Value("fasta_file_path")
    val version = Value("version")
    val releaseDate = Value("release_date")
    val sequenceCount = Value("sequence_count")
    val serializedProperties = Value("serialized_properties")
  }

  def getColumnsAsStrList( f: MsiDbSeqDatabaseTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[MsiDbSeqDatabaseTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: MsiDbSeqDatabaseTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[MsiDbSeqDatabaseTable.columns.type]( f )
  }

}

object MsiDbSequenceMatchTable extends TableDefinition {

  val tableName = "sequence_match"

  object columns extends Enumeration {

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

  def getColumnsAsStrList( f: MsiDbSequenceMatchTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[MsiDbSequenceMatchTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: MsiDbSequenceMatchTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[MsiDbSequenceMatchTable.columns.type]( f )
  }

}

object MsiDbSpectrumTable extends TableDefinition {

  val tableName = "spectrum"

  object columns extends Enumeration {

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

  def getColumnsAsStrList( f: MsiDbSpectrumTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[MsiDbSpectrumTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: MsiDbSpectrumTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[MsiDbSpectrumTable.columns.type]( f )
  }

}

object MsiDbUsedEnzymeTable extends TableDefinition {

  val tableName = "used_enzyme"

  object columns extends Enumeration {

    val searchSettingsId = Value("search_settings_id")
    val enzymeId = Value("enzyme_id")
  }

  def getColumnsAsStrList( f: MsiDbUsedEnzymeTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[MsiDbUsedEnzymeTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: MsiDbUsedEnzymeTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[MsiDbUsedEnzymeTable.columns.type]( f )
  }

}

object MsiDbUsedPtmTable extends TableDefinition {

  val tableName = "used_ptm"

  object columns extends Enumeration {

    val searchSettingsId = Value("search_settings_id")
    val ptmSpecificityId = Value("ptm_specificity_id")
    val shortName = Value("short_name")
    val isFixed = Value("is_fixed")
    val `type` = Value("type")
  }

  def getColumnsAsStrList( f: MsiDbUsedPtmTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[MsiDbUsedPtmTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: MsiDbUsedPtmTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[MsiDbUsedPtmTable.columns.type]( f )
  }

}


