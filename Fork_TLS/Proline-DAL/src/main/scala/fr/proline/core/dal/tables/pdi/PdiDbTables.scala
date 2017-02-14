package fr.proline.core.dal.tables.pdi

import fr.proline.core.dal.tables._

object PdiDbAdminInfosColumns extends ColumnEnumeration {
  val $tableName = PdiDbAdminInfosTable.name
  val MODEL_VERSION = Column("model_version")
  val DB_CREATION_DATE = Column("db_creation_date")
  val MODEL_UPDATE_DATE = Column("model_update_date")
  val CHR_LOCATION_UPDATE_TIMESTAMP = Column("chr_location_update_timestamp")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
}

abstract class PdiDbAdminInfosTable extends TableDefinition[PdiDbAdminInfosColumns.type]

object PdiDbAdminInfosTable extends PdiDbAdminInfosTable {
  val name = "admin_infos"
  val columns = PdiDbAdminInfosColumns
}

object PdiDbBioSequenceColumns extends ColumnEnumeration {
  val $tableName = PdiDbBioSequenceTable.name
  val ID = Column("id")
  val ALPHABET = Column("alphabet")
  val SEQUENCE = Column("sequence")
  val LENGTH = Column("length")
  val MASS = Column("mass")
  val PI = Column("pi")
  val CRC64 = Column("crc64")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
}

abstract class PdiDbBioSequenceTable extends TableDefinition[PdiDbBioSequenceColumns.type]

object PdiDbBioSequenceTable extends PdiDbBioSequenceTable {
  val name = "bio_sequence"
  val columns = PdiDbBioSequenceColumns
}

object PdiDbBioSequenceAnnotationColumns extends ColumnEnumeration {
  val $tableName = PdiDbBioSequenceAnnotationTable.name
  val ID = Column("id")
  val VERSION = Column("version")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val BIO_SEQUENCE_ID = Column("bio_sequence_id")
  val TAXON_ID = Column("taxon_id")
  val OBJECT_TREE_ID = Column("object_tree_id")
  val SCHEMA_NAME = Column("schema_name")
}

abstract class PdiDbBioSequenceAnnotationTable extends TableDefinition[PdiDbBioSequenceAnnotationColumns.type]

object PdiDbBioSequenceAnnotationTable extends PdiDbBioSequenceAnnotationTable {
  val name = "bio_sequence_annotation"
  val columns = PdiDbBioSequenceAnnotationColumns
}

object PdiDbBioSequenceGeneMapColumns extends ColumnEnumeration {
  val $tableName = PdiDbBioSequenceGeneMapTable.name
  val BIO_SEQUENCE_ID = Column("bio_sequence_id")
  val GENE_ID = Column("gene_id")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val TAXON_ID = Column("taxon_id")
}

abstract class PdiDbBioSequenceGeneMapTable extends TableDefinition[PdiDbBioSequenceGeneMapColumns.type]

object PdiDbBioSequenceGeneMapTable extends PdiDbBioSequenceGeneMapTable {
  val name = "bio_sequence_gene_map"
  val columns = PdiDbBioSequenceGeneMapColumns
}

object PdiDbBioSequenceRelationColumns extends ColumnEnumeration {
  val $tableName = PdiDbBioSequenceRelationTable.name
  val NA_SEQUENCE_ID = Column("na_sequence_id")
  val AA_SEQUENCE_ID = Column("aa_sequence_id")
  val FRAME_NUMBER = Column("frame_number")
}

abstract class PdiDbBioSequenceRelationTable extends TableDefinition[PdiDbBioSequenceRelationColumns.type]

object PdiDbBioSequenceRelationTable extends PdiDbBioSequenceRelationTable {
  val name = "bio_sequence_relation"
  val columns = PdiDbBioSequenceRelationColumns
}

object PdiDbChromosomeLocationColumns extends ColumnEnumeration {
  val $tableName = PdiDbChromosomeLocationTable.name
  val ID = Column("id")
  val CHROMOSOME_IDENTIFIER = Column("chromosome_identifier")
  val LOCATION = Column("location")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val GENE_ID = Column("gene_id")
  val TAXON_ID = Column("taxon_id")
}

abstract class PdiDbChromosomeLocationTable extends TableDefinition[PdiDbChromosomeLocationColumns.type]

