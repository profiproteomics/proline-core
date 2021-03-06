package fr.proline.core.dal.helper

import fr.profi.util.collection._
import fr.profi.util.primitives._
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.msi.MsiDbResultSetRelationTable
import fr.proline.core.dal.tables.msi.MsiDbResultSummaryRelationTable
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.DoJDBCWork
import fr.proline.core.om.storer.msi.impl.StorerContext
import fr.proline.core.orm.msi.PeptideReadablePtmString

import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.collection.mutable.HashSet
import scala.collection.mutable.LongMap

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
        "SELECT decoy_result_set_id FROM result_set WHERE id IN " +
        targetResultSetIds.mkString("(", ", ", ")") +
        " AND decoy_result_set_id IS NOT NULL"
      )
    }
  }
  
  def getDecoyRsIdByTargetRsId(targetResultSetIds: Seq[Long]): LongMap[Long] = {
    if ( targetResultSetIds == null || targetResultSetIds.isEmpty )
      return LongMap.empty[Long]

    DoJDBCReturningWork.withEzDBC(msiDbCtx) { ezDBC =>
      val decoyrRsIdByTargetRsId = new LongMap[Long](targetResultSetIds.length)
      
      ezDBC.selectAndProcess(
        "SELECT id, decoy_result_set_id FROM result_set WHERE id IN " +
        targetResultSetIds.mkString("(", ", ", ")") +
        " AND decoy_result_set_id IS NOT NULL"
      ) { r =>
        decoyrRsIdByTargetRsId.put(r.nextLong, r.nextLong)
      }
      
      decoyrRsIdByTargetRsId
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
  
  def getResultSummaryLeavesIds(rsmId: Long): Array[Long] = {
      var allRSMIds = new ArrayBuffer[Long]()
           
      DoJDBCWork.withEzDBC(msiDbCtx) { ezDBC =>
        var childDefined = false
         val sqlQuery = new SelectQueryBuilder1(MsiDbResultSummaryRelationTable).mkSelectQuery( (t,c) =>
          List(t.CHILD_RESULT_SUMMARY_ID) -> "WHERE "~ t.PARENT_RESULT_SUMMARY_ID ~" = "~ rsmId
        )
        
        ezDBC.selectAndProcess(sqlQuery){ r =>
            childDefined = true
            val nextChildId = r.nextLong
            allRSMIds ++= getResultSummaryLeavesIds(nextChildId)
         }
               
        if (!childDefined)
          allRSMIds += rsmId
        
      } // End of jdbcWork anonymous inner class
    
      allRSMIds.toArray
  }
  

  def getResultSetLeavesId(rsId: Long): Array[Long] = {
     var allRSIds = new ArrayBuffer[Long]()
           
      DoJDBCWork.withEzDBC(msiDbCtx) { ezDBC =>
        var childDefined = false
         val sqlQuery = new SelectQueryBuilder1(MsiDbResultSetRelationTable).mkSelectQuery( (t,c) =>
          List(t.CHILD_RESULT_SET_ID) -> "WHERE "~ t.PARENT_RESULT_SET_ID ~" = "~ rsId
        )
        
        ezDBC.selectAndProcess(sqlQuery){ r =>
            childDefined = true
            val nextChildId = r.nextLong
            allRSIds ++= getResultSetLeavesId(nextChildId)
         }
               
        if (!childDefined)
          allRSIds += rsId
        
      } // End of jdbcWork anonymous inner class
    
      allRSIds.toArray
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
        s"WHERE id IN (${rsIds.mkString(",")}) " +
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
        s"AND result_set_relation.parent_result_set_id IN (${rsIds.mkString(",")})"
      ) { r =>
        val(childRsId, msiSearchIdOpt) = (r.nextLong,r.nextLongOption)
        childRsIds += childRsId
        msiSearchIdOpt.map( childMsiSearchIds += _ )
      }
    }

    // If we have found child result sets
    _getChildMsiSearchIds(childRsIds.distinct, childMsiSearchIds)
  }

  def getMsiSearchIdsByParentResultSetId(rsIds: Seq[Long]): LongMap[Set[Long]] = {
    if (rsIds == null || rsIds.isEmpty)
      return LongMap.empty[Set[Long]]
    
    val msiSearchIdsByParentResultSetId = new LongMap[HashSet[Long]]
    val parentRsIds = DoJDBCReturningWork.withEzDBC(msiDbCtx) { ezDBC =>
      ezDBC.select(
        "SELECT id, msi_search_id FROM result_set " +
        s"WHERE id IN (${rsIds.mkString(",")}) " +
        "AND msi_search_id IS NOT NULL"
      ) { r =>
          val( parentRsId, msiSearchId ) = (r.nextLong, r.nextLong)
          msiSearchIdsByParentResultSetId.getOrElseUpdate(parentRsId, new HashSet[Long]) += msiSearchId         
          parentRsId
        }
    }

    _getMsiSearchIdsByParentResultSetId( parentRsIds, msiSearchIdsByParentResultSetId )

    msiSearchIdsByParentResultSetId.map(t => (t._1 -> t._2.toSet))
  }
  
  @tailrec
  private def _getMsiSearchIdsByParentResultSetId(rsIds: Seq[Long], msiSearchIdsByParentResultSetId: LongMap[HashSet[Long]]) {
    if( rsIds.isEmpty ) return
    
    val childRsIds = new ArrayBuffer[Long]()
    
    DoJDBCWork.withEzDBC(msiDbCtx) { ezDBC =>
      ezDBC.selectAndProcess(
        "SELECT result_set_relation.parent_result_set_id, result_set_relation.child_result_set_id, result_set.msi_search_id FROM result_set, result_set_relation " +
        "WHERE result_set.id = result_set_relation.child_result_set_id " +
        s"AND result_set_relation.parent_result_set_id IN (${rsIds.mkString(",")})"
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

  def getResultSetIdByResultSummaryId(rsmIds: Seq[Long]): LongMap[Long] = {
    if (rsmIds == null || rsmIds.isEmpty)
      return LongMap.empty[Long]

    // Retrieve parent peaklist ids corresponding to the provided MSI search ids
    DoJDBCReturningWork.withEzDBC(msiDbCtx) { ezDBC =>
      val rsIdByRsmId = new LongMap[Long]()
      val sqlQuery = s"SELECT id, result_set_id FROM result_summary WHERE id IN (${rsmIds.mkString(",")})"
      ezDBC.selectAndProcess(sqlQuery) { r =>
        rsIdByRsmId.put(r.nextLong, r.nextLong)
      }
      rsIdByRsmId
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
        s"AND msi_search.id IN (${msiSearchIds.mkString(",")})"
      ) { _.nextLong }
    }

    ptmSpecifIds.distinct.toArray
  }

  /** Build score types (search_engine:score_name) and map them by id */
  def getScoringTypeById(): LongMap[String] = {
    _getScorings.toLongMapWith { scoring => (scoring.id -> (scoring.search_engine + ":" + scoring.name)) }
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

  def getScoringsByResultSummaryIds(rsmIds: Seq[Long]): Seq[String] = {
    if (rsmIds == null || rsmIds.isEmpty)
      return Seq.empty[String]

    DoJDBCReturningWork.withEzDBC(msiDbCtx) { ezDBC =>
      ezDBC.select("SELECT scoring.search_engine, scoring.name " +
        "FROM scoring, peptide_set " +
        s"WHERE peptide_set.scoring_id = scoring.id AND peptide_set.result_summary_id IN (${rsmIds.mkString(",")})" +
        "GROUP BY scoring.search_engine, scoring.name") { r => r.nextString + ":" + r.nextString }
    }
  }

  def getSeqLengthByBioSeqId(bioSeqIds: Seq[Long]): LongMap[Int] = {
    if( bioSeqIds == null || bioSeqIds.isEmpty )
      return LongMap.empty[Int]

    val seqLengthByProtId = new LongMap[Int]()
    seqLengthByProtId.sizeHint(bioSeqIds.length)

    DoJDBCWork.withEzDBC(msiDbCtx) { ezDBC =>
      val maxNbIters = ezDBC.getInExpressionCountLimit
      // Iterate over groups of peptide ids
      bioSeqIds.grouped(maxNbIters).foreach { tmpBioSeqIds =>
        if ((tmpBioSeqIds != null) && !tmpBioSeqIds.isEmpty) {
          ezDBC.selectAndProcess("SELECT id, length FROM bio_sequence WHERE id IN (" + tmpBioSeqIds.mkString(",") + ")") { r =>
            seqLengthByProtId.put(r.nextLong, r.nextInt)
          }
        }
      }
    }

    seqLengthByProtId
  }

  // TODO: add number field to the table
  def getSpectrumNumberById(pklIds: Seq[Long]): LongMap[Int] = {

    if (pklIds == null || pklIds.isEmpty) {
      LongMap.empty[Int]
    } else {
      val specNumById = new LongMap[Int]
      var specCount = 0

      DoJDBCWork.withEzDBC(msiDbCtx) { ezDBC =>
        ezDBC.selectAndProcess("SELECT id FROM spectrum WHERE " + pklIds.map(id => s"peaklist_id=$id").mkString(" OR ") ) { r =>
          val spectrumId = r.nextLong
          specNumById += (spectrumId -> specCount)
          specCount += 1
        }
      }

      specNumById
    }

  }

  def getSpectrumIndexByIdByPL(pklIds: Seq[Long]): mutable.LongMap[mutable.LongMap[Int]] = {

    if (pklIds == null || pklIds.isEmpty) {
      mutable.LongMap.empty[mutable.LongMap[Int]]
    } else {
      val specNumByIdByPeakList = new mutable.LongMap[mutable.LongMap[Int]]
      val specCountByPL = new mutable.LongMap[Int]

      DoJDBCWork.withEzDBC(msiDbCtx) { ezDBC =>
        ezDBC.selectAndProcess("SELECT id, peaklist_id FROM spectrum  WHERE peaklist_id IN (" + pklIds.mkString(",") + ") ORDER BY peaklist_id, first_time, first_scan") { r =>
          val spectrumId = r.nextLong
          val plId = r.nextLong
          val specNumById =specNumByIdByPeakList.getOrElseUpdate(plId,new mutable.LongMap[Int])
          var specCount =specCountByPL.getOrElseUpdate(plId,1)
          specNumById += (spectrumId -> specCount)
          specCountByPL.put(plId,specCount+1)
        }
      }
      specNumByIdByPeakList
    }

  }

  def getReadablePtmForResultSets( rsIds: Seq[Long]): mutable.Map[Long,PeptideReadablePtmString] ={
    val pepReadablePtmStringByPepId = mutable.Map.empty[Long,PeptideReadablePtmString]

    val getReadablePtmQuery = "SELECT peptide_id, readable_ptm_string FROM peptide_readable_ptm_string WHERE peptide_readable_ptm_string.result_set_id IN  (" + rsIds.mkString(",") + ") "
    DoJDBCWork.withEzDBC(msiDbCtx) { msiEzDBC =>
      msiEzDBC.selectAndProcess(getReadablePtmQuery){ resultSetRow  =>
        val pepId = resultSetRow.getLong("peptide_id")
        if(!pepReadablePtmStringByPepId.contains(pepId)) {
          val pepReadablePtmAsStr = resultSetRow.getString("readable_ptm_string")
          val pepReadablePtm = new PeptideReadablePtmString()
          pepReadablePtm.setReadablePtmString(pepReadablePtmAsStr)
          pepReadablePtmStringByPepId.put(pepId, pepReadablePtm)
        }
      }
    } // end of JDBC work
    pepReadablePtmStringByPepId
  }

    // Unimod Id are Long
  def getUnimodIdByPtmId(): Map[Long,Long] = {
     
    val unimodIdByPtmId = new HashMap[Long,Long]
     
    DoJDBCWork.withEzDBC(msiDbCtx) { ezDBC => {
      
        ezDBC.selectAndProcess( "SELECT id, unimod_id FROM ptm" ) { r =>
          val ptmId = toLong(r.nextAny)
          val unimodId = r.nextAny
          if(unimodId != null)
    	      unimodIdByPtmId += (ptmId -> toLong(unimodId) )       
  	      } 
       } 
    }
    
    Map() ++ unimodIdByPtmId
  } 
}