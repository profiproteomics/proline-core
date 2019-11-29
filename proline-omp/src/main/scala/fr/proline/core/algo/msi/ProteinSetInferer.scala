package fr.proline.core.algo.msi

import fr.proline.core.algo.msi.filtering.IPeptideInstanceFilter
import fr.proline.core.algo.msi.validation.{BuildPeptideInstanceBuilder, IPeptideInstanceBuilder, PeptideInstanceBuilders}
import fr.proline.core.om.model.msi.{ResultSet, ResultSummary}
import inference._

trait IProteinSetInferer {

  /**
   * Create a ResultSummary for specified resultSet.
   * Only validated peptideMatch will be considered while inferring Protein Sets
   *
   */
  def computeResultSummary( resultSet: ResultSet, keepSubsummableSubsets: Boolean, peptideInstanceFilters: Option[Seq[IPeptideInstanceFilter]] = None) : ResultSummary

}

object InferenceMethod extends Enumeration {
  val PARSIMONIOUS = Value("PARSIMONIOUS")
}

object ProteinSetInferer {
  
  def apply( methodName: InferenceMethod.Value, peptideInstanceBuilder: IPeptideInstanceBuilder = BuildPeptideInstanceBuilder(PeptideInstanceBuilders.STANDARD)): IProteinSetInferer = {
    methodName match {
      case InferenceMethod.PARSIMONIOUS => new ParsimoniousProteinSetInferer(peptideInstanceBuilder)
    }
  }

}