package fr.proline.core.om.provider.msq.impl

import java.util.NoSuchElementException

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
import fr.proline.core.orm.uds.ObjectTreeSchema.{SchemaName => ObjectTreeSchemaName}

class SQLQuantConfigProvider(val udsDbCtx: UdsDbConnectionContext) extends IQuantConfigProvider {
  
  import ObjectTreeSchemaName._
  
  private val quantMethodProvider = new SQLQuantMethodProvider(udsDbCtx)
  
  private val DsObjectTreeMapTable = UdsDbDataSetObjectTreeMapTable
  private val ObjectTreeTable = UdsDbObjectTreeTable

  def getQuantConfigAndMethod( quantitationId:Long ): Option[(IQuantConfig,IQuantMethod)] = {

    val quantConfigAsStrAndquantMethodOpt = getQuantConfigAsString(quantitationId)

    if (!quantConfigAsStrAndquantMethodOpt.isDefined) return None
    val (quantConfigAsStr, schemaName, quantMethod) = quantConfigAsStrAndquantMethodOpt.get

    val quantConfig: IQuantConfig = schemaName match {
      case LABEL_FREE_QUANT_CONFIG => ProfiJson.deserialize[LabelFreeQuantConfig](quantConfigAsStr)
      case ISOBARIC_TAGGING_QUANT_CONFIG => ProfiJson.deserialize[IsobaricTaggingQuantConfig](quantConfigAsStr)
      case AGGREGATION_QUANT_CONFIG=> ProfiJson.deserialize[AggregationQuantConfig](quantConfigAsStr)
      case _ => throw new Exception("this quant method is not supported yet")
    }
    Some(quantConfig, quantMethod)
  }

  def getQuantConfigAsString( quantitationId:Long ): Option[(String,ObjectTreeSchemaName, IQuantMethod)] = {
    
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

      try {
        val quantConfigAsStr = udsEzDBC.selectString(objTreeSqlQuery)
        Some((quantConfigAsStr, schemaName, quantMethod))
      } catch {
        case e: NoSuchElementException => {   //No objectTree found. In case of label free, test for aggregation params
          if(schemaName.equals(LABEL_FREE_QUANT_CONFIG)) {
            val objTreeSqlQuery2 = objTreeQueryBuilder.mkSelectQuery((t1, c1, t2, c2) => List(t2.CLOB_DATA) ->
              " WHERE " ~ t1.DATA_SET_ID ~ " = " ~ quantitationId ~ " AND " ~ t1.OBJECT_TREE_ID ~ " = " ~ t2.ID ~
                " AND " ~ t1.SCHEMA_NAME ~ " = '" ~ AGGREGATION_QUANT_CONFIG ~ "'"
            )

            try {
              val quantConfigAsStr = udsEzDBC.selectString(objTreeSqlQuery2)
              return Some((quantConfigAsStr, AGGREGATION_QUANT_CONFIG, quantMethod))
            } catch {
              case e: NoSuchElementException => return None
            }
          }
          return None
        }
      }

    } // END of DoJDBCReturningWork

  }
  
}