object PdiDbChromosomeLocationTable extends PdiDbChromosomeLocationTable {
  val name = "chromosome_location"
  val columns = PdiDbChromosomeLocationColumns
}

object PdiDbFastaFileEntryIndexColumns extends ColumnEnumeration {
  val $tableName = PdiDbFastaFileEntryIndexTable.name
  val ID = Column("id")
  val BLOCK_START = Column("block_start")
  val BLOCK_LENGTH = Column("block_length")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val BIO_SEQUENCE_ID = Column("bio_sequence_id")
  val SEQ_DB_ENTRY_ID = Column("seq_db_entry_id")
  val SEQ_DB_INSTANCE_ID = Column("seq_db_instance_id")
}

abstract class PdiDbFastaFileEntryIndexTable extends TableDefinition[PdiDbFastaFileEntryIndexColumns.type]

object PdiDbFastaFileEntryIndexTable extends PdiDbFastaFileEntryIndexTable {
  val name = "fasta_file_entry_index"
  val columns = PdiDbFastaFileEntryIndexColumns
}

object PdiDbFastaParsingRuleColumns extends ColumnEnumeration {
  val $tableName = PdiDbFastaParsingRuleTable.name
  val ID = Column("id")
  val DB_TYPE = Column("db_type")
  val ENTRY_ID = Column("entry_id")
  val ENTRY_AC = Column("entry_ac")
  val ENTRY_NAME = Column("entry_name")
  val GENE_NAME = Column("gene_name")
  val ORGANISM_NAME = Column("organism_name")
  val TAXON_ID = Column("taxon_id")
}

abstract class PdiDbFastaParsingRuleTable extends TableDefinition[PdiDbFastaParsingRuleColumns.type]

object PdiDbFastaParsingRuleTable extends PdiDbFastaParsingRuleTable {
  val name = "fasta_parsing_rule"
  val columns = PdiDbFastaParsingRuleColumns
}

object PdiDbGeneColumns extends ColumnEnumeration {
  val $tableName = PdiDbGeneTable.name
  val ID = Column("id")
  val NAME = Column("name")
  val NAME_TYPE = Column("name_type")
  val SYNONYMS = Column("synonyms")
  val IS_ACTIVE = Column("is_active")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val TAXON_ID = Column("taxon_id")
}

abstract class PdiDbGeneTable extends TableDefinition[PdiDbGeneColumns.type]

object PdiDbGeneTable extends PdiDbGeneTable {
  val name = "gene"
  val columns = PdiDbGeneColumns
}

object PdiDbObjectTreeColumns extends ColumnEnumeration {
  val $tableName = PdiDbObjectTreeTable.name
  val ID = Column("id")
  val BLOB_DATA = Column("blob_data")
  val CLOB_DATA = Column("clob_data")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val SCHEMA_NAME = Column("schema_name")
}

abstract class PdiDbObjectTreeTable extends TableDefinition[PdiDbObjectTreeColumns.type]

object PdiDbObjectTreeTable extends PdiDbObjectTreeTable {
  val name = "object_tree"
  val columns = PdiDbObjectTreeColumns
}

object PdiDbObjectTreeSchemaColumns extends ColumnEnumeration {
  val $tableName = PdiDbObjectTreeSchemaTable.name
  val NAME = Column("name")
  val TYPE = Column("type")
  val IS_BINARY_MODE = Column("is_binary_mode")
  val VERSION = Column("version")
  val SCHEMA = Column("schema")
  val DESCRIPTION = Column("description")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
}

abstract class PdiDbObjectTreeSchemaTable extends TableDefinition[PdiDbObjectTreeSchemaColumns.type]

object PdiDbObjectTreeSchemaTable extends PdiDbObjectTreeSchemaTable {
  val name = "object_tree_schema"
  val columns = PdiDbObjectTreeSchemaColumns
}

object PdiDbProteinIdentifierColumns extends ColumnEnumeration {
  val $tableName = PdiDbProteinIdentifierTable.name
  val ID = Column("id")
  val VALUE = Column("value")
  val IS_AC_NUMBER = Column("is_ac_number")
  val IS_ACTIVE = Column("is_active")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val BIO_SEQUENCE_ID = Column("bio_sequence_id")
  val TAXON_ID = Column("taxon_id")
  val SEQ_DB_CONFIG_ID = Column("seq_db_config_id")
}

