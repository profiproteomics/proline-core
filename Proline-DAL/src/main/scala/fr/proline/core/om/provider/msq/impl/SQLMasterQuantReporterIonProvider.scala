package fr.proline.core.om.provider.msq.impl

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.LongMap
import fr.profi.util.serialization.ProfiJson
import fr.profi.util.primitives._
import fr.proline.context.MsiDbConnectionContext
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.SelectQueryBuilder3
import fr.proline.core.dal.tables.msi.MsiDbMasterQuantComponentTable
import fr.proline.core.dal.tables.msi.MsiDbMasterQuantReporterIonTable
import fr.proline.core.dal.tables.msi.MsiDbObjectTreeTable
import fr.proline.core.om.model.msq.MasterQuantReporterIon
import fr.proline.core.om.model.msq.MasterQuantReporterIonProperties
import fr.proline.core.om.model.msq.QuantReporterIon

class SQLMasterQuantReporterIonProvider(val msiDbCtx: MsiDbConnectionContext) {
  
  val MQRepIonTable = MsiDbMasterQuantReporterIonTable
  val MQRepIonCols = MQRepIonTable.columns
  val MQComponentTable = MsiDbMasterQuantComponentTable
  val ObjectTreeTable = MsiDbObjectTreeTable
  
  def getMasterQuantReporterIons(mqRepIonIds: Seq[Long]): Array[MasterQuantReporterIon] = {    
    require( mqRepIonIds != null, "mqRepIonIds is null")
    if (mqRepIonIds.isEmpty) return Array()
    
    // Load master quant reporter ions corresponding to the provided ids
    val mqRepIonQueryBuilder = new SelectQueryBuilder3(MQRepIonTable,MQComponentTable,ObjectTreeTable)
    val mqRepIonQuery = mqRepIonQueryBuilder.mkSelectQuery(
      (t1,c1,t2,c2,t3,c3) => List(t1.*,t2.SELECTION_LEVEL,t3.CLOB_DATA) -> 
      " WHERE "~ t1.ID ~" IN ("~ mqRepIonIds.mkString(",") ~")" ~
      " AND "~ t1.MASTER_QUANT_COMPONENT_ID ~" = "~ t2.ID ~
      " AND "~ t2.OBJECT_TREE_ID ~" = "~ t3.ID
    )
    
    this.loadMasterQuantReporterIons(mqRepIonQuery)
  }
  
  def getMQPeptideIonsMQReporterIons(mqPepIonIds: Seq[Long]): Array[MasterQuantReporterIon] = {    
    require( mqPepIonIds != null, "mqPepIonIds is null")
    if (mqPepIonIds.isEmpty) return Array()
    
    // Load master quant reporter ions corresponding to the provided quant peptide ions ids
    val mqRepIonQueryBuilder = new SelectQueryBuilder3(MQRepIonTable,MQComponentTable,ObjectTreeTable)
    val mqRepIonQuery = mqRepIonQueryBuilder.mkSelectQuery(
      (t1,c1,t2,c2,t3,c3) => List(t1.*,t2.SELECTION_LEVEL,t3.CLOB_DATA) -> 
      " WHERE "~ t1.MASTER_QUANT_PEPTIDE_ION_ID ~" IN ("~ mqPepIonIds.mkString(",") ~")" ~
      " AND "~ t1.MASTER_QUANT_COMPONENT_ID ~" = "~ t2.ID ~
      " AND "~ t2.OBJECT_TREE_ID ~" = "~ t3.ID
    )

    this.loadMasterQuantReporterIons(mqRepIonQuery)
  }

  def getQuantResultSummariesMQReporterIons(quantRsmIds: Seq[Long]): Array[MasterQuantReporterIon] = {
    require(quantRsmIds != null, "quantRsmIds is null")
    if( quantRsmIds.isEmpty ) return Array()
    
    val quantRsmIdsAsStr = quantRsmIds.mkString(",")
    
    // Load master quant reporter ions corresponding to the provided result summary ids
    val mqRepIonQueryBuilder = new SelectQueryBuilder3(MQRepIonTable,MQComponentTable,ObjectTreeTable)
    // TODO: check if this SQL query could be simplified
    val mqRepIonQuery = mqRepIonQueryBuilder.mkSelectQuery(
      (t1,c1,t2,c2,t3,c3) => List(t1.*,t2.SELECTION_LEVEL,t3.CLOB_DATA) -> 
      " WHERE "~ t1.RESULT_SUMMARY_ID ~" IN ("~ quantRsmIdsAsStr ~")" ~
      " AND "~ t1.MASTER_QUANT_COMPONENT_ID ~" = "~ t2.ID ~
      " AND "~ t2.OBJECT_TREE_ID ~" = "~ t3.ID
    )
      
    this.loadMasterQuantReporterIons(mqRepIonQuery)
  }
  
  protected def loadMasterQuantReporterIons(sqlQuery: String): Array[MasterQuantReporterIon] = {
    
    DoJDBCReturningWork.withEzDBC(msiDbCtx) { msiEzDBC =>
      
      val mqRepIons = new ArrayBuffer[MasterQuantReporterIon]()
      msiEzDBC.selectAndProcess(sqlQuery) { r =>
        mqRepIons += this._buildMasterQuantReporterIon( r.toAnyMap() )
      }
      
      mqRepIons.toArray
    }

  }
  
  private def _buildMasterQuantReporterIon(record: AnyMap): MasterQuantReporterIon = {
    
    val r = record
    
    val quantRepIons = ProfiJson.deserialize[Array[QuantReporterIon]]( r.getString(ObjectTreeTable.columns.CLOB_DATA) )
    val quantRepIonMap = new LongMap[QuantReporterIon](quantRepIons.length)
    for (qpi <- quantRepIons if qpi != null) quantRepIonMap.put(qpi.quantChannelId, qpi)
    
    // TODO: load MQ queries before loading reporter ions ?
    
    // Build the master quant peptide ion
    new MasterQuantReporterIon(
      id = r.getLong(MQRepIonCols.ID),
      charge = 0, //r.getInt(MQRepIonCols.CHARGE), // TODO: add this column to the master_quant_reporter_ion table or remove it ?
      elutionTime = 0f, //r.getFloat(MQRepIonCols.ELUTION_TIME), // TODO: add this column to the master_quant_reporter_ion table or remove it ?
      msQueryId = r.getLong(MQRepIonCols.MS_QUERY_ID),
      spectrumId = 0, // TODO: find a way to load this field or remove it ?
      scanNumber = 0, // TODO: find a way to load this field or remove it ?
      quantReporterIonMap = quantRepIonMap,
      selectionLevel = r.getInt(MQComponentTable.columns.SELECTION_LEVEL),
      // TODO: v2.0 => add to masterQuantPeptideId field to MSIdb
      masterQuantPeptideIonId = r.getLongOption(MQRepIonCols.MASTER_QUANT_PEPTIDE_ION_ID),
      properties = r.getStringOption(MQRepIonCols.SERIALIZED_PROPERTIES).map(ProfiJson.deserialize[MasterQuantReporterIonProperties](_))
    )
  }

}

