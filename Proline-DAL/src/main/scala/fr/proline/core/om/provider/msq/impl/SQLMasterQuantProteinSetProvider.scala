package fr.proline.core.om.provider.msq.impl

import scala.collection.mutable.ArrayBuffer

import fr.profi.jdbc.easy.EasyDBC
import fr.profi.util.primitives._
import fr.profi.util.serialization.ProfiJson
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.SelectQueryBuilder3
import fr.proline.core.dal.tables.msi.MsiDbMasterQuantComponentTable
import fr.proline.core.dal.tables.msi.MsiDbObjectTreeTable
import fr.proline.core.dal.tables.msi.MsiDbPeptideInstanceTable
import fr.proline.core.dal.tables.msi.MsiDbProteinSetTable
import fr.proline.core.om.model.msi.ProteinSet
import fr.proline.core.om.model.msq._
import fr.proline.core.om.provider.msi.impl.SQLPeptideInstanceProvider
import fr.proline.core.om.provider.msi.impl.SQLPeptideProvider
import fr.proline.core.om.provider.msi.impl.SQLPeptideSetProvider
import fr.proline.core.om.provider.msi.impl.SQLProteinSetProvider
import fr.proline.repository.ProlineDatabaseType

class SQLMasterQuantProteinSetProvider(
  val msiDbCtx: DatabaseConnectionContext,
  val psDbCtx: DatabaseConnectionContext
) {
  require( msiDbCtx.getProlineDatabaseType() == ProlineDatabaseType.MSI, "msiDbCtx must be of type MSI")
  require( psDbCtx.getProlineDatabaseType() == ProlineDatabaseType.PS, "psDbCtx must be of type PS")
  
  protected val pepProvider = new SQLPeptideProvider(psDbCtx)
  protected val pepInstProvider = new SQLPeptideInstanceProvider(msiDbCtx,pepProvider)
  protected val pepSetProvider = new SQLPeptideSetProvider(msiDbCtx,pepInstProvider)
  protected val protSetProvider = new SQLProteinSetProvider(msiDbCtx,pepSetProvider)  
  protected val mqPepProvider = new SQLMasterQuantPeptideProvider(msiDbCtx, psDbCtx)
  
  val MQComponentTable = MsiDbMasterQuantComponentTable
  val MQCompCols = MQComponentTable.columns
  val ObjectTreeTable = MsiDbObjectTreeTable
  val ObjectTreeCols = ObjectTreeTable.columns
  
  def getMasterQuantProteinSets( mqProtSetIds: Seq[Long], loadMQPeptides: Boolean = true ): Array[MasterQuantProteinSet] = {
    if( mqProtSetIds.isEmpty ) return Array()
    
    val protSets = protSetProvider.getProteinSets(mqProtSetIds)
    val protSetById = protSets.map( protSet => protSet.id -> protSet ).toMap
    
    val mqPeps = if( loadMQPeptides == false ) Array.empty[MasterQuantPeptide]
    else {
      // Retrieve master quant peptide ids corresponding to peptide instances
      val mqPepIds = protSets.flatMap( _.peptideSet.getPeptideInstances.map(_.masterQuantComponentId) ).filter(_ > 0)
      
      /*val pepInstIds = protSets.flatMap( _.peptideSet.getPeptideInstances.map(_.id) )
      val mqPepIds = if( pepInstIds.isEmpty ) Array.empty[Long]
      else {
        DoJDBCReturningWork.withEzDBC(msiDbCtx, { msiEzDBC =>
          val mqPepIdQueryBuilder = new SelectQueryBuilder1(MsiDbPeptideInstanceTable)
          val mqPepIdSqlQuery = mqPepIdQueryBuilder.mkSelectQuery(
            (t1,c1) => List(t1.MASTER_QUANT_COMPONENT_ID) -> 
            " WHERE "~ t1.ID ~" IN ("~ pepInstIds.mkString(",") ~")"
          )
          
          val tmpMqPepIds = new ArrayBuffer[Long]
          msiEzDBC.selectAndProcess(mqPepIdSqlQuery) { r =>
            val mqPepIdOpt = r.nextLongOption
            if (mqPepIdOpt.isDefined) tmpMqPepIds += mqPepIdOpt.get
          }
          
          tmpMqPepIds.toArray
        })
      }*/
      
      // Load master quant peptides
      mqPepProvider.getMasterQuantPeptides(mqPepIds)
    }
    
    val mqPepByPepInstId = Map() ++ mqPeps.map(mqp => mqp.peptideInstance.get.id -> mqp)
    
    this.getMasterQuantProteinSets(mqProtSetIds, protSetById, mqPepByPepInstId)
  }
  
  def getMasterQuantProteinSets(
    mqProtSetIds: Seq[Long],
    protSetById: Map[Long,ProteinSet],
    mqPepByPepInstId: Map[Long,MasterQuantPeptide]
  ): Array[MasterQuantProteinSet] = {
    if( mqProtSetIds.isEmpty ) return Array()

    DoJDBCReturningWork.withEzDBC(msiDbCtx, { msiEzDBC =>

      this.buildMasterQuantProteinSets(
        this.selectMasterQuantProteinSets(msiEzDBC, mqProtSetIds),
        protSetById,
        mqPepByPepInstId
      )

    })

  }

  // TODO: factorize code with method getMasterQuantProteinSets( mqProtSetIds: Seq[Long], loadMQPeptides: Boolean = true )
  def getQuantResultSummariesMQProteinSets( quantRsmIds: Seq[Long], loadMQPeptides: Boolean = true ): Array[MasterQuantProteinSet] = {
    if( quantRsmIds.isEmpty ) return Array()

    val protSets = protSetProvider.getResultSummariesProteinSets(quantRsmIds)
    val protSetById = protSets.map( protSet => protSet.id -> protSet ).toMap

    val mqPepByPepInstId = if( loadMQPeptides == false ) Map.empty[Long,MasterQuantPeptide]
    else {
      // Retrieve master quant peptide ids corresponding to peptide instances
      val mqPepIds = protSets.flatMap( _.peptideSet.getPeptideInstances.map(_.masterQuantComponentId) ).filter(_ > 0)
      /*val pepInstIds = protSets.flatMap( _.peptideSet.getPeptideInstances.map(_.id) )

      val mqPepIds = if( pepInstIds.isEmpty ) Array.empty[Long]
      else {
        DoJDBCReturningWork.withEzDBC(msiDbCtx, { msiEzDBC =>
          val mqPepIdQueryBuilder = new SelectQueryBuilder1(MsiDbPeptideInstanceTable)
          val mqPepIdSqlQuery = mqPepIdQueryBuilder.mkSelectQuery(
            (t1,c1) => List(t1.MASTER_QUANT_COMPONENT_ID) ->
              " WHERE "~ t1.ID ~" IN ("~ pepInstIds.mkString(",") ~")"
          )

          val tmpMqPepIds = new ArrayBuffer[Long]
          msiEzDBC.selectAndProcess(mqPepIdSqlQuery) { r =>
            val mqPepIdOpt = r.nextLongOption
            if (mqPepIdOpt.isDefined) tmpMqPepIds += mqPepIdOpt.get
          }

          tmpMqPepIds.toArray
        })
      }*/

      // Load master quant peptides
      val mqPeps = mqPepProvider.getMasterQuantPeptides(mqPepIds)

      mqPeps.map(mqp => mqp.peptideInstance.get.id -> mqp).toMap
    }

    this.getQuantResultSummariesMQProteinSets(quantRsmIds, protSetById, mqPepByPepInstId)
  }
  
  def getQuantResultSummariesMQProteinSets(
    quantRsmIds: Seq[Long],
    protSetById: Map[Long,ProteinSet],
    mqPepByPepInstId: Map[Long,MasterQuantPeptide]
  ): Array[MasterQuantProteinSet] = {
    if( quantRsmIds.isEmpty ) return Array()
    
    DoJDBCReturningWork.withEzDBC(msiDbCtx, { msiEzDBC =>
    
      this.buildMasterQuantProteinSets(
        this.selectQuantResultSummariesMasterQuantProteinSets(msiEzDBC, quantRsmIds),
        protSetById,
        mqPepByPepInstId
      )
      
    })
    
  }
  
  def selectMasterQuantProteinSets(
    msiEzDBC: EasyDBC,
    mqProtSetIds: Seq[Long]
  ): (IValueContainer => MasterQuantProteinSet) => Seq[MasterQuantProteinSet] = {
    
    val mqProtSetCompQueryBuilder = new SelectQueryBuilder3(MsiDbProteinSetTable,MQComponentTable,ObjectTreeTable)
    val mqProtSetCompQuery = mqProtSetCompQueryBuilder.mkSelectQuery(
      (t1,c1,t2,c2,t3,c3) => List(t1.ID,t2.SELECTION_LEVEL,t2.SERIALIZED_PROPERTIES,t3.CLOB_DATA) -> 
      " WHERE "~ t1.ID ~" IN ("~ mqProtSetIds.mkString(",") ~")" ~
      " AND "~ t1.MASTER_QUANT_COMPONENT_ID ~" = "~ t2.ID ~
      " AND "~ t2.OBJECT_TREE_ID ~" = "~ t3.ID
      //" AND "~ t3.SCHEMA_NAME ~" = '"~ quantProteinSetSchema ~"'"
    )
       
    msiEzDBC.select(mqProtSetCompQuery)
  }
  
  def selectQuantResultSummariesMasterQuantProteinSets(
    msiEzDBC: EasyDBC,
    quantRsmIds: Seq[Long]
  ): (IValueContainer => MasterQuantProteinSet) => Seq[MasterQuantProteinSet] = {
    
    val mqProtSetCompQueryBuilder = new SelectQueryBuilder3(MsiDbProteinSetTable,MQComponentTable,ObjectTreeTable)
    val mqProtSetCompQuery = mqProtSetCompQueryBuilder.mkSelectQuery(
      (t1,c1,t2,c2,t3,c3) => List(t1.ID,t2.SELECTION_LEVEL,t2.SERIALIZED_PROPERTIES,t3.CLOB_DATA) -> 
      " WHERE "~ t2.RESULT_SUMMARY_ID ~" IN ("~ quantRsmIds.mkString(",") ~")" ~
      " AND "~ t1.MASTER_QUANT_COMPONENT_ID ~" = "~ t2.ID ~
      " AND "~ t2.OBJECT_TREE_ID ~" = "~ t3.ID
      //" AND "~ t3.SCHEMA_NAME ~" = '"~ quantProteinSetSchema ~"'"
    )
    
    msiEzDBC.select(mqProtSetCompQuery)
  }
  
  def buildMasterQuantProteinSets(
    eachMQProtSetRecord: (IValueContainer => MasterQuantProteinSet) => Seq[MasterQuantProteinSet],
    protSetById: Map[Long,ProteinSet],
    mqPepByPepInstId: Map[Long,MasterQuantPeptide] = Map()
  ): Array[MasterQuantProteinSet] = {
    
    eachMQProtSetRecord { r =>
      val mqProtSetId = r.getLong(MQCompCols.ID)
      val protSet = protSetById(mqProtSetId)
      val quantProtSets = ProfiJson.deserialize[Array[QuantProteinSet]](r.getString(ObjectTreeCols.CLOB_DATA))
      val quantProtSetMap = Map() ++ (for( qps <- quantProtSets if qps != null ) yield qps.quantChannelId -> qps)

      val mqPeptides = if( mqPepByPepInstId.isEmpty ) Array.empty[MasterQuantPeptide]
      else { // Retrieve master quant peptides corresponding to this protein set
        //val mqPeptides = for( pepInst <- protSet.peptideSet.getPeptideInstances if mqPepByPepInstId.contains(pi.id) ) yield
        protSet.peptideSet.getPeptideInstances
          .withFilter(pi => mqPepByPepInstId.contains(pi.id) )
          .map( pi => mqPepByPepInstId(pi.id) )
      }
      
      new MasterQuantProteinSet(
         proteinSet = protSet,
         quantProteinSetMap = quantProtSetMap, // QuantProteinSet by quant channel id
         masterQuantPeptides = mqPeptides,
         selectionLevel = protSet.selectionLevel,
         properties = r.getStringOption(MQCompCols.SERIALIZED_PROPERTIES).map(ProfiJson.deserialize[MasterQuantProteinSetProperties](_))
       )
    }.toArray
    
  }

}