abstract class PdiDbProteinIdentifierTable extends TableDefinition[PdiDbProteinIdentifierColumns.type]

object PdiDbProteinIdentifierTable extends PdiDbProteinIdentifierTable {
  val name = "protein_identifier"
  val columns = PdiDbProteinIdentifierColumns
}

object PdiDbSeqDbConfigColumns extends ColumnEnumeration {
  val $tableName = PdiDbSeqDbConfigTable.name
  val ID = Column("id")
  val NAME = Column("name")
  val ALPHABET = Column("alphabet")
  val REF_ENTRY_FORMAT = Column("ref_entry_format")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val FASTA_PARSING_RULE_ID = Column("fasta_parsing_rule_id")
  val IS_NATIVE = Column("is_native")
}

abstract class PdiDbSeqDbConfigTable extends TableDefinition[PdiDbSeqDbConfigColumns.type]

object PdiDbSeqDbConfigTable extends PdiDbSeqDbConfigTable {
  val name = "seq_db_config"
  val columns = PdiDbSeqDbConfigColumns
}

object PdiDbSeqDbEntryColumns extends ColumnEnumeration {
  val $tableName = PdiDbSeqDbEntryTable.name
  val ID = Column("id")
  val IDENTIFIER = Column("identifier")
  val NAME = Column("name")
  val VERSION = Column("version")
  val REF_FILE_BLOCK_START = Column("ref_file_block_start")
  val REF_FILE_BLOCK_LENGTH = Column("ref_file_block_length")
  val IS_ACTIVE = Column("is_active")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val BIO_SEQUENCE_ID = Column("bio_sequence_id")
  val TAXON_ID = Column("taxon_id")
  val SEQ_DB_INSTANCE_ID = Column("seq_db_instance_id")
  val SEQ_DB_CONFIG_ID = Column("seq_db_config_id")
}

abstract class PdiDbSeqDbEntryTable extends TableDefinition[PdiDbSeqDbEntryColumns.type]

object PdiDbSeqDbEntryTable extends PdiDbSeqDbEntryTable {
  val name = "seq_db_entry"
  val columns = PdiDbSeqDbEntryColumns
}

object PdiDbSeqDbEntryGeneMapColumns extends ColumnEnumeration {
  val $tableName = PdiDbSeqDbEntryGeneMapTable.name
  val SEQ_DB_ENTRY_ID = Column("seq_db_entry_id")
  val GENE_ID = Column("gene_id")
  val SEQ_DB_INSTANCE_ID = Column("seq_db_instance_id")
}

abstract class PdiDbSeqDbEntryGeneMapTable extends TableDefinition[PdiDbSeqDbEntryGeneMapColumns.type]

object PdiDbSeqDbEntryGeneMapTable extends PdiDbSeqDbEntryGeneMapTable {
  val name = "seq_db_entry_gene_map"
  val columns = PdiDbSeqDbEntryGeneMapColumns
}

object PdiDbSeqDbEntryObjectTreeMapColumns extends ColumnEnumeration {
  val $tableName = PdiDbSeqDbEntryObjectTreeMapTable.name
  val SEQ_DB_ENTRY_ID = Column("seq_db_entry_id")
  val SCHEMA_NAME = Column("schema_name")
  val OBJECT_TREE_ID = Column("object_tree_id")
}

abstract class PdiDbSeqDbEntryObjectTreeMapTable extends TableDefinition[PdiDbSeqDbEntryObjectTreeMapColumns.type]

object PdiDbSeqDbEntryObjectTreeMapTable extends PdiDbSeqDbEntryObjectTreeMapTable {
  val name = "seq_db_entry_object_tree_map"
  val columns = PdiDbSeqDbEntryObjectTreeMapColumns
}

object PdiDbSeqDbEntryProteinIdentifierMapColumns extends ColumnEnumeration {
  val $tableName = PdiDbSeqDbEntryProteinIdentifierMapTable.name
  val SEQ_DB_ENTRY_ID = Column("seq_db_entry_id")
  val PROTEIN_IDENTIFIER_ID = Column("protein_identifier_id")
  val SEQ_DB_INSTANCE_ID = Column("seq_db_instance_id")
}

abstract class PdiDbSeqDbEntryProteinIdentifierMapTable extends TableDefinition[PdiDbSeqDbEntryProteinIdentifierMapColumns.type]

