package fr.proline.core.om.storer.msi.impl

import java.sql.Connection
import org.postgresql.copy.CopyIn
import org.postgresql.copy.CopyManager
import org.postgresql.core.BaseConnection
import com.weiglewilczek.slf4s.Logging
import fr.profi.jdbc.easy._
import fr.profi.util.serialization.ProfiJson
import fr.proline.core.dal.tables.msi.MsiDbMsQueryTable
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.DoJDBCWork
import fr.proline.core.om.model.msi.Ms1Query
import fr.proline.core.om.model.msi.Ms2Query
import fr.proline.core.om.model.msi.MsQuery
import fr.proline.util.sql.encodeRecordForPgCopy
import fr.proline.repository.util.PostgresUtils
import fr.proline.util.primitives._

object PgMsiSearchWriter extends AbstractSQLMsiSearchWriter() with Logging {

  // val connection = msiDb.ezDBC.connection
  // val bulkCopyManager = new CopyManager( msiDb.ezDBC.connection.asInstanceOf[BaseConnection] )
  
  private val msqIdQuery = new SelectQueryBuilder1(MsiDbMsQueryTable).mkSelectQuery( (t,c) => 
    List(t.INITIAL_ID,t.ID) -> "WHERE "~ t.MSI_SEARCH_ID ~" = ?"
  )

  override def insertMsQueries(msiSearchId: Long, msQueries: Seq[MsQuery], context: StorerContext): StorerContext = {

    DoJDBCWork.withEzDBC( context.getMSIDbConnectionContext, { msiEzDBC =>
      
      val con = msiEzDBC.connection

      // Create TMP table
      val tmpMsQueryTableName = "tmp_ms_query_" + (scala.math.random * 1000000).toInt
      logger.info("creating temporary table '" + tmpMsQueryTableName + "'...")

      val stmt = con.createStatement()
      stmt.executeUpdate("CREATE TEMP TABLE " + tmpMsQueryTableName + " (LIKE ms_query) ON COMMIT DROP")

      // Bulk insert of MS queries
      logger.info("BULK insert of MS queries")

      val msQueryTableCols = MsiDbMsQueryTable.columnsAsStrList.filter(_ != "id").mkString(",")

      val bulkCopyManager =  PostgresUtils.getCopyManager(con)
      val pgBulkLoader = bulkCopyManager.copyIn("COPY " + tmpMsQueryTableName + " ( id, " + msQueryTableCols + " ) FROM STDIN")

      for (msQuery <- msQueries) {

        msQuery.msLevel match {
          case 1 => _copyMsQuery(pgBulkLoader, msQuery.asInstanceOf[Ms1Query], msiSearchId, Option.empty[Long])
          case 2 => {
            val ms2Query = msQuery.asInstanceOf[Ms2Query]
            // FIXME: it should not be null
            var spectrumId = Option.empty[Long]
            if (context.spectrumIdByTitle != null) {
              ms2Query.spectrumId = context.spectrumIdByTitle(ms2Query.spectrumTitle)
              spectrumId = Some(ms2Query.spectrumId)
            }
            else {
              throw new Exception("spectrumIdByTitle must not be null")
            }
            _copyMsQuery(pgBulkLoader, msQuery, msiSearchId, spectrumId)
          }
        }

      }

      // End of BULK copy
      val nbInsertedMsQueries = pgBulkLoader.endCopy()

      // Move TMP table content to MAIN table
      logger.info("move TMP table " + tmpMsQueryTableName + " into MAIN ms_query table")
      stmt.executeUpdate("INSERT into ms_query (" + msQueryTableCols + ") " +
        "SELECT " + msQueryTableCols + " FROM " + tmpMsQueryTableName)

      // Retrieve generated spectrum ids
      val msQueryIdByInitialId = msiEzDBC.select(msqIdQuery, msiSearchId) { r =>
        (toLong(r.nextAny) -> toLong(r.nextAny))
      } toMap

      // Iterate over MS queries to update them
      msQueries.foreach { msQuery => msQuery.id = msQueryIdByInitialId(msQuery.initialId) }

    }, true) // End of jdbcWork

    context
  }

  private def _copyMsQuery(pgBulkLoader: CopyIn, msQuery: MsQuery, msiSearchId: Long, spectrumId: Option[Long]): Unit = {
    
    //if( spectrumId <= 0 )
    //throw new Exception("spectrum must first be persisted")

    // Build a row containing MS queries values
    val msQueryValues = List(
      msQuery.id,
      msQuery.initialId,
      msQuery.charge,
      msQuery.moz,
      msQuery.properties.map(ProfiJson.serialize(_)),
      spectrumId,
      msiSearchId
    )

    // Store MS query
    val msQueryBytes = encodeRecordForPgCopy(msQueryValues)
    pgBulkLoader.writeToCopy(msQueryBytes, 0, msQueryBytes.length)

  }

}


