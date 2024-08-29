package fr.proline.core.om.storer.msi.impl

import com.typesafe.scalalogging.LazyLogging
import fr.profi.jdbc.PreparedStatementWrapper
import fr.profi.util.serialization.ProfiJson
import fr.proline.context.MsiDbConnectionContext
import fr.proline.core.dal.{DoJDBCReturningWork, DoJDBCWork}
import fr.proline.core.dal.tables.msi.{MsiDbMsQueryTable, MsiDbPeptideMatchTable}
import fr.proline.core.om.model.msi.{Ms2Query, MsQuery, PeptideMatch}
import fr.proline.core.om.storer.msi.IPeptideMatchWriter
import fr.profi.jdbc.easy._
import fr.proline.core.dal.helper.MsiDbHelper

object SQLPeptideMatchWriter extends AbstractPeptideMatchWriter

abstract class  AbstractPeptideMatchWriter extends  IPeptideMatchWriter with LazyLogging {

  override def insertPeptideMatches(peptideMatches: Seq[PeptideMatch], msiDbConCtxt: MsiDbConnectionContext): Unit = {
    require(peptideMatches != null, "peptides is null")
    if (peptideMatches.nonEmpty) {
      //insert msQuery first
      val msQueries = peptideMatches.map(pm => pm.msQuery).filter(_.id < 0)
      inserMsQueries(msQueries, msiDbConCtxt)

      DoJDBCWork.withEzDBC(msiDbConCtxt) { msiEzDBC =>
        val scoringIdByType = new MsiDbHelper(msiDbConCtxt).getScoringIdByType()
        val peptideMatchInsertQuery = MsiDbPeptideMatchTable.mkInsertQuery { (c, colsList) =>
          colsList.filter(_ != c.ID)
        }

        msiEzDBC.executePrepared(peptideMatchInsertQuery, generateKeys = true) { stmt =>
          var scoreType = ""
          peptideMatches.foreach(pm => {
            scoreType = pm.scoreType.toString
            val scoringId = scoringIdByType.get(scoreType)
            if (scoringId.isEmpty)
              throw new IllegalArgumentException("requirement failed: " + "can't find a scoring id for the score type '" + scoreType + "'")
            else
              this.insertPeptideMatch(stmt, pm, scoringId.get)
          })
        }
      }
    }
  }

  private def insertPeptideMatch(stmt: PreparedStatementWrapper, peptideMatch: PeptideMatch, scoringId: Long): Unit = {

    val bestChildId = peptideMatch.bestChildId
    val bestChildIdOpt = if (peptideMatch.bestChildId == 0) Option.empty[Long] else Some(peptideMatch.bestChildId)

    stmt.executeWith(
      peptideMatch.charge,
      peptideMatch.msQuery.moz,
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
      peptideMatch.msQuery.id,
      bestChildIdOpt,
      scoringId,
      peptideMatch.resultSetId
    )

    peptideMatch.id = stmt.generatedLong
  }


  private[impl] def inserMsQueries(queries: Seq[MsQuery], msiDBCtx: MsiDbConnectionContext): Unit = {

    val msQueryInsertQuery = MsiDbMsQueryTable.mkInsertQuery((col, colsList) => colsList.filter(_ != col.ID))

    DoJDBCReturningWork.withEzDBC(msiDBCtx) { msiEzDBC =>

      msiEzDBC.executePrepared(msQueryInsertQuery, generateKeys = true) { stmt =>
        for (msQuery <- queries) {
          val spectrumId = if (msQuery.msLevel.equals(1)) Option.empty[Long] else Some(msQuery.asInstanceOf[Ms2Query].spectrumId)
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
