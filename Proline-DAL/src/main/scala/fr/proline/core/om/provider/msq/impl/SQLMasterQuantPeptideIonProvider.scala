package fr.proline.core.om.provider.msq.impl

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.LongMap

import fr.profi.util.collection._
import fr.profi.util.serialization.ProfiJson
import fr.profi.util.primitives._
import fr.proline.context.MsiDbConnectionContext
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.SelectQueryBuilder3
import fr.proline.core.dal.tables.msi.MsiDbMasterQuantComponentTable
import fr.proline.core.dal.tables.msi.MsiDbMasterQuantPeptideIonTable
import fr.proline.core.dal.tables.msi.MsiDbObjectTreeTable
import fr.proline.core.om.model.msq.MasterQuantPeptideIon
import fr.proline.core.om.model.msq.MasterQuantPeptideIonProperties
import fr.proline.core.om.model.msq.MasterQuantReporterIon
import fr.proline.core.om.model.msq.QuantPeptideIon
import fr.proline.core.om.provider.msq.IMasterQuantPeptideIonProvider

class SQLMasterQuantPeptideIonProvider(
  val msiDbCtx: MsiDbConnectionContext,
  loadReporterIons: Boolean = false
) extends IMasterQuantPeptideIonProvider {
  
  private val mqRepIonProvider = new SQLMasterQuantReporterIonProvider(msiDbCtx)
  
  private val MQPepIonTable = MsiDbMasterQuantPeptideIonTable
  private val MQPepIonCols = MQPepIonTable.columns
  private val MQComponentTable = MsiDbMasterQuantComponentTable
  private val ObjectTreeTable = MsiDbObjectTreeTable
  
  def getMasterQuantPeptideIons( mqPepIonIds: Seq[Long] ): Array[MasterQuantPeptideIon] = {    
    require( mqPepIonIds != null, "mqPepIonIds is null")
    if( mqPepIonIds.isEmpty ) return Array()
    
    // Load master quant peptide ions corresponding to the provided ids
    val mqPepIonQueryBuilder = new SelectQueryBuilder3(MQPepIonTable,MQComponentTable,ObjectTreeTable)
    val mqPepIonQuery = mqPepIonQueryBuilder.mkSelectQuery(
      (t1,c1,t2,c2,t3,c3) => List(t1.*,t2.SELECTION_LEVEL,t3.CLOB_DATA) -> 
      " WHERE "~ t1.ID ~" IN ("~ mqPepIonIds.mkString(",") ~")" ~
      " AND "~ t1.MASTER_QUANT_COMPONENT_ID ~" = "~ t2.ID ~
      " AND "~ t2.OBJECT_TREE_ID ~" = "~ t3.ID
    )
    
    val mqRepIons = if (!loadReporterIons) Array.empty[MasterQuantReporterIon]
    else mqRepIonProvider.getMQPeptideIonsMQReporterIons(mqPepIonIds)
    
    this.loadMasterQuantPeptideIons(mqPepIonQuery, mqRepIons)
  }
  
  def getMasterQuantPeptidesMQPeptideIons( mqPepIds: Seq[Long] ): Array[MasterQuantPeptideIon] = {    
    require( mqPepIds != null, "mqPepIds is null")
    if( mqPepIds.isEmpty ) return Array()
    
    // Load master quant peptide ions corresponding to the provided master quant peptide ids
    val mqPepIonQueryBuilder = new SelectQueryBuilder3(MQPepIonTable,MQComponentTable,ObjectTreeTable)
    val mqPepIonQuery = mqPepIonQueryBuilder.mkSelectQuery(
      (t1,c1,t2,c2,t3,c3) => List(t1.*,t2.SELECTION_LEVEL,t3.CLOB_DATA) -> 
      " WHERE "~ t1.MASTER_QUANT_PEPTIDE_ID ~" IN ("~ mqPepIds.mkString(",") ~")" ~
      " AND "~ t1.MASTER_QUANT_COMPONENT_ID ~" = "~ t2.ID ~
      " AND "~ t2.OBJECT_TREE_ID ~" = "~ t3.ID
    )
    
    val mqPepIons = this.loadMasterQuantPeptideIons(mqPepIonQuery, Array.empty[MasterQuantReporterIon])
    
    if (loadReporterIons) {
      this.loadMasterQuantReporterIons(mqPepIons)
    }
    
    mqPepIons
  }

  def getLcMsMasterFeaturesMQPeptideIons( lcMsMasterFeatureIds: Seq[Long] ): Array[MasterQuantPeptideIon] = {
    require( lcMsMasterFeatureIds != null, "lcMsMasterFeatureIds is null")
    if( lcMsMasterFeatureIds.isEmpty ) return Array()

    // Load master quant peptide ions corresponding to the provided LC-MS master feature ids
    val mqPepIonQueryBuilder = new SelectQueryBuilder3(MQPepIonTable,MQComponentTable,ObjectTreeTable)
    val mqPepIonQuery = mqPepIonQueryBuilder.mkSelectQuery(
      (t1,c1,t2,c2,t3,c3) => List(t1.*,t2.SELECTION_LEVEL,t3.CLOB_DATA) ->
        " WHERE "~ t1.LCMS_MASTER_FEATURE_ID ~" IN ("~ lcMsMasterFeatureIds.mkString(",") ~")" ~
        " AND "~ t1.MASTER_QUANT_COMPONENT_ID ~" = "~ t2.ID ~
        " AND "~ t2.OBJECT_TREE_ID ~" = "~ t3.ID
    )

    val mqPepIons = this.loadMasterQuantPeptideIons(mqPepIonQuery, Array.empty[MasterQuantReporterIon])
    
    if (loadReporterIons) {
      this.loadMasterQuantReporterIons(mqPepIons)
    }
    
    mqPepIons
  }

  def getQuantResultSummariesMQPeptideIons(quantRsmIds: Seq[Long]): Array[MasterQuantPeptideIon] = {
    require( quantRsmIds != null, "quantRsmIds is null")
    if( quantRsmIds.isEmpty ) return Array()
    
    val quantRsmIdsAsStr = quantRsmIds.mkString(",")
    
    // Load master quant peptide ions corresponding to the provided result summary ids
    val mqPepIonQueryBuilder = new SelectQueryBuilder3(MQPepIonTable,MQComponentTable,ObjectTreeTable)
    // TODO: check if this SQL query could be simplified
    val mqPepIonQuery = mqPepIonQueryBuilder.mkSelectQuery(
      (t1,c1,t2,c2,t3,c3) => List(t1.*,t2.SELECTION_LEVEL,t3.CLOB_DATA) -> 
      " WHERE "~ t1.RESULT_SUMMARY_ID ~" IN ("~ quantRsmIdsAsStr ~")" ~
      //" AND "~ t2.RESULT_SUMMARY_ID ~" IN("~ quantRsmIdsAsStr ~")" ~
      " AND "~ t1.MASTER_QUANT_COMPONENT_ID ~" = "~ t2.ID ~
      " AND "~ t2.OBJECT_TREE_ID ~" = "~ t3.ID
    )
    
    val mqRepIons = if (!loadReporterIons) Array.empty[MasterQuantReporterIon]
    else mqRepIonProvider.getQuantResultSummariesMQReporterIons(quantRsmIds)
    
    this.loadMasterQuantPeptideIons(mqPepIonQuery, mqRepIons)
  }
  
  protected def loadMasterQuantPeptideIons(sqlQuery: String, mqRepIons: Array[MasterQuantReporterIon]): Array[MasterQuantPeptideIon] = {
    
    val mqRepIonsByMqPepIonId = mqRepIons.groupByLong(_.masterQuantPeptideIonId.getOrElse(0) )
    
    DoJDBCReturningWork.withEzDBC(msiDbCtx) { msiEzDBC =>
      
      val mqPepIons = new ArrayBuffer[MasterQuantPeptideIon]()
      msiEzDBC.selectAndProcess(sqlQuery) { r =>
        val mqRepIons = mqRepIonsByMqPepIonId.get(r.getLong(MQPepIonCols.ID)).getOrElse(Array.empty[MasterQuantReporterIon])
        mqPepIons += this._buildMasterQuantPeptideIon(r, mqRepIons)
      }
      
      mqPepIons.toArray
    }

  }
  
  protected def loadMasterQuantReporterIons(mqPepIons: Array[MasterQuantPeptideIon]) = {
    
    val mqPepIonIds = mqPepIons.map(_.id)
    val mqRepIons = mqRepIonProvider.getMQPeptideIonsMQReporterIons(mqPepIonIds)
    val mqRepIonsByMqPepIonId = mqRepIons.groupByLong(_.masterQuantPeptideIonId.getOrElse(0))
    mqPepIons.foreach { mqPepIon =>
      mqPepIon.masterQuantReporterIons = mqRepIonsByMqPepIonId.get(mqPepIon.id).getOrElse(Array.empty[MasterQuantReporterIon])
    }

  }
  
  private def _buildMasterQuantPeptideIon(record: IValueContainer, mqRepIons: Array[MasterQuantReporterIon]): MasterQuantPeptideIon = {
    
    val r = record
    
    val quantPepIons = ProfiJson.deserialize[Array[QuantPeptideIon]]( r.getString(ObjectTreeTable.columns.CLOB_DATA) )
    val quantPepIonMap = new LongMap[QuantPeptideIon](quantPepIons.length)
    for( qpi <- quantPepIons if qpi != null ) quantPepIonMap.put(qpi.quantChannelId, qpi)
    
    // Build the master quant peptide ion
    new MasterQuantPeptideIon(
      id = r.getLong(MQPepIonCols.ID),
      unlabeledMoz = r.getDouble(MQPepIonCols.MOZ),
      charge = r.getInt(MQPepIonCols.CHARGE),
      elutionTime = toFloat(r.getAny(MQPepIonCols.ELUTION_TIME)),
      peptideMatchesCount = r.getInt(MQPepIonCols.PEPTIDE_MATCH_COUNT),
      selectionLevel = r.getInt(MQComponentTable.columns.SELECTION_LEVEL),
      masterQuantPeptideId = toLong(r.getAny(MQPepIonCols.MASTER_QUANT_PEPTIDE_ID)),
      resultSummaryId = toLong(r.getAny(MQPepIonCols.RESULT_SUMMARY_ID)),
      peptideInstanceId = r.getLongOption(MQPepIonCols.PEPTIDE_INSTANCE_ID),
      bestPeptideMatchId = r.getLongOption(MQPepIonCols.BEST_PEPTIDE_MATCH_ID),
      lcmsMasterFeatureId = r.getLongOption(MQPepIonCols.LCMS_MASTER_FEATURE_ID),
      unmodifiedPeptideIonId = r.getLongOption(MQPepIonCols.UNMODIFIED_PEPTIDE_ION_ID),
      quantPeptideIonMap = quantPepIonMap,
      properties = r.getStringOption(MQPepIonCols.SERIALIZED_PROPERTIES).map(ProfiJson.deserialize[MasterQuantPeptideIonProperties](_)),
      masterQuantReporterIons = mqRepIons
    )
  }

}

