package fr.proline.core.dal.helper

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.collection.mutable.HashSet
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.{ DoJDBCReturningWork, DoJDBCWork }
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.msi.MsiDbResultSetRelationTable
import fr.proline.core.dal.tables.msi.MsiDbResultSummaryRelationTable
import fr.profi.util.primitives._

class MsiDbHelper(msiDbCtx: DatabaseConnectionContext) {

  def getDecoyRsId(targetResultSetId: Long): Option[Long] = {
    val decoyRsIds = DoJDBCReturningWork.withEzDBC(msiDbCtx) { ezDBC =>
      ezDBC.select(
        "SELECT decoy_result_set_id FROM result_set WHERE id = " + targetResultSetId
      ) { _.nextLongOption }
    }
    
    decoyRsIds.headOption.flatten
  }

  def getDecoyRsIds(targetResultSetIds: Seq[Long]): Array[Long] = {
    if ( targetResultSetIds == null || targetResultSetIds.isEmpty )
      return Array.empty[Long]

    DoJDBCReturningWork.withEzDBC(msiDbCtx) { ezDBC =>
      ezDBC.selectLongs(
        "SELECT decoy_result_set_id FROM result_set WHERE id in " +
        targetResultSetIds.mkString("(", ", ", ")") +
        " AND decoy_result_set_id IS NOT NULL")
    }
  }

  def getDecoyRsmIds(targetResultSummaryIds: Seq[Long]): Array[Long] = {
    if ( targetResultSummaryIds == null || targetResultSummaryIds.isEmpty )
      return Array.empty[Long]

    DoJDBCReturningWork.withEzDBC(msiDbCtx) { ezDBC =>
      ezDBC.selectLongs(
        "SELECT decoy_result_summary_id FROM result_summary WHERE id in " +
        targetResultSummaryIds.mkString("(", ", ", ")") +
        " AND decoy_result_summary_id IS NOT NULL")
    }
  }
  
  lazy val rsRelationQB = new SelectQueryBuilder1(MsiDbResultSetRelationTable)
  
  def getResultSetChildrenIds(rsId: Long): Array[Long] = {
    
    this._getChildrenIds(Array(rsId), { parentRsIds =>
      
      if (parentRsIds == null || parentRsIds.isEmpty) Array.empty[Long]
      else {
        // WAS "select child_result_set_id from result_set_relation where result_set_relation.parent_result_set_id = ?"
        DoJDBCReturningWork.withEzDBC(msiDbCtx) { ezDBC =>
          ezDBC.selectLongs(rsRelationQB.mkSelectQuery((t, cols) =>
            List(t.CHILD_RESULT_SET_ID) -> " WHERE " ~ t.PARENT_RESULT_SET_ID ~ " IN(" ~ parentRsIds.mkString(",") ~ ")"
          ))
        }
      }
      
    })
  }

  lazy val rsmRelationQB = new SelectQueryBuilder1(MsiDbResultSummaryRelationTable)
  
  def getResultSummaryChildrenIds(rsmId: Long): Array[Long] = {
    this._getChildrenIds(Array(rsmId), { parentRsmIds =>

      if (parentRsmIds == null || parentRsmIds.isEmpty) Array.empty[Long]
      else {
        // WAS "select child_result_summary_id from result_summary_relation where result_summary_relation.parent_result_summary_id = ?"
        DoJDBCReturningWork.withEzDBC(msiDbCtx) { ezDBC =>
          ezDBC.selectLongs(rsmRelationQB.mkSelectQuery( (t, cols) =>
            List(t.CHILD_RESULT_SUMMARY_ID) -> " WHERE " ~ t.PARENT_RESULT_SUMMARY_ID ~ " IN(" ~ parentRsmIds.mkString(",") ~ ")"
          ))
        }
      }
      
    })
  }
  
  private def _getChildrenIds(ids: Array[Long], parentIdsToChildIds: Array[Long] => Array[Long]): Array[Long] = {
    if(ids.isEmpty) return Array.empty[Long]
    
    val childIds = new ArrayBuffer[Long]()
    
    this._appendChildrenIds(ids, childIds, parentIdsToChildIds)

    childIds.toArray
  }
  
  @tailrec
  private def _appendChildrenIds(
    parentRsmIds: Array[Long],
    childRsmIds: ArrayBuffer[Long],
    parentIdsToChildIds: Array[Long] => Array[Long]
  ): Array[Long] = {
    if (parentRsmIds.isEmpty) return Array.empty[Long]
    
    val firstChildrenIds = parentIdsToChildIds(parentRsmIds)
    childRsmIds ++= firstChildrenIds
    
    this._appendChildrenIds(firstChildrenIds, childRsmIds, parentIdsToChildIds)
  }

