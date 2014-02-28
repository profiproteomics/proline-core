package fr.proline.core.service.msq.quantify

import fr.proline.core.om.model.msi.ProteinSet


/*** 
 * List properties String used in JSON SC result. The output result should be formatted as 
 *     
*	"{"SpectralCountResult":{[
*	{"rsm_id":<id>,"proteins_spectral_counts":[{"protein_accession"=<protAcc>,"bsc"=<valBSC>,"ssc"=<valSSC>,"wsc"=<valWSC>}]},
*	{"rsm_id":<id>,"proteins_spectral_counts":[{"protein_accession"=<protAcc>,"bsc"=<valBSC>,"ssc"=<valSSC>,"wsc"=<valWSC>}]}
*	 ]}}"
 */
object SpectralCountsJSONProperties {
  
    final val rootPropName : String = "\"SpectralCountResult\""
    final val rsmIDPropName : String = "\"rsm_id\""
    final val protSCsListPropName : String = "\"proteins_spectral_counts\""
    final val protACPropName : String = "\"protein_accession\""
    final val protMatchId : String = "\"prot_match_id\""
    final val protSetId : String = "\"prot_set_id\""      
    final val protSetStatus : String ="\"prot_status\""
    final val pepNbr : String ="\"pep_nbr\""
    final val bscPropName : String = "\"bsc\""
    final val sscPropName : String = "\"ssc\""
    final val wscPropName : String = "\"wsc\""
           
}

/**
 * Case clas to group the 3 different SC 
 */
case class SpectralCountsStruct( val basicSC : Float, val specificSC : Float, val weightedSC : Float)


/**
 * ProteinSet description for spectralCount calculation
 */
case class ProteinSetPeptidesDescription(
			val proteinSet: ProteinSet, 
			val typicalPMAcc: String, 
			val nbrPepSpecific: Int, 
			val weightByPeptideId: scala.collection.mutable.Map[Long, Float] = null)
			