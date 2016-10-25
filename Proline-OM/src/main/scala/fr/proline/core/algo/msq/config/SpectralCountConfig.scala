package fr.proline.core.algo.msq.config

import fr.proline.core.om.model.msi.ProteinSet

case class SpectralCountConfig(
  // TODO: rename into identResultSummaryId to be consistent with the MQC properties
  parentRSMId: Option[Long],
  // TODO: rename into identDatasetId to be consistent with the MQC properties
  parentDSId: Option[Long],
  // TODO: rename to weightsRefRsmIds to be consistent with the MQC properties
  weightRefRSMIds: Seq[Long] = Seq.empty[Long]
) extends IQuantConfig

/**
 * ProteinSet description for spectralCount calculation
 */
case class ProteinSetSCDescription(
  val proteinSet: ProteinSet,
  val typicalPMAcc: String,
  val refRSMPeptidesInfo: PeptidesSCDescription,
  var peptideInfoByRSMId: Map[Long, PeptidesSCDescription] = Map.empty[Long, PeptidesSCDescription]
)

/**
 * Peptide information for spectralCount calculation
 */
case class PeptidesSCDescription(
  var pepSpecificIds: Seq[Long],
  var nbrPSMSpecific: Int,
  var weightByPeptideId: scala.collection.mutable.Map[Long, Float] = null
)
