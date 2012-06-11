package fr.proline.core.dal

import fr.proline.core.utils.sql.TableDefinition

object PsDbAdminInfosTable extends TableDefinition {

  val tableName = "admin_infos"

  object columns extends Enumeration {

    val modelVersion = Value("model_version")
    val dbCreationDate = Value("db_creation_date")
    val modelUpdateDate = Value("model_update_date")
  }

  def getColumnsAsStrList( f: PsDbAdminInfosTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[PsDbAdminInfosTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: PsDbAdminInfosTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[PsDbAdminInfosTable.columns.type]( f )
  }

}

object PsDbAtomLabelTable extends TableDefinition {

  val tableName = "atom_label"

  object columns extends Enumeration {

    val id = Value("id")
    val name = Value("name")
    val symbol = Value("symbol")
    val monoMass = Value("mono_mass")
    val averageMass = Value("average_mass")
    val serializedProperties = Value("serialized_properties")
  }

  def getColumnsAsStrList( f: PsDbAtomLabelTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[PsDbAtomLabelTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: PsDbAtomLabelTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[PsDbAtomLabelTable.columns.type]( f )
  }

}

object PsDbPeptideTable extends TableDefinition {

  val tableName = "peptide"

  object columns extends Enumeration {

    val id = Value("id")
    val sequence = Value("sequence")
    val ptmString = Value("ptm_string")
    val calculatedMass = Value("calculated_mass")
    val serializedProperties = Value("serialized_properties")
    val atomLabelId = Value("atom_label_id")
  }

  def getColumnsAsStrList( f: PsDbPeptideTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[PsDbPeptideTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: PsDbPeptideTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[PsDbPeptideTable.columns.type]( f )
  }

}

object PsDbPeptidePtmTable extends TableDefinition {

  val tableName = "peptide_ptm"

  object columns extends Enumeration {

    val id = Value("id")
    val seqPosition = Value("seq_position")
    val monoMass = Value("mono_mass")
    val averageMass = Value("average_mass")
    val serializedProperties = Value("serialized_properties")
    val peptideId = Value("peptide_id")
    val ptmSpecificityId = Value("ptm_specificity_id")
    val atomLabelId = Value("atom_label_id")
  }

  def getColumnsAsStrList( f: PsDbPeptidePtmTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[PsDbPeptidePtmTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: PsDbPeptidePtmTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[PsDbPeptidePtmTable.columns.type]( f )
  }

}

object PsDbPeptidePtmInsertStatusTable extends TableDefinition {

  val tableName = "peptide_ptm_insert_status"

  object columns extends Enumeration {

    val id = Value("id")
    val isOk = Value("is_ok")
    val peptideId = Value("peptide_id")
  }

  def getColumnsAsStrList( f: PsDbPeptidePtmInsertStatusTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[PsDbPeptidePtmInsertStatusTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: PsDbPeptidePtmInsertStatusTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[PsDbPeptidePtmInsertStatusTable.columns.type]( f )
  }

}

object PsDbPtmTable extends TableDefinition {

  val tableName = "ptm"

  object columns extends Enumeration {

    val id = Value("id")
    val unimodId = Value("unimod_id")
    val fullName = Value("full_name")
    val shortName = Value("short_name")
    val serializedProperties = Value("serialized_properties")
  }

  def getColumnsAsStrList( f: PsDbPtmTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[PsDbPtmTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: PsDbPtmTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[PsDbPtmTable.columns.type]( f )
  }

}

object PsDbPtmClassificationTable extends TableDefinition {

  val tableName = "ptm_classification"

  object columns extends Enumeration {

    val id = Value("id")
    val name = Value("name")
  }

  def getColumnsAsStrList( f: PsDbPtmClassificationTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[PsDbPtmClassificationTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: PsDbPtmClassificationTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[PsDbPtmClassificationTable.columns.type]( f )
  }

}

object PsDbPtmEvidenceTable extends TableDefinition {

  val tableName = "ptm_evidence"

  object columns extends Enumeration {

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

  def getColumnsAsStrList( f: PsDbPtmEvidenceTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[PsDbPtmEvidenceTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: PsDbPtmEvidenceTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[PsDbPtmEvidenceTable.columns.type]( f )
  }

}

object PsDbPtmSpecificityTable extends TableDefinition {

  val tableName = "ptm_specificity"

  object columns extends Enumeration {

    val id = Value("id")
    val location = Value("location")
    val residue = Value("residue")
    val serializedProperties = Value("serialized_properties")
    val ptmId = Value("ptm_id")
    val classificationId = Value("classification_id")
  }

  def getColumnsAsStrList( f: PsDbPtmSpecificityTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[PsDbPtmSpecificityTable.columns.type]( f )
  }
  
  def makeInsertQuery( f: PsDbPtmSpecificityTable.columns.type => List[Enumeration#Value] ): String = {
    this._makeInsertQuery[PsDbPtmSpecificityTable.columns.type]( f )
  }

}

