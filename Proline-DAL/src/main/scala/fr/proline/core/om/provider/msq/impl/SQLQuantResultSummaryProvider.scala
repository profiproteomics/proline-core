package fr.proline.core.om.provider.msq.impl

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import com.codahale.jerkson.Json.parse

import fr.profi.jdbc.easy.EasyDBC
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.SelectQueryBuilder2
import fr.proline.core.dal.tables.SelectQueryBuilder3
import fr.proline.core.dal.tables.msi.MsiDbMasterQuantComponentTable
import fr.proline.core.dal.tables.msi.MsiDbObjectTreeTable
import fr.proline.core.dal.tables.msi.MsiDbPeptideInstanceTable
import fr.proline.core.dal.tables.msi.MsiDbProteinSetTable
import fr.proline.core.om.model.msi.PeptideInstance
import fr.proline.core.om.model.msi.ProteinSet
import fr.proline.core.om.model.msq._
import fr.proline.core.om.provider.msi.impl.SQLResultSummaryProvider
import fr.proline.core.om.provider.msq.IQuantResultSummaryProvider
import fr.proline.util.primitives._

class SQLQuantResultSummaryProvider(
  override val msiDbCtx: DatabaseConnectionContext,
  override val psDbCtx: DatabaseConnectionContext,
  override val udsDbCtx: DatabaseConnectionContext
) extends SQLResultSummaryProvider(msiDbCtx,psDbCtx,udsDbCtx) with IQuantResultSummaryProvider {  
  
  protected val mqPepIonsProvider = new SQLMasterQuantPeptideIonProvider(msiDbCtx)
  val MQComponentTable = MsiDbMasterQuantComponentTable
  val MQCompCols = MQComponentTable.columns
  val ObjectTreeTable = MsiDbObjectTreeTable
  val ObjectTreeCols = ObjectTreeTable.columns
  
  // TODO: create an enumeration
  val LabelFreeQuantPeptidesSchema = "object_tree.label_free_quant_peptides"
  val QuantProteinSetSchema = "object_tree.quant_protein_sets"

  def getQuantResultSummariesAsOptions( quantRsmIds: Seq[Long], quantChannelIds: Seq[Long], loadResultSet: Boolean ): Array[Option[QuantResultSummary]] = {
    val rsms = this.getQuantResultSummaries(quantRsmIds, quantChannelIds, loadResultSet)
    val rsmById = rsms.map { rsm => rsm.id -> rsm } toMap;
    quantRsmIds.map { rsmById.get(_) } toArray
  }
  
  // TODO: find a way to handle master quant reporter ions
  def getQuantResultSummaries( quantRsmIds: Seq[Long], quantChannelIds: Seq[Long], loadResultSet: Boolean ): Array[QuantResultSummary] = {
    
    val rsms = this.getResultSummaries(quantRsmIds, loadResultSet)
    
    // Map peptide instances bey thier id
    val pepInstById = Map() ++ rsms.flatMap( _.peptideInstances.map( pi => pi.id -> pi ) )
    
    val pepInstByMQPepId = DoJDBCReturningWork.withEzDBC(msiDbCtx, { msiEzDBC =>
      
      // Retrieve the mapping of ids between peptide instances and master quant peptides
      val mqPepIdByPepInstIdQuery = new SelectQueryBuilder1(MsiDbPeptideInstanceTable).mkSelectQuery( (t1,c1) => 
        List(t1.ID,t1.MASTER_QUANT_COMPONENT_ID) -> 
        "WHERE "~ t1.RESULT_SUMMARY_ID ~" IN("~ quantRsmIds.mkString(",") ~")"
      )
      
      val pepInstByMqPepIdBuilder = Map.newBuilder[Long,PeptideInstance]
      msiEzDBC.selectAndProcess(mqPepIdByPepInstIdQuery) { r =>
        val pepInstId = toLong(r.nextAny)
        r.nextLongOption.foreach { mqPepId =>
          pepInstByMqPepIdBuilder += mqPepId -> pepInstById(pepInstId)
        }
      }
      
      pepInstByMqPepIdBuilder.result
    })
    
    // Load master quant peptide ions
    val mqPepIons = mqPepIonsProvider.getQuantResultSummariesMQPeptideIons(quantRsmIds)
    
    // Group master quant peptides ions by the masterQuantPeptideId
    val mqPepIonsByMQPepId = mqPepIons.groupBy(_.masterQuantPeptideId)
    
    // Load master quant peptides
    val mqPeps = this.getMasterQuantPeptides( quantRsmIds, pepInstByMQPepId,mqPepIonsByMQPepId )
    
    // Load master quant protein sets
    val protSetById = Map() ++ rsms.flatMap( _.proteinSets.map( ps => ps.id -> ps ) )
    val mqPepByPepInstId = Map() ++ mqPeps.withFilter(_.peptideInstance.isDefined).map(mqp => mqp.peptideInstance.get.id -> mqp)
    val mqProtSets = this.getMasterQuantProteinSets(quantRsmIds, protSetById, mqPepByPepInstId)
    
    // Group master quant components by RSM id
    val mqPepIonsByRsmId = mqPepIons.groupBy( _.resultSummaryId )
    val mqPepsByRsmId = mqPeps.groupBy( _.resultSummaryId )
    val mqProtSetsByRsmId = mqProtSets.groupBy( _.proteinSet.resultSummaryId )

    rsms.map { rsm =>
      
      new QuantResultSummary(
        quantChannelIds = quantChannelIds.toArray,
        masterQuantProteinSets = mqProtSetsByRsmId(rsm.id),
        masterQuantPeptides = mqPepsByRsmId(rsm.id),
        masterQuantPeptideIons = mqPepIonsByRsmId(rsm.id),
        resultSummary = rsm
      )
    }

  }
  
  protected def getMasterQuantPeptides(
    quantRsmIds: Seq[Long],
    pepInstByMQPepId: Map[Long,PeptideInstance],
    mqPepIonsByMQPepId: Map[Long,Array[MasterQuantPeptideIon]]
  ): Array[MasterQuantPeptide] = {
    
    DoJDBCReturningWork.withEzDBC(msiDbCtx, { msiEzDBC =>
    
      val mqPepCompQueryBuilder = new SelectQueryBuilder2(MQComponentTable,ObjectTreeTable)
      val mqPepCompQuery = mqPepCompQueryBuilder.mkSelectQuery(
        (t1,c1,t2,c2) => List(t1.ID,t1.SELECTION_LEVEL,t1.RESULT_SUMMARY_ID,t1.SERIALIZED_PROPERTIES,t2.CLOB_DATA) -> 
        " WHERE "~ t1.RESULT_SUMMARY_ID ~" IN("~ quantRsmIds.mkString(",") ~")" ~
        " AND "~ t1.OBJECT_TREE_ID ~" = "~ t2.ID ~
        " AND "~ t2.SCHEMA_NAME ~" = '"~ LabelFreeQuantPeptidesSchema ~"'"
      )
      
      msiEzDBC.select(mqPepCompQuery) { r =>
  
        val mqPepId: Long = toLong(r.getAny(MQCompCols.ID))
        val pepInst = pepInstByMQPepId.get(mqPepId)
        val mqPepIons = mqPepIonsByMQPepId(mqPepId)
        val quantPeptides = parse[Array[QuantPeptide]]( r.getString(ObjectTreeTable.columns.CLOB_DATA) )
        val quantPeptideMap = Map() ++ (for( qp <- quantPeptides if qp != null ) yield qp.quantChannelId -> qp)
        
        // Build the master quant peptide ion
        new MasterQuantPeptide(
          id = mqPepId,
          peptideInstance = pepInst,
          quantPeptideMap = quantPeptideMap,
          masterQuantPeptideIons = mqPepIons,
          selectionLevel = r.getInt(MQCompCols.SELECTION_LEVEL),
          resultSummaryId = toLong(r.getAny(MQCompCols.RESULT_SUMMARY_ID)),
          properties = r.getStringOption(MQCompCols.SERIALIZED_PROPERTIES).map(parse[MasterQuantPeptideProperties](_))
        )
        
      } toArray
      
    })
    
  }
  
  protected def getMasterQuantProteinSets(
    quantRsmIds: Seq[Long],
    protSetById: Map[Long,ProteinSet],
    mqPepByPepInstId: Map[Long,MasterQuantPeptide]
    ): Array[MasterQuantProteinSet] = {
    
    DoJDBCReturningWork.withEzDBC(msiDbCtx, { msiEzDBC =>
    
      val mqProtSetCompQueryBuilder = new SelectQueryBuilder3(MsiDbProteinSetTable,MQComponentTable,ObjectTreeTable)
      val mqProtSetCompQuery = mqProtSetCompQueryBuilder.mkSelectQuery(
        (t1,c1,t2,c2,t3,c3) => List(t1.ID,t2.SELECTION_LEVEL,t2.SERIALIZED_PROPERTIES,t3.CLOB_DATA) -> 
        " WHERE "~ t2.RESULT_SUMMARY_ID ~" IN("~ quantRsmIds.mkString(",") ~")" ~
        " AND "~ t1.MASTER_QUANT_COMPONENT_ID ~" = "~ t2.ID ~
        " AND "~ t2.OBJECT_TREE_ID ~" = "~ t3.ID ~
        " AND "~ t3.SCHEMA_NAME ~" = '"~ QuantProteinSetSchema ~"'"
      )
         
      msiEzDBC.select(mqProtSetCompQuery) { r =>
  
        val mqProtSetId: Long = toLong(r.getAny(MQCompCols.ID))
        val protSet = protSetById(mqProtSetId)
        val quantProtSets = parse[Array[QuantProteinSet]](r.getString(ObjectTreeCols.CLOB_DATA))
        val quantProtSetMap = Map() ++ (for( qps <- quantProtSets if qps != null ) yield qps.quantChannelId -> qps)
        
        // Retrieve master quant peptides corresponding to this protein set
        //val mqPeptides = for( pepInst <- protSet.peptideSet.getPeptideInstances if mqPepByPepInstId.contains(pi.id) ) yield
        val mqPeptides = protSet.peptideSet.getPeptideInstances
          .withFilter(pi => mqPepByPepInstId.contains(pi.id) )
          .map( pi => mqPepByPepInstId(pi.id) )
        
        new MasterQuantProteinSet(
           proteinSet = protSet,
           quantProteinSetMap = quantProtSetMap, // QuantProteinSet by quant channel id
           masterQuantPeptides = mqPeptides,
           selectionLevel = protSet.selectionLevel,
           properties = r.getStringOption(MQCompCols.SERIALIZED_PROPERTIES).map(parse[MasterQuantProteinSetProperties](_))
         )
        
      } toArray
      
    })
    
  }

}