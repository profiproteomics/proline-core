package fr.proline.core.om.provider.msi

import fr.proline.core.om.model.msi.ResultSet
import fr.proline.context.DatabaseConnectionContext

trait IResultSetProvider {

  def getResultSetsAsOptions( resultSetIds: Seq[Long] ): Array[Option[ResultSet]]
  
  def getResultSets( resultSetIds: Seq[Long] ): Array[ResultSet]
  
  def getResultSet( resultSetId:Long ): Option[ResultSet] = { getResultSetsAsOptions( Array(resultSetId) )(0) }
 
}