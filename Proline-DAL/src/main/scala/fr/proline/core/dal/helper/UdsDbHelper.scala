package fr.proline.core.dal.helper

import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.uds.UdsDbDataSetTable
import fr.proline.core.orm.uds.Dataset.DatasetType
import scala.collection.mutable.HashMap

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
  
  def getDatasetsFirstChildrenIds( dsIds: Seq[Int] ): Array[Int] = {
    
    DoJDBCReturningWork.withEzDBC( udsDbCtx, { ezDBC =>
      
      ezDBC.selectInts( datasetQB.mkSelectQuery( (t,cols) => 
        List(t.RESULT_SET_ID) ->
        " WHERE "~ t.PARENT_DATASET_ID ~" IN("~ dsIds.mkString(",") ~")"
      ) )
    
    })

  }
  
  def getDatasetsChildrenIds( dsIds: Array[Int], isRoot: Boolean = true ): Array[Int] = {
    
    val childrenIds = if( dsIds.length == 0 ) Array.empty[Int]
    else this.getDatasetsChildrenIds(this.getDatasetsFirstChildrenIds(dsIds),false)
    
    if( isRoot ) childrenIds
    else dsIds ++ childrenIds
  }
  
  def fillDatasetHierarchyIdMap( dsIds: Seq[Int], hierachyMap: HashMap[Int,Int] ) {
    
    val firstChildrenIds = if( dsIds.length == 0 ) Array.empty[Int]
    else this.getDatasetsFirstChildrenIds(dsIds)
    
    if( firstChildrenIds.length > 0 ) {
      for( parentDsId <- dsIds; childDsId <- firstChildrenIds ) {
        hierachyMap += childDsId -> parentDsId
      }
      this.fillDatasetHierarchyIdMap(firstChildrenIds, hierachyMap)
    }

    ()
  }
  
  def getIdentificationIdsForParentDSId( parentDsId: Int ): Array[Int] = {
    
    // Retrieve all children ids recursively
    val childrenIds = this.getDatasetsChildrenIds( Array(parentDsId) )
    
    // Retrieve RS ids of IDENTIFICATION datasets
    DoJDBCReturningWork.withEzDBC( udsDbCtx, { ezDBC =>
      
      ezDBC.selectInts( datasetQB.mkSelectQuery( (t,cols) => 
        List(t.ID) ->
        " WHERE "~ t.ID ~" IN("~ childrenIds.mkString(",") ~")" ~
        " AND "~ t.TYPE ~"= '"~ DatasetType.IDENTIFICATION ~ "'"
      ) )
    
    })

  }
  
  def getRSIdByIdentificationId( identIds: Seq[Int] ): Map[Int,Int] = {
    
    DoJDBCReturningWork.withEzDBC( udsDbCtx, { ezDBC =>
      
      ezDBC.select( datasetQB.mkSelectQuery( (t,cols) => 
        List(t.ID,t.RESULT_SET_ID) ->
        " WHERE "~ t.ID ~" IN("~ identIds.mkString(",") ~")"
      ) ) { r =>
        r.nextInt -> r.nextInt
      } toMap
    
    })

  }
  
  def getRSIdByIdentificationIdForParentDSId( parentDsId: Int ): Map[Int,Int] = {
    
    DoJDBCReturningWork.withEzDBC( udsDbCtx, { ezDBC =>
      
      ezDBC.select( datasetQB.mkSelectQuery( (t,cols) => 
        List(t.ID,t.RESULT_SET_ID) ->
        " WHERE "~ t.PARENT_DATASET_ID ~"="~ parentDsId ~
        " AND "~ t.TYPE ~"= '"~ DatasetType.IDENTIFICATION ~ "'"
      ) ) { r =>
        r.nextInt -> r.nextInt
      } toMap
    
    })

  }
  
}