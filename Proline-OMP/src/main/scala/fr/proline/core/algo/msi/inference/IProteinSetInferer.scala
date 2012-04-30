package fr.proline.core.algo.msi.inference

import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.om.model.msi.ResultSummary

trait IProteinSetInferer {

  def computeResultSummary( resultSet: ResultSet ): ResultSummary
  
}