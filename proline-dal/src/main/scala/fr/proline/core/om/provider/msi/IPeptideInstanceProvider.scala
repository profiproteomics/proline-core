package fr.proline.core.om.provider.msi

import fr.proline.core.om.model.msi.PeptideInstance
import fr.proline.context.DatabaseConnectionContext

trait IPeptideInstanceProvider {
  
  /**
   *  Get PeptideInstances (wrapped in Option) with specified Ids.
   *  If no PeptideInstance is defined for a specified id, Option.None will be returned.
   *  Returned Array will contains Option[PeptideInstance] in the same order as their specified ids.
   *  @param pepInstIds: Sequence of ids of PeptideInstance to search for
   *  @return Array of Option[PeptideInstance] corresponding to found PeptideInstance
   */
  def getPeptideInstancesAsOptions( pepInstIds: Seq[Long] ): Array[Option[PeptideInstance]]
  
  /**
   *  Get PeptideInstances with specified Ids.
   *  @param pepInstIds: Sequence of ids of PeptideInstance to search for
   *  @return Array of PeptideInstance corresponding to found PeptideInstance
   */
  def getPeptideInstances( pepInstIds: Seq[Long] ): Array[PeptideInstance]
  
  /**
   *  Get PeptideInstance (wrapped in Option) with specified Id.
   *  If no PeptideInstance is defined for specified id, Option.None will be returned.
   *  @param pepInstId: id of PeptideInstance to search for
   *  @return Option[PeptideInstance] corresponding to found PeptideInstance
   */
  def getPeptideInstance( pepInstId:Long ): Option[PeptideInstance] = {
    getPeptideInstancesAsOptions( Array(pepInstId) )(0)
  }
  
  /**
   *  Get PeptideInstance (wrapped in Option) associated to ResultSummary with specified Ids.
   *  @param resultSummaryIds: Sequence of ResultSummary ids to get PeptideInstance for
   *  @return Array of Option[PeptideInstance]belonging to specified one of the specified ResultSummary
   */
  def getResultSummariesPeptideInstances( resultSummaryIds: Seq[Long] ): Array[PeptideInstance]
  
  /**
   *  Get PeptideInstance (wrapped in Option) associated to ResultSummary with specified Id.
   *  @param resultSummaryId: ResultSummary id to ger PeptideInstance for
   *  @return Array of Option[PeptideInstance]belonging to specified ResultSummary
   */
  def getResultSummaryPeptideInstances( resultSummaryId: Long ): Array[PeptideInstance] = {
    getResultSummariesPeptideInstances( Array(resultSummaryId) )
  }
}