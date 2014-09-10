package fr.proline.core.om.provider.msq.impl

import fr.profi.util.serialization.ProfiJson
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.SelectQueryBuilder3
import fr.proline.core.dal.tables.msi.MsiDbMasterQuantComponentTable
import fr.proline.core.dal.tables.msi.MsiDbMasterQuantPeptideIonTable
import fr.proline.core.dal.tables.msi.MsiDbObjectTreeTable
import fr.proline.core.om.model.msq.MasterQuantPeptideIon
import fr.proline.core.om.model.msq.MasterQuantPeptideIonProperties
import fr.proline.core.om.model.msq.QuantPeptideIon
import fr.proline.core.om.provider.msq.IMasterQuantPeptideIonProvider
import fr.proline.core.orm.msi.ObjectTreeSchema.SchemaName
import fr.profi.util.primitives._
import scala.collection.mutable.ArrayBuffer

class SQLMasterQuantPeptideIonProvider(val msiDbCtx: DatabaseConnectionContext) extends IMasterQuantPeptideIonProvider {
  
  val MQPepIonTable = MsiDbMasterQuantPeptideIonTable
  val MQPepIonCols = MQPepIonTable.columns
  val MQComponentTable = MsiDbMasterQuantComponentTable
  val ObjectTreeTable = MsiDbObjectTreeTable
  
  val LabelFreeQuantPeptideIonsSchema = SchemaName.LABEL_FREE_QUANT_PEPTIDE_IONS.toString
  
  def getMasterQuantPeptideIonsAsOptions( mqPepIonIds: Seq[Long] ): Array[Option[MasterQuantPeptideIon]] = {
    throw new Exception("NYI")
  }
  
  def getMasterQuantPeptideIons( mqPepIonIds: Seq[Long] ): Array[MasterQuantPeptideIon] = {
    
    require( mqPepIonIds != null, "mqPepIonIds is null")
    
    if( mqPepIonIds.isEmpty ) return Array()
    
    DoJDBCReturningWork.withEzDBC(msiDbCtx, { msiEzDBC =>
      
      val mqPepIonIdsAsStr = mqPepIonIds.mkString(",")
      val mqPepIons = new ArrayBuffer[MasterQuantPeptideIon]()
      
      // Load master quant peptide ions corresponding to the provided result summary ids
      val mqPepIonQueryBuilder = new SelectQueryBuilder3(MQPepIonTable,MQComponentTable,ObjectTreeTable)
      val mqPepIonQuery = mqPepIonQueryBuilder.mkSelectQuery(
        (t1,c1,t2,c2,t3,c3) => List(t1.*,t2.SELECTION_LEVEL,t3.CLOB_DATA) -> 
        " WHERE "~ t1.ID ~" IN("~ mqPepIonIdsAsStr ~")" ~
        " AND "~ t1.MASTER_QUANT_COMPONENT_ID ~" = "~ t2.ID ~
        " AND "~ t2.OBJECT_TREE_ID ~" = "~ t3.ID
      )
      
      msiEzDBC.selectAndProcess(mqPepIonQuery) { r =>
        mqPepIons += this._buildMasterQuantPeptideIon( r.toAnyMap() )
      }
      
      mqPepIons.toArray
    })
    
  }

  def getQuantResultSummariesMQPeptideIons(quantRsmIds: Seq[Long]): Array[MasterQuantPeptideIon] = {
    
    DoJDBCReturningWork.withEzDBC(msiDbCtx, { msiEzDBC =>
      
      val quantRsmIdsAsStr = quantRsmIds.mkString(",")
      val mqPepIons = new ArrayBuffer[MasterQuantPeptideIon]()
      
      // Load master quant peptide ions corresponding to the provided result summary ids
      val mqPepIonQueryBuilder = new SelectQueryBuilder3(MQPepIonTable,MQComponentTable,ObjectTreeTable)
      // TODO: check if this SQL query could be simplified
      val mqPepIonQuery = mqPepIonQueryBuilder.mkSelectQuery(
        (t1,c1,t2,c2,t3,c3) => List(t1.*,t2.SELECTION_LEVEL,t3.CLOB_DATA) -> 
        " WHERE "~ t1.RESULT_SUMMARY_ID ~" IN("~ quantRsmIdsAsStr ~")" ~
        " AND "~ t2.RESULT_SUMMARY_ID ~" IN("~ quantRsmIdsAsStr ~")" ~
        " AND "~ t1.MASTER_QUANT_COMPONENT_ID ~" = "~ t2.ID ~
        " AND "~ t2.OBJECT_TREE_ID ~" = "~ t3.ID ~
        " AND "~ t3.SCHEMA_NAME ~" = '"~ LabelFreeQuantPeptideIonsSchema ~"'"
      )
      
      msiEzDBC.selectAndProcess(mqPepIonQuery) { r =>
        mqPepIons += this._buildMasterQuantPeptideIon( r.toAnyMap() )
      }
      
      mqPepIons.toArray
    })

  }
  
  private def _buildMasterQuantPeptideIon( record: AnyMap ): MasterQuantPeptideIon = {
    
    val r = record
    
    val quantPepIons = ProfiJson.deserialize[Array[QuantPeptideIon]]( r.getString(ObjectTreeTable.columns.CLOB_DATA) )
    val quantPepIonMap = Map() ++ (for( qpi <- quantPepIons if qpi != null ) yield qpi.quantChannelId -> qpi)
    
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
      properties = r.getStringOption(MQPepIonCols.SERIALIZED_PROPERTIES).map(ProfiJson.deserialize[MasterQuantPeptideIonProperties](_))
    )
  }

}

