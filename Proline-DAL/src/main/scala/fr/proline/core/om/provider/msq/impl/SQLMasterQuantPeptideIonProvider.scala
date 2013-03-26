package fr.proline.core.om.provider.msq.impl

import com.codahale.jerkson.Json.parse

import fr.proline.core.dal.SQLConnectionContext
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.SelectQueryBuilder3
import fr.proline.core.dal.tables.msi.MsiDbMasterQuantComponentTable
import fr.proline.core.dal.tables.msi.MsiDbMasterQuantPeptideIonTable
import fr.proline.core.dal.tables.msi.MsiDbObjectTreeTable
import fr.proline.core.om.model.msq.MasterQuantPeptideIon
import fr.proline.core.om.model.msq.MasterQuantPeptideIonProperties
import fr.proline.core.om.model.msq.QuantPeptideIon
import fr.proline.util.primitives._

class SQLMasterQuantPeptideIonProvider(val msiSqlCtx: SQLConnectionContext) { //extends IMasterQuantPeptideIonProvider
  
  val MQPepIonTable = MsiDbMasterQuantPeptideIonTable
  val MQPepIonCols = MQPepIonTable.columns
  val MQComponentTable = MsiDbMasterQuantComponentTable
  val ObjectTreeTable = MsiDbObjectTreeTable

  def getQuantResultSummariesMQPeptideIons(quantRsmIds: Seq[Int]): Array[MasterQuantPeptideIon] = {
    
    val msiEzDBC = msiSqlCtx.ezDBC
    val quantRsmIdsAsStr = quantRsmIds.mkString(",")
    
    // Load master quant peptide ions corresponding to the provided result summary ids
    val mqPepIonQueryBuilder = new SelectQueryBuilder3(MQPepIonTable,MQComponentTable,ObjectTreeTable)
    val mqPepIonQuery = mqPepIonQueryBuilder.mkSelectQuery(
      (t1,c1,t2,c2,t3,c3) => List(t1.*,t2.SELECTION_LEVEL,t3.SERIALIZED_DATA) -> 
      " WHERE "~ t1.RESULT_SUMMARY_ID ~" IN("~ quantRsmIdsAsStr ~")" ~
      " AND "~ t2.RESULT_SUMMARY_ID ~" IN("~ quantRsmIdsAsStr ~")" ~
      " AND "~ t1.MASTER_QUANT_COMPONENT_ID ~" = "~ t2.ID ~
      " AND "~ t2.OBJECT_TREE_ID ~" = "~ t3.ID
    )
    
    msiSqlCtx.ezDBC.select(mqPepIonQuery) { r =>

      val mqPepIonId: Int = toInt(r.getAnyVal(MQPepIonCols.ID))
      val quantPepIons = parse[Array[QuantPeptideIon]]( r.getString(ObjectTreeTable.columns.SERIALIZED_DATA) )
      
      // Build the master quant peptide ion
      new MasterQuantPeptideIon(
        id = mqPepIonId,
        unlabeledMoz = r.getDouble(MQPepIonCols.MOZ),
        charge = r.getInt(MQPepIonCols.CHARGE),
        elutionTime = toFloat(r.getAnyVal(MQPepIonCols.ELUTION_TIME)),
        peptideMatchesCount = 0,//r.getInt(MQPepIonCols.p), // TODO: add to MSIdb
        selectionLevel = r.getInt(MQComponentTable.columns.SELECTION_LEVEL),
        resultSummaryId = r.getInt(MQPepIonCols.RESULT_SUMMARY_ID),
        bestPeptideMatchId = r.getIntOption(MQPepIonCols.BEST_PEPTIDE_MATCH_ID),
        lcmsFeatureId = r.getIntOption(MQPepIonCols.LCMS_FEATURE_ID),
        unmodifiedPeptideIonId = r.getIntOption(MQPepIonCols.UNMODIFIED_PEPTIDE_ION_ID),
        quantPeptideIonMap = Map() ++ quantPepIons.map( qpi => qpi.quantChannelId -> qpi ),
        properties = r.getStringOption(MQPepIonCols.SERIALIZED_PROPERTIES).map(parse[MasterQuantPeptideIonProperties](_))
      )
      
    } toArray

  }

}

