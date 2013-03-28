package fr.proline.core.om.provider.msi.impl

import scala.collection.mutable.ArrayBuffer
import com.codahale.jerkson.Json.parse
import fr.profi.jdbc.easy._
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.msi.MsiDbMsQueryTable
import fr.proline.core.om.model.msi.Ms1Query
import fr.proline.core.om.model.msi.Ms2Query
import fr.proline.core.om.model.msi.MsQuery
import fr.proline.core.om.model.msi.MsQueryProperties
import fr.proline.core.om.provider.msi.IMsQueryProvider
import fr.proline.util.primitives._
import fr.proline.core.dal.tables.msi.MsiDbMsiSearchTable
import fr.proline.core.dal.tables.msi.MsiDbPeaklistRelationTable
import fr.proline.core.dal.tables.msi.MsiDbSpectrumTable
import fr.proline.core.dal.DoJDBCReturningWork

class SQLMsQueryProvider(val msiSqlCtx: DatabaseConnectionContext) extends IMsQueryProvider {
  
  val MsQueryCols = MsiDbMsQueryTable.columns

  def getMsiSearchesMsQueries(msiSearchIds: Seq[Int]): Array[MsQuery] = {
    
    DoJDBCReturningWork.withEzDBC(msiSqlCtx, { msiEzDBC =>
      
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
      val parentPeaklistIds = msiEzDBC.selectInts(pklIdQuery)
      
      // Retrieve child peaklist ids if they exist
      val pklIds = new ArrayBuffer[Int]
      for (parentPeaklistId <- parentPeaklistIds) {
  
        // Retrieve child peaklist ids corresponding to the current peaklist id
        val childPeaklistIds = msiEzDBC.select(childPklIdQuery, parentPeaklistId) { _.nextInt }
        
        if (childPeaklistIds.length > 0) { pklIds ++= childPeaklistIds }
        else { pklIds += parentPeaklistId }
      }
      
      val specTitleQuery = new SelectQueryBuilder1(MsiDbSpectrumTable).mkSelectQuery( (t,c) =>
        List(t.ID,t.TITLE) -> "WHERE "~ t.PEAKLIST_ID ~" IN("~ pklIds.mkString(",") ~")"
      )
  
      // Retrieve parent peaklist ids corresponding to the provided MSI search ids
      val spectrumTitleById = msiEzDBC.select(specTitleQuery) { r => (r.nextInt, r.nextString) } toMap
  
      // Load MS queries corresponding to the provided MSI search ids
      val msQueries = msiEzDBC.select(msqQuery) { r =>
        
        val spectrumId = r.getInt(MsQueryCols.SPECTRUM_ID)
  
        // Decode JSON properties
        val properties = r.getStringOption(MsQueryCols.SERIALIZED_PROPERTIES).map(parse[MsQueryProperties](_))
        val msQueryId: Int = toInt(r.getAnyVal(MsQueryCols.ID))
  
        // Build the MS query object
        val msQuery = if (spectrumId != 0) { // we can assume it is a MS2 query
          val spectrumTitle = spectrumTitleById(spectrumId)
          new Ms2Query(
            id = msQueryId,
            initialId = r.getInt(MsQueryCols.INITIAL_ID),
            moz = r.getDouble(MsQueryCols.MOZ),
            charge = r.getInt(MsQueryCols.CHARGE),
            spectrumTitle = spectrumTitle,
            spectrumId = spectrumId,
            properties = properties
          )
  
        } else {
          new Ms1Query(
            id = msQueryId,
            initialId = r.getInt(MsQueryCols.INITIAL_ID),
            moz = r.getDouble(MsQueryCols.MOZ),
            charge = r.getInt(MsQueryCols.CHARGE),
            properties = properties
          )
        }
  
        msQuery
      }
  
      msQueries.toArray
    }, false)    

  }

}

