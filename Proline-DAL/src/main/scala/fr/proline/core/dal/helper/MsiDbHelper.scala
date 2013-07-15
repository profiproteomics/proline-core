package fr.proline.core.dal.helper

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.collection.mutable.HashSet

import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.{ DoJDBCReturningWork, DoJDBCWork }
import fr.proline.util.primitives._

class MsiDbHelper(msiDbCtx: DatabaseConnectionContext) {

  def getDecoyRsId(targetResultSetId: Long): Option[Long] = {
    DoJDBCReturningWork.withEzDBC(msiDbCtx, { ezDBC =>
      ezDBC.select(
        "SELECT decoy_result_set_id FROM result_set WHERE id = " + targetResultSetId
      ) { _.nextLongOption }(0)
    })
  }

  def getDecoyRsIds(targetResultSetIds: Seq[Long]): Array[Long] = {
    DoJDBCReturningWork.withEzDBC(msiDbCtx, { ezDBC =>
      ezDBC.selectLongs(
        "SELECT decoy_result_set_id FROM result_set WHERE id in " +
          targetResultSetIds.mkString("(", ", ", ")") +
          " AND decoy_result_set_id IS NOT NULL")
    })
  }

  def getDecoyRsmIds(targetResultSummaryIds: Seq[Long]): Array[Long] = {
    DoJDBCReturningWork.withEzDBC(msiDbCtx, { ezDBC =>
      ezDBC.selectLongs(
        "SELECT decoy_result_summary_id FROM result_summary WHERE id in " +
          targetResultSummaryIds.mkString("(", ", ", ")") +
          " AND decoy_result_summary_id IS NOT NULL")
    })
  }

  def getResultSetsMsiSearchIds(rsIds: Seq[Long]): Array[Long] = {
    val parentMsiSearchIds = DoJDBCReturningWork.withEzDBC(msiDbCtx, { ezDBC =>
      ezDBC.selectLongs(
        "SELECT DISTINCT msi_search_id FROM result_set " +
          "WHERE id IN (" + rsIds.mkString(",") + ") " +
          "AND msi_search_id IS NOT NULL"
      )
    })
    val childMsiSearchIds = DoJDBCReturningWork.withEzDBC(msiDbCtx, { ezDBC =>
      ezDBC.selectLongs(
        "SELECT DISTINCT msi_search_id FROM result_set, result_set_relation " +
          "WHERE result_set.id = result_set_relation.child_result_set_id " +
          "AND result_set_relation.parent_result_set_id IN (" + rsIds.mkString(",") + ") " +
          "AND msi_search_id IS NOT NULL"
      )
    })

    parentMsiSearchIds ++ childMsiSearchIds
  }

  def getMsiSearchIdsByParentResultSetId(rsIds: Seq[Long]): Map[Long, Set[Long]] = {

    val msiSearchIdsByParentResultSetId = new HashMap[Long, HashSet[Long]]
    DoJDBCWork.withEzDBC(msiDbCtx, { ezDBC =>
      ezDBC.selectAndProcess(
        "SELECT id, msi_search_id FROM result_set " +
          "WHERE id IN (" + rsIds.mkString(",") + ") " +
          "AND msi_search_id IS NOT NULL"
      ) { r =>
          val id: Long = toLong(r.nextAny)
          msiSearchIdsByParentResultSetId.getOrElseUpdate(id, new HashSet[Long]) += toLong(r.nextAny)
        }
    })

    DoJDBCWork.withEzDBC(msiDbCtx, { ezDBC =>
      ezDBC.selectAndProcess(
        "SELECT result_set_relation.parent_result_set_id, result_set.msi_search_id FROM result_set, result_set_relation " +
          "WHERE result_set.id = result_set_relation.child_result_set_id " +
          "AND result_set_relation.parent_result_set_id IN (" + rsIds.mkString(",") + ") " +
          "AND msi_search_id IS NOT NULL"
      ) { r =>
          msiSearchIdsByParentResultSetId.getOrElseUpdate(toLong(r.nextAny), new HashSet[Long]) += toLong(r.nextAny)
        }
    })

    Map() ++ msiSearchIdsByParentResultSetId.map(t => (t._1 -> t._2.toSet))
  }

