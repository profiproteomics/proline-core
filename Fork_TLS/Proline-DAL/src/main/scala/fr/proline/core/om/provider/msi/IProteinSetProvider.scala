package fr.proline.core.om.provider.msi

import fr.proline.core.om.model.msi.ProteinSet
import fr.proline.context.DatabaseConnectionContext

trait IProteinSetProvider {
  
  def getProteinSetsAsOptions( protSetIds: Seq[Long] ): Array[Option[ProteinSet]]
  
  def getProteinSets( protSetIds: Seq[Long] ): Array[ProteinSet]
  
  def getProteinSet( protSetId:Long ): Option[ProteinSet] = {
    getProteinSetsAsOptions( Array(protSetId) )(0)
  }
  
  def getResultSummariesProteinSets( resultSummaryIds: Seq[Long] ): Array[ProteinSet]
  
  def getResultSummaryProteinSets( resultSummaryId: Long ): Array[ProteinSet] = {
    getResultSummariesProteinSets( Array(resultSummaryId) )
  }
}