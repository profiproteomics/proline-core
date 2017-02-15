package fr.proline.core.om.provider.msi.impl

import com.typesafe.scalalogging.LazyLogging
import fr.proline.context._
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.msi.MsiDbResultSetTable
import fr.proline.core.om.builder.ResultSetDescriptorBuilder
import fr.proline.core.om.model.msi._
import fr.proline.core.om.provider.msi._

// TODO: replace SQLResultSetLoader by the SQLLazyResultSetLoader
trait SQLLazyResultSetLoader extends LazyLogging {

  import fr.proline.core.dal.helper.MsiDbHelper

  val msiDbCtx: MsiDbConnectionContext
  val psDbCtx: DatabaseConnectionContext
  val udsDbCtx: UdsDbConnectionContext

  val RSCols = MsiDbResultSetTable.columns

  protected def getLazyResultSet(
    rsDescriptor: ResultSetDescriptor,
    isValidatedContent: Boolean,
    loadPeptideMatches: (ResultSetDescriptor) => Array[PeptideMatch],
    loadProteinMatches: (ResultSetDescriptor) => Array[ProteinMatch],
    lazyDecoyRsLoader: () => Map[Long, LazyResultSet]
  ): LazyResultSet = {
    this.getLazyResultSets(Array(rsDescriptor), isValidatedContent, loadPeptideMatches, loadProteinMatches, lazyDecoyRsLoader)(0)
  }

  protected def getLazyResultSets(
    rsDescriptors: Array[ResultSetDescriptor],
    isValidatedContent: Boolean,
    loadPeptideMatches: (ResultSetDescriptor) => Array[PeptideMatch],
    loadProteinMatches: (ResultSetDescriptor) => Array[ProteinMatch],
    lazyDecoyRsLoader: () => Map[Long, LazyResultSet]
  ): Array[LazyResultSet] = {
    
    val rsIds = rsDescriptors.map(_.id)
    val rsDescriptorById = rsDescriptors.view.map(rsd => rsd.id -> rsd)
    
    // Lazy loading of lazy decoy RS
    lazy val lazyDecoyRsById = lazyDecoyRsLoader()
    
    // Instantiate a MSIdb helper to load some data related to MSI searches
    val msiDbHelper = new MsiDbHelper(msiDbCtx)
    val parentMsiSearchIds = rsDescriptors.map( _.msiSearchId ).filter( _ > 0 ).distinct
    val parentMsiSearchIdSet = parentMsiSearchIds.toSet
    val mergedRsIds = rsDescriptors.withFilter(!_.isSearchResult).map( _.id )
    val msiSearchIdsByParentRsId = msiDbHelper.getMsiSearchIdsByParentResultSetId(mergedRsIds)
    // TODO: do we really need to do this filtering ???
    val childMsiSearchIdsByParentRsId = msiSearchIdsByParentRsId.map { case (parentRsId, msiSearchIds) =>
      parentRsId -> msiSearchIds.filterNot( parentMsiSearchIdSet.contains(_) )
    }
    val childMsiSearchIds = childMsiSearchIdsByParentRsId.flatMap(_._2).toArray.distinct

    lazy val parentMsiSearchById = if (!parentMsiSearchIds.isEmpty) {    
      logger.info(s"Lazy loading ${parentMsiSearchIds.length} parent MSI search(es)...")
      
      new SQLMsiSearchProvider(udsDbCtx, msiDbCtx, psDbCtx)
        .getMSISearches(parentMsiSearchIds)      
        .view.map(ms => ms.id -> ms).toMap      
    } else Map.empty[Long, fr.proline.core.om.model.msi.MSISearch]
    
    lazy val childMsiSearchById = if (!childMsiSearchIds.isEmpty) {
      logger.info(s"Lazy loading ${childMsiSearchIds.length} child MSI search(es)...")
      new SQLMsiSearchProvider(udsDbCtx, msiDbCtx, psDbCtx)
        .getMSISearches(childMsiSearchIds)      
        .view.map(ms => ms.id -> ms).toMap      
    } else Map.empty[Long, fr.proline.core.om.model.msi.MSISearch]

    val resultSets = for(rsDescriptor <- rsDescriptors) yield {

      val loadMsiSearchOpt = if(rsDescriptor.msiSearchId == 0) None
      else {
        val loader = { rsd: ResultSetDescriptor =>          
          parentMsiSearchById(rsd.msiSearchId)
        }
        Some(loader)
      }
      
      val loadChildMsiSearchesOpt = if (rsDescriptor.isSearchResult || !childMsiSearchIdsByParentRsId.contains(rsDescriptor.id)) None
      else {
        val loader = { rsd: ResultSetDescriptor =>
          val childMsiSearchIds = childMsiSearchIdsByParentRsId.getOrElse(rsd.id, Set() ).toArray
          childMsiSearchIds.withFilter(childMsiSearchById.contains(_)).map( childMsiSearchById(_) ).sortBy( _.jobNumber )
        }
        Some(loader)
      }
      
      val loadDecoyRsOpt = if( rsDescriptor.decoyResultSetId == 0 ) None
      else {
        val loader = { rsd: ResultSetDescriptor =>        
          lazyDecoyRsById(rsd.decoyResultSetId)
        }
        Some(loader)
      }
      
      val rs = new LazyResultSet(
        descriptor = rsDescriptor,
        isValidatedContent = isValidatedContent,
        loadPeptideMatches = loadPeptideMatches,
        loadProteinMatches = loadProteinMatches,
        loadMsiSearch = loadMsiSearchOpt,
        loadLazyDecoyResultSet = loadDecoyRsOpt,
        loadChildMsiSearches = loadChildMsiSearchesOpt
      )
      
      rs
    }

    resultSets.toArray
  }
  