  def getResultSetsMsiSearchIds(rsIds: Seq[Long], hierarchicalQuery: Boolean = true ): Array[Long] = {

    if ( rsIds == null || rsIds.isEmpty )
      return Array.empty[Long]

    val parentMsiSearchIds = DoJDBCReturningWork.withEzDBC(msiDbCtx) { ezDBC =>
      ezDBC.selectLongs(
        "SELECT DISTINCT msi_search_id FROM result_set " +
        "WHERE id IN (" + rsIds.mkString(",") + ") " +
        "AND msi_search_id IS NOT NULL"
      )
    }

    // TODO: use getMsiSearchIdsByParentResultSetId instead of _getChildMsiSearchId to reduce code redundancy ???
    if( hierarchicalQuery ) {
      val childMsiSearchIds = new ArrayBuffer[Long]()
      _getChildMsiSearchIds(rsIds, childMsiSearchIds)
      parentMsiSearchIds ++ childMsiSearchIds.distinct
    } else {
      parentMsiSearchIds
    }
  }

  @tailrec
  private def _getChildMsiSearchIds(rsIds: Seq[Long], childMsiSearchIds: ArrayBuffer[Long]): Unit = {
    if( rsIds.isEmpty ) return ()

    val childRsIds = new ArrayBuffer[Long]()
    DoJDBCWork.withEzDBC(msiDbCtx) { ezDBC =>
      ezDBC.selectAndProcess(
        "SELECT result_set_relation.child_result_set_id, result_set.msi_search_id FROM result_set, result_set_relation " +
        "WHERE result_set.id = result_set_relation.child_result_set_id " +
        "AND result_set_relation.parent_result_set_id IN (" + rsIds.mkString(",") + ") "
      ) { r =>
        val(childRsId, msiSearchIdOpt) = (r.nextLong,r.nextLongOption)
        childRsIds += childRsId
        msiSearchIdOpt.map( childMsiSearchIds += _ )
      }
    }

    // If we have found child result sets
    _getChildMsiSearchIds(childRsIds.distinct, childMsiSearchIds)
  }

  def getMsiSearchIdsByParentResultSetId(rsIds: Seq[Long]): Map[Long, Set[Long]] = {
    if (rsIds == null || rsIds.isEmpty)
      return Map.empty[Long, Set[Long]]
    
    val msiSearchIdsByParentResultSetId = new HashMap[Long, HashSet[Long]]
    val parentRsIds = DoJDBCReturningWork.withEzDBC(msiDbCtx) { ezDBC =>
      ezDBC.select(
        "SELECT id, msi_search_id FROM result_set " +
        "WHERE id IN (" + rsIds.mkString(",") + ") " +
        "AND msi_search_id IS NOT NULL"
      ) { r =>
          val( parentRsId, msiSearchId ) = (r.nextLong, r.nextLong)
          msiSearchIdsByParentResultSetId.getOrElseUpdate(parentRsId, new HashSet[Long]) += msiSearchId         
          parentRsId
        }
    }

    _getMsiSearchIdsByParentResultSetId( parentRsIds, msiSearchIdsByParentResultSetId )

    Map() ++ msiSearchIdsByParentResultSetId.map(t => (t._1 -> t._2.toSet))
  }
  
  @tailrec
  private def _getMsiSearchIdsByParentResultSetId(rsIds: Seq[Long], msiSearchIdsByParentResultSetId: HashMap[Long, HashSet[Long]]) {
    if( rsIds.isEmpty ) return
    
    val childRsIds = new ArrayBuffer[Long]()
    
    DoJDBCWork.withEzDBC(msiDbCtx) { ezDBC =>
      ezDBC.selectAndProcess(
        "SELECT result_set_relation.parent_result_set_id, result_set_relation.child_result_set_id, result_set.msi_search_id FROM result_set, result_set_relation " +
        "WHERE result_set.id = result_set_relation.child_result_set_id " +
        "AND result_set_relation.parent_result_set_id IN (" + rsIds.mkString(",") + ") "
      ) { r =>
          val(parentRsId, childRsId, msiSearchIdOpt) = (r.nextLong,r.nextLong,r.nextLongOption)
          childRsIds += childRsId
          
          msiSearchIdOpt.map { msiSearchId =>
            msiSearchIdsByParentResultSetId.getOrElseUpdate(parentRsId, new HashSet[Long]) += msiSearchId
          }
        }
    }
    
    _getMsiSearchIdsByParentResultSetId( childRsIds.distinct, msiSearchIdsByParentResultSetId )
  }

