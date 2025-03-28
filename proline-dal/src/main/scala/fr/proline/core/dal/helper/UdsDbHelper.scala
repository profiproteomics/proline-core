package fr.proline.core.dal.helper

import scala.util.matching.Regex
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.uds.UdsDbDataSetTable
import fr.proline.core.dal.tables.uds.UdsDbProteinMatchDecoyRuleTable
import fr.proline.core.dal.tables.uds.UdsDbMasterQuantChannelTable
import fr.proline.core.dal.tables.uds.UdsDbQuantChannelTable
import fr.proline.core.orm.uds.Dataset.DatasetType

import scala.collection.mutable.HashMap
import fr.profi.util.primitives._

import scala.collection.mutable

class UdsDbHelper(udsDbCtx: DatabaseConnectionContext) {

  val datasetQB = new SelectQueryBuilder1(UdsDbDataSetTable)

  @throws(classOf[NoSuchElementException])
  def getLastProjectIdentificationNumber(projectId: Long): Int = {

    DoJDBCReturningWork.withEzDBC(udsDbCtx){ ezDBC =>

      ezDBC.selectInt(datasetQB.mkSelectQuery((t, cols) =>
        List(t.NUMBER) ->
          " WHERE " ~ t.PROJECT_ID ~ "=" ~ projectId ~
          " AND " ~ t.TYPE ~ " = '" ~ DatasetType.IDENTIFICATION ~ "'" ~
          " ORDER BY " ~ t.NUMBER ~ " DESC LIMIT 1"
      ))

      /*ezDBC.selectInt(
        "SELECT number FROM data_set" +
        " WHERE project_id = "+projectId +
        " AND type = 'IDENTIFICATION' " +
        " ORDER BY number DESC LIMIT 1"
      )*/

    } //end DoJDBCReturningWork

  }

  def getDatasetProjectId(dsId: Long): Long = {

    DoJDBCReturningWork.withEzDBC(udsDbCtx) { ezDBC =>

      ezDBC.selectLong(datasetQB.mkSelectQuery((t, cols) =>
        List(t.PROJECT_ID) -> "WHERE " ~ t.ID ~ "=" ~ dsId
      ))

    }//end DoJDBCReturningWork

  }

  def getDatasetsFirstChildrenIds(dsIds: Seq[Long]): Array[Long] = {

    if ((dsIds == null) || dsIds.isEmpty) {
      Array.empty[Long]
    } else {
      DoJDBCReturningWork.withEzDBC(udsDbCtx) { ezDBC =>

        ezDBC.selectLongs(datasetQB.mkSelectQuery((t, cols) =>
          List(t.RESULT_SET_ID) ->
            " WHERE " ~ t.PARENT_DATASET_ID ~ " IN(" ~ dsIds.mkString(",") ~ ")"
        ))

      }//end DoJDBCReturningWork
    }

  }

  def getDatasetsChildrenIds(dsIds: Array[Long], isRoot: Boolean = true): Array[Long] = {

    val childrenIds = if (dsIds.length == 0) Array.empty[Long]
    else this.getDatasetsChildrenIds(this.getDatasetsFirstChildrenIds(dsIds), false)

    if (isRoot) childrenIds
    else dsIds ++ childrenIds
  }

  def getDatasetsLeaveIds(dsId: Long): Array[Long] = {
    val leaveDSIds = Array.newBuilder[Long]
    val childIds = getDatasetsFirstChildrenIds(Array(dsId))
    if(! childIds.isEmpty)
      getDatasetsLeaveIds(childIds, leaveDSIds)
    Array(dsId)
  }

  private def  getDatasetsLeaveIds(dsIds: Array[Long], leaveIds: mutable.ArrayBuilder[Long]) {
    val nonLeafDSIdsBuilder = Array.newBuilder[Long]
    dsIds.foreach(dsId=> {
      val childIds = getDatasetsFirstChildrenIds(Array(dsId))
      if(! childIds.isEmpty)
        nonLeafDSIdsBuilder ++= childIds
      else
        leaveIds += dsId
    })

    val nonLeafDSIds = nonLeafDSIdsBuilder.result()
    if(!nonLeafDSIds.isEmpty)
      getDatasetsLeaveIds(nonLeafDSIds, leaveIds)
  }

  def fillDatasetHierarchyIdMap(dsIds: Seq[Long], hierachyMap: HashMap[Long, Long]) {

    val firstChildrenIds = if (dsIds.length == 0) Array.empty[Long]
    else this.getDatasetsFirstChildrenIds(dsIds)

    if (firstChildrenIds.length > 0) {
      for (parentDsId <- dsIds; childDsId <- firstChildrenIds) {
        hierachyMap += childDsId -> parentDsId
      }
      this.fillDatasetHierarchyIdMap(firstChildrenIds, hierachyMap)
    }

    ()
  }

  def getIdentificationIdsForParentDSId(parentDsId: Long): Array[Long] = {

    // Retrieve all children ids recursively
    val childrenIds = getDatasetsChildrenIds(Array(parentDsId))

    if ((childrenIds == null) || childrenIds.isEmpty) {
      Array.empty[Long]
    } else {
      // Retrieve RS ids of IDENTIFICATION datasets
      DoJDBCReturningWork.withEzDBC(udsDbCtx) { ezDBC =>

        ezDBC.selectLongs(datasetQB.mkSelectQuery((t, cols) =>
          List(t.ID) ->
            " WHERE " ~ t.ID ~ " IN(" ~ childrenIds.mkString(",") ~ ")" ~
            " AND " ~ t.TYPE ~ "= '" ~ DatasetType.IDENTIFICATION ~ "'"
        ))

      }//end DoJDBCReturningWork
    }

  }

