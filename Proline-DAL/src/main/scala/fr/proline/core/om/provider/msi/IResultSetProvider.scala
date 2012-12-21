package fr.proline.core.om.provider.msi

import fr.proline.core.om.model.msi.ResultSet
import fr.proline.repository.DatabaseContext

trait IResultSetProvider {

  def getResultSetsAsOptions( resultSetIds: Seq[Int], pdiDb: DatabaseContext, psDb: DatabaseContext, msiDb: DatabaseContext ): Array[Option[ResultSet]]
  
  def getResultSets( resultSetIds: Seq[Int], pdiDb: DatabaseContext, psDb: DatabaseContext, msiDb: DatabaseContext ): Array[ResultSet]
  
  def getResultSet( resultSetId:Int, pdiDb: DatabaseContext, psDb: DatabaseContext, msiDb: DatabaseContext ): Option[ResultSet] = { getResultSetsAsOptions( Array(resultSetId), pdiDb, psDb, msiDb )(0) }
 
}