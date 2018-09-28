package fr.proline.core.om.provider.msq.impl

import fr.profi.jdbc.easy.EasyDBC
import fr.profi.util.serialization.ProfiJson
import fr.proline.context.UdsDbConnectionContext
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables._
import fr.proline.core.dal.tables.uds.UdsDbDataSetTable
import fr.proline.core.dal.tables.uds.UdsDbQuantLabelTable
import fr.proline.core.dal.tables.uds.UdsDbQuantMethodTable
import fr.proline.core.om.model.msq._
import fr.proline.core.om.provider.msq.IQuantMethodProvider

class SQLQuantMethodProvider(val udsDbCtx: UdsDbConnectionContext) extends IQuantMethodProvider {
  
  import AbundanceUnit._
  import QuantMethodType._

  private val DatasetTable = UdsDbDataSetTable
  private val QuantMethodTable = UdsDbQuantMethodTable
  private val QuantLabelTable = UdsDbQuantLabelTable
  
  private val QuantMethodCols = QuantMethodTable.columns
  private val QuantLabelCols = QuantLabelTable.columns
  
  def getQuantMethod( quantMethodId:Long ): Option[IQuantMethod] = {
    
    DoJDBCReturningWork.withEzDBC(udsDbCtx) { udsEzDBC =>
      
      // Retrieve the quant method
      val quantMethodQueryBuilder = new SelectQueryBuilder1(QuantMethodTable)
      val quantMethodSqlQuery = quantMethodQueryBuilder.mkSelectQuery( (t,c) => c.filter(_ != t.ID) ->
        " WHERE "~ t.ID ~" = "~ quantMethodId
      )
      
      val quantMethodOpt = udsEzDBC.selectHeadOption(quantMethodSqlQuery) { r =>
        val quantMethodType = QuantMethodType.withName(r.getString(QuantMethodCols.TYPE))
        val abundanceUnit = AbundanceUnit.withName(r.getString(QuantMethodCols.ABUNDANCE_UNIT))
        //val serializedProperties = r.nextString
        
        quantMethodType match {
          case LABEL_FREE => {
            abundanceUnit match {
              case FEATURE_INTENSITY => LabelFreeQuantMethod
              case _ => throw new Exception("can't load this quant method")
            }
          }
          case ISOBARIC_TAG => {
            val quantLabels = this._getQuantLabels[IsobaricTag](quantMethodId, udsEzDBC)
            IsobaricTaggingQuantMethod(quantLabels)
          }
          case _ => throw new Exception("can't load this quant method")
        }
      }
      
      quantMethodOpt
      
    } // END of DoJDBCReturningWork

  }
  
  def getQuantitationQuantMethod( quantitationId:Long ): Option[IQuantMethod] = {
    
    DoJDBCReturningWork.withEzDBC(udsDbCtx) { udsEzDBC =>
      
      // Retrieve the quant method id
      val quantMethodIdSqlQuery = new SelectQueryBuilder1(DatasetTable).mkSelectQuery( (t1,c1) => List(t1.QUANT_METHOD_ID) ->
        " WHERE "~ t1.ID ~" = "~ quantitationId
      )
      val quantMethodId = udsEzDBC.selectLong(quantMethodIdSqlQuery)
      
      this.getQuantMethod(quantMethodId)
    }
    
  }
  
  private def _getQuantLabels[T <: IQuantLabel]( quantMethodId: Long, udsEzDBC: EasyDBC ): List[T] = {
    
    import QuantLabelType._
    
    // Retrieve the quant labels
    val quantTableQueryBuilder = new SelectQueryBuilder1(QuantLabelTable)
    val quantTableSqlQuery = quantTableQueryBuilder.mkSelectQuery( (t,allCols) => allCols ->
      " WHERE "~ t.QUANT_METHOD_ID ~" = "~ quantMethodId
    )
    
    udsEzDBC.select(quantTableSqlQuery) { r =>
      val quantLabelId = r.getLong(QuantLabelCols.ID)
      val quantLabelName = r.getString(QuantLabelCols.NAME)
      val quantLabelTypeAsStr = r.getString(QuantLabelCols.TYPE)
      val serializedProperties = r.getString(QuantLabelCols.SERIALIZED_PROPERTIES)
      
      val quantLabelTypeOpt = QuantLabelType.maybeNamed(quantLabelTypeAsStr) 
      
      quantLabelTypeOpt match {
        case Some(ISOBARIC_TAG) => IsobaricTag(
          id = quantLabelId,
          name = quantLabelName,
          properties = ProfiJson.deserialize[IsobaricTagProperties](serializedProperties)
        ).asInstanceOf[T]
        case _ => throw new Exception(s"the label $quantLabelTypeAsStr is not supported yet")
      }
      
    } toList
  }
}