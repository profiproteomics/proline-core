package fr.proline.core.om.provider.msq.impl

import fr.profi.util.serialization.ProfiJson
import fr.proline.context.UdsDbConnectionContext
import fr.proline.core.algo.msq.config.profilizer.ProfilizerConfig
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.tables._
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.uds.UdsDbDataSetObjectTreeMapTable
import fr.proline.core.dal.tables.uds.UdsDbObjectTreeTable
import fr.proline.core.om.model.msq._
import fr.proline.core.om.provider.msq.IProfilizerConfigProvider
import fr.proline.core.orm.uds.ObjectTreeSchema.{ SchemaName => ObjectTreeSchemaName }

class SQLProfilizerConfigProvider(val udsDbCtx: UdsDbConnectionContext) extends IProfilizerConfigProvider {
  
  private val DsObjectTreeMapTable = UdsDbDataSetObjectTreeMapTable
  private val ObjectTreeTable = UdsDbObjectTreeTable
  private val profilizerConfigSchemaName = ObjectTreeSchemaName.POST_QUANT_PROCESSING_CONFIG
  
  def getProfilizerConfig( quantitationId:Long ): Option[ProfilizerConfig] = {
    
    DoJDBCReturningWork.withEzDBC(udsDbCtx) { udsEzDBC =>
      
      // Retrieve the object tree containing the quant config
      val objTreeQueryBuilder = new SelectQueryBuilder2(DsObjectTreeMapTable,ObjectTreeTable)
      val objTreeSqlQuery = objTreeQueryBuilder.mkSelectQuery( (t1,c1,t2,c2) => List(t2.CLOB_DATA) ->
        " WHERE "~ t1.DATA_SET_ID ~" = "~ quantitationId ~" AND "~ t1.OBJECT_TREE_ID ~" = "~ t2.ID ~
        " AND "~ t1.SCHEMA_NAME ~" = '"~ profilizerConfigSchemaName ~"'"
      )
      
      udsEzDBC.selectHeadOption(objTreeSqlQuery) { r =>
        val profilizerConfigAsStr = r.nextString
        ProfiJson.deserialize[ProfilizerConfig](profilizerConfigAsStr)
      }
      
    } // END of DoJDBCReturningWork

  }
  
}