package fr.proline.core.om.provider

import fr.proline.core.om.msi.ResultSetClasses.ResultSet

trait IResultSetProvider {

  def getResultSets( resultSetIds: Seq[Int] ): Array[ResultSet]
  
  def getResultSet( resultSetId:Int ): ResultSet = { getResultSets( Array(0) )(0) }
 
}