  protected def getResultSetDescriptors(
    rsIds: Seq[Long]
  ): Array[ResultSetDescriptor] = {

    // Execute SQL query to load result sets
    val rsQuery = new SelectQueryBuilder1(MsiDbResultSetTable).mkSelectQuery((t, c) =>
      List(t.*) -> "WHERE "~ t.ID ~" IN ("~ rsIds.mkString(",") ~") ORDER BY "~ t.ID
    )

    DoJDBCReturningWork.withEzDBC(msiDbCtx) { msiEzDBC =>
      msiEzDBC.select(rsQuery) { record =>
        ResultSetDescriptorBuilder.buildResultSetDescriptor(record)
      }
    } toArray

  }

}

class SQLLazyResultSetProvider(
  val msiDbCtx: MsiDbConnectionContext,
  val psDbCtx: DatabaseConnectionContext,
  val udsDbCtx: UdsDbConnectionContext
) extends SQLLazyResultSetLoader { 

  def getLazyResultSets(
    rsIds: Seq[Long],
    resultSetFilter: Option[ResultSetFilter] = None
  ): Array[LazyResultSet] = {
    if (rsIds.isEmpty) return Array()
    
    val start = System.currentTimeMillis()
    logger.info(s"Start loading ${rsIds.length} lazy result set(s)")

    val pepMatchFilterOpt = resultSetFilter.map( rsf => 
      new PeptideMatchFilter(maxPrettyRank = rsf.maxPeptideMatchPrettyRank, minScore = rsf.minPeptideMatchScore)
    )

    // Lazy loading of peptide matches
    val pepMatchProvider = new SQLPeptideMatchProvider(msiDbCtx, psDbCtx)
    lazy val pepMatchesByRsId = pepMatchProvider.getResultSetsPeptideMatches(rsIds, pepMatchFilterOpt).groupBy(_.resultSetId)
    
    val loadPeptideMatches = { rsd: ResultSetDescriptor =>
      logger.info(s"Lazy loading peptide matches of ${rsIds.length} result set(s)...")
      pepMatchesByRsId(rsd.id)
    }

    // Lazy loading of protein matches
    val protMatchProvider = new SQLProteinMatchProvider(msiDbCtx)
    lazy val protMatchesByRsId = protMatchProvider.getResultSetsProteinMatches(rsIds).groupBy(_.resultSetId)

    val loadProteinMatches = { rsd: ResultSetDescriptor =>
      logger.info(s"Lazy loading protein matches of ${rsIds.length} result set(s)...")
      protMatchesByRsId(rsd.id)
    }
    
    val rsDescriptors = this.getResultSetDescriptors(rsIds)
    val decoyRsIds = rsDescriptors.map( _.decoyResultSetId ).filter( _ > 0 )
    
    val lazyDecoyRsLoader = { () =>
      if(decoyRsIds.isEmpty) Map.empty[Long,LazyResultSet]
      else {
        logger.info(s"Lazy loading decoy ${decoyRsIds.length} result set(s)...")
        
        val lazyDecoyResultSets = this.getLazyResultSets(decoyRsIds, resultSetFilter)        
        lazyDecoyResultSets.view.map( rs => rs.id -> rs ).toMap
      }
    }
    
    val resultSets = this.getLazyResultSets(rsDescriptors, false, loadPeptideMatches, loadProteinMatches, lazyDecoyRsLoader)
    
    logger.info(s"${rsIds.length} lazy result set(s) loaded in ${ (System.currentTimeMillis() - start) / 1000 } s")

    resultSets
  }

} 
