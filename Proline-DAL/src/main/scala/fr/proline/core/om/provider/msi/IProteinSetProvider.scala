package fr.proline.core.om.provider.msi

import fr.proline.core.om.model.msi.ProteinSet
import fr.proline.repository.DatabaseContext

trait IProteinSetProvider {
  
  val msiDbCtx: DatabaseContext
  
  def getProteinSetsAsOptions( protSetIds: Seq[Int] ): Array[Option[ProteinSet]]
  
  def getProteinSets( protSetIds: Seq[Int] ): Array[ProteinSet]
  
  def getProteinSet( protSetId:Int ): Option[ProteinSet] = {
    getProteinSetsAsOptions( Array(protSetId) )(0)
  }
  
  def getResultSummariesProteinSets( resultSummaryIds: Seq[Int] ): Array[ProteinSet]
  
  def getResultSummaryProteinSets( resultSummaryId: Int ): Array[ProteinSet] = {
    getResultSummariesProteinSets( Array(resultSummaryId) )
  }
}