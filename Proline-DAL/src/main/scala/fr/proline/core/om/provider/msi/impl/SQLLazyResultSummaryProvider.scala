package fr.proline.core.om.provider.msi.impl

import fr.profi.util.collection._
import fr.profi.util.misc.MapIfNotNull
import fr.profi.util.serialization.ProfiJson
import fr.proline.context._
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.helper.MsiDbHelper
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.msi.MsiDbResultSummaryTable
import fr.proline.core.om.model.msi._

class SQLLazyResultSummaryProvider(
  val msiDbCtx: MsiDbConnectionContext,
  val psDbCtx: DatabaseConnectionContext,
  val udsDbCtx: UdsDbConnectionContext = null
) extends SQLLazyResultSetLoader {

  // Instantiate a MSIdb helper
  val msiDbHelper = new MsiDbHelper(msiDbCtx)
  
  // Instantiate some providers
  val pepMatchProvider = new SQLPeptideMatchProvider(msiDbCtx, psDbCtx)
  val protMatchProvider = new SQLProteinMatchProvider(msiDbCtx)
  val pepInstProvider = new SQLPeptideInstanceProvider(msiDbCtx,psDbCtx)

  val RSMCols = MsiDbResultSummaryTable.columns
  
  def getLazyResultSummary(
    rsmId: Long,
    loadFullResultSet: Boolean = false,
    linkPeptideSets: Boolean = false,
    linkResultSetEntities: Boolean = false
  ): Option[LazyResultSummary] = {
    this.getLazyResultSummaries(Seq(rsmId),loadFullResultSet,linkPeptideSets,linkResultSetEntities).headOption
  }
  
  def getLazyResultSummaries(
    rsmIds: Seq[Long],
    loadFullResultSet: Boolean = false,
    linkPeptideSets: Boolean = false,
    linkResultSetEntities: Boolean = false
  ): Array[LazyResultSummary] = {
    if (rsmIds.isEmpty) return Array()
    
    // Load result summary descriptors
    val rsmDescriptors = this.getResultSummaryDescriptors(rsmIds)    
    
    // Init some values
    val rsIds = rsmDescriptors.map(_.resultSetId)
    val decoyRsmIds = rsmDescriptors.map( _.decoyResultSummaryId ).filter( _ > 0 )
    
    // Load lazy result sets
    val lazyResultSets = if (loadFullResultSet) {
      val lazyRsProvider = new SQLLazyResultSetProvider(msiDbCtx, psDbCtx, udsDbCtx)
      lazyRsProvider.getLazyResultSets(rsIds)
    } else {
      this.getResultSummariesLazyResultSets(rsmIds, decoyRsmIds, rsIds)
    }
    
    // Load lazy decoy result summaries
    val lazyDecoyRsmLoader = { () =>
      if(decoyRsmIds.isEmpty) Map.empty[Long,LazyResultSummary]
      else {
        logger.info(s"Lazy loading decoy ${decoyRsmIds.length} result summaries...")
        
        val decoyRsmDescriptors = this.getResultSummaryDescriptors(decoyRsmIds) 
        val lazyDecoyResultSets = lazyResultSets.map(_.lazyDecoyResultSet.get)
        
        val lazyDecoyRsms = this._getLazyResultSummaries(
          decoyRsmDescriptors,
          lazyDecoyResultSets,
          () => Map.empty[Long,LazyResultSummary],
          linkPeptideSets = linkPeptideSets,
          linkResultSetEntities = linkResultSetEntities
        )
        
        lazyDecoyRsms.view.map( rsm => rsm.id -> rsm ).toMap
      }
    }
    
    this._getLazyResultSummaries(
      rsmDescriptors,
      lazyResultSets,
      lazyDecoyRsmLoader,
      linkPeptideSets,
      linkResultSetEntities
    )
  }

  private def _getLazyResultSummaries(
    rsmDescriptors: Array[ResultSummaryDescriptor],
    lazyResultSets: Array[LazyResultSet],
    lazyDecoyRsmLoader: () => Map[Long, LazyResultSummary],
    linkPeptideSets: Boolean,
    linkResultSetEntities: Boolean    
  ): Array[LazyResultSummary] = {
    
    val rsmIds = rsmDescriptors.map(_.id)
    val lazyRsById = lazyResultSets.view.map(lazyRs => lazyRs.id -> lazyRs).toMap
    
    // Lazy loading of lazy decoy RSM
    lazy val lazyDecoyRsmById = lazyDecoyRsmLoader()

    // Lazy loading of peptide instances
    lazy val peptideInstances = {
      logger.info(s"Lazy loading peptide instances of ${rsmIds.length} result summaries...")
      pepInstProvider.getResultSummariesPeptideInstances(rsmIds)
    }
    lazy val pepInstancesByRsmId = peptideInstances.groupBy(_.resultSummaryId)
    lazy val inMemPepInstProvider = new InMemoryPeptideInstanceProvider(peptideInstances)
    
    // Lazy loading of peptide sets
    lazy val peptideSets = {
      val pepSetProvider = new SQLPeptideSetProvider(msiDbCtx, inMemPepInstProvider)
      logger.info(s"Lazy loading peptide sets of ${rsmIds.length} result summaries...")
      pepSetProvider.getResultSummariesPeptideSets(rsmIds)
    }
    lazy val peptideSetsByRsmId = peptideSets.groupBy(_.resultSummaryId)
    lazy val inMemPepSetProvider = new InMemoryPeptideSetProvider(peptideSets)

    // Lazy loading of protein sets
    lazy val proteinSets = {
      val protSetProvider = new SQLProteinSetProvider(msiDbCtx, inMemPepSetProvider)
      logger.info(s"Lazy loading protein sets of ${rsmIds.length} result summaries...")
      protSetProvider.getResultSummariesProteinSets(rsmIds)
    }
    lazy val proteinSetsByRsmId = proteinSets.groupBy(_.resultSummaryId)
    
    val rsms = for( rsmDescriptor <- rsmDescriptors ) yield {
      
      val loadDecoyRsmOpt = if( rsmDescriptor.decoyResultSummaryId == 0 ) None
      else {
        val loader = { rsd: ResultSummaryDescriptor =>        
          lazyDecoyRsmById(rsd.decoyResultSummaryId)
        }
        Some(loader)
      }
      
      new LazyResultSummary(
        descriptor = rsmDescriptor,
        lazyResultSet = lazyRsById(rsmDescriptor.resultSetId),
        linkPeptideSets = linkPeptideSets,
        linkResultSetEntities = linkResultSetEntities,
        loadPeptideInstances = { rsmd => pepInstancesByRsmId(rsmd.id) },
        loadPeptideSets = { rsmd => peptideSetsByRsmId(rsmd.id) },
        loadProteinSets = { rsmd => proteinSetsByRsmId(rsmd.id) },
        loadLazyDecoyResultSummary = loadDecoyRsmOpt
        //loadPeptideValidationRocCurve = null,
        //loadProteinValidationRocCurve = null
      )
    }
    
    rsms.toArray
  }
  
  protected def getResultSummaryDescriptors(
    rsmIds: Seq[Long]
  ): Array[ResultSummaryDescriptor] = {
    
    // Execute SQL query to load result summaries
    /*val rsmQuery = new SelectQueryBuilder2(MsiDbResultSummaryTable,MsiDbResultSetTable).mkSelectQuery( (t1,c1,t2,c2) =>
      List(t1.*,t2.TYPE) -> "WHERE "~ t1.ID ~" IN("~ rsmIds.mkString(",") ~") AND "~ t1.RESULT_SET_ID ~"="~ t2.ID
    )*/
    val rsmQuery = new SelectQueryBuilder1(MsiDbResultSummaryTable).mkSelectQuery( (t1,c1) =>
      List(t1.*) -> "WHERE "~ t1.ID ~" IN ("~ rsmIds.mkString(",") ~")"
    )

    val rsmDescById = DoJDBCReturningWork.withEzDBC(msiDbCtx) { msiEzDBC =>
    
      msiEzDBC.select(rsmQuery) { r =>
        
        // Decode JSON properties
        val propertiesAsJSON = r.getString(RSMCols.SERIALIZED_PROPERTIES)
        val properties = MapIfNotNull(propertiesAsJSON) { ProfiJson.deserialize[ResultSummaryProperties](_) }
        
        ResultSummaryDescriptor(
          id = r.getLong(RSMCols.ID),
          description = r.getString(RSMCols.DESCRIPTION),
          modificationTimestamp = r.getDate(RSMCols.MODIFICATION_TIMESTAMP),
          isQuantified = r.getBooleanOrElse(RSMCols.IS_QUANTIFIED,false),
          decoyResultSummaryId = r.getLongOrElse(RSMCols.DECOY_RESULT_SUMMARY_ID, 0L),
          resultSetId = r.getLong(RSMCols.RESULT_SET_ID),
          properties = properties
        )
      }
      
    } mapByLong(_.id)
    
    rsmIds.toArray.withFilter(rsmDescById.contains(_)).map( rsmDescById(_) )
  }
  
  /**
   * Load result sets validated entities (corresponding to the result summaries).
   */
  protected def getResultSummariesLazyResultSets(
    rsmIds: Seq[Long],
    decoyRsmIds: Seq[Long],
    rsIds: Seq[Long]
  ): Array[LazyResultSet] = {
    
    val rsDescriptors = this.getResultSetDescriptors(rsIds)
    val decoyRsIds = rsDescriptors.map(_.decoyResultSetId).filter( _ > 0 )
    
    val lazyDecoyRsLoader = { () =>
      if(decoyRsIds.isEmpty) Map.empty[Long,LazyResultSet]
      else {
        val decoyResultSets = this.getResultSummariesLazyResultSets(decoyRsmIds, Seq(), decoyRsIds)        
        decoyResultSets.view.map( rs => rs.id -> rs ).toMap
      }
    }
    
    lazy val pepMatchesByRsId = {
      logger.info(s"Lazy loading peptide matches of ${rsIds.length} result set(s)...")
      pepMatchProvider.getResultSummariesPeptideMatches(rsmIds).groupBy(_.resultSetId)
    }
    lazy val protMatchesByRsId = {
      logger.info(s"Lazy loading protein matches of ${rsIds.length} result set(s)...")
      protMatchProvider.getResultSummariesProteinMatches(rsmIds).groupBy(_.resultSetId)
    }
    
    val loadPeptideMatches = { rsd: ResultSetDescriptor => pepMatchesByRsId(rsd.id) }        
    val loadProteinMatches = { rsd: ResultSetDescriptor => protMatchesByRsId(rsd.id) }

    // Note: we set isValidatedContent to false here because we may have loaded matches belonging non-validated protein sets
    this.getLazyResultSets(rsDescriptors, false, loadPeptideMatches, loadProteinMatches, lazyDecoyRsLoader)
  }

}