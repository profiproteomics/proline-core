package fr.proline.core.om.provider

import fr.proline.core.om.model.msi.PeptideInstance


trait IPeptideInstanceProvider {
  def getPeptideInstances( pepInstIds: Seq[Int] ): Array[Option[PeptideInstance]]
  
  def getPeptideInstance( pepInstId:Int ): Option[PeptideInstance] = {
    getPeptideInstances( Array(pepInstId) )(0)
  }
  
  def getResultSummariesPeptideInstances( resultSummaryIds: Seq[Int] ): Array[Option[PeptideInstance]]
  
  def getResultSummaryPeptideInstances( resultSummaryId: Int ): Array[Option[PeptideInstance]] = {
    getResultSummariesPeptideInstances( Array(resultSummaryId) )
  }
}