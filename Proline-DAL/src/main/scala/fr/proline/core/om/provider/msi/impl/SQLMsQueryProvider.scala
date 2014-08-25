package fr.proline.core.om.provider.msi.impl

import scala.collection.mutable.ArrayBuffer

import fr.profi.jdbc.easy._
import fr.profi.util.primitives._
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.msi._
import fr.proline.core.om.builder.MsQueryBuilder
import fr.proline.core.om.model.msi._
import fr.proline.core.om.provider.msi.IMsQueryProvider
import fr.proline.repository.ProlineDatabaseType

class SQLMsQueryProvider(val msiDbCtx: DatabaseConnectionContext) extends IMsQueryProvider {
  
  require( msiDbCtx.getProlineDatabaseType == ProlineDatabaseType.MSI, "MsiDb connection required")
  
  val MsQueryCols = MsiDbMsQueryTable.columns
  
  def getUnassignedMsQueries( resultSetIds: Seq[Long], msiSearchIds: Seq[Long] ): Array[MsQuery] = {
    if( msiSearchIds == null || msiSearchIds.length == 0 ) return Array.empty[MsQuery]
    
    /*
     * this method searches in table ms_query for tuples NOT related to a peptide_match
     * there is two approaches to get these tuples:
     * 1) "NOT_IN" request : SELECT * FROM ms_query WHERE id NOT IN (SELECT ms_query_id FROM peptide_match WHERE ...);
     * 2) "LEFT_JOIN" request : SELECT * FROM ms_query m LEFT JOIN peptide_match p ON p.ms_query_id=m.id WHERE p.ms_query_id IS NULL AND m.msi_search_id=?;
     * 
     * The NOT_IN request is slower than LEFT_JOIN. The mean time on a 10MB Mascot sample file : 395ms against 299ms for the whole method and on the first run(it takes less longer after)
     * The NOT_IN request can be run using the SelectQueryBuilder
     * The LEFT_JOIN request is more efficient, because it gets everything in only request. It constructs a joint table of ms_query and peptide_match for all the rows in both tables. If there is
     * no peptide_matches for a ms_query, the joint table will contain NULL values. The request filters the joint table to keep only rows with NULL values on peptide matches, so that we get all 
     * the unassigned ms_query (because they do not have any related peptide match)
     * 
     * The NOT_IN request will be used for the moment, as it should not impact the performances. If it occurs that it takes too long it should be replaced with the LEFT_JOIN request
     * 
     */
    
    DoJDBCReturningWork.withEzDBC(msiDbCtx, { msiEzDBC =>
      
      // first solution with NOT_IN request
      val msQueryIdsSql = new SelectQueryBuilder1(MsiDbPeptideMatchTable).mkSelectQuery( (t,c) =>
        List(t.MS_QUERY_ID) -> "WHERE "~ t.RESULT_SET_ID ~" IN ("~ resultSetIds.mkString(",") ~")"
      )
      val unmatchedMsQueryIdsSql = new SelectQueryBuilder1(MsiDbMsQueryTable).mkSelectQuery( (t,c) =>
        List(t.ID) -> "WHERE "~ t.MSI_SEARCH_ID ~" = "~ 1 ~" AND "~ t.ID ~ s" NOT IN (${msQueryIdsSql})"
      )
      
      // second solution: using LEFT_JOIN request 
      // val unmatchedMsQueryIdsSql = "SELECT ms_query.id FROM ms_query LEFT JOIN peptide_match ON ms_query.id = peptide_match.ms_query_id WHERE peptide_match.ms_query_id IS NULL AND ms_query.msi_search_id IN (" + msiSearchIds.mkString(",") + ")";
      
      // use the dedicated method to get full MsQuery objects
      val msq = msiEzDBC.selectLongs(unmatchedMsQueryIdsSql)
      getMsQueries(msq)

    }, false)

  }

