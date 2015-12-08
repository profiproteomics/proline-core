package fr.proline.core.om.provider.msq.impl

import fr.profi.jdbc.easy.EasyDBC
import fr.profi.util.primitives._
import fr.profi.util.serialization.ProfiJson
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.SelectQueryBuilder2
import fr.proline.core.dal.tables.msi.MsiDbMasterQuantComponentTable
import fr.proline.core.dal.tables.msi.MsiDbObjectTreeTable
import fr.proline.core.om.model.msi.PeptideInstance
import fr.proline.core.om.model.msq._
import fr.proline.core.om.provider.msi.impl.SQLPeptideInstanceProvider
import fr.proline.core.om.provider.msi.impl.SQLPeptideProvider
import fr.proline.core.orm.msi.ObjectTreeSchema.SchemaName
import fr.proline.repository.ProlineDatabaseType

class SQLMasterQuantPeptideProvider(
  val msiDbCtx: DatabaseConnectionContext,
  val psDbCtx: DatabaseConnectionContext
) {  
  require( msiDbCtx.getProlineDatabaseType() == ProlineDatabaseType.MSI, "msiDbCtx must be of type MSI")
  require( psDbCtx.getProlineDatabaseType() == ProlineDatabaseType.PS, "psDbCtx must be of type PS")
  
  protected val pepProvider = new SQLPeptideProvider(psDbCtx)
  protected val pepInstProvider = new SQLPeptideInstanceProvider(msiDbCtx,pepProvider)
  protected val mqPepIonsProvider = new SQLMasterQuantPeptideIonProvider(msiDbCtx)
  
  val MQComponentTable = MsiDbMasterQuantComponentTable
  val MQCompCols = MQComponentTable.columns
  val ObjectTreeTable = MsiDbObjectTreeTable
  val ObjectTreeCols = ObjectTreeTable.columns
  
  final val labelFreeQuantPeptidesSchema = SchemaName.LABEL_FREE_QUANT_PEPTIDES.toString
  final val spectralCountQuantPeptidesSchema = SchemaName.SPECTRAL_COUNTING_QUANT_PEPTIDES.toString
  //final val quantProteinSetSchema = SchemaName.QUANT_PROTEIN_SETS.toString

  def getMasterQuantPeptides(
    mqPepIds: Seq[Long]
  ): Array[MasterQuantPeptide] = {
    if( mqPepIds.isEmpty ) return Array()
    
    val mqPepIons = mqPepIonsProvider.getMasterQuantPeptidesMQPeptideIons(mqPepIds)
    val pepInstIds = mqPepIons.flatMap( mqPepIon => mqPepIon.peptideInstanceId )
    val pepInstById = pepInstProvider.getPeptideInstances(pepInstIds).map( pepInst => pepInst.id -> pepInst ).toMap
    val pepInstByMQPepId = ( for(
      mqPepIon <- mqPepIons;
      pepInstId <- mqPepIon.peptideInstanceId
    ) yield mqPepIon.masterQuantPeptideId -> pepInstById(pepInstId) ) toMap
    
    val mqPepIonsByMQPepId = mqPepIons.groupBy( _.masterQuantPeptideId )
    
    this.getMasterQuantPeptides(mqPepIds, pepInstByMQPepId, mqPepIonsByMQPepId)
  }
  
  def getMasterQuantPeptides(
    mqPepIds: Seq[Long],
    pepInstByMQPepId: Map[Long,PeptideInstance],
    mqPepIonsByMQPepId: Map[Long,Array[MasterQuantPeptideIon]]
  ): Array[MasterQuantPeptide] = {
    if( mqPepIds.isEmpty ) return Array()
    
    DoJDBCReturningWork.withEzDBC(msiDbCtx, { msiEzDBC =>
    
      this.buildMasterQuantPeptides(
        this.selectMasterQuantPeptides(msiEzDBC, mqPepIds),
        pepInstByMQPepId,
        mqPepIonsByMQPepId
      )

    })
    
  }

  // TODO: factorize code with method getMasterQuantPeptides( mqPepIds: Seq[Long] )
  def getQuantResultSummariesMQPeptides(
    quantRsmIds: Seq[Long]
  ): Array[MasterQuantPeptide] = {
    if( quantRsmIds.isEmpty ) return Array()

    val mqPepIons = mqPepIonsProvider.getQuantResultSummariesMQPeptideIons(quantRsmIds)
    val pepInstIds = mqPepIons.flatMap( mqPepIon => mqPepIon.peptideInstanceId )
    val pepInstById = pepInstProvider.getPeptideInstances(pepInstIds).map( pepInst => pepInst.id -> pepInst ).toMap
    val pepInstByMQPepId = ( for(
      mqPepIon <- mqPepIons;
      pepInstId <- mqPepIon.peptideInstanceId
    ) yield mqPepIon.masterQuantPeptideId -> pepInstById(pepInstId) ).toMap

    val mqPepIonsByMQPepId = mqPepIons.groupBy( _.masterQuantPeptideId )

    this.getQuantResultSummariesMQPeptides(quantRsmIds, pepInstByMQPepId, mqPepIonsByMQPepId)
  }
  
  def getQuantResultSummariesMQPeptides(
    quantRsmIds: Seq[Long],
    pepInstByMQPepId: Map[Long,PeptideInstance],
    mqPepIonsByMQPepId: Map[Long,Array[MasterQuantPeptideIon]]
  ): Array[MasterQuantPeptide] = {
    if( quantRsmIds.isEmpty ) return Array()
    
    DoJDBCReturningWork.withEzDBC(msiDbCtx, { msiEzDBC =>
    
      this.buildMasterQuantPeptides(
        this.selectQuantResultSummariesMasterQuantPeptides(msiEzDBC, quantRsmIds),
        pepInstByMQPepId,
        mqPepIonsByMQPepId
      )

    })
    
  }
  
  def selectMasterQuantPeptides(
    msiEzDBC: EasyDBC,
    mqPepIds: Seq[Long]
  ): (IValueContainer => MasterQuantPeptide) => Seq[MasterQuantPeptide] = {
    
    val mqPepCompQueryBuilder = new SelectQueryBuilder2(MQComponentTable,ObjectTreeTable)
    val mqPepCompQuery = mqPepCompQueryBuilder.mkSelectQuery(
      (t1,c1,t2,c2) => List(t1.ID,t1.SELECTION_LEVEL,t1.RESULT_SUMMARY_ID,t1.SERIALIZED_PROPERTIES,t2.CLOB_DATA) -> 
      " WHERE "~ t1.ID ~" IN ("~ mqPepIds.mkString(",") ~")" ~
      " AND "~ t1.OBJECT_TREE_ID ~" = "~ t2.ID
    )
    
    msiEzDBC.select(mqPepCompQuery)
  }
  
  def selectQuantResultSummariesMasterQuantPeptides(
    msiEzDBC: EasyDBC,
    quantRsmIds: Seq[Long]
  ): (IValueContainer => MasterQuantPeptide) => Seq[MasterQuantPeptide] = {
    
    // TODO: index master_quant_component table and/or object_tree table on schema name
    val mqPepCompQueryBuilder = new SelectQueryBuilder2(MQComponentTable,ObjectTreeTable)
    val mqPepCompQuery = mqPepCompQueryBuilder.mkSelectQuery(
      (t1,c1,t2,c2) => List(t1.ID,t1.SELECTION_LEVEL,t1.RESULT_SUMMARY_ID,t1.SERIALIZED_PROPERTIES,t2.CLOB_DATA) -> 
      " WHERE "~ t1.RESULT_SUMMARY_ID ~" IN ("~ quantRsmIds.mkString(",") ~")" ~
      " AND ( "~ t1.SCHEMA_NAME ~" = '"~ labelFreeQuantPeptidesSchema ~"' OR "~ t1.SCHEMA_NAME ~" = '"~ spectralCountQuantPeptidesSchema ~"') " ~
      " AND "~ t1.OBJECT_TREE_ID ~" = "~ t2.ID
    )
     
    msiEzDBC.select(mqPepCompQuery)
  }
  
  def buildMasterQuantPeptides(
    eachMQPepRecord: (IValueContainer => MasterQuantPeptide) => Seq[MasterQuantPeptide],
    pepInstByMQPepId: Map[Long,PeptideInstance],
    mqPepIonsByMQPepId: Map[Long,Array[MasterQuantPeptideIon]]
  ): Array[MasterQuantPeptide] = {
    
    eachMQPepRecord { r =>
      val mqPepId = r.getLong(MQCompCols.ID)
      val pepInst = pepInstByMQPepId.get(mqPepId)
      val mqPepIons = if(mqPepIonsByMQPepId.get(mqPepId).isDefined) mqPepIonsByMQPepId(mqPepId) else Array.empty[MasterQuantPeptideIon]
      val quantPeptides = ProfiJson.deserialize[Array[QuantPeptide]]( r.getString(ObjectTreeTable.columns.CLOB_DATA) )
      val quantPeptideMap = Map() ++ (for( qp <- quantPeptides if qp != null ) yield qp.quantChannelId -> qp)
      
      // Build the master quant peptide ion
      new MasterQuantPeptide(
        id = mqPepId,
        peptideInstance = pepInst,
        quantPeptideMap = quantPeptideMap,
        masterQuantPeptideIons = mqPepIons,
        selectionLevel = r.getInt(MQCompCols.SELECTION_LEVEL),
        resultSummaryId = toLong(r.getAny(MQCompCols.RESULT_SUMMARY_ID)),
        properties = r.getStringOption(MQCompCols.SERIALIZED_PROPERTIES).map(ProfiJson.deserialize[MasterQuantPeptideProperties](_))
      )
    }.toArray
    
  }

}