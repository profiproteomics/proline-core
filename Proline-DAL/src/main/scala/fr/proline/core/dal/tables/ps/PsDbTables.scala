package fr.proline.core.dal.tables.ps

import fr.proline.core.dal.tables._

object PsDbAdminInfosColumns extends ColumnEnumeration {
  val modelVersion = Value("model_version")
  val dbCreationDate = Value("db_creation_date")
  val modelUpdateDate = Value("model_update_date")
}

abstract class PsDbAdminInfosTable extends TableDefinition[PsDbAdminInfosColumns.type]

object PsDbAdminInfosTable extends PsDbAdminInfosTable {
  val tableName = "admin_infos"
  val columns = PsDbAdminInfosColumns
}

object PsDbAtomLabelColumns extends ColumnEnumeration {
  val id = Value("id")
  val name = Value("name")
  val symbol = Value("symbol")
  val monoMass = Value("mono_mass")
  val averageMass = Value("average_mass")
  val serializedProperties = Value("serialized_properties")
}

abstract class PsDbAtomLabelTable extends TableDefinition[PsDbAtomLabelColumns.type]

object PsDbAtomLabelTable extends PsDbAtomLabelTable {
  val tableName = "atom_label"
  val columns = PsDbAtomLabelColumns
}

object PsDbPeptideColumns extends ColumnEnumeration {
  val id = Value("id")
  val sequence = Value("sequence")
  val ptmString = Value("ptm_string")
  val calculatedMass = Value("calculated_mass")
  val serializedProperties = Value("serialized_properties")
  val atomLabelId = Value("atom_label_id")
}

abstract class PsDbPeptideTable extends TableDefinition[PsDbPeptideColumns.type]

object PsDbPeptideTable extends PsDbPeptideTable {
  val tableName = "peptide"
  val columns = PsDbPeptideColumns
}

object PsDbPeptidePtmColumns extends ColumnEnumeration {
  val id = Value("id")
  val seqPosition = Value("seq_position")
  val monoMass = Value("mono_mass")
  val averageMass = Value("average_mass")
  val serializedProperties = Value("serialized_properties")
  val peptideId = Value("peptide_id")
  val ptmSpecificityId = Value("ptm_specificity_id")
  val atomLabelId = Value("atom_label_id")
}

abstract class PsDbPeptidePtmTable extends TableDefinition[PsDbPeptidePtmColumns.type]

object PsDbPeptidePtmTable extends PsDbPeptidePtmTable {
  val tableName = "peptide_ptm"
  val columns = PsDbPeptidePtmColumns
}

object PsDbPeptidePtmInsertStatusColumns extends ColumnEnumeration {
  val id = Value("id")
  val isOk = Value("is_ok")
  val peptideId = Value("peptide_id")
}

abstract class PsDbPeptidePtmInsertStatusTable extends TableDefinition[PsDbPeptidePtmInsertStatusColumns.type]

object PsDbPeptidePtmInsertStatusTable extends PsDbPeptidePtmInsertStatusTable {
  val tableName = "peptide_ptm_insert_status"
  val columns = PsDbPeptidePtmInsertStatusColumns
}

object PsDbPtmColumns extends ColumnEnumeration {
  val id = Value("id")
  val unimodId = Value("unimod_id")
  val fullName = Value("full_name")
  val shortName = Value("short_name")
  val serializedProperties = Value("serialized_properties")
}

abstract class PsDbPtmTable extends TableDefinition[PsDbPtmColumns.type]

object PsDbPtmTable extends PsDbPtmTable {
  val tableName = "ptm"
  val columns = PsDbPtmColumns
}

object PsDbPtmClassificationColumns extends ColumnEnumeration {
  val id = Value("id")
  val name = Value("name")
}

abstract class PsDbPtmClassificationTable extends TableDefinition[PsDbPtmClassificationColumns.type]

object PsDbPtmClassificationTable extends PsDbPtmClassificationTable {
  val tableName = "ptm_classification"
  val columns = PsDbPtmClassificationColumns
}

object PsDbPtmEvidenceColumns extends ColumnEnumeration {
  val id = Value("id")
  val `type` = Value("type")
  val isRequired = Value("is_required")
  val composition = Value("composition")
  val monoMass = Value("mono_mass")
  val averageMass = Value("average_mass")
  val serializedProperties = Value("serialized_properties")
  val specificityId = Value("specificity_id")
  val ptmId = Value("ptm_id")
}

abstract class PsDbPtmEvidenceTable extends TableDefinition[PsDbPtmEvidenceColumns.type]

object PsDbPtmEvidenceTable extends PsDbPtmEvidenceTable {
  val tableName = "ptm_evidence"
  val columns = PsDbPtmEvidenceColumns
}

object PsDbPtmSpecificityColumns extends ColumnEnumeration {
  val id = Value("id")
  val location = Value("location")
  val residue = Value("residue")
  val serializedProperties = Value("serialized_properties")
  val ptmId = Value("ptm_id")
  val classificationId = Value("classification_id")
}

abstract class PsDbPtmSpecificityTable extends TableDefinition[PsDbPtmSpecificityColumns.type]

object PsDbPtmSpecificityTable extends PsDbPtmSpecificityTable {
  val tableName = "ptm_specificity"
  val columns = PsDbPtmSpecificityColumns
}

