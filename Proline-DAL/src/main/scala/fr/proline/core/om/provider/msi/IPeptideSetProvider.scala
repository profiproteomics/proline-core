package fr.proline.core.om.provider.msi

import fr.proline.core.om.model.msi.PeptideSet
import fr.proline.context.DatabaseConnectionContext

trait IPeptideSetProvider {
  
  def getPeptideSetsAsOptions( protSetIds: Seq[Long] ): Array[Option[PeptideSet]]
  
  def getPeptideSets( protSetIds: Seq[Long] ): Array[PeptideSet]
  
  def getPeptideSet( protSetId:Long ): Option[PeptideSet] = {
    getPeptideSetsAsOptions( Array(protSetId) )(0)
  }
  
  def getResultSummariesPeptideSets( resultSummaryIds: Seq[Long] ): Array[PeptideSet]
  
  def getResultSummaryPeptideSets( resultSummaryId: Long ): Array[PeptideSet] = {
    getResultSummariesPeptideSets( Array(resultSummaryId) )
  }
}