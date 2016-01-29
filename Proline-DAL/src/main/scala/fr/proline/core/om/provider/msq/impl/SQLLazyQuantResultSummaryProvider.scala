package fr.proline.core.om.provider.msq.impl

import fr.profi.util.collection._
import fr.profi.util.primitives._
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.msi.MsiDbMasterQuantComponentTable
import fr.proline.core.dal.tables.msi.MsiDbObjectTreeTable
import fr.proline.core.om.model.msq._
import fr.proline.core.om.provider.msi.impl.SQLLazyResultSummaryProvider
import fr.proline.core.om.provider.msq.IQuantResultSummaryProvider

class SQLLazyQuantResultSummaryProvider(
  override val msiDbCtx: DatabaseConnectionContext,
  override val psDbCtx: DatabaseConnectionContext,
  override val udsDbCtx: DatabaseConnectionContext
) extends SQLLazyResultSummaryProvider(msiDbCtx,psDbCtx,udsDbCtx) {  
  
  protected val mqProtSetProvider = new SQLMasterQuantProteinSetProvider(msiDbCtx, psDbCtx)
  protected val mqPepProvider = new SQLMasterQuantPeptideProvider(msiDbCtx, psDbCtx)
  protected val mqPepIonProvider = new SQLMasterQuantPeptideIonProvider(msiDbCtx)
  
  val MQComponentTable = MsiDbMasterQuantComponentTable
  val MQCompCols = MQComponentTable.columns
  val ObjectTreeTable = MsiDbObjectTreeTable
  val ObjectTreeCols = ObjectTreeTable.columns
  
  // TODO: add a get MasterQuantRsmDescriptor
  
  // TODO: find a way to handle master quant reporter ions
  def getLazyQuantResultSummaries(
    quantitationId: Long,
    quantRsmIds: Seq[Long], // TODO: remove me and add a masterQuantChannelId filter ???
    loadFullResultSet: Boolean = false,
    linkPeptideSets: Boolean = false,
    linkResultSetEntities: Boolean = false
  ): Array[LazyQuantResultSummary] = {
    if( quantRsmIds.isEmpty ) return Array()
    
    val lazyRsms = this.getLazyResultSummaries(quantRsmIds, loadFullResultSet, linkPeptideSets, linkResultSetEntities)
    
    // Lazy loading of master quant peptide ions
    lazy val mqPepIons = mqPepIonProvider.getQuantResultSummariesMQPeptideIons(quantRsmIds)
    lazy val mqPepIonsByRsmId = mqPepIons.groupByLong( _.resultSummaryId )
    
    // Lazy loading of master quant peptides
    lazy val mqPeps = {
      
      // Group master quant peptides ions by the masterQuantPeptideId
      val mqPepIonsByMQPepId = mqPepIons.groupBy(_.masterQuantPeptideId)
      
      val pepInstByMQPepId = ( for( 
        rsm <- lazyRsms; 
        pepInst <- rsm.peptideInstances;
        if pepInst.masterQuantComponentId > 0
      ) yield pepInst.masterQuantComponentId -> pepInst ).toMap
      
      mqPepProvider.getQuantResultSummariesMQPeptides( quantRsmIds, pepInstByMQPepId, mqPepIonsByMQPepId )
    }
    lazy val mqPepsByRsmId = mqPeps.groupByLong( _.resultSummaryId )
 
    // Lazy loading of master quant protein sets
    lazy val mqProtSetsByRsmId = {
      val protSetById = Map() ++ lazyRsms.flatMap( _.proteinSets.map( ps => ps.id -> ps ) )
      val mqPepByPepInstId = Map() ++ mqPeps.withFilter(_.peptideInstance.isDefined).map(mqp => mqp.peptideInstance.get.id -> mqp)
      val mqProtSets = mqProtSetProvider.getQuantResultSummariesMQProteinSets(quantRsmIds, protSetById, mqPepByPepInstId)
    
      mqProtSets.groupByLong( _.proteinSet.resultSummaryId )
    }
    
    lazyRsms.map { lazyRsm =>
      
      new LazyQuantResultSummary(
        lazyResultSummary = lazyRsm,
        loadMasterQuantProteinSets = { lrsm => mqProtSetsByRsmId(lrsm.id) },
        loadMasterQuantPeptides = { lrsm => mqPepsByRsmId(lrsm.id) },
        loadMasterQuantPeptideIons = { lrsm => mqPepIonsByRsmId.get(lrsm.id).getOrElse(Array()) }
      )
    }

  }

}