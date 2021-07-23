package fr.proline.core.om.provider.msq.impl

import fr.proline.context.UdsDbConnectionContext
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables._
import fr.proline.core.dal.tables.uds.{UdsDbDataSetObjectTreeMapTable, UdsDbObjectTreeTable}
import fr.proline.core.orm.uds.ObjectTreeSchema.{SchemaName => ObjectTreeSchemaName}

class SQLPostProcessingConfigProvider(val udsDbCtx: UdsDbConnectionContext)  {
  
  private val DsObjectTreeMapTable = UdsDbDataSetObjectTreeMapTable
  private val ObjectTreeTable = UdsDbObjectTreeTable
  private val postProcessConfigSchemaName = ObjectTreeSchemaName.POST_QUANT_PROCESSING_CONFIG

  def getPostProcessingAsString(quantitationId:Long ): Option[String] = {
    
    DoJDBCReturningWork.withEzDBC(udsDbCtx) { udsEzDBC =>
      
      // Retrieve the object tree containing the quant config
      val objTreeQueryBuilder = new SelectQueryBuilder2(DsObjectTreeMapTable,ObjectTreeTable)
      val objTreeSqlQuery = objTreeQueryBuilder.mkSelectQuery( (t1,c1,t2,c2) => List(t2.CLOB_DATA) ->
        " WHERE "~ t1.DATA_SET_ID ~" = "~ quantitationId ~" AND "~ t1.OBJECT_TREE_ID ~" = "~ t2.ID ~
        " AND "~ t1.SCHEMA_NAME ~" = '"~ postProcessConfigSchemaName ~"'"
      )
      
      udsEzDBC.selectHeadOption(objTreeSqlQuery) { r =>
        r.nextString
      }
      
    } // END of DoJDBCReturningWork

  }
  
}