package fr.proline.core.om.provider.msi
import fr.proline.core.om.model.msi.ProteinSet



trait IProteinSetProvider {
  def getProteinSets( protSetIds: Seq[Int] ): Array[Option[ProteinSet]]
  
  def getProteinSet( protSetId:Int ): Option[ProteinSet] = {
    getProteinSets( Array(protSetId) )(0)
  }
  
  def getResultSummariesProteinSets( resultSummaryIds: Seq[Int] ): Array[Option[ProteinSet]]
  
  def getResultSummaryProteinSets( resultSummaryId: Int ): Array[Option[ProteinSet]] = {
    getResultSummariesProteinSets( Array(resultSummaryId) )
  }
}