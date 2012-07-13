package fr.proline.core.om.provider.msi.impl

import fr.proline.core.om.provider.msi.IPeptideSetProvider
import fr.proline.core.om.model.msi.PeptideSet

class InMemoryPeptideSetProvider( peptideSets: Seq[PeptideSet] ) extends IPeptideSetProvider {
  
  lazy val peptideSetById = Map() ++ peptideSets.map { p => p.id -> p }
  lazy val peptideSetsByRsmId = peptideSets.groupBy( _.resultSummaryId )

  def getPeptideSetsAsOptions( pepSetIds: Seq[Int] ): Array[Option[PeptideSet]] = {
    pepSetIds.map { this.peptideSetById.get(_) } toArray
  }
  
  def getPeptideSets( pepSetIds: Seq[Int] ): Array[PeptideSet] = {
    this.getPeptideSetsAsOptions( pepSetIds ).filter( _ != None ).map( _.get )
  }
  
  def getResultSummariesPeptideSets( rsmIds: Seq[Int] ): Array[PeptideSet] = {
    rsmIds.flatMap { i => this.peptideSetsByRsmId.getOrElse( i, Seq.empty[PeptideSet] ) } toArray
  }
  
}