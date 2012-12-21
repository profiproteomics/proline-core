package fr.proline.core.om.storer.msi.impl

import java.sql.Connection

import org.postgresql.copy.CopyIn
import org.postgresql.copy.CopyManager
import org.postgresql.core.BaseConnection

import com.codahale.jerkson.Json.generate
import com.weiglewilczek.slf4s.Logging

import fr.proline.core.dal.tables.msi.MsiDbMsQueryTable
import fr.proline.core.dal.ProlineEzDBC
import fr.proline.core.om.model.msi.Ms1Query
import fr.proline.core.om.model.msi.Ms2Query
import fr.proline.core.om.model.msi.MsQuery
import fr.proline.repository.util.JDBCWork
import fr.proline.util.sql.encodeRecordForPgCopy

class PgMsiSearchStorer() extends SQLiteMsiSearchStorer() with Logging {

  // val connection = msiDb.ezDBC.connection
  // val bulkCopyManager = new CopyManager( msiDb.ezDBC.connection.asInstanceOf[BaseConnection] )

  override def storeMsQueries(msiSearchId: Int, msQueries: Seq[MsQuery], context: StorerContext): StorerContext = {

    val jdbcWork = new JDBCWork() {

      override def execute(con: Connection) {

        // Create TMP table
        val tmpMsQueryTableName = "tmp_ms_query_" + (scala.math.random * 1000000).toInt
        logger.info("creating temporary table '" + tmpMsQueryTableName + "'...")

        val stmt = con.createStatement()
        stmt.executeUpdate("CREATE TEMP TABLE " + tmpMsQueryTableName + " (LIKE ms_query)")

        // Bulk insert of MS queries
        logger.info("BULK insert of MS queries")

        val msQueryTableCols = MsiDbMsQueryTable.columnsAsStrList.filter(_ != "id").mkString(",")

        val bulkCopyManager = new CopyManager(con.asInstanceOf[BaseConnection])
        val pgBulkLoader = bulkCopyManager.copyIn("COPY " + tmpMsQueryTableName + " ( id, " + msQueryTableCols + " ) FROM STDIN")

        for (msQuery <- msQueries) {

          msQuery.msLevel match {
            case 1 => _copyMsQuery(pgBulkLoader, msQuery.asInstanceOf[Ms1Query], msiSearchId, Option.empty[Int])
            case 2 => {
              val ms2Query = msQuery.asInstanceOf[Ms2Query]
              // FIXME: it should not be null
              var spectrumId = Option.empty[Int]
              if (context.spectrumIdByTitle != null) {
                ms2Query.spectrumId = context.spectrumIdByTitle(ms2Query.spectrumTitle)
                spectrumId = Some(ms2Query.spectrumId)
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
        val msiEzDBC = ProlineEzDBC(con, context.msiDbContext.getDriverType)

        val msQueryIdByInitialId = msiEzDBC.select(
          "SELECT initial_id, id FROM ms_query WHERE msi_search_id = " + msiSearchId) { r =>
            (r.nextInt -> r.nextInt)
          } toMap

        // Iterate over MS queries to update them
        msQueries.foreach { msQuery => msQuery.id = msQueryIdByInitialId(msQuery.initialId) }

      }

    } // End of jdbcWork anonymous inner class

    context.msiDbContext.doWork(jdbcWork, true)

    context
  }

  private def _copyMsQuery(pgBulkLoader: CopyIn, msQuery: MsQuery, msiSearchId: Int, spectrumId: Option[Int]): Unit = {

    import com.codahale.jerkson.Json.generate

    //if( spectrumId <= 0 )
    //throw new Exception("spectrum must first be persisted")

    val spectrumIdAsStr = if (spectrumId == None) "" else spectrumId.get.toString
    val msqPropsAsJSON = if (msQuery.properties != None) generate(msQuery.properties.get) else ""

    // Build a row containing MS queries values
    val msQueryValues = List(
      msQuery.id,
      msQuery.initialId,
      msQuery.charge,
      msQuery.moz,
      msqPropsAsJSON,
      spectrumIdAsStr,
      msiSearchId)

    // Store MS query
    val msQueryBytes = encodeRecordForPgCopy(msQueryValues)
    pgBulkLoader.writeToCopy(msQueryBytes, 0, msQueryBytes.length)

  }

}


