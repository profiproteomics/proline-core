package fr.proline.core.algo.msi

import fr.proline.core.om.model.msi.ResultSet

/**
 * @author David Bouyssie
 *
 */
trait IResultSetSplitter {
  
  def split(rs: ResultSet, acDecoyRegex: util.matching.Regex): Tuple2[ResultSet, ResultSet]

}