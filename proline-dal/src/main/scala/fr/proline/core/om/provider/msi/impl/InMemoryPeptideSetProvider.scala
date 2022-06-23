package fr.proline.core.om.provider.msi.impl

import fr.proline.core.om.provider.msi.IPeptideSetProvider
import fr.proline.core.om.model.msi.PeptideSet
import fr.proline.context.DatabaseConnectionContext

class InMemoryPeptideSetProvider( peptideSets: Seq[PeptideSet] ) extends IPeptideSetProvider {
  
  lazy val peptideSetById = peptideSets.view.map( p => p.id -> p ).toMap
  lazy val peptideSetsByRsmId = peptideSets.groupBy( _.resultSummaryId )

  def getPeptideSetsAsOptions( pepSetIds: Seq[Long] ): Array[Option[PeptideSet]] = {
    pepSetIds.map { this.peptideSetById.get(_) }.toArray
  }
  
  def getPeptideSets( pepSetIds: Seq[Long] ): Array[PeptideSet] = {
    this.getPeptideSetsAsOptions( pepSetIds ).withFilter( _.isDefined ).map( _.get )
  }
  
  def getResultSummariesPeptideSets( rsmIds: Seq[Long] ): Array[PeptideSet] = {
    rsmIds.flatMap { i => this.peptideSetsByRsmId.getOrElse( i, Seq.empty[PeptideSet] ) }.toArray
  }
  
}