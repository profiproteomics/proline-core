package fr.proline.core.om.provider.msi

import fr.proline.core.om.model.msi.PeptideSet
import fr.proline.context.DatabaseConnectionContext

trait IPeptideSetProvider {
  
  def getPeptideSetsAsOptions( pepSetIds: collection.Seq[Long] ): Array[Option[PeptideSet]]
  
  def getPeptideSets( pepSetIds: collection.Seq[Long] ): Array[PeptideSet]
  
  def getPeptideSet( pepSetId:Long ): Option[PeptideSet] = {
    getPeptideSetsAsOptions( Array(pepSetId) )(0)
  }
  
  def getResultSummariesPeptideSets( resultSummaryIds: collection.Seq[Long] ): Array[PeptideSet]
  
  def getResultSummaryPeptideSets( resultSummaryId: Long ): Array[PeptideSet] = {
    getResultSummariesPeptideSets( Array(resultSummaryId) )
  }
}