object PdiDbSeqDbEntryProteinIdentifierMapTable extends PdiDbSeqDbEntryProteinIdentifierMapTable {
  val name = "seq_db_entry_protein_identifier_map"
  val columns = PdiDbSeqDbEntryProteinIdentifierMapColumns
}

object PdiDbSeqDbInstanceColumns extends ColumnEnumeration {
  val $tableName = PdiDbSeqDbInstanceTable.name
  val ID = Column("id")
  val FASTA_FILE_PATH = Column("fasta_file_path")
  val REF_FILE_PATH = Column("ref_file_path")
  val IS_INDEXED = Column("is_indexed")
  val IS_DELETED = Column("is_deleted")
  val REVISION = Column("revision")
  val CREATION_TIMESTAMP = Column("creation_timestamp")
  val SEQUENCE_COUNT = Column("sequence_count")
  val RESIDUE_COUNT = Column("residue_count")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val SEQ_DB_RELEASE_ID = Column("seq_db_release_id")
  val SEQ_DB_CONFIG_ID = Column("seq_db_config_id")
}

abstract class PdiDbSeqDbInstanceTable extends TableDefinition[PdiDbSeqDbInstanceColumns.type]

object PdiDbSeqDbInstanceTable extends PdiDbSeqDbInstanceTable {
  val name = "seq_db_instance"
  val columns = PdiDbSeqDbInstanceColumns
}

object PdiDbSeqDbReleaseColumns extends ColumnEnumeration {
  val $tableName = PdiDbSeqDbReleaseTable.name
  val ID = Column("id")
  val DATE = Column("date")
  val VERSION = Column("version")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
}

abstract class PdiDbSeqDbReleaseTable extends TableDefinition[PdiDbSeqDbReleaseColumns.type]

object PdiDbSeqDbReleaseTable extends PdiDbSeqDbReleaseTable {
  val name = "seq_db_release"
  val columns = PdiDbSeqDbReleaseColumns
}

object PdiDbTaxonColumns extends ColumnEnumeration {
  val $tableName = PdiDbTaxonTable.name
  val ID = Column("id")
  val SCIENTIFIC_NAME = Column("scientific_name")
  val RANK = Column("rank")
  val IS_ACTIVE = Column("is_active")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val PARENT_TAXON_ID = Column("parent_taxon_id")
}

abstract class PdiDbTaxonTable extends TableDefinition[PdiDbTaxonColumns.type]

object PdiDbTaxonTable extends PdiDbTaxonTable {
  val name = "taxon"
  val columns = PdiDbTaxonColumns
}

object PdiDbTaxonExtraNameColumns extends ColumnEnumeration {
  val $tableName = PdiDbTaxonExtraNameTable.name
  val ID = Column("id")
  val CLASS = Column("class")
  val VALUE = Column("value")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val TAXON_ID = Column("taxon_id")
}

abstract class PdiDbTaxonExtraNameTable extends TableDefinition[PdiDbTaxonExtraNameColumns.type]

object PdiDbTaxonExtraNameTable extends PdiDbTaxonExtraNameTable {
  val name = "taxon_extra_name"
  val columns = PdiDbTaxonExtraNameColumns
}

object PdiDb {
  val tables = Array(
    PdiDbAdminInfosTable,
    PdiDbBioSequenceTable,
    PdiDbBioSequenceAnnotationTable,
    PdiDbBioSequenceGeneMapTable,
    PdiDbBioSequenceRelationTable,
    PdiDbChromosomeLocationTable,
    PdiDbFastaFileEntryIndexTable,
    PdiDbFastaParsingRuleTable,
    PdiDbGeneTable,
    PdiDbObjectTreeTable,
    PdiDbObjectTreeSchemaTable,
    PdiDbProteinIdentifierTable,
    PdiDbSeqDbConfigTable,
    PdiDbSeqDbEntryTable,
    PdiDbSeqDbEntryGeneMapTable,
    PdiDbSeqDbEntryObjectTreeMapTable,
    PdiDbSeqDbEntryProteinIdentifierMapTable,
    PdiDbSeqDbInstanceTable,
    PdiDbSeqDbReleaseTable,
    PdiDbTaxonTable,
    PdiDbTaxonExtraNameTable
  )
}


