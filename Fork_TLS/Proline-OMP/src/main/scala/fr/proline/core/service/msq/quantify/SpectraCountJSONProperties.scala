package fr.proline.core.service.msq.quantify



/*** 
 * List properties String used in JSON SC result or in assocated serialized properties. The output result should be formatted as 
 *     
*	"{"spectral_count_result":{[
*	{"rsm_id":<id>,"proteins_spectral_counts":[ { "protein_accession"=<protAcc>, "prot_match_id"=<pmId_idfRSM>, "prot_set_id"=<psId_idfRSM>, "prot_status"=<typical,sameset,subset>,"pep_nbr"=Int,"bsc"=<valBSC>,"ssc"=<valSSC>,"wsc"=<valWSC>}, {...}]},
*	{"rsm_id":<id>,"proteins_spectral_counts":[ { "protein_accession"=<protAcc>, "prot_match_id"=<pmId_idfRSM>, "prot_set_id"=<psId_idfRSM>, "prot_status"=<typical,sameset,subset>,"pep_nbr"=Int,"bsc"=<valBSC>,"ssc"=<valSSC>,"wsc"=<valWSC>}, {...}]},
*	 ]}}"
 */
object SpectralCountsJSONProperties {
  
  /** 
   * WeightedSC result properties
   */
    final val rootPropName : String = "\"spectral_count_result\""
    final val rsmIDPropName : String = "\"rsm_id\""
    final val protSCsListPropName : String = "\"proteins_spectral_counts\""
    final val protACPropName : String = "\"protein_accession\""
    final val protMatchId : String = "\"prot_match_id\""
    final val protSetId : String = "\"prot_set_id\""      
    final val protMatchStatus : String ="\"prot_status\""
    final val pepNbr : String ="\"pep_nbr\""
    final val bscPropName : String = "\"bsc\""
    final val sscPropName : String = "\"ssc\""
    final val wscPropName : String = "\"wsc\""
           
}

