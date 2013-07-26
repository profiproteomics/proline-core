package fr.proline.core.om.provider.msi

import fr.proline.core.om.model.msi.ResultSet
import fr.proline.context.DatabaseConnectionContext

case class ResultSetFilter(
  val maxPeptideMatchRank: Int
)

trait IResultSetProvider {

  def getResultSetsAsOptions( resultSetIds: Seq[Long], resultSetFilter: Option[ResultSetFilter] = None ): Array[Option[ResultSet]]
  
  def getResultSets( resultSetIds: Seq[Long], resultSetFilter: Option[ResultSetFilter] = None ): Array[ResultSet]
  
  def getResultSet( resultSetId:Long, resultSetFilter: Option[ResultSetFilter] = None ): Option[ResultSet] = {
    getResultSetsAsOptions( Array(resultSetId), resultSetFilter )(0)
  }
 
}