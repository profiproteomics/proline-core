package fr.proline.core.om.provider.msi.impl

import fr.profi.util.collection._
import fr.proline.core.om.provider.msi.IPeptideInstanceProvider
import fr.proline.core.om.model.msi.PeptideInstance
import fr.proline.context.DatabaseConnectionContext

// TODO: create a generic implementation ???
class InMemoryPeptideInstanceProvider( peptideInstances: Array[PeptideInstance] ) extends IPeptideInstanceProvider {
  
  protected lazy val peptideInstanceById = peptideInstances.mapByLong( _.id )
  protected lazy val peptideInstancesByRsmId = peptideInstances.groupByLong( _.resultSummaryId )

  def getPeptideInstancesAsOptions( pepSetIds: Seq[Long] ): Array[Option[PeptideInstance]] = {
    pepSetIds.map { this.peptideInstanceById.get(_) }.toArray
  }
  
  def getPeptideInstances( pepSetIds: Seq[Long] ): Array[PeptideInstance] = {
    this.getPeptideInstancesAsOptions( pepSetIds ).withFilter( _.isDefined ).map( _.get )
  }
  
  def getResultSummariesPeptideInstances( rsmIds: Seq[Long] ): Array[PeptideInstance] = {
    rsmIds.toArray.flatMap { i => this.peptideInstancesByRsmId.getOrElse( i, Array.empty[PeptideInstance] ) }
  }
  
}