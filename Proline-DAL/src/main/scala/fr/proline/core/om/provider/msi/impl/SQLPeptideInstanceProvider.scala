package fr.proline.core.om.provider.msi.impl

import scala.collection.mutable.ArrayBuffer
import com.weiglewilczek.slf4s.Logging

import fr.profi.util.serialization.ProfiJson
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
) extends IPeptideInstanceProvider  with Logging {
  
  def this(msiSqlCtx: DatabaseConnectionContext, psSqlCtx: DatabaseConnectionContext) = {
    this(msiSqlCtx, new SQLPeptideProvider(psSqlCtx) )
  }

  val PepInstCols = MsiDbPeptideInstanceTable.columns
  val PepMatchMappingCols = MsiDbPeptideInstancePeptideMatchMapTable.columns
  
  def getPeptideInstancesAsOptions(pepInstIds: Seq[Long]): Array[Option[PeptideInstance]] = {

    val pepInsts = this.getPeptideInstances(pepInstIds)
    val pepInstById = pepInsts.map { p => p.id -> p } toMap

    pepInstIds.map { pepInstById.get(_) } toArray
  }

  def getPeptideInstances(pepInstIds: Seq[Long]): Array[PeptideInstance] = {
    
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

  def getResultSummariesPeptideInstances(rsmIds: Seq[Long]): Array[PeptideInstance] = {
    
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
    val uniqPepIds = pepInstRecords.map { v => toLong(v(PepInstCols.PEPTIDE_ID)) } distinct
    val peptides = this.peptideProvider.getPeptides(uniqPepIds)

    // Map peptides by their id
    val peptideById = Map() ++ peptides.map { pep => (pep.id -> pep) }

    // Group peptide matches mapping by peptide instance id
    val pepMatchesMappingsByPepInstId = pepInstPepMatchMapRecords.groupBy {
      v => toLong(v(PepMatchMappingCols.PEPTIDE_INSTANCE_ID))
    }

    // Build peptide instances
    val pepInsts = new Array[PeptideInstance](pepInstRecords.length)

    for (pepInstIdx <- 0 until pepInstRecords.length) {

      // Retrieve peptide instance record
      val pepInstRecord = pepInstRecords(pepInstIdx)

      // Retrieve the corresponding peptide
      val pepId: Long = toLong(pepInstRecord(PepInstCols.PEPTIDE_ID))
      require(peptideById.contains(pepId), "undefined peptide with id ='" + pepId + "'")
      val peptide = peptideById(pepId)

      // Retrieve peptide match ids and properties
      val pepInstId: Long = toLong(pepInstRecord(PepInstCols.ID))
      val pepMatchIds = new ArrayBuffer[Long]()
      val pepMatchPropertyMapBuilder = Map.newBuilder[Long, PeptideMatchResultSummaryProperties]

      pepMatchesMappingsByPepInstId(pepInstId).foreach { pepMatchMapping =>
        val pepMatchId = toLong(pepMatchMapping(PepMatchMappingCols.PEPTIDE_MATCH_ID))
        pepMatchIds += pepMatchId

        val propertiesAsJSON = pepMatchMapping(PepMatchMappingCols.SERIALIZED_PROPERTIES).asInstanceOf[String]
        if (propertiesAsJSON != null) {
          pepMatchPropertyMapBuilder += pepMatchId -> ProfiJson.deserialize[PeptideMatchResultSummaryProperties](propertiesAsJSON)
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
        bestPeptideMatchId = toLong(pepInstRecord(PepInstCols.BEST_PEPTIDE_MATCH_ID)),
        unmodifiedPeptideId = if(pepInstRecord(PepInstCols.UNMODIFIED_PEPTIDE_ID) != null) toLong(pepInstRecord(PepInstCols.UNMODIFIED_PEPTIDE_ID)) else 0l,
        resultSummaryId = toLong(pepInstRecord(PepInstCols.RESULT_SUMMARY_ID)),
        properties = Option(propertiesAsJSON).map(ProfiJson.deserialize[PeptideInstanceProperties](_)),
        peptideMatchPropertiesById = pepMatchPropertyMapBuilder.result()
      )

      pepInsts(pepInstIdx) = pepInst

    }

    pepInsts

  }

}