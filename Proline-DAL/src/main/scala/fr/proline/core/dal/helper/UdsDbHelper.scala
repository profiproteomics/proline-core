package fr.proline.core.dal.helper

import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.uds.UdsDbDataSetTable
import fr.proline.core.orm.uds.Dataset.DatasetType

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
  
  def getIdentificationRSIdsByParentDSId( parentDsId: Int ): Seq[Int] = {
    
    DoJDBCReturningWork.withEzDBC( udsDbCtx, { ezDBC =>
      
      ezDBC.selectInts( datasetQB.mkSelectQuery( (t,cols) => 
        List(t.RESULT_SET_ID) ->
        " WHERE "~ t.PARENT_DATASET_ID ~"="~ parentDsId ~
        " AND "~ t.TYPE ~"="~ DatasetType.IDENTIFICATION
      ) )
    
    })

  }
  
}