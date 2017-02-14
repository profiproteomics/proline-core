package fr.proline.core.om.provider.msi.impl

import scala.collection.mutable.ArrayBuffer
import fr.profi.jdbc.easy._
import fr.profi.util.primitives._
import fr.proline.context.MsiDbConnectionContext
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.msi._
import fr.proline.core.om.builder.MsQueryBuilder
import fr.proline.core.om.model.msi._
import fr.proline.core.om.provider.msi.IMsQueryProvider
import fr.proline.repository.ProlineDatabaseType
import fr.proline.core.dal.tables.SelectQueryBuilder2

class SQLMsQueryProvider(val msiDbCtx: MsiDbConnectionContext) extends IMsQueryProvider {
  
  require( msiDbCtx.getProlineDatabaseType == ProlineDatabaseType.MSI, "MsiDb connection required")
  
  val MsQueryCols = MsiDbMsQueryTable.columns
  
  def getUnassignedMsQueries( msiSearchIds: Seq[Long] ): Array[MsQuery] = {
    
    DoJDBCReturningWork.withEzDBC(msiDbCtx) { msiEzDBC =>

      val msQueryIdsSql = new SelectQueryBuilder2(MsiDbResultSetTable, MsiDbPeptideMatchTable).mkSelectQuery((t1,c1,t2,c2) => 
        List(t2.MS_QUERY_ID) -> "WHERE "~ t1.ID ~ " = " ~ t2.RESULT_SET_ID ~ " AND " ~ t1.MSI_SEARCH_ID ~ " IN (" ~ msiSearchIds.mkString(",") ~ ")"
      )
      // at last, get the msquery ids without a match in peptide_match
      val unmatchedMsQueryIdsSql = new SelectQueryBuilder1(MsiDbMsQueryTable).mkSelectQuery( (t,c) =>
        List(t.ID) -> "WHERE "~ t.MSI_SEARCH_ID ~" IN ("~ msiSearchIds.mkString(",") ~") AND "~ t.ID ~ s" NOT IN (${msQueryIdsSql})"
      )

      // use the dedicated method to get full MsQuery objects
      val msq = msiEzDBC.selectLongs(unmatchedMsQueryIdsSql)
      getMsQueries(msq)

    }

  }

  def getMsiSearchesMsQueries(msiSearchIds: Seq[Long]): Array[MsQuery] = {
    if( msiSearchIds == null || msiSearchIds.isEmpty ) return Array.empty[MsQuery]
    
    DoJDBCReturningWork.withEzDBC(msiDbCtx) { msiEzDBC =>
      
      val msiSearchIdsAsStr = msiSearchIds.mkString(",")
      val msqQuery = new SelectQueryBuilder1(MsiDbMsQueryTable).mkSelectQuery( (t,c) =>
        List(t.*) -> "WHERE "~ t.MSI_SEARCH_ID ~" IN ("~ msiSearchIdsAsStr ~")"
      )
      val pklIdQuery = new SelectQueryBuilder1(MsiDbMsiSearchTable).mkSelectQuery( (t,c) =>
        List(t.PEAKLIST_ID) -> "WHERE "~ t.ID ~" IN ("~ msiSearchIdsAsStr ~")"
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
        List(t.ID,t.TITLE) -> "WHERE "~ pklIds.map(id => t.PEAKLIST_ID + "=" + id).mkString(" OR ")
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
    }

  }

  def getMsQueries( msQueryIds: Seq[Long] ): Array[MsQuery] = {
    if( msQueryIds.isEmpty ) return Array()
    
    DoJDBCReturningWork.withEzDBC(msiDbCtx) { msiEzDBC =>

      val msQueriesIdsAsStr = msQueryIds.mkString(",")
      val msqQuery = new SelectQueryBuilder1(MsiDbMsQueryTable).mkSelectQuery((t, c) =>
        List(t.*) -> "WHERE " ~ t.ID ~ " IN (" ~ msQueriesIdsAsStr ~ ")"
      )

      // Retrieve spectrum ids corresponding to the provided MSI search ids
      val spectrumIdsQuery = new SelectQueryBuilder1(MsiDbMsQueryTable).mkSelectQuery((t, c) =>
        List(t.SPECTRUM_ID) -> "WHERE " ~ t.ID ~ " IN (" ~ msQueriesIdsAsStr ~ ")"
      )
      val specTitleQuery = new SelectQueryBuilder1(MsiDbSpectrumTable).mkSelectQuery( (t,c) =>
        List(t.ID,t.TITLE) -> "WHERE "~ t.ID ~" IN ("~ spectrumIdsQuery ~")"
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
               
    }
     
  }
}

