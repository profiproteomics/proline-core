package fr.proline.core.dal.tables.ps

import fr.proline.core.dal.tables._

object PsDbAdminInfosColumns extends ColumnEnumeration {
  val $tableName = PsDbAdminInfosTable.name
  val MODEL_VERSION = Column("model_version")
  val DB_CREATION_DATE = Column("db_creation_date")
  val MODEL_UPDATE_DATE = Column("model_update_date")
}

abstract class PsDbAdminInfosTable extends TableDefinition[PsDbAdminInfosColumns.type]

object PsDbAdminInfosTable extends PsDbAdminInfosTable {
  val name = "admin_infos"
  val columns = PsDbAdminInfosColumns
}

object PsDbAtomLabelColumns extends ColumnEnumeration {
  val $tableName = PsDbAtomLabelTable.name
  val ID = Column("id")
  val NAME = Column("name")
  val SYMBOL = Column("symbol")
  val MONO_MASS = Column("mono_mass")
  val AVERAGE_MASS = Column("average_mass")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
}

abstract class PsDbAtomLabelTable extends TableDefinition[PsDbAtomLabelColumns.type]

object PsDbAtomLabelTable extends PsDbAtomLabelTable {
  val name = "atom_label"
  val columns = PsDbAtomLabelColumns
}

object PsDbPeptideColumns extends ColumnEnumeration {
  val $tableName = PsDbPeptideTable.name
  val ID = Column("id")
  val SEQUENCE = Column("sequence")
  val PTM_STRING = Column("ptm_string")
  val CALCULATED_MASS = Column("calculated_mass")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val ATOM_LABEL_ID = Column("atom_label_id")
}

abstract class PsDbPeptideTable extends TableDefinition[PsDbPeptideColumns.type]

object PsDbPeptideTable extends PsDbPeptideTable {
  val name = "peptide"
  val columns = PsDbPeptideColumns
}

object PsDbPeptidePtmColumns extends ColumnEnumeration {
  val $tableName = PsDbPeptidePtmTable.name
  val ID = Column("id")
  val SEQ_POSITION = Column("seq_position")
  val MONO_MASS = Column("mono_mass")
  val AVERAGE_MASS = Column("average_mass")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val PEPTIDE_ID = Column("peptide_id")
  val PTM_SPECIFICITY_ID = Column("ptm_specificity_id")
  val ATOM_LABEL_ID = Column("atom_label_id")
}

abstract class PsDbPeptidePtmTable extends TableDefinition[PsDbPeptidePtmColumns.type]

object PsDbPeptidePtmTable extends PsDbPeptidePtmTable {
  val name = "peptide_ptm"
  val columns = PsDbPeptidePtmColumns
}

object PsDbPeptidePtmInsertStatusColumns extends ColumnEnumeration {
  val $tableName = PsDbPeptidePtmInsertStatusTable.name
  val PEPTIDE_ID = Column("peptide_id")
  val IS_OK = Column("is_ok")
}

abstract class PsDbPeptidePtmInsertStatusTable extends TableDefinition[PsDbPeptidePtmInsertStatusColumns.type]

object PsDbPeptidePtmInsertStatusTable extends PsDbPeptidePtmInsertStatusTable {
  val name = "peptide_ptm_insert_status"
  val columns = PsDbPeptidePtmInsertStatusColumns
}

object PsDbPtmColumns extends ColumnEnumeration {
  val $tableName = PsDbPtmTable.name
  val ID = Column("id")
  val UNIMOD_ID = Column("unimod_id")
  val FULL_NAME = Column("full_name")
  val SHORT_NAME = Column("short_name")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
}

abstract class PsDbPtmTable extends TableDefinition[PsDbPtmColumns.type]

object PsDbPtmTable extends PsDbPtmTable {
  val name = "ptm"
  val columns = PsDbPtmColumns
}

object PsDbPtmClassificationColumns extends ColumnEnumeration {
  val $tableName = PsDbPtmClassificationTable.name
  val ID = Column("id")
  val NAME = Column("name")
}

abstract class PsDbPtmClassificationTable extends TableDefinition[PsDbPtmClassificationColumns.type]

object PsDbPtmClassificationTable extends PsDbPtmClassificationTable {
  val name = "ptm_classification"
  val columns = PsDbPtmClassificationColumns
}

object PsDbPtmEvidenceColumns extends ColumnEnumeration {
  val $tableName = PsDbPtmEvidenceTable.name
  val ID = Column("id")
  val TYPE = Column("type")
  val IS_REQUIRED = Column("is_required")
  val COMPOSITION = Column("composition")
  val MONO_MASS = Column("mono_mass")
  val AVERAGE_MASS = Column("average_mass")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val SPECIFICITY_ID = Column("specificity_id")
  val PTM_ID = Column("ptm_id")
}

abstract class PsDbPtmEvidenceTable extends TableDefinition[PsDbPtmEvidenceColumns.type]

object PsDbPtmEvidenceTable extends PsDbPtmEvidenceTable {
  val name = "ptm_evidence"
  val columns = PsDbPtmEvidenceColumns
}

object PsDbPtmSpecificityColumns extends ColumnEnumeration {
  val $tableName = PsDbPtmSpecificityTable.name
  val ID = Column("id")
  val LOCATION = Column("location")
  val RESIDUE = Column("residue")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val PTM_ID = Column("ptm_id")
  val CLASSIFICATION_ID = Column("classification_id")
}

abstract class PsDbPtmSpecificityTable extends TableDefinition[PsDbPtmSpecificityColumns.type]

object PsDbPtmSpecificityTable extends PsDbPtmSpecificityTable {
  val name = "ptm_specificity"
  val columns = PsDbPtmSpecificityColumns
}

