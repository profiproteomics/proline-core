package fr.proline.core.algo.msq

import fr.proline.core.om.model.msi.ProteinSet

case class SpectralCountConfig (
	parentRSMId : Option[Long],
	parentDSId : Option[Long],
	weightRefRSMIds : Seq[Long] = Seq.empty[Long]
)



/**
 * ProteinSet description for spectralCount calculation
 */
case class ProteinSetSCDescription(
			val proteinSet: ProteinSet, 
			val typicalPMAcc: String, 
			val refRSMPeptidesInfo: PeptidesSCDescription, 
			var peptideInfoByRSMId : Map[Long, PeptidesSCDescription] = Map.empty[Long, PeptidesSCDescription]
			)
		
/**
 * Peptide information for spectralCount calculation
 */
case class PeptidesSCDescription(		
			var pepSpecificIds: Seq[Long], 
			var nbrPSMSpecific: Int, 
			var weightByPeptideId: scala.collection.mutable.Map[Long, Float] = null)
