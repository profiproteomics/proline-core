package fr.proline.core.om.storer.msi.impl

import com.typesafe.scalalogging.LazyLogging
import fr.profi.util.serialization.ProfiJson
import fr.profi.util.sql.encodeRecordForPgCopy
import fr.proline.context.MsiDbConnectionContext
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.helper.MsiDbHelper
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.msi.{MsiDbMsQueryTable, MsiDbPeptideMatchTable}
import fr.proline.core.om.model.msi.{Ms2Query, MsQuery, PeptideMatch}
import fr.proline.repository.util.PostgresUtils
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.om.storer.msi.IPeptideMatchWriter
import fr.profi.jdbc.easy._
import scala.collection.mutable.ArrayBuffer

object PgPeptideMatchWriter extends  IPeptideMatchWriter with LazyLogging{

  private val pepMatchTableColsWithoutPK = MsiDbPeptideMatchTable.columnsAsStrList.filter(_ != "id").mkString(",")
  private val msQueryTableColsWithoutPK = MsiDbMsQueryTable.columnsAsStrList.filter(_ != "id").mkString(",")


  def insertPeptideMatches(peptideMatches: Seq[PeptideMatch], msiDbConCtxt : MsiDbConnectionContext): Unit = {

    //insert msQuery first
    val msQueries = peptideMatches.map(pm => pm.msQuery).filter(_.id<0)
    inserMsQueries(msQueries, msiDbConCtxt)

    DoJDBCReturningWork.withEzDBC(msiDbConCtxt) { msiEzDBC =>

      val msiCon = msiEzDBC.connection
      val bulkCopyManager = PostgresUtils.getCopyManager(msiCon)
      val scoringIdByType = new MsiDbHelper(msiDbConCtxt).getScoringIdByType()

      logger.debug("Number of peptideMatches to write :" + peptideMatches.length)

      // Bulk insert of peptide matches
      logger.info("BULK insert of peptide matches")

      var scoringErr = false
      var scoreType: String = ""
      val rsId: Long = if (peptideMatches.isEmpty) -1L else peptideMatches.head.resultSetId
      val allPepIds = new ArrayBuffer[Long]()

      //val pgBulkLoader = bulkCopyManager.copyIn("COPY " + tmpPepMatchTableName + " ( id, " + pepMatchTableColsWithoutPK + " ) FROM STDIN")
      val pgBulkLoader = bulkCopyManager.copyIn(
        s"COPY ${MsiDbPeptideMatchTable.name} ($pepMatchTableColsWithoutPK) FROM STDIN"
      )

      // Iterate over peptide matches to store them
      for (peptideMatch <- peptideMatches if !scoringErr) {
        allPepIds += peptideMatch.peptideId
        scoreType = peptideMatch.scoreType.toString
        val scoringId = scoringIdByType.get(scoreType)
        if (scoringId.isEmpty)
          scoringErr = true
        else {

          val msQuery = peptideMatch.msQuery
          val bestChildId = peptideMatch.bestChildId

          // Build a row containing peptide match values
          val pepMatchValues = List(
            //peptideMatch.id,
            peptideMatch.charge,
            msQuery.moz,
            peptideMatch.score,
            peptideMatch.rank,
            peptideMatch.cdPrettyRank,
            peptideMatch.sdPrettyRank,
            peptideMatch.deltaMoz,
            peptideMatch.missedCleavage,
            peptideMatch.fragmentMatchesCount,
            peptideMatch.isDecoy,
            peptideMatch.properties.map(ProfiJson.serialize(_)),
            peptideMatch.peptide.id,
            msQuery.id,
            if (bestChildId == 0) None else Some(bestChildId),
            scoringId.get,
            peptideMatch.resultSetId
          )

          // Store peptide match
          val pepMatchBytes = encodeRecordForPgCopy(pepMatchValues)
          pgBulkLoader.writeToCopy(pepMatchBytes, 0, pepMatchBytes.length)
        }
      } // end go through peptideMatch or until scoringErr !

      // End of BULK copy
      if (scoringErr) {
        //Error : Cancel copy and throw exception
        pgBulkLoader.cancelCopy()
        throw new IllegalArgumentException("requirement failed: " + "can't find a scoring id for the score type '" + scoreType + "'")
      }

      val nbInsertedPepMatches = pgBulkLoader.endCopy()

      // Retrieve generated peptide match ids
      //Same peptide could be identified by two PSMs from same query if seq contains X that could be replaced by I/L for instance
      // Get PepMatch MS_QUERY_ID, RANK, PEPTIDE_ID, ID for RS
     val pepMatchUniqueFKsQueryRank = new SelectQueryBuilder1(MsiDbPeptideMatchTable).mkSelectQuery((t, _) =>
        List(t.MS_QUERY_ID, t.RANK, t.PEPTIDE_ID, t.ID) -> "WHERE " ~ t.RESULT_SET_ID ~ " = " ~ rsId ~ " AND " ~ t.PEPTIDE_ID ~ " IN (" ~ allPepIds.mkString(",") ~ ")"
      )

      val pepMatchIdByKey = msiEzDBC.select(pepMatchUniqueFKsQueryRank) { r =>
        _formatPeptideMatchKey(r.nextLong, r.nextInt, r.nextLong) -> r.nextLong
      }.toMap

      // Iterate over peptide matches to update them
      peptideMatches.foreach {
        pepMatch => {
          val key = _formatPeptideMatchKey(pepMatch.msQuery.id, pepMatch.rank, pepMatch.peptide.id)
          val newId = pepMatchIdByKey(key)
          pepMatch.id = newId
        }
      }
      nbInsertedPepMatches.toInt
    }

  }
  private def _formatPeptideMatchKey(msQueryId: Long, peptideMatchRank: Int, peptideId: Long): String = {
    msQueryId + "_" + peptideMatchRank + "%" + peptideId
  }

  private def inserMsQueries(queries : Seq[MsQuery], msiDBCtx : MsiDbConnectionContext): Unit = {

    val msQueryInsertQuery = MsiDbMsQueryTable.mkInsertQuery((col, colsList) => colsList.filter(_ != col.ID))

    DoJDBCReturningWork.withEzDBC(msiDBCtx) { msiEzDBC =>

      msiEzDBC.executePrepared(msQueryInsertQuery, true) { stmt =>
        for (msQuery <- queries) {
          val spectrumId = if(msQuery.msLevel.equals(1) ) Option.empty[Long] else Some(msQuery.asInstanceOf[Ms2Query].spectrumId)
          stmt.executeWith(
            msQuery.initialId,
            msQuery.charge,
            msQuery.moz,
            msQuery.properties.map(ProfiJson.serialize(_)),
            spectrumId,
            msQuery.msiSearchId
          )

          msQuery.id = stmt.generatedLong
        }
      }

    }
  }

}
