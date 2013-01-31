package fr.proline.core.om.provider.msi.impl

import scala.collection.mutable.ArrayBuffer
import com.codahale.jerkson.Json.parse
import fr.profi.jdbc.SQLQueryExecution
import fr.proline.core.dal.tables.msi.{ MsiDbPeptideInstanceTable, MsiDbPeptideInstancePeptideMatchMapTable }
import fr.proline.core.om.model.msi.PeptideInstance
import fr.proline.core.om.model.msi.Peptide
import fr.proline.core.om.model.msi.PeptideMatchValidationProperties
import fr.proline.core.om.provider.msi.IPeptideInstanceProvider
import fr.proline.core.om.provider.msi.IPeptideProvider
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.SQLConnectionContext

class SQLPeptideInstanceProvider(val msiDbCtx: SQLConnectionContext,
  val psDbCtx: SQLConnectionContext = null,
  var peptideProvider: Option[IPeptideProvider] = None) extends IPeptideInstanceProvider {

  val PepInstCols = MsiDbPeptideInstanceTable.columns
  val PepMatchMappingCols = MsiDbPeptideInstancePeptideMatchMapTable.columns

  private def _getPeptideProvider(): IPeptideProvider = {
    if (this.peptideProvider != None) this.peptideProvider.get
    else new SQLPeptideProvider(psDbCtx)
  }

  def getPeptideInstancesAsOptions(pepInstIds: Seq[Int]): Array[Option[PeptideInstance]] = {

    val pepInsts = this.getPeptideInstances(pepInstIds)
    val pepInstById = pepInsts.map { p => p.id -> p } toMap

    pepInstIds.map { pepInstById.get(_) } toArray

  }

  def getPeptideInstances(pepInstIds: Seq[Int]): Array[PeptideInstance] = {
    val pepInstRecords = this._getPepInstRecords(pepInstIds)
    val pepInstPepMatchMapRecords = this._getPepInstPepMatchMapRecords(pepInstIds)
    this._buildPeptideInstances(pepInstRecords, pepInstPepMatchMapRecords)
  }

  def getResultSummariesPeptideInstances(rsmIds: Seq[Int]): Array[PeptideInstance] = {
    val pepInstRecords = this._getRSMsPepInstRecords(rsmIds)
    val pepInstPepMatchMapRecords = this._getRSMsPepInstPepMatchMapRecords(rsmIds)
    this._buildPeptideInstances(pepInstRecords, pepInstPepMatchMapRecords) // TODO LMN Use a real SQL Db Context here
  }

  private def _getRSMsPepInstRecords(rsmIds: Seq[Int]): Array[Map[String, Any]] = {
    msiDbCtx.ezDBC.selectAllRecordsAsMaps("SELECT * FROM peptide_instance WHERE result_summary_id IN (" + rsmIds.mkString(",") + ")")
  }

  private def _getPepInstRecords(pepInstIds: Seq[Int]): Array[Map[String, Any]] = {
    // TODO: use max nb iterations
    msiDbCtx.ezDBC.selectAllRecordsAsMaps("SELECT * FROM peptide_instance WHERE id IN (" + pepInstIds.mkString(",") + ")")
  }

  private def _getRSMsPepInstPepMatchMapRecords(rsmIds: Seq[Int]): Array[Map[String, Any]] = {
    msiDbCtx.ezDBC.selectAllRecordsAsMaps("SELECT * FROM peptide_instance_peptide_match_map WHERE result_summary_id IN (" + rsmIds.mkString(",") + ")")
  }

  private def _getPepInstPepMatchMapRecords(pepInstIds: Seq[Int]): Array[Map[String, Any]] = {
    // TODO: use max nb iterations
    msiDbCtx.ezDBC.selectAllRecordsAsMaps("SELECT * FROM peptide_instance_peptide_match_map WHERE peptide_instance_id IN (" + pepInstIds.mkString(",") + ")")
  }

  private def _buildPeptideInstances(pepInstRecords: Seq[Map[String, Any]],
    pepInstPepMatchMapRecords: Seq[Map[String, Any]]): Array[PeptideInstance] = {

    import fr.proline.util.primitives.LongOrIntAsInt._
    import fr.proline.util.primitives.DoubleOrFloatAsFloat._

    // Load peptides
    val uniqPepIds = pepInstRecords map { _(PepInstCols.PEPTIDE_ID).asInstanceOf[Int] } distinct
    val peptides = this._getPeptideProvider().getPeptides(uniqPepIds)

    // Map peptides by their id
    val peptideById = Map() ++ peptides.map { pep => (pep.id -> pep) }

    // Group peptide matches mapping by peptide instance id
    val pepMatchesMappingsByPepInstId = pepInstPepMatchMapRecords.groupBy {
      _(PepMatchMappingCols.PEPTIDE_INSTANCE_ID).asInstanceOf[Int]
    }

    // Build peptide instances
    val pepInsts = new Array[PeptideInstance](pepInstRecords.length)

    for (pepInstIdx <- 0 until pepInstRecords.length) {

      // Retrieve peptide instance record
      val pepInstRecord = pepInstRecords(pepInstIdx)

      // Retrieve the corresponding peptide
      val pepId: Int = pepInstRecord(PepInstCols.PEPTIDE_ID).asInstanceOf[AnyVal]
      if (!peptideById.contains(pepId)) throw new Exception("undefined peptide with id ='" + pepId + "'")
      val peptide = peptideById(pepId)

      // Retrieve peptide match ids and properties
      val pepInstId: Int = pepInstRecord(PepInstCols.ID).asInstanceOf[AnyVal]
      val pepMatchIds = new ArrayBuffer[Int]()
      val pepMatchPropertyMapBuilder = Map.newBuilder[Int, PeptideMatchValidationProperties]

      pepMatchesMappingsByPepInstId(pepInstId).foreach { pepMatchMapping =>
        val pepMatchId = pepMatchMapping(PepMatchMappingCols.PEPTIDE_MATCH_ID).asInstanceOf[Int]
        pepMatchIds += pepMatchId

        val propertiesAsJSON = pepMatchMapping(PepMatchMappingCols.SERIALIZED_PROPERTIES).asInstanceOf[String]
        if (propertiesAsJSON != null) {
          pepMatchPropertyMapBuilder += pepMatchId -> parse[PeptideMatchValidationProperties](propertiesAsJSON)
        }
      }

      // Decode JSON properties
      /*val propertiesAsJSON = pepInstRecord(PepInstCols.serializedProperties).asInstanceOf[String]
      var properties = Option.empty[PeptideMatchProperties]
      if( propertiesAsJSON != null ) {
        properties = Some( parse[PeptideMatchProperties](propertiesAsJSON) )
      }*/

      // TODO: load properties
      val pepInst = new PeptideInstance(id = pepInstId,
        peptide = peptide,
        proteinMatchesCount = pepInstRecord(PepInstCols.PROTEIN_MATCH_COUNT).asInstanceOf[Int],
        proteinSetsCount = pepInstRecord(PepInstCols.PROTEIN_SET_COUNT).asInstanceOf[Int],
        selectionLevel = pepInstRecord(PepInstCols.SELECTION_LEVEL).asInstanceOf[Int],
        elutionTime = pepInstRecord(PepInstCols.ELUTION_TIME).asInstanceOf[AnyVal],
        peptideMatchIds = pepMatchIds.toArray,
        bestPeptideMatchId = pepInstRecord(PepInstCols.BEST_PEPTIDE_MATCH_ID).asInstanceOf[Int],
        unmodifiedPeptideId = pepInstRecord(PepInstCols.UNMODIFIED_PEPTIDE_ID).asInstanceOf[Int],
        resultSummaryId = pepInstRecord(PepInstCols.RESULT_SUMMARY_ID).asInstanceOf[Int],
        peptideMatchPropertiesById = pepMatchPropertyMapBuilder.result())

      pepInsts(pepInstIdx) = pepInst

    }

    pepInsts

  }

}