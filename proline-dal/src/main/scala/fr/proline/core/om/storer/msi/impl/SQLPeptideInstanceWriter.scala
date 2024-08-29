package fr.proline.core.om.storer.msi.impl

import com.typesafe.scalalogging.LazyLogging
import fr.profi.jdbc.PreparedStatementWrapper
import fr.profi.util.serialization.ProfiJson
import fr.proline.context.MsiDbConnectionContext
import fr.proline.core.dal.DoJDBCWork
import fr.proline.core.dal.tables.msi.{MsiDbPeptideInstancePeptideMatchMapTable, MsiDbPeptideInstanceTable}
import fr.proline.core.om.model.msi.PeptideInstance
import fr.proline.core.om.storer.msi.IPeptideInstanceWriter
import fr.profi.jdbc.easy._

object SQLPeptideInstanceWriter extends IPeptideInstanceWriter with  LazyLogging{

  override def insertPeptideInstances(pepInstances: Seq[PeptideInstance], msiDbConCtxt: MsiDbConnectionContext): Unit = {


    val insertPepInstance = (stmt: PreparedStatementWrapper, pepInstance: PeptideInstance) => {

      val pepId = pepInstance.peptide.id
      val unmodPepId = pepInstance.getUnmodifiedPeptideId

      // Insert peptide instance
      stmt.executeWith(
        pepInstance.peptideMatchesCount,
        pepInstance.proteinMatchesCount,
        pepInstance.proteinSetsCount,
        pepInstance.validatedProteinSetsCount,
        pepInstance.totalLeavesMatchCount,
        pepInstance.selectionLevel,
        pepInstance.elutionTime,
        pepInstance.properties.map(ProfiJson.serialize(_)),
        pepInstance.bestPeptideMatchId,
        pepId,
        if (unmodPepId > 0) Some(unmodPepId) else Option.empty[Long],
        Option.empty[Long],
        pepInstance.resultSummaryId
      )

      // Update peptide instance id
      pepInstance.id = stmt.generatedLong
    }

    logger.debug("storing peptide instances...")
    val pepInstInsertQuery = MsiDbPeptideInstanceTable.mkInsertQuery { (c, colsList) => colsList.filter(_ != c.ID) }

    DoJDBCWork.withEzDBC(msiDbConCtxt) { msiEzDBC =>

      // Insert peptide instances
      msiEzDBC.executePrepared(pepInstInsertQuery, true) { stmt =>

        pepInstances.foreach { pepInst =>
          insertPepInstance(stmt, pepInst)
        }
      }

      // Link peptide instances with peptide matches
      val pepInstPepMatchMapInsertQuery = MsiDbPeptideInstancePeptideMatchMapTable.mkInsertQuery()

      msiEzDBC.executeInBatch(pepInstPepMatchMapInsertQuery) { stmt =>

        pepInstances.foreach { pepInst =>
          val pepMatchRsmPropsById = pepInst.peptideMatchPropertiesById

          pepInst.peptideMatchIds.foreach { pepMatchId =>
            val pepMatchPropsAsJSON = if (pepMatchRsmPropsById != null) Some(ProfiJson.serialize(pepMatchRsmPropsById(pepMatchId))) else None

            // Insert peptide match mapping
            stmt.executeWith(
              pepInst.id,
              pepMatchId,
              pepMatchPropsAsJSON,
              pepInst.resultSummaryId
            )
          }
        }
      }
    } // End of JDBC work
  }
}
