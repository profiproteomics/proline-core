package fr.proline.core.om.provider

import fr.proline.core.om.msi.ResultSetClasses.ResultSet

trait IResultSetProvider {

  def getResultSets( resultSetIds: Seq[Int] ): Array[Option[ResultSet]]
  
  def getResultSet( resultSetId:Int ): Option[ResultSet] = { getResultSets( Array(0) )(0) }
 
}