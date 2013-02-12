package fr.proline.core.om.provider.msi.impl

import scala.collection.mutable.ArrayBuffer
import com.codahale.jerkson.Json.parse

import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.TableDefinition
import fr.proline.core.dal.tables.msi.{ MsiDbPeptideInstanceTable, MsiDbPeptideInstancePeptideMatchMapTable }
import fr.proline.core.om.model.msi.PeptideInstance
import fr.proline.core.om.model.msi.PeptideInstanceProperties
import fr.proline.core.om.model.msi.Peptide
import fr.proline.core.om.model.msi.PeptideMatchResultSummaryProperties
import fr.proline.core.om.provider.msi.IPeptideInstanceProvider
import fr.proline.core.om.provider.msi.IPeptideProvider
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.SQLConnectionContext

class SQLPeptideInstanceProvider(
  val msiSqlCtx: SQLConnectionContext,
  val psSqlCtx: SQLConnectionContext = null,
  var peptideProvider: Option[IPeptideProvider] = None
) extends IPeptideInstanceProvider {

  val PepInstCols = MsiDbPeptideInstanceTable.columns
  val PepMatchMappingCols = MsiDbPeptideInstancePeptideMatchMapTable.columns

  private def _getPeptideProvider(): IPeptideProvider = {
    this.peptideProvider.getOrElse(new SQLPeptideProvider(psSqlCtx))
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
    this._buildPeptideInstances(pepInstRecords, pepInstPepMatchMapRecords)
  }

  private def _getRSMsPepInstRecords(rsmIds: Seq[Int]): Array[Map[String, Any]] = {
    val sqlQuery = new SelectQueryBuilder1(MsiDbPeptideInstanceTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.RESULT_SUMMARY_ID ~" IN("~ rsmIds.mkString(",") ~")"
    )
    msiSqlCtx.ezDBC.selectAllRecordsAsMaps(sqlQuery)
  }

  private def _getPepInstRecords(pepInstIds: Seq[Int]): Array[Map[String, Any]] = {
    // TODO: use max nb iterations
    val sqlQuery = new SelectQueryBuilder1(MsiDbPeptideInstanceTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.ID ~" IN("~ pepInstIds.mkString(",") ~")"
    )
    msiSqlCtx.ezDBC.selectAllRecordsAsMaps(sqlQuery)
  }

  private def _getRSMsPepInstPepMatchMapRecords(rsmIds: Seq[Int]): Array[Map[String, Any]] = {
    val sqlQuery = new SelectQueryBuilder1(MsiDbPeptideInstancePeptideMatchMapTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.RESULT_SUMMARY_ID ~" IN("~ rsmIds.mkString(",") ~")"
    )
    msiSqlCtx.ezDBC.selectAllRecordsAsMaps(sqlQuery)
  }

  private def _getPepInstPepMatchMapRecords(pepInstIds: Seq[Int]): Array[Map[String, Any]] = {
    // TODO: use max nb iterations
    val sqlQuery = new SelectQueryBuilder1(MsiDbPeptideInstancePeptideMatchMapTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.PEPTIDE_INSTANCE_ID ~" IN("~ pepInstIds.mkString(",") ~")"
    )
    msiSqlCtx.ezDBC.selectAllRecordsAsMaps(sqlQuery)
  }

  private def _buildPeptideInstances(
    pepInstRecords: Seq[Map[String, Any]],
    pepInstPepMatchMapRecords: Seq[Map[String, Any]]
  ): Array[PeptideInstance] = {

    import fr.proline.util.primitives.LongOrIntAsInt._
    import fr.proline.util.primitives.DoubleOrFloatAsFloat._

    // Load peptides
    val uniqPepIds = pepInstRecords.map { _(PepInstCols.PEPTIDE_ID).asInstanceOf[Int] } distinct
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
      assert(peptideById.contains(pepId), "undefined peptide with id ='" + pepId + "'")
      val peptide = peptideById(pepId)

      // Retrieve peptide match ids and properties
      val pepInstId: Int = pepInstRecord(PepInstCols.ID).asInstanceOf[AnyVal]
      val pepMatchIds = new ArrayBuffer[Int]()
      val pepMatchPropertyMapBuilder = Map.newBuilder[Int, PeptideMatchResultSummaryProperties]

      pepMatchesMappingsByPepInstId(pepInstId).foreach { pepMatchMapping =>
        val pepMatchId = pepMatchMapping(PepMatchMappingCols.PEPTIDE_MATCH_ID).asInstanceOf[Int]
        pepMatchIds += pepMatchId

        val propertiesAsJSON = pepMatchMapping(PepMatchMappingCols.SERIALIZED_PROPERTIES).asInstanceOf[String]
        if (propertiesAsJSON != null) {
          pepMatchPropertyMapBuilder += pepMatchId -> parse[PeptideMatchResultSummaryProperties](propertiesAsJSON)
        }
      }

      // Decode JSON properties
      val propertiesAsJSON = pepInstRecord(PepInstCols.SERIALIZED_PROPERTIES).asInstanceOf[String]

      // TODO: load properties
      val pepInst = new PeptideInstance(
        id = pepInstId,
        peptide = peptide,
        proteinMatchesCount = pepInstRecord(PepInstCols.PROTEIN_MATCH_COUNT).asInstanceOf[Int],
        proteinSetsCount = pepInstRecord(PepInstCols.PROTEIN_SET_COUNT).asInstanceOf[Int],
        selectionLevel = pepInstRecord(PepInstCols.SELECTION_LEVEL).asInstanceOf[Int],
        elutionTime = pepInstRecord(PepInstCols.ELUTION_TIME).asInstanceOf[AnyVal],
        peptideMatchIds = pepMatchIds.toArray,
        bestPeptideMatchId = pepInstRecord(PepInstCols.BEST_PEPTIDE_MATCH_ID).asInstanceOf[Int],
        unmodifiedPeptideId = pepInstRecord(PepInstCols.UNMODIFIED_PEPTIDE_ID).asInstanceOf[Int],
        resultSummaryId = pepInstRecord(PepInstCols.RESULT_SUMMARY_ID).asInstanceOf[Int],
        properties = Option(propertiesAsJSON).map(parse[PeptideInstanceProperties](_)),
        peptideMatchPropertiesById = pepMatchPropertyMapBuilder.result()
      )

      pepInsts(pepInstIdx) = pepInst

    }

    pepInsts

  }

}