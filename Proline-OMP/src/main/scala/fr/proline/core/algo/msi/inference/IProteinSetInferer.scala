package fr.proline.core.algo.msi.inference

import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.context.IExecutionContext

trait IProteinSetInferer {

  /**
   * Create a ResultSummary for specified resultSet.
   * Only validated peptideMatch will be considered while inferring Protein Sets
   * 
   *     
   */
  def computeResultSummary( resultSet: ResultSet ) : ResultSummary
  
}