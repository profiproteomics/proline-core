package fr.proline.core.algo.lcms

import fr.profi.util.ms.MassTolUnit
import fr.profi.util.serialization.ProfiJson
import fr.profi.util.serialization.ProfiJson.deserialize
import fr.proline.core.algo.lcms.filtering.{Filter, FilterOperator}
import fr.proline.core.algo.msq.config.{ExtractionParams, LabelFreeQuantConfig, LabelFreeQuantConfigConverter, MzToleranceParams}
import fr.proline.core.om.msi.{AbstractSerializationSpec, SerializationSpecif}
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class SerializationSpec extends AbstractSerializationSpec {

    val quantConfig = LabelFreeQuantConfig(
      extractionParams = ExtractionParams( // for extraction and MS2 mapping
        mozTol = 5.0,
        mozTolUnit = MassTolUnit.PPM.toString
      ),
      clusteringParams = ClusteringParams(
        mozTol = 5.0,
        mozTolUnit = MassTolUnit.PPM.toString,
        timeTol = 15f, // in seconds
        intensityComputation = ClusterIntensityComputation.MOST_INTENSE.toString,
        timeComputation = ClusterTimeComputation.MOST_INTENSE.toString
      ),
      alignmentConfig = Some(AlignmentConfig(
        methodName = AlnMethod.ITERATIVE,
        methodParams = Some(AlignmentParams(
          massInterval = Some(20000), // big value mean not used
          maxIterations = Some(3)
        )),
        smoothingMethodName = AlnSmoothing.TIME_WINDOW,
        smoothingMethodParams = Some(AlnSmoothingParams(
          windowSize = 60, // seconds
          windowOverlap = 20, // percents
          minWindowLandmarks = Some(50)
        )),
        ftMappingMethodName = FeatureMappingMethod.FEATURE_COORDINATES,
        ftMappingParams = FeatureMappingParams(
          mozTol = Some(5.0),
          mozTolUnit = Some(MassTolUnit.PPM.toString),
          timeTol = 300
        )
      )),
      crossAssignmentConfig = Some(CrossAssignmentConfig(
        methodName = CrossAssignMethod.BETWEEN_ALL_RULS,
        ftMappingParams = CrossAssignFeatMappingParams(
          mozTol = Some(5.0),
          mozTolUnit = Some(MassTolUnit.PPM.toString),
          timeTol = Some(30f),
          useAutomaticTimeTol = false,
          maxAutoTimeTol = None,
          minAutoTimeTol = None,
          useMozCalibration = false
        ),
        ftFilter = Some(Filter(
          name = FeatureFilterType.INTENSITY.toString,
          operator = FilterOperator.GT.toString,
          value = 0.0
        ))
      )),
      normalizationMethod = Some(NormalizationMethod.MEDIAN_RATIO),
      detectionMethodName = DetectionMethod.DETECT_PEAKELS,
      detectionParams = Some(DetectionParams(
        psmMatchingParams = Some(MzToleranceParams(mozTol = 5.0, mozTolUnit = "PPM"))
      )),
      mozCalibrationSmoothingMethod = MozCalibrationSmoothing.LOESS,
      mozCalibrationSmoothingParams = None
    )

    val expectedOutput =
      """{"config_version":"3.0","extraction_params":{"moz_tol":5.0,"moz_tol_unit":"ppm"},"clustering_params":{""" +
        """"moz_tol":5.0,"moz_tol_unit":"ppm","time_tol":15.0,"intensity_computation":"MOST_INTENSE","time_comput""" +
        """ation":"MOST_INTENSE"},"alignment_config":{"method_name":"ITERATIVE","method_params":{"mass_interval":""" +
        """20000,"max_iterations":3},"smoothing_method_name":"TIME_WINDOW","smoothing_method_params":{"window_siz""" +
        """e":60,"window_overlap":20,"min_window_landmarks":50},"ft_mapping_method_name":"FEATURE_COORDINATES","f""" +
        """t_mapping_params":{"moz_tol":5.0,"moz_tol_unit":"ppm","time_tol":300.0}},"moz_calibration_smoothing_method""""+
        """:"LOESS","cross_assignment_config":{"method_name":"BETWEEN_ALL_RUNS","ft_mapping_params":{"moz_tol":5.0,"moz_tol_unit":"""+
        """"ppm","time_tol":30.0,"use_moz_calibration":false,"""+
        """"use_automatic_time_tol":false},"restrain_to_reliable_features":true,"ft_filter":{"name":"INTENSITY","operator":"GT","value":""" +
        """0.0}},"normalization_method":"MEDIAN_RATIO","detection_method_name":"DETECT_PEAKELS","detection_params""" +
        """":{"psm_matching_params":{"moz_tol":5.0,"moz_tol_unit":"PPM"}},"use_last_peakel_detection":false}"""


    val jsonSpecifs = List(
      SerializationSpecif(
        "A LabelFree Quant Config",
        profiDeserializer = Some(jsonData => ProfiJson.deserialize[LabelFreeQuantConfig](jsonData)),
        quantConfig, expectedOutput)
    )

    this.checkJsonSpecifs(jsonSpecifs)


    val v1ParamsStr = """{"extraction_params":{"moz_tol":5.0,"moz_tol_unit":"ppm"},"clustering_params":"""+
      """{"moz_tol":5.0,"moz_tol_unit":"ppm","time_tol":15.0,"intensity_computation":"MOST_INTENSE","""+
      """"time_computation":"MOST_INTENSE"},"aln_method_name":"ITERATIVE","aln_params":{"mass_interval":20000,"""+
      """"smoothing_method_name":"TIME_WINDOW","smoothing_params":{"window_size":60,"window_overlap":20,"""+
      """"min_window_landmarks":50},"ft_mapping_params":{"moz_tol":5.0,"moz_tol_unit":"ppm","time_tol":300.0},"""+
      """"max_iterations":3},"ft_filter":{"name":"INTENSITY","operator":"GT","value":0.0},"ft_mapping_params":"""+
      """{"moz_tol":5.0,"moz_tol_unit":"ppm","time_tol":30.0},"normalization_method":"MEDIAN_RATIO","""+
      """"detect_features":false,"detect_peakels":true,"start_from_validated_peptides":true,"""+
      """"use_last_peakel_detection":false,"perform_cross_assignment_inside_groups_only":false, "config_version":"1.0", "restrain_cross_assignment_to_reliable_features":false}"""

    val v1ParamsAsMap = ProfiJson.deserialize[Map[String,Any]](v1ParamsStr)
    val lastParamsAsMap = LabelFreeQuantConfigConverter.convertFromV1(v1ParamsAsMap)
    val lastParamsStr = ProfiJson.serialize(lastParamsAsMap)

    val lastCfgParams = ProfiJson.deserialize[LabelFreeQuantConfig](lastParamsStr)
    logger.info("OUTPUT last Cfg  OBJECT :\n"+ lastCfgParams)


  val lastQuantConfigExpectedStr =
    """{"config_version": "3.0","extraction_params":{"moz_tol_unit":"PPM","moz_tol":"5.0"},"""+
    """"clustering_params":{"moz_tol_unit":"PPM","intensity_computation":"MOST_INTENSE","time_computation":"MOST_INTENSE","time_tol":15.0,"moz_tol":"5.0"},"""+
    """"alignment_config": {"method_name": "ITERATIVE", "method_params": {"mass_interval": """ +
    """ 20000, "max_iterations": 3}, "smoothing_method_name": "TIME_WINDOW", "smoothing_method_params": {"window_size":60,"window_overlap":20,"""+
    """"min_window_landmarks":50},"ft_mapping_method_name":"FEATURE_COORDINATES","ft_mapping_params":{"moz_tol":5.0,"moz_tol_unit":"ppm","time_tol":300.0}""" +
    """},"moz_calibration_smoothing_method":"LOESS","cross_assignment_config":{"method_name":"BETWEEN_ALL_RUNS","ft_mapping_params":{"moz_tol":5.0,"moz_tol_unit":"ppm","""+
    """"time_tol":"30.0","use_moz_calibration":false,"use_automatic_time_tol":false},"restrain_to_reliable_features":true},"ft_filter":{"name":"INTENSITY","value":0.0,"operator":"GT"}},"""+
    """"detection_method_name":"DETECT_PEAKELS","detection_params":{"psm_matching_params":{"moz_tol":"5.0","moz_tol_unit":"PPM"},"isotope_matching_params":{"moz_tol":"5.0","moz_tol_unit":"PPM"}}"""

  val deserializedQuantConfig = deserialize[LabelFreeQuantConfig](lastQuantConfigExpectedStr)
  val alnConfig = deserializedQuantConfig.alignmentConfig.get
  logger.info("method Name = {}", alnConfig.methodName)

  val ignoreErrors = alnConfig.ignoreErrors.getOrElse(false)
  logger.info("ignoreErrors : {}", ignoreErrors)

}