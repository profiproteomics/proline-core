package fr.proline.core.algo.msi.inference

import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.om.model.msi.ResultSummary

trait IProteinSetInferer {

  /**
   * Create a ResultSummary for specified resultSet.
   * All data will be considered while inferring Protein Sets
   * 
   * TODO : Specify a set of peptideMatch to consider   
   */
  def computeResultSummary( resultSet: ResultSet ): ResultSummary
  
}