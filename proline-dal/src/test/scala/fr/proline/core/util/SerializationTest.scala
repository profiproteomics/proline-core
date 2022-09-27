package fr.proline.core.util

import fr.profi.util.serialization.ProfiJson
import fr.proline.core.algo.lcms.DetectionMethod
import fr.proline.core.algo.msq.config.LabelFreeQuantConfigConverter
import fr.proline.core.om.model.msi.PtmDataSet
import org.junit.Test
import org.junit.Assert

class SerializationTest {

  @Test
  def testSerialzeXic1Param(): Unit = {
    val quantConfigAsStr = "{\"start_from_validated_peptides\":true,\"ft_mapping_params\":{\"moz_tol_unit\":\"PPM\",\"time_tol\":\"42.0\",\"moz_tol\":\"5.0\"},\"detect_peakels\":true,\"use_last_peakel_detection\":false,\"restrain_cross_assignment_to_reliable_features\":true,\"clustering_params\":{\"moz_tol_unit\":\"PPM\",\"intensity_computation\":\"MOST_INTENSE\",\"time_computation\":\"MOST_INTENSE\",\"time_tol\":\"15.0\",\"moz_tol\":\"5.0\"},\"perform_cross_assignment_inside_groups_only\":true,\"aln_method_name\":\"EXHAUSTIVE\",\"extraction_params\":{\"moz_tol_unit\":\"PPM\",\"moz_tol\":\"5.0\"},\"aln_params\":{\"max_iterations\":\"3\",\"ft_mapping_method_name\":\"PEPTIDE_IDENTITY\",\"ft_mapping_params\":{\"moz_tol_unit\":\"PPM\",\"time_tol\":\"300.0\",\"moz_tol\":\"5.0\"},\"mass_interval\":\"20000\",\"smoothing_method_name\":\"LOESS\",\"smoothing_params\":{\"window_size\":\"200\",\"window_overlap\":\"20\",\"min_window_landmarks\":\"50\"}},\"ft_filter\":{\"name\":\"INTENSITY\",\"value\":\"0.0\",\"operator\":\"GT\"},\"detect_features\":false}"
    val qcAsMap= ProfiJson.deserialize[Map[String,Object]](quantConfigAsStr)
    val quantConfigV2AsMap = LabelFreeQuantConfigConverter.convertFromV1(qcAsMap)
    Assert.assertEquals(quantConfigV2AsMap("detection_method_name"),DetectionMethod.DETECT_PEAKELS.toString)
    //TODO add some verification
  }

  @Test
  def testSerialzeXic2Param(): Unit = {
    val quantConfigAsStr = "{\"start_from_validated_peptides\":true,\"ft_mapping_params\":{\"moz_tol_unit\":\"PPM\",\"time_tol\":\"30.0\",\"moz_tol\":\"5.0\"},\"detect_peakels\":true,\"clustering_params\":{\"moz_tol_unit\":\"PPM\",\"intensity_computation\":\"MOST_INTENSE\",\"time_computation\":\"MOST_INTENSE\",\"time_tol\":\"15\",\"moz_tol\":\"5.0\"},\"aln_method_name\":\"ITERATIVE\",\"extraction_params\":{\"moz_tol_unit\":\"PPM\",\"moz_tol\":\"5.0\"},\"aln_params\":{\"max_iterations\":\"3\",\"ft_mapping_params\":{\"moz_tol_unit\":\"PPM\",\"time_tol\":\"30.0\",\"moz_tol\":\"5.0\"},\"ft_mapping_method_name\":\"FEATURE_COORDINATES\",\"mass_interval\":\"20000\",\"smoothing_method_name\":\"LOESS\",\"smoothing_params\":{\"window_size\":\"200\",\"window_overlap\":\"20\",\"min_window_landmarks\":\"50\"}},\"ft_filter\":{\"name\":\"INTENSITY\",\"value\":\"0.0\",\"operator\":\"GT\"},\"detect_features\":false}"
    val qcAsMap= ProfiJson.deserialize[Map[String,Object]](quantConfigAsStr)
    val quantConfigV2AsMap = LabelFreeQuantConfigConverter.convertFromV1(qcAsMap)
    Assert.assertEquals(quantConfigV2AsMap("detection_method_name"),DetectionMethod.DETECT_PEAKELS.toString)
    //TODO add some verification
  }

  @Test
  def testDeserialzePTMDataset(): Unit = {
    val ptmDataset: PtmDataSet = ProfiJson.deserialize[PtmDataSet](ptmJson)
    Assert.assertNotNull(ptmDataset)
    Assert.assertEquals(8, ptmDataset.ptmSites.length)
    Assert.assertEquals("1.0", ptmDataset.version)
    //TODO add some verification
  }

