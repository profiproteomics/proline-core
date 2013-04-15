package fr.proline.core.algo.msq

import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.om.model.msq.MasterQuantPeptide
import fr.proline.core.om.model.msq.MasterQuantProteinSet
import fr.proline.core.orm.uds.MasterQuantitationChannel

/**
 * @author David Bouyssie
 *
 */
trait IQuantifierAlgo {

  def computeMasterQuantPeptides(
    udsMasterQuantChannel: MasterQuantitationChannel,
    mergedRSM: ResultSummary,
    resultSummaries: Seq[ResultSummary]
  ): Array[MasterQuantPeptide]
  
 def computeMasterQuantProteinSets(
   udsMasterQuantChannel: MasterQuantitationChannel,
   masterQuantPeptides: Seq[MasterQuantPeptide],
   mergedResultSummary: ResultSummary,                                     
   resultSummaries: Seq[ResultSummary]
 ): Array[MasterQuantProteinSet]
  
}