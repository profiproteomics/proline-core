package fr.proline.core.om.provider.msq.impl

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap

import fr.profi.jdbc.easy.EasyDBC
import fr.profi.util.serialization.ProfiJson
import fr.proline.context._
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
import fr.profi.util.primitives._

class SQLQuantResultSummaryProvider(
  override val msiDbCtx: MsiDbConnectionContext,
  override val psDbCtx: DatabaseConnectionContext,
  override val udsDbCtx: UdsDbConnectionContext
) extends SQLResultSummaryProvider(msiDbCtx,psDbCtx,udsDbCtx) with IQuantResultSummaryProvider {  
  
  protected val mqProtSetProvider = new SQLMasterQuantProteinSetProvider(msiDbCtx, psDbCtx)
  protected val mqPepProvider = new SQLMasterQuantPeptideProvider(msiDbCtx, psDbCtx)
  protected val mqPepIonProvider = new SQLMasterQuantPeptideIonProvider(msiDbCtx)
  
  val MQComponentTable = MsiDbMasterQuantComponentTable
  val MQCompCols = MQComponentTable.columns
  val ObjectTreeTable = MsiDbObjectTreeTable
  val ObjectTreeCols = ObjectTreeTable.columns
  
  /*def getQuantResultSummariesAsOptions( quantRsmIds: Seq[Long], quantChannelIds: Seq[Long], loadResultSet: Boolean ): Array[Option[QuantResultSummary]] = {
    if( quantRsmIds.isEmpty ) return Array()
    
    val rsms = this.getQuantResultSummaries(quantRsmIds, quantChannelIds, loadResultSet)
    val rsmById = rsms.map { rsm => rsm.id -> rsm } toMap;
    quantRsmIds.map { rsmById.get(_) } toArray
  }*/
  
  // TODO: find a way to handle master quant reporter ions
  def getQuantResultSummaries( quantRsmIds: Seq[Long], quantChannelIds: Seq[Long], loadResultSet: Boolean ): Array[QuantResultSummary] = {
    if( quantRsmIds.isEmpty ) return Array()
    
    val rsms = this.getResultSummaries(quantRsmIds, loadResultSet)
    
    val pepInstByMQPepId = ( for( 
      rsm <- rsms; 
      pepInst <- rsm.peptideInstances;
      if pepInst.masterQuantComponentId > 0
    ) yield pepInst.masterQuantComponentId -> pepInst ).toMap
    
    // Load master quant peptide ions
    val mqPepIons = mqPepIonProvider.getQuantResultSummariesMQPeptideIons(quantRsmIds)
    
    // Group master quant peptides ions by the masterQuantPeptideId
    val mqPepIonsByMQPepId = mqPepIons.groupBy(_.masterQuantPeptideId)
    
    // Load master quant peptides
    val mqPeps = mqPepProvider.getQuantResultSummariesMQPeptides( quantRsmIds, pepInstByMQPepId, mqPepIonsByMQPepId )
    
    // Load master quant protein sets
    val protSetById = Map() ++ rsms.flatMap( _.proteinSets.map( ps => ps.id -> ps ) )
    val mqPepByPepInstId = Map() ++ mqPeps.withFilter(_.peptideInstance.isDefined).map(mqp => mqp.peptideInstance.get.id -> mqp)
    val mqProtSets = mqProtSetProvider.getQuantResultSummariesMQProteinSets(quantRsmIds, protSetById, mqPepByPepInstId)
    
    // Group master quant components by RSM id
    val mqPepIonsByRsmId = mqPepIons.groupBy( _.resultSummaryId )
    val mqPepsByRsmId = mqPeps.groupBy( _.resultSummaryId )
    val mqProtSetsByRsmId = mqProtSets.groupBy( _.proteinSet.resultSummaryId )

    rsms.map { rsm =>
      
      // Retrieve master quant peptides if they are defined
      val mqPeps = mqPepsByRsmId.getOrElse(rsm.id, Array.empty[MasterQuantPeptide])
      
      // Retrieve master quant peptide ions if they are defined
      val mqPepIons = mqPepIonsByRsmId.getOrElse(rsm.id, Array.empty[MasterQuantPeptideIon])
      
      new QuantResultSummary(
        quantChannelIds = quantChannelIds.toArray,
        masterQuantProteinSets = mqProtSetsByRsmId(rsm.id),
        masterQuantPeptides = mqPeps,
        masterQuantPeptideIons = mqPepIons,        
        resultSummary = rsm
      )
    }

  }
  
}