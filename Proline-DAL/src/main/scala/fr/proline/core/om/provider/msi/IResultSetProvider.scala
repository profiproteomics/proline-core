package fr.proline.core.om.provider.msi

import fr.proline.core.om.model.msi.ResultSet
import fr.proline.repository.DatabaseContext

trait IResultSetProvider {
  
  val msiDbCtx: DatabaseContext
  val psDbCtx: DatabaseContext
  val pdiDbCtx: DatabaseContext

  def getResultSetsAsOptions( resultSetIds: Seq[Int] ): Array[Option[ResultSet]]
  
  def getResultSets( resultSetIds: Seq[Int] ): Array[ResultSet]
  
  def getResultSet( resultSetId:Int ): Option[ResultSet] = { getResultSetsAsOptions( Array(resultSetId) )(0) }
 
}