 val ptmJson = """{
 	"ptm_ids": [
 		1,
 		4,
 		28,
 		16
 	],
 	"leaf_result_summary_ids": [
 		11
 	],
 	"ptm_sites": [
 		{
 			"id": 0,
 			"protein_match_id": 215267,
 			"ptm_definition_id": 115,
 			"seq_position": 154,
 			"best_peptide_match_id": 194884,
 			"localization_confidence": 1.0,
 			"peptide_ids_by_ptm_position": {
 				"15": [
 					28910
 				]
 			},
 			"isomeric_peptide_ids": []
 		},
 		{
 			"id": 1,
 			"protein_match_id": 201176,
 			"ptm_definition_id": 115,
 			"seq_position": 27,
 			"best_peptide_match_id": 194884,
 			"localization_confidence": 1.0,
 			"peptide_ids_by_ptm_position": {
 				"15": [
 					28910
 				]
 			},
 			"isomeric_peptide_ids": [],
 			"is_nterminal": false,
 			"is_cterminal": false
 		},
 		{
 			"id": 386,
 			"protein_match_id": 228076,
 			"ptm_definition_id": 115,
 			"seq_position": 83,
 			"best_peptide_match_id": 230451,
 			"localization_confidence": 1.0,
 			"peptide_ids_by_ptm_position": {
 				"8": [
 					67675
 				]
 			},
 			"isomeric_peptide_ids": [],
 			"is_nterminal": false,
 			"is_cterminal": false
 		},
 		{
 			"id": 387,
 			"protein_match_id": 228076,
 			"ptm_definition_id": 115,
 			"seq_position": 78,
 			"best_peptide_match_id": 230451,
 			"localization_confidence": 1.0,
 			"peptide_ids_by_ptm_position": {
 				"3": [
 					67675
 				]
 			},
 			"isomeric_peptide_ids": [],
 			"is_nterminal": false,
 			"is_cterminal": false
 		},
 		{
 			"id": 388,
 			"protein_match_id": 217645,
 			"ptm_definition_id": 115,
 			"seq_position": 83,
 			"best_peptide_match_id": 230451,
 			"localization_confidence": 1.0,
 			"peptide_ids_by_ptm_position": {
 				"8": [
 					67675
 				]
 			},
 			"isomeric_peptide_ids": [],
 			"is_nterminal": false,
 			"is_cterminal": false
 		},
 		{
 			"id": 2824,
 			"protein_match_id": 201638,
 			"ptm_definition_id": 115,
 			"seq_position": 223,
 			"best_peptide_match_id": 228010,
 			"localization_confidence": 1.0,
 			"peptide_ids_by_ptm_position": {
 				"2": [
 					54026
 				]
 			},
 			"isomeric_peptide_ids": [],
 			"is_nterminal": false,
 			"is_cterminal": false
 		},
 		{
 			"id": 3461,
 			"protein_match_id": 228189,
 			"ptm_definition_id": 115,
 			"seq_position": 338,
 			"best_peptide_match_id": 210801,
 			"localization_confidence": 1.0,
 			"peptide_ids_by_ptm_position": {
 				"2": [
 					66767
 				]
 			},
 			"isomeric_peptide_ids": [],
 			"is_nterminal": false,
 			"is_cterminal": false
 		},
 		{
 			"id": 3462,
 			"protein_match_id": 228189,
 			"ptm_definition_id": 115,
 			"seq_position": 339,
 			"best_peptide_match_id": 210801,
 			"localization_confidence": 1.0,
 			"peptide_ids_by_ptm_position": {
 				"3": [
 					66767
 				]
 			},
 			"isomeric_peptide_ids": [],
 			"is_nterminal": false,
 			"is_cterminal": false
 		}
 	],
 	"ptm_clusters": [
 		{
 			"id": 1,
 			"ptm_site_locations": [
 				386,
 				387
 			],
 			"best_peptide_match_id": 230451,
 			"localization_confidence": 1.0,
 			"peptide_ids": [
 				67675
 			],
 			"isomeric_peptide_ids": [],
 			"selection_level": 2,
 			"selection_information": "Exact Position Matching"
 		},
 		{
 			"id": 2,
 			"ptm_site_locations": [
 				3461,
 				3462
 			],
 			"best_peptide_match_id": 210801,
 			"localization_confidence": 1.0,
 			"peptide_ids": [
 				66767
 			],
 			"isomeric_peptide_ids": [],
 			"selection_level": 2,
 			"selection_information": "Exact Position Matching"
 		}
 	]
 }"""



}
