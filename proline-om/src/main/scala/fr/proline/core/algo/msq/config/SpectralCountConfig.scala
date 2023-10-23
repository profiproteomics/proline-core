package fr.proline.core.algo.msq.config

import fr.proline.core.om.model.msi.ProteinSet
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

case class SpectralCountConfig(
  @JsonDeserialize(contentAs = classOf[java.lang.Long] )
  identResultSummaryId: Option[Long],
  @JsonDeserialize(contentAs = classOf[java.lang.Long] )
  identDatasetId : Option[Long],  
  weightsRefRsmIds: Array[Long] = Array.empty[Long]  
) extends IQuantConfig

/**
 * ProteinSet description for spectralCount calculation
 */
case class ProteinSetSCDescription(
  val proteinSet: ProteinSet,
  val typicalPMAcc: String,
  val samesetPMAcc: Set[String],
  val refRSMPeptidesInfo: PeptidesSCDescription,
  var peptideInfoByRSMId: Map[Long, PeptidesSCDescription] = Map.empty[Long, PeptidesSCDescription]
)

/**
 * Peptide information for spectralCount calculation
 */
case class PeptidesSCDescription(
  var pepSpecificIds: Set[Long],
  var nbrPSMSpecific: Int,
  var weightByPeptideId: scala.collection.mutable.Map[Long, Float] = null
)
