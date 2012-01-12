package fr.proline.core.om.provider

import fr.proline.core.om.msi.ProteinClasses.ProteinSet

trait IProteinSetProvider {
  def getProteinSets( protSetIds: Seq[Int] ): Array[ProteinSet]
  
  def getProteinSet( protSetId:Int ): ProteinSet = {
    getProteinSets( Array(protSetId) )(0)
  }
  
  def getResultSummariesProteinSets( resultSummaryIds: Seq[Int] ): Array[ProteinSet]
  
  def getResultSummaryProteinSets( resultSummaryId: Int ): Array[ProteinSet] = {
    getResultSummariesProteinSets( Array(resultSummaryId) )
  }
}