  def getResultSetIdByResultSummaryId(rsmIds: Seq[Long]): Map[Long, Long] = {
    // Retrieve parent peaklist ids corresponding to the provided MSI search ids
    DoJDBCReturningWork.withEzDBC(msiDbCtx, { ezDBC =>
      ezDBC.select(
        "SELECT id, result_set_id FROM result_summary " +
          "WHERE id IN (" + rsmIds.mkString(",") + ")") { r => (toLong(r.nextAny), toLong(r.nextAny)) } toMap
    })
  }
  
  def getMsiSearchesPtmSpecificityIds(msiSearchIds: Seq[Long]): Array[Long] = {

    // Retrieve parent peaklist ids corresponding to the provided MSI search ids
    val ptmSpecifIds = DoJDBCReturningWork.withEzDBC(msiDbCtx, { ezDBC =>
      ezDBC.select(
        "SELECT DISTINCT ptm_specificity_id FROM used_ptm, search_settings, msi_search " +
          "WHERE used_ptm.search_settings_id = search_settings.id " +
          "AND search_settings.id = msi_search.search_settings_id " +
          "AND msi_search.id IN (" + msiSearchIds.mkString(",") + ")") { v => toLong(v.nextAny) }
    })

    ptmSpecifIds.distinct.toArray
  }



  /** Build score types (search_engine:score_name) and map them by id */
  def getScoringTypeById(): Map[Long, String] = {
    Map() ++ _getScorings.map { scoring => (scoring.id -> (scoring.search_engine + ":" + scoring.name)) }
  }

  def getScoringIdByType(): Map[String, Long] = {
    Map() ++ _getScorings.map { scoring => ((scoring.search_engine + ":" + scoring.name) -> scoring.id) }
  }

  private case class ScoringRecord(id: Long, search_engine: String, name: String)

  /** Load and return scorings as records */
  private def _getScorings(): Seq[ScoringRecord] = {
    DoJDBCReturningWork.withEzDBC(msiDbCtx, { ezDBC =>
      ezDBC.select("SELECT id, search_engine, name FROM scoring") { r =>
        ScoringRecord(toLong(r.nextAny), r.nextString, r.nextString)
      }
    })
  }

  def getScoringByResultSummaryIds(resultSummaryId: Seq[Long]): Seq[String] = {
	DoJDBCReturningWork.withEzDBC(msiDbCtx, { ezDBC =>
      ezDBC.select("select scoring.search_engine, scoring.name " + 
      		"from scoring, peptide_set " + 
      		"where peptide_set.scoring_id = scoring.id AND peptide_set.result_summary_id IN (" +resultSummaryId.mkString(",") + ")" + 
      		"group by scoring.search_engine, scoring.name") { r => r.nextString+":"+r.nextString }
    })
  }
  
  def getSeqLengthByBioSeqId(bioSeqIds: Iterable[Long]): Map[Long, Int] = {

    val seqLengthByProtIdBuilder = Map.newBuilder[Long, Int]

    DoJDBCWork.withEzDBC(msiDbCtx, { ezDBC =>
      val maxNbIters = ezDBC.getInExpressionCountLimit

      // Iterate over groups of peptide ids
      bioSeqIds.grouped(maxNbIters).foreach {
        tmpBioSeqIds =>
          {
            // Retrieve peptide PTMs for the current group of peptide ids
            ezDBC.selectAndProcess("SELECT id, length FROM bio_sequence WHERE id IN (" + tmpBioSeqIds.mkString(",") + ")") { r =>
              seqLengthByProtIdBuilder += (toLong(r.nextAny) -> r.nextInt)
            }
          }
      }
    })

    seqLengthByProtIdBuilder.result()

  }


  // TODO: add number field to the table
  def getSpectrumNumberById(pklIds: Seq[Long]): Map[Long, Int] = {

    val specNumById = new HashMap[Long, Int]
    var specCount = 0

    DoJDBCWork.withEzDBC(msiDbCtx, { ezDBC =>
      ezDBC.selectAndProcess("SELECT id FROM spectrum WHERE peaklist_id IN (" + pklIds.mkString(",") + ")") { r =>
        val spectrumId: Long = toLong(r.nextAny)
        specNumById += (spectrumId -> specCount)
        specCount += 1
      }
    })

    Map() ++ specNumById
  }
}