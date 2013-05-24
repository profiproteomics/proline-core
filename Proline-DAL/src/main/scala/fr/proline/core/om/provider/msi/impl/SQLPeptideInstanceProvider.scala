package fr.proline.core.om.provider.msi.impl

import scala.collection.mutable.ArrayBuffer
import com.codahale.jerkson.Json.parse

import fr.proline.core.dal.DoJDBCReturningWork
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
import fr.proline.util.primitives._

class SQLPeptideInstanceProvider(
  val msiSqlCtx: DatabaseConnectionContext,
  var peptideProvider: IPeptideProvider
) extends IPeptideInstanceProvider {
  
  def this(msiSqlCtx: DatabaseConnectionContext, psSqlCtx: DatabaseConnectionContext) = {
    this(msiSqlCtx, new SQLPeptideProvider(psSqlCtx) )
  }

  val PepInstCols = MsiDbPeptideInstanceTable.columns
  val PepMatchMappingCols = MsiDbPeptideInstancePeptideMatchMapTable.columns
  
  def getPeptideInstancesAsOptions(pepInstIds: Seq[Int]): Array[Option[PeptideInstance]] = {

    val pepInsts = this.getPeptideInstances(pepInstIds)
    val pepInstById = pepInsts.map { p => p.id -> p } toMap

    pepInstIds.map { pepInstById.get(_) } toArray
  }

  def getPeptideInstances(pepInstIds: Seq[Int]): Array[PeptideInstance] = {
    
    DoJDBCReturningWork.withEzDBC(msiSqlCtx, { msiEzDBC =>
      
      // TODO: use max nb iterations
      val sqlQuery1 = new SelectQueryBuilder1(MsiDbPeptideInstanceTable).mkSelectQuery( (t,c) =>
        List(t.*) -> "WHERE "~ t.ID ~" IN("~ pepInstIds.mkString(",") ~")"
      )
      val pepInstRecords = msiEzDBC.selectAllRecordsAsMaps(sqlQuery1)
      
      val sqlQuery2 = new SelectQueryBuilder1(MsiDbPeptideInstancePeptideMatchMapTable).mkSelectQuery( (t,c) =>
        List(t.*) -> "WHERE "~ t.PEPTIDE_INSTANCE_ID ~" IN("~ pepInstIds.mkString(",") ~")"
      )
      val pepInstPepMatchMapRecords = msiEzDBC.selectAllRecordsAsMaps(sqlQuery2)
      
      this._buildPeptideInstances(pepInstRecords, pepInstPepMatchMapRecords)
      
    })
  }

  def getResultSummariesPeptideInstances(rsmIds: Seq[Int]): Array[PeptideInstance] = {
    
    DoJDBCReturningWork.withEzDBC(msiSqlCtx, { msiEzDBC =>
      
      // TODO: use max nb iterations
      val sqlQuery1 = new SelectQueryBuilder1(MsiDbPeptideInstanceTable).mkSelectQuery( (t,c) =>
        List(t.*) -> "WHERE "~ t.RESULT_SUMMARY_ID ~" IN("~ rsmIds.mkString(",") ~")"
      )      
      val pepInstRecords = msiEzDBC.selectAllRecordsAsMaps(sqlQuery1)
      
      val sqlQuery2 = new SelectQueryBuilder1(MsiDbPeptideInstancePeptideMatchMapTable).mkSelectQuery( (t,c) =>
        List(t.*) -> "WHERE "~ t.RESULT_SUMMARY_ID ~" IN("~ rsmIds.mkString(",") ~")"
      )
      val pepInstPepMatchMapRecords = msiEzDBC.selectAllRecordsAsMaps(sqlQuery2)
  
      this._buildPeptideInstances(pepInstRecords, pepInstPepMatchMapRecords)
      
    })    
  }
  
  private def _buildPeptideInstances(
    pepInstRecords: Seq[Map[String, Any]],
    pepInstPepMatchMapRecords: Seq[Map[String, Any]]
  ): Array[PeptideInstance] = {
    
    // Load peptides
    val uniqPepIds = pepInstRecords.map { _(PepInstCols.PEPTIDE_ID).asInstanceOf[Int] } distinct
    val peptides = this.peptideProvider.getPeptides(uniqPepIds)

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
      val pepId: Int = toInt(pepInstRecord(PepInstCols.PEPTIDE_ID))
      require(peptideById.contains(pepId), "undefined peptide with id ='" + pepId + "'")
      val peptide = peptideById(pepId)

      // Retrieve peptide match ids and properties
      val pepInstId: Int = toInt(pepInstRecord(PepInstCols.ID))
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
      
      val pepInst = new PeptideInstance(
        id = pepInstId,
        peptide = peptide,
        proteinMatchesCount = pepInstRecord(PepInstCols.PROTEIN_MATCH_COUNT).asInstanceOf[Int],
        proteinSetsCount = pepInstRecord(PepInstCols.PROTEIN_SET_COUNT).asInstanceOf[Int],
        validatedProteinSetsCount = pepInstRecord(PepInstCols.VALIDATED_PROTEIN_SET_COUNT).asInstanceOf[Int],
        totalLeavesMatchCount = pepInstRecord(PepInstCols.TOTAL_LEAVES_MATCH_COUNT).asInstanceOf[Int],
        selectionLevel = pepInstRecord(PepInstCols.SELECTION_LEVEL).asInstanceOf[Int],
        elutionTime = Option(pepInstRecord(PepInstCols.ELUTION_TIME)).map( toFloat(_) ).getOrElse(0f),
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