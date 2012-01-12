package fr.proline.core.om.provider

import fr.proline.core.om.msi.PeptideClasses.PeptideInstance

trait IPeptideInstanceProvider {
  def getPeptideInstances( pepInstIds: Seq[Int] ): Array[PeptideInstance]
  
  def getPeptideInstance( pepInstId:Int ): PeptideInstance = {
    getPeptideInstances( Array(pepInstId) )(0)
  }
  
  def getResultSummariesPeptideInstances( resultSummaryIds: Seq[Int] ): Array[PeptideInstance]
  
  def getResultSummaryPeptideInstances( resultSummaryId: Int ): Array[PeptideInstance] = {
    getResultSummariesPeptideInstances( Array(resultSummaryId) )
  }
}