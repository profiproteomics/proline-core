package fr.proline.core.util

import fr.profi.util.serialization.ProfiJson
import fr.proline.core.algo.msq.config.LabelFreeQuantConfigConverter
import org.junit.Test

class SerializationTest {

  @Test
  def testSerialzeXic1Param() {
    val quantConfigAsStr = "{\"start_from_validated_peptides\":true,\"ft_mapping_params\":{\"moz_tol_unit\":\"PPM\",\"time_tol\":\"42.0\",\"moz_tol\":\"5.0\"},\"detect_peakels\":true,\"use_last_peakel_detection\":false,\"restrain_cross_assignment_to_reliable_features\":true,\"clustering_params\":{\"moz_tol_unit\":\"PPM\",\"intensity_computation\":\"MOST_INTENSE\",\"time_computation\":\"MOST_INTENSE\",\"time_tol\":\"15.0\",\"moz_tol\":\"5.0\"},\"perform_cross_assignment_inside_groups_only\":true,\"aln_method_name\":\"EXHAUSTIVE\",\"extraction_params\":{\"moz_tol_unit\":\"PPM\",\"moz_tol\":\"5.0\"},\"aln_params\":{\"max_iterations\":\"3\",\"ft_mapping_method_name\":\"PEPTIDE_IDENTITY\",\"ft_mapping_params\":{\"moz_tol_unit\":\"PPM\",\"time_tol\":\"300.0\",\"moz_tol\":\"5.0\"},\"mass_interval\":\"20000\",\"smoothing_method_name\":\"LOESS\",\"smoothing_params\":{\"window_size\":\"200\",\"window_overlap\":\"20\",\"min_window_landmarks\":\"50\"}},\"ft_filter\":{\"name\":\"INTENSITY\",\"value\":\"0.0\",\"operator\":\"GT\"},\"detect_features\":false}"
    val qcAsMap= ProfiJson.deserialize[Map[String,Object]](quantConfigAsStr)
    val quantConfigV2AsMap = LabelFreeQuantConfigConverter.convertFromV1(qcAsMap)
    //TODO add some verification
  }

  @Test
  def testSerialzeXic2Param() {
    val quantConfigAsStr = "{\"start_from_validated_peptides\":true,\"ft_mapping_params\":{\"moz_tol_unit\":\"PPM\",\"time_tol\":\"30.0\",\"moz_tol\":\"5.0\"},\"detect_peakels\":true,\"clustering_params\":{\"moz_tol_unit\":\"PPM\",\"intensity_computation\":\"MOST_INTENSE\",\"time_computation\":\"MOST_INTENSE\",\"time_tol\":\"15\",\"moz_tol\":\"5.0\"},\"aln_method_name\":\"ITERATIVE\",\"extraction_params\":{\"moz_tol_unit\":\"PPM\",\"moz_tol\":\"5.0\"},\"aln_params\":{\"max_iterations\":\"3\",\"ft_mapping_params\":{\"moz_tol_unit\":\"PPM\",\"time_tol\":\"30.0\",\"moz_tol\":\"5.0\"},\"ft_mapping_method_name\":\"FEATURE_COORDINATES\",\"mass_interval\":\"20000\",\"smoothing_method_name\":\"LOESS\",\"smoothing_params\":{\"window_size\":\"200\",\"window_overlap\":\"20\",\"min_window_landmarks\":\"50\"}},\"ft_filter\":{\"name\":\"INTENSITY\",\"value\":\"0.0\",\"operator\":\"GT\"},\"detect_features\":false}"
    val qcAsMap= ProfiJson.deserialize[Map[String,Object]](quantConfigAsStr)
    val quantConfigV2AsMap = LabelFreeQuantConfigConverter.convertFromV1(qcAsMap)
    //TODO add some verification
  }

}
