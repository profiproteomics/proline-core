package fr.proline.core.dal.helper

import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.uds.UdsDbDataSetTable
import fr.proline.core.orm.uds.Dataset.DatasetType
import scala.collection.mutable.HashMap
import fr.proline.util.primitives._

class UdsDbHelper( udsDbCtx: DatabaseConnectionContext ) {
  
  val datasetQB = new SelectQueryBuilder1(UdsDbDataSetTable)
  
  @throws( classOf[NoSuchElementException] )
  def getLastProjectIdentificationNumber( projectId: Int): Int = {
    
    DoJDBCReturningWork.withEzDBC( udsDbCtx, { ezDBC =>
      
      ezDBC.selectInt( datasetQB.mkSelectQuery( (t,cols) => 
        List(t.NUMBER) ->
        " WHERE "~ t.PROJECT_ID ~"="~ projectId ~
        " AND "~ t.TYPE ~" = '"~ DatasetType.IDENTIFICATION ~"'" ~
        " ORDER BY "~ t.NUMBER ~" DESC LIMIT 1"
      ))
      
      /*ezDBC.selectInt(
        "SELECT number FROM data_set" +
        " WHERE project_id = "+projectId +
        " AND type = 'IDENTIFICATION' " +
        " ORDER BY number DESC LIMIT 1"
      )*/
    
    })

  }
  
  def getDatasetProjectId( dsId: Int): Int = {
    
    DoJDBCReturningWork.withEzDBC( udsDbCtx, { ezDBC =>
      
      ezDBC.selectInt( datasetQB.mkSelectQuery( (t,cols) => 
        List(t.PROJECT_ID) -> "WHERE "~ t.ID ~ "="~ dsId
      ) )
    
    })

  }
  
  def getDatasetsFirstChildrenIds( dsIds: Seq[Long] ): Array[Long] = {
    
    DoJDBCReturningWork.withEzDBC( udsDbCtx, { ezDBC =>
      
      ezDBC.selectLongs( datasetQB.mkSelectQuery( (t,cols) => 
        List(t.RESULT_SET_ID) ->
        " WHERE "~ t.PARENT_DATASET_ID ~" IN("~ dsIds.mkString(",") ~")"
      ) )
    
    })

  }
  
  def getDatasetsChildrenIds( dsIds: Array[Long], isRoot: Boolean = true ): Array[Long] = {
    
    val childrenIds = if( dsIds.length == 0 ) Array.empty[Long]
    else this.getDatasetsChildrenIds(this.getDatasetsFirstChildrenIds(dsIds),false)
    
    if( isRoot ) childrenIds
    else dsIds ++ childrenIds
  }
  
  def fillDatasetHierarchyIdMap( dsIds: Seq[Long], hierachyMap: HashMap[Long,Long] ) {
    
    val firstChildrenIds = if( dsIds.length == 0 ) Array.empty[Long]
    else this.getDatasetsFirstChildrenIds(dsIds)
    
    if( firstChildrenIds.length > 0 ) {
      for( parentDsId <- dsIds; childDsId <- firstChildrenIds ) {
        hierachyMap += childDsId -> parentDsId
      }
      this.fillDatasetHierarchyIdMap(firstChildrenIds, hierachyMap)
    }

    ()
  }
  
  def getIdentificationIdsForParentDSId( parentDsId: Long ): Array[Long] = {
    
    // Retrieve all children ids recursively
    val childrenIds = this.getDatasetsChildrenIds( Array(parentDsId) )
    
    // Retrieve RS ids of IDENTIFICATION datasets
    DoJDBCReturningWork.withEzDBC( udsDbCtx, { ezDBC =>
      
      ezDBC.selectLongs( datasetQB.mkSelectQuery( (t,cols) => 
        List(t.ID) ->
        " WHERE "~ t.ID ~" IN("~ childrenIds.mkString(",") ~")" ~
        " AND "~ t.TYPE ~"= '"~ DatasetType.IDENTIFICATION ~ "'"
      ) )
    
    })

  }
  
  def getRSIdByIdentificationId( identIds: Seq[Long] ): Map[Long,Long] = {
    
    DoJDBCReturningWork.withEzDBC( udsDbCtx, { ezDBC =>
      
      ezDBC.select( datasetQB.mkSelectQuery( (t,cols) => 
        List(t.ID,t.RESULT_SET_ID) ->
        " WHERE "~ t.ID ~" IN("~ identIds.mkString(",") ~")"
      ) ) { r =>
        toLong(r.nextAny) ->toLong(r.nextAny)
      } toMap
    
    })

  }
  
  def getRSIdByIdentificationIdForParentDSId( parentDsId: Long ): Map[Long, Long] = {
    
    DoJDBCReturningWork.withEzDBC( udsDbCtx, { ezDBC =>
      
      ezDBC.select( datasetQB.mkSelectQuery( (t,cols) => 
        List(t.ID,t.RESULT_SET_ID) ->
        " WHERE "~ t.PARENT_DATASET_ID ~"="~ parentDsId ~
        " AND "~ t.TYPE ~"= '"~ DatasetType.IDENTIFICATION ~ "'"
      ) ) { r =>
        toLong(r.nextAny) -> toLong(r.nextAny)
      } toMap
    
    })

  }
  
}