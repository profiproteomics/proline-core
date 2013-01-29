package fr.proline.core.om.provider.msi

import fr.proline.core.om.model.msi.PeptideSet
import fr.proline.context.DatabaseConnectionContext

trait IPeptideSetProvider {
  
  val msiDbCtx: DatabaseConnectionContext
  val psDbCtx: DatabaseConnectionContext
  
  def getPeptideSetsAsOptions( protSetIds: Seq[Int] ): Array[Option[PeptideSet]]
  
  def getPeptideSets( protSetIds: Seq[Int] ): Array[PeptideSet]
  
  def getPeptideSet( protSetId:Int ): Option[PeptideSet] = {
    getPeptideSetsAsOptions( Array(protSetId) )(0)
  }
  
  def getResultSummariesPeptideSets( resultSummaryIds: Seq[Int] ): Array[PeptideSet]
  
  def getResultSummaryPeptideSets( resultSummaryId: Int ): Array[PeptideSet] = {
    getResultSummariesPeptideSets( Array(resultSummaryId) )
  }
}