  def getMsiSearchesMsQueries(msiSearchIds: Seq[Long]): Array[MsQuery] = {
    if( msiSearchIds == null || msiSearchIds.length == 0 ) return Array.empty[MsQuery]
    
    DoJDBCReturningWork.withEzDBC(msiDbCtx, { msiEzDBC =>
      
      val msiSearchIdsAsStr = msiSearchIds.mkString(",")
      val msqQuery = new SelectQueryBuilder1(MsiDbMsQueryTable).mkSelectQuery( (t,c) =>
        List(t.*) -> "WHERE "~ t.MSI_SEARCH_ID ~" IN("~ msiSearchIdsAsStr ~")"
      )
      val pklIdQuery = new SelectQueryBuilder1(MsiDbMsiSearchTable).mkSelectQuery( (t,c) =>
        List(t.PEAKLIST_ID) -> "WHERE "~ t.ID ~" IN("~ msiSearchIdsAsStr ~")"
      )
      val childPklIdQuery = new SelectQueryBuilder1(MsiDbPeaklistRelationTable).mkSelectQuery( (t,c) =>
        List(t.CHILD_PEAKLIST_ID) -> "WHERE "~ t.PARENT_PEAKLIST_ID ~" = ?"
      )
  
      // Retrieve parent peaklist ids corresponding to the provided MSI search ids
      val parentPeaklistIds = msiEzDBC.selectLongs(pklIdQuery)
      
      // Retrieve child peaklist ids if they exist
      val pklIds = new ArrayBuffer[Long]
      for (parentPeaklistId <- parentPeaklistIds) {
  
        // Retrieve child peaklist ids corresponding to the current peaklist id
        val childPeaklistIds = msiEzDBC.select(childPklIdQuery, parentPeaklistId) { v => toLong(v.nextAny) }
        
        if (childPeaklistIds.length > 0) { pklIds ++= childPeaklistIds }
        else { pklIds += parentPeaklistId }
      }
      
      val specTitleQuery = new SelectQueryBuilder1(MsiDbSpectrumTable).mkSelectQuery( (t,c) =>
        List(t.ID,t.TITLE) -> "WHERE "~ t.PEAKLIST_ID ~" IN("~ pklIds.mkString(",") ~")"
      )
  
      // Retrieve parent peaklist ids corresponding to the provided MSI search ids
      val spectrumTitleById = msiEzDBC.select(specTitleQuery) { r => (toLong(r.nextAny), r.nextString) } toMap
  
      // Load MS queries corresponding to the provided MSI search ids
      val msQueries = msiEzDBC.select(msqQuery) { r =>
        
        val spectrumId = r.getLong(MsQueryCols.SPECTRUM_ID)
  
        // Build the MS query object
        val msQuery = if (spectrumId != 0) { // we can assume it is a MS2 query
          val spectrumTitle = spectrumTitleById(spectrumId)
          MsQueryBuilder.buildMs2Query(r, spectrumTitleById)  
        } else {
          MsQueryBuilder.buildMs1Query(r)
        }
  
        msQuery
      }
  
      msQueries.toArray
    }, false)    

  }

  def getMsQueries( msQueryIds: Seq[Long] ): Array[MsQuery] = {
    
    DoJDBCReturningWork.withEzDBC(msiDbCtx, { msiEzDBC =>

      val msQueriesIdsAsStr = msQueryIds.mkString(",")
      val msqQuery = new SelectQueryBuilder1(MsiDbMsQueryTable).mkSelectQuery((t, c) =>
        List(t.*) -> "WHERE " ~ t.ID ~ " IN(" ~ msQueriesIdsAsStr ~ ")"
      )

      val spectrumIdsQuery = new SelectQueryBuilder1(MsiDbMsQueryTable).mkSelectQuery((t, c) =>
        List(t.SPECTRUM_ID) -> "WHERE " ~ t.ID ~ " IN(" ~ msQueriesIdsAsStr ~ ")"
      )
		
      // Retrieve spectrum ids corresponding to the provided MSI search ids
      val spectrumIds = msiEzDBC.selectLongs(spectrumIdsQuery)
      
      val specTitleQuery = new SelectQueryBuilder1(MsiDbSpectrumTable).mkSelectQuery( (t,c) =>
        List(t.ID,t.TITLE) -> "WHERE "~ t.ID ~" IN("~ spectrumIds.mkString(",") ~")"
      )
      
      // Retrieve parent peaklist ids corresponding to the provided MSI search ids
      val spectrumTitleById = msiEzDBC.select(specTitleQuery) { r => (toLong(r.nextAny), r.nextString) } toMap
      
      // Load MS queries corresponding to the provided MSI search ids
      val msQueries = msiEzDBC.select(msqQuery) { r =>
        
        val spectrumId = r.getLong(MsQueryCols.SPECTRUM_ID)
        
        // Build the MS query object
        val msQuery = if (spectrumId != 0) { // we can assume it is a MS2 query
          val spectrumTitle = spectrumTitleById(spectrumId)
          MsQueryBuilder.buildMs2Query(r, spectrumTitleById)  
        } else {
          MsQueryBuilder.buildMs1Query(r)
        }
  
        msQuery
      }
  
      msQueries.toArray
               
    }, false) 
     
  }
}