  def getRSIdByIdentificationId(identIds: Seq[Long]): Map[Long, Long] = {

    if ((identIds == null) || identIds.isEmpty) {
      Map.empty[Long, Long]
    } else {
      DoJDBCReturningWork.withEzDBC(udsDbCtx) { ezDBC =>

        ezDBC.select(datasetQB.mkSelectQuery((t, cols) =>
          List(t.ID, t.RESULT_SET_ID) ->
            " WHERE " ~ t.ID ~ " IN(" ~ identIds.mkString(",") ~ ")"
        )) { r =>
          toLong(r.nextAny) -> toLong(r.nextAny)
        }.toMap

      }//end DoJDBCReturningWork
    }

  }

  def getRSIdByIdentificationIdForParentDSId(parentDsId: Long): Map[Long, Long] = {

    DoJDBCReturningWork.withEzDBC(udsDbCtx) { ezDBC =>

      ezDBC.select(datasetQB.mkSelectQuery((t, cols) =>
        List(t.ID, t.RESULT_SET_ID) ->
          " WHERE " ~ t.PARENT_DATASET_ID ~ "=" ~ parentDsId ~
          " AND " ~ t.TYPE ~ "= '" ~ DatasetType.IDENTIFICATION ~ "'"
      )) { r =>
        toLong(r.nextAny) -> toLong(r.nextAny)
      }.toMap

    }//end DoJDBCReturningWork

  }

  def getProteinMatchDecoyRegexById(): Map[Long, Regex] = {

    DoJDBCReturningWork.withEzDBC(udsDbCtx) { ezDBC =>

      val sqlQuery = new SelectQueryBuilder1(UdsDbProteinMatchDecoyRuleTable).mkSelectQuery(
        (t1, c1) => List(t1.ID, t1.AC_DECOY_TAG) -> ""
      )

      ezDBC.select(sqlQuery) { r =>
        toLong(r.nextAny) -> new Regex(r.nextString)
      }.toMap

    }//end DoJDBCReturningWork

  }

  def getMasterQuantChannelQuantRsmId(masterQuantChannelId: Long): Option[Long] = {

    DoJDBCReturningWork.withEzDBC(udsDbCtx) { ezDBC =>
      val sqlQuery = new SelectQueryBuilder1(UdsDbMasterQuantChannelTable).mkSelectQuery(
        (t1, c1) => List(t1.QUANT_RESULT_SUMMARY_ID) ->
          " WHERE " ~ t1.ID ~ " = " ~ masterQuantChannelId
      )

      ezDBC.selectHeadOption(sqlQuery) { _.nextLong }
    }//end DoJDBCReturningWork

  }

  def getQuantChannelIds(masterQuantChannelId: Long): Array[Long] = {

    DoJDBCReturningWork.withEzDBC(udsDbCtx) { ezDBC =>
      val quantChannelQuery = new SelectQueryBuilder1(UdsDbQuantChannelTable).mkSelectQuery(
        (t1, c1) => List(t1.ID) ->
          " WHERE " ~ t1.MASTER_QUANT_CHANNEL_ID ~ " = " ~ masterQuantChannelId ~
          " ORDER BY " ~ t1.NUMBER
      )

      ezDBC.selectLongs(quantChannelQuery)
    }//end DoJDBCReturningWork

  }
  
  def getQuantChannelIdByIdentRsmId(masterQuantChannelId: Long): Map[Long,Long] = {

    DoJDBCReturningWork.withEzDBC(udsDbCtx) { ezDBC =>
      val rsmIdForquantChannelQuery = new SelectQueryBuilder1(UdsDbQuantChannelTable).mkSelectQuery(
        (t1, c1) => List(t1.IDENT_RESULT_SUMMARY_ID, t1.ID) ->
        " WHERE " ~ t1.MASTER_QUANT_CHANNEL_ID ~ " = " ~ masterQuantChannelId)

      ezDBC.select(rsmIdForquantChannelQuery) { r =>
        r.nextLong -> r.nextLong
      }.toMap
    }// end DoJDBCReturningWork

  }

  def getMasterQuantChannelIdsForQuantId(quantDatasetId: Long): Array[Long] = {

    DoJDBCReturningWork.withEzDBC(udsDbCtx) { ezDBC =>
      val quantChannelQuery = new SelectQueryBuilder1(UdsDbMasterQuantChannelTable).mkSelectQuery(
        (t1, c1) => List(t1.ID) ->
          " WHERE " ~ t1.QUANTITATION_ID ~ " = " ~ quantDatasetId ~
          " ORDER BY " ~ t1.NUMBER
      )

      ezDBC.selectLongs(quantChannelQuery)
    }//end DoJDBCReturningWork

  }

  def getQuantitationId(masterQuantChannelId: Long): Option[Long] = {

    DoJDBCReturningWork.withEzDBC(udsDbCtx) { ezDBC =>
      val sqlQuery = new SelectQueryBuilder1(UdsDbMasterQuantChannelTable).mkSelectQuery(
        (t1, c1) => List(t1.QUANTITATION_ID) ->
          " WHERE " ~ t1.ID ~ " = " ~ masterQuantChannelId
      )

      ezDBC.selectHeadOption(sqlQuery) { _.nextLong }
    }//end DoJDBCReturningWork

  }

}