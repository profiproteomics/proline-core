package fr.proline.core.om.provider.msi

import fr.proline.core.om.model.msi.PeptideSet
import fr.proline.context.DatabaseConnectionContext

trait IPeptideSetProvider {
  
  def getPeptideSetsAsOptions( pepSetIds: Seq[Long] ): Array[Option[PeptideSet]]
  
  def getPeptideSets( pepSetIds: Seq[Long] ): Array[PeptideSet]
  
  def getPeptideSet( pepSetId:Long ): Option[PeptideSet] = {
    getPeptideSetsAsOptions( Array(pepSetId) )(0)
  }
  
  def getResultSummariesPeptideSets( resultSummaryIds: Seq[Long] ): Array[PeptideSet]
  
  def getResultSummaryPeptideSets( resultSummaryId: Long ): Array[PeptideSet] = {
    getResultSummariesPeptideSets( Array(resultSummaryId) )
  }
}