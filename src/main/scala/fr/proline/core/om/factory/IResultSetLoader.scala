package fr.proline.core.om.factory

import fr.proline.core.om.msi.ResultSetClasses.ResultSet

trait IResultSetLoader {

  def getResultSets( resultSetIds: Seq[Int] ): Array[ResultSet]
  
  def getResultSet( resultSetId:Int ): ResultSet = { getResultSets( Array(0) )(0) }
 
}