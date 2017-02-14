package fr.proline.core.om.provider.msq.impl

import fr.profi.util.serialization.ProfiJson
import fr.proline.context.UdsDbConnectionContext
import fr.proline.core.algo.msq.config._
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.tables._
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.uds.UdsDbDataSetObjectTreeMapTable
import fr.proline.core.dal.tables.uds.UdsDbObjectTreeTable
import fr.proline.core.om.model.msq._
import fr.proline.core.om.provider.msq.IQuantConfigProvider
import fr.proline.core.orm.uds.ObjectTreeSchema.{ SchemaName => ObjectTreeSchemaName }

class SQLQuantConfigProvider(val udsDbCtx: UdsDbConnectionContext) extends IQuantConfigProvider {
  
  import ObjectTreeSchemaName._
  
  private val quantMethodProvider = new SQLQuantMethodProvider(udsDbCtx)
  
  private val DsObjectTreeMapTable = UdsDbDataSetObjectTreeMapTable
  private val ObjectTreeTable = UdsDbObjectTreeTable
  
  def getQuantConfigAndMethod( quantitationId:Long ): Option[(IQuantConfig,IQuantMethod)] = {
    
    DoJDBCReturningWork.withEzDBC(udsDbCtx) { udsEzDBC =>
      
      val quantMethodOpt = quantMethodProvider.getQuantitationQuantMethod(quantitationId)
      if (quantMethodOpt.isEmpty) return None
      
      val quantMethod = quantMethodOpt.get
      
      val schemaName = quantMethod match {
        case LabelFreeQuantMethod => LABEL_FREE_QUANT_CONFIG
        case iqMethod: IsobaricTaggingQuantMethod => ISOBARIC_TAGGING_QUANT_CONFIG
        case _ => throw new Exception("this quant method is not supported yet")
      }
      
      // Retrieve the object tree containing the quant config
      val objTreeQueryBuilder = new SelectQueryBuilder2(DsObjectTreeMapTable,ObjectTreeTable)
      val objTreeSqlQuery = objTreeQueryBuilder.mkSelectQuery( (t1,c1,t2,c2) => List(t2.CLOB_DATA) ->
        " WHERE "~ t1.DATA_SET_ID ~" = "~ quantitationId ~" AND "~ t1.OBJECT_TREE_ID ~" = "~ t2.ID ~
        " AND "~ t1.SCHEMA_NAME ~" = '"~ schemaName ~"'"
      )
      
      val quantConfigAsStr = udsEzDBC.selectString(objTreeSqlQuery)
      
      val quantConfig: IQuantConfig = schemaName match {
        case LABEL_FREE_QUANT_CONFIG => ProfiJson.deserialize[LabelFreeQuantConfig](quantConfigAsStr)
        case ISOBARIC_TAGGING_QUANT_CONFIG => ProfiJson.deserialize[IsobaricTaggingQuantConfig](quantConfigAsStr)
        case _ => throw new Exception("this quant method is not supported yet")
      }
      
      Some( (quantConfig,quantMethod) )
      
    } // END of DoJDBCReturningWork

  }
  
}