  def getResultSetIdByResultSummaryId(rsmIds: Seq[Long]): Map[Long, Long] = {
    if (rsmIds == null || rsmIds.isEmpty)
      return Map.empty[Long, Long]

    // Retrieve parent peaklist ids corresponding to the provided MSI search ids
    DoJDBCReturningWork.withEzDBC(msiDbCtx) { ezDBC =>
      ezDBC.select(
        "SELECT id, result_set_id FROM result_summary " +
        "WHERE id IN (" + rsmIds.mkString(",") + ")") { r => (r.nextLong, r.nextLong) } toMap
    }
  }

  def getMsiSearchesPtmSpecificityIds(msiSearchIds: Seq[Long]): Array[Long] = {
    if (msiSearchIds == null || msiSearchIds.isEmpty)
      return Array.empty[Long]
    
    // Retrieve parent peaklist ids corresponding to the provided MSI search ids
    val ptmSpecifIds = DoJDBCReturningWork.withEzDBC(msiDbCtx) { ezDBC =>
      ezDBC.select(
        "SELECT DISTINCT ptm_specificity_id FROM used_ptm, search_settings, msi_search " +
        "WHERE used_ptm.search_settings_id = search_settings.id " +
        "AND search_settings.id = msi_search.search_settings_id " +
        "AND msi_search.id IN (" + msiSearchIds.mkString(",") + ")"
      ) { _.nextLong }
    }

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
    DoJDBCReturningWork.withEzDBC(msiDbCtx) { ezDBC =>
      ezDBC.select("SELECT id, search_engine, name FROM scoring") { r =>
        ScoringRecord(r.nextLong, r.nextString, r.nextString)
      }
    }
  }

  def getScoringsByResultSummaryIds(resultSummaryId: Seq[Long]): Seq[String] = {
    if (resultSummaryId == null || resultSummaryId.isEmpty)
      return Seq.empty[String]

    DoJDBCReturningWork.withEzDBC(msiDbCtx) { ezDBC =>
      ezDBC.select("SELECT scoring.search_engine, scoring.name " +
        "FROM scoring, peptide_set " +
        "WHERE peptide_set.scoring_id = scoring.id AND peptide_set.result_summary_id IN (" + resultSummaryId.mkString(",") + ")" +
        "GROUP BY scoring.search_engine, scoring.name") { r => r.nextString + ":" + r.nextString }
    }
  }

  def getSeqLengthByBioSeqId(bioSeqIds: Iterable[Long]): Map[Long, Int] = {
    if( bioSeqIds == null || bioSeqIds.isEmpty )
      return Map.empty[Long, Int]

    val seqLengthByProtIdBuilder = Map.newBuilder[Long, Int]

    DoJDBCWork.withEzDBC(msiDbCtx) { ezDBC =>
      val maxNbIters = ezDBC.getInExpressionCountLimit

      // Iterate over groups of peptide ids
      bioSeqIds.grouped(maxNbIters).foreach { tmpBioSeqIds =>
        if ((tmpBioSeqIds != null) && !tmpBioSeqIds.isEmpty) {
          ezDBC.selectAndProcess("SELECT id, length FROM bio_sequence WHERE id IN (" + tmpBioSeqIds.mkString(",") + ")") { r =>
            seqLengthByProtIdBuilder += (r.nextLong -> r.nextInt)
          }
        }
      }
    }

    seqLengthByProtIdBuilder.result()
  }

  // TODO: add number field to the table
  def getSpectrumNumberById(pklIds: Seq[Long]): Map[Long, Int] = {

    if (pklIds == null || pklIds.isEmpty) {
      Map.empty[Long, Int]
    } else {
      val specNumById = new HashMap[Long, Int]
      var specCount = 0

      DoJDBCWork.withEzDBC(msiDbCtx) { ezDBC =>
        ezDBC.selectAndProcess("SELECT id FROM spectrum WHERE " + pklIds.map(id => s"peaklist_id=$id").mkString(" OR ") ) { r =>
          val spectrumId = r.nextLong
          specNumById += (spectrumId -> specCount)
          specCount += 1
        }
      }

      Map() ++ specNumById
    }

  }
}