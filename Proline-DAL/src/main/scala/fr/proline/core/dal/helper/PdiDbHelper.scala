package fr.proline.core.dal.helper

import fr.profi.jdbc.SQLQueryExecution
import fr.profi.util.primitives._

class PdiDbHelper(sqlExec: SQLQueryExecution) {

  import scala.collection.mutable.ArrayBuffer

  def getBioSequenceNameByTaxonAndId(bioSeqIds: Seq[Long]): Map[(Long, Long), String] = {

    val proteinNameByTaxonAndId = new scala.collection.mutable.HashMap[(Long, Long), String]

    bioSeqIds.grouped(sqlExec.getInExpressionCountLimit).foreach { tmpBioSeqIds =>

      if ((tmpBioSeqIds != null) && !tmpBioSeqIds.isEmpty) {

        val sqlQuery = "SELECT taxon_id, bio_sequence_id, name FROM seq_db_entry " +
          "WHERE bio_sequence_id IN (" + tmpBioSeqIds.mkString(",") + ") ORDER BY is_active DESC"

        sqlExec.selectAndProcess(sqlQuery) { r =>

          val taxonAndSeqId = (toLong(r.nextAny), toLong(r.nextAny))
          val bioSeqName = r.nextString

          if (!proteinNameByTaxonAndId.contains(taxonAndSeqId))
            proteinNameByTaxonAndId += (taxonAndSeqId -> bioSeqName)

        }

      }

    }

    Map() ++ proteinNameByTaxonAndId

  }

}