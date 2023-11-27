package fr.proline.core.algo.msq.config

import com.fasterxml.jackson.module.scala.JsonScalaEnumeration
import fr.proline.core.algo.lcms.{MozCalibrationSmoothing, _}

import scala.annotation.meta.field

trait ILcMsQuantConfig extends IMsQuantConfig {
  val extractionParams: ExtractionParams
  val clusteringParams: ClusteringParams
  val alignmentConfig: Option[AlignmentConfig]
  val crossAssignmentConfig: Option[CrossAssignmentConfig]
  val normalizationMethod: Option[NormalizationMethod.Value]
  val detectionMethodName: DetectionMethod.Value
  val detectionParams: Option[DetectionParams]
  val useLastPeakelDetection: Boolean
  val pepIonSummarizingMethod: Option[MqPepIonAbundanceSummarizingMethod.Value]
  val mozCalibrationSmoothingMethod: MozCalibrationSmoothing.Value
  val mozCalibrationSmoothingParams: Option[AlnSmoothingParams]
}

trait ILabelFreeQuantConfig extends ILcMsQuantConfig

object LabelFreeQuantConfigConverter {

  // Convert LabelFreeQuantConfig Version 2 to last LabelFreeQuantConfig
  def convertFromV2(map: Map[String, Any]): Map[String, Any] = {
    val extractionParams = map("extraction_params").asInstanceOf[Map[String, Any]]
    val clusteringMap = map("clustering_params").asInstanceOf[Map[String, Any]]

    var alignSmoothName: Option[String] = None
    var alignSmoothParam : Option[Map[String,Any]]=  None
    val alignmentConfig : Map[String,Any] = if(map.contains("alignment_config")){
      var alignMap =  map("alignment_config").asInstanceOf[Map[String, Any]]

      //Get SmoothParam for MozCalibration
      alignSmoothName = Some (alignMap("smoothing_method_name").asInstanceOf[String])
      if(alignMap.contains("smoothing_method_params"))
        alignSmoothParam= Some(alignMap("smoothing_method_params").asInstanceOf[Map[String, Any]])

      //Create new Feature
      val featureMappingParamMap = alignMap("ft_mapping_method_params").asInstanceOf[Map[String, Any]]
      var newFeatureParams =  Map.empty[String, Any]
      if(featureMappingParamMap.contains("moz_tol"))
        newFeatureParams += ("moz_tol"->featureMappingParamMap("moz_tol"))
      if (featureMappingParamMap.contains("moz_tol"))
        newFeatureParams += ("moz_tol_unit" -> featureMappingParamMap("moz_tol_unit"))
      if (featureMappingParamMap.contains("time_tol"))
        newFeatureParams += ("time_tol" -> featureMappingParamMap("time_tol"))
      newFeatureParams += ("use_moz_calibration" -> false)
      newFeatureParams += ("use_automatic_time_tol" -> false)

      alignMap += ("ft_mapping_method_params"->newFeatureParams)
      alignMap
    } else
      Map.empty[String, Any]

    val crossAssignmentConfig : Map[String,Any] = if (map.contains("cross_assignment_config")) {
      var crossCgfMap = map("cross_assignment_config").asInstanceOf[Map[String, Any]]
      val featureMappingParamMap = crossCgfMap("ft_mapping_params").asInstanceOf[Map[String, Any]]
      var newFeatureParams = Map.empty[String, Any]
      if (featureMappingParamMap.contains("moz_tol"))
        newFeatureParams += ("moz_tol" -> featureMappingParamMap("moz_tol"))
      if (featureMappingParamMap.contains("moz_tol"))
        newFeatureParams += ("moz_tol_unit" -> featureMappingParamMap("moz_tol_unit"))
      if (featureMappingParamMap.contains("time_tol"))
        newFeatureParams += ("time_tol" -> featureMappingParamMap("time_tol"))
      newFeatureParams += ("use_moz_calibration" -> false)
      newFeatureParams += ("use_automatic_time_tol" -> false)

      crossCgfMap += ("ft_mapping_params" -> newFeatureParams)
      crossCgfMap
    } else
      Map.empty[String, Any]

    val normalizationMethod = map.get("normalization_method")
    val detectionMethodName = map("detection_method_name")

    val detectionConfig : Map[String,Any] = if (map.contains("detection_params")) {
      var detectionCgfMap = map("detection_params").asInstanceOf[Map[String, Any]]
      if(detectionCgfMap.contains("ft_mapping_params")) {
        val featureMappingParamMap = detectionCgfMap("ft_mapping_params").asInstanceOf[Map[String, Any]]
        var newFeatureParams = Map.empty[String, Any]
        if (featureMappingParamMap.contains("moz_tol"))
          newFeatureParams += ("moz_tol" -> featureMappingParamMap("moz_tol"))
        if (featureMappingParamMap.contains("moz_tol"))
          newFeatureParams += ("moz_tol_unit" -> featureMappingParamMap("moz_tol_unit"))
        if (featureMappingParamMap.contains("time_tol"))
          newFeatureParams += ("time_tol" -> featureMappingParamMap("time_tol"))
        newFeatureParams += ("use_moz_calibration" -> false)
        newFeatureParams += ("use_automatic_time_tol" -> false)

        detectionCgfMap += ("ft_mapping_method_params" -> newFeatureParams)
      }
      detectionCgfMap
    } else
      Map.empty[String, Any]

    val useLastPeakelDetection = map.get("use_last_peakel_detection")
    val pepIonSummarizingMeth =  map.get("pep_ion_summarizing_methdd")
    val mozSmoothingMethod = alignSmoothName.getOrElse(MozCalibrationSmoothing.LOESS.toString)//Which default value TODO

    var mapV2 = Map.empty[String, Any] ++ Map(
      "config_version" -> "3.0",
      "extraction_params" -> extractionParams,
      "clustering_params" -> clusteringMap,
      "moz_calibration_smoothing_method" -> mozSmoothingMethod,
      "detection_method_name" -> detectionMethodName
    )
    if(useLastPeakelDetection.isDefined)
      mapV2 += ("use_last_peakel_detection" ->useLastPeakelDetection)
    if(alignSmoothParam.isDefined)
      mapV2 += ("moz_calibration_smoothing_params" -> alignSmoothParam)
    if(normalizationMethod.isDefined)
      mapV2 += ("normalization_method" -> normalizationMethod)
    if(alignmentConfig.nonEmpty)
      mapV2 += ("alignment_config" -> alignmentConfig)

    if (crossAssignmentConfig.nonEmpty)
      mapV2 += ("cross_assignment_config" -> crossAssignmentConfig)

    if (detectionConfig.nonEmpty)
      mapV2 += ("detection_params" -> detectionConfig)

    if(pepIonSummarizingMeth.isDefined)
      mapV2 += ("pep_ion_summarizing_method" -> pepIonSummarizingMeth)

    mapV2
  }

  def convertFromV1(map: Map[String, Any]): Map[String, Any] = {
    val extractionParams = map("extraction_params").asInstanceOf[Map[String, Any]]
    val clusteringMap = map("clustering_params").asInstanceOf[Map[String, Any]]
    val normalizationMethod = map.get("normalization_method")
    val useLastPeakelDetection = map.get("use_last_peakel_detection")
    val peakelsDetection = map("detect_peakels").asInstanceOf[Boolean]
    val featuresDetection = map("detect_features").asInstanceOf[Boolean]
    val startFromValidatedPeptides = map.get("start_from_validated_peptides")
    val ftMapping = map.get("ft_mapping_params")
    val alnMethodName = map.get("aln_method_name")
    val alnParams = map("aln_params").asInstanceOf[Map[String, Any]]
    val alnMassInterval = alnParams.get("mass_interval")
    val alnSmoothingMethodName = alnParams.get("smoothing_method_name")
    val alnSmoothingParams = alnParams.get("smoothing_params")
    val alnFtMappingMethodName = alnParams.get("ft_mapping_method_name")
    val alnFtMappingParams = alnParams.get("ft_mapping_params")
    val alnMaxIterations = alnParams.get("max_iterations")
    val alnRemoveOutliers = alnParams.get("remove_outliers")
    val alnIgnoreErrors = alnParams.get("ignore_errors")
    val ftFilter = map.get("ft_filter")
    val performCrossAssignmentInsideGroupsOnly = map.get("perform_cross_assignment_inside_groups_only")
    val restrainToReliableFeatures = map.get("restrain_cross_assignment_to_reliable_features")

    val detectionMethod = if (peakelsDetection) {
      DetectionMethod.DETECT_PEAKELS
    } else if (featuresDetection) {
      DetectionMethod.DETECT_FEATURES
    } else {
      DetectionMethod.EXTRACT_IONS
    }

    var mapV2 = Map.empty[String, Any] ++ Map(
      "config_version" -> "2.0",
      "extraction_params" -> extractionParams,
      "clustering_params" -> clusteringMap,
      "detection_method_name" -> detectionMethod.toString
    )

    if(normalizationMethod.isDefined)
      mapV2 += "normalization_method" -> normalizationMethod.get

    var alignmentConfig = Map("method_name" -> alnMethodName.get)
    if (alnMethodName.get.equals(AlnMethod.ITERATIVE.toString)) {
      alignmentConfig += ("method_params" -> Map("mass_interval" -> alnMassInterval.get, "max_iterations" -> alnMaxIterations.get))
    }
    alignmentConfig += ("smoothing_method_name" -> alnSmoothingMethodName.get)
    if (alnSmoothingParams.isDefined && !alnSmoothingMethodName.get.equals(AlnSmoothing.LOESS.toString))
      alignmentConfig += ("smoothing_method_params" -> alnSmoothingParams.get)
    if (alnFtMappingMethodName.isDefined)
      alignmentConfig += ("ft_mapping_method_name" -> alnFtMappingMethodName.get)
    if (alnFtMappingMethodName.isDefined && !alnFtMappingMethodName.get.equals(FeatureMappingMethod.PEPTIDE_IDENTITY.toString) && alnFtMappingParams.isDefined) {
      alignmentConfig += ("ft_mapping_method_params" -> alnFtMappingParams.get)
    } else if(alnFtMappingParams.isDefined){
      // for PEPTIDE_IDENTITY method remove mozXXX parameters
      alignmentConfig += ("ft_mapping_method_params" -> alnFtMappingParams.get.asInstanceOf[Map[String, Any]].filterKeys(!_.startsWith("moz")))
    }
    if (alnRemoveOutliers.isDefined)
      alignmentConfig += ("remove_outliers" -> alnRemoveOutliers.get)
    if (alnIgnoreErrors.isDefined)
      alignmentConfig += ("ignore_errors" -> alnIgnoreErrors.get)

    mapV2 += ("alignment_config" -> alignmentConfig)

    val crossAssignmentMethod =  {
      if (performCrossAssignmentInsideGroupsOnly.isDefined && performCrossAssignmentInsideGroupsOnly.get.asInstanceOf[Boolean]) {
        CrossAssignMethod.WITHIN_GROUPS_ONLY.toString
      } else {
        CrossAssignMethod.BETWEEN_ALL_RULS.toString
      }
    }
    var crossAssignmentConfig = Map.empty[String, Any]
    crossAssignmentConfig += ("method_name" -> crossAssignmentMethod)
    crossAssignmentConfig += ("ft_mapping_params" -> ftMapping.get) //suppose is defined
    if (restrainToReliableFeatures.isDefined)
      crossAssignmentConfig += ("restrain_to_reliable_features" -> restrainToReliableFeatures.get)
    if(ftFilter.isDefined)
      crossAssignmentConfig += ("ft_filter" -> ftFilter.get)
    mapV2 += ("cross_assignment_config" -> crossAssignmentConfig)

    if (useLastPeakelDetection.isDefined)
      mapV2 += ("use_last_peakel_detection" -> useLastPeakelDetection.get)
    var detectionParams = Map.empty[String, Any]
    if (detectionMethod.equals(DetectionMethod.EXTRACT_IONS) && startFromValidatedPeptides.isDefined)
      detectionParams += ("start_from_validated_peptides" -> startFromValidatedPeptides.get)
    if (detectionMethod.equals(DetectionMethod.DETECT_PEAKELS)) {
      detectionParams += ("psm_matching_params" -> extractionParams)
      detectionParams += ("isotope_matching_params" -> extractionParams)
    } else {
      detectionParams += ("ft_mapping_params" -> ftMapping.get) //suppose is defined
    }

    mapV2 += ("detection_params" -> detectionParams)
    convertFromV2(mapV2)
  }
}

//VDS TODO : Should we keep previous definition ?
//case class LabelFreeQuantConfigV2(
//  override val configVersion: String = "2.0",
//  extractionParams: ExtractionParams,
//  clusteringParams: ClusteringParams,
//  alignmentConfig:Option[AlignmentConfig],
//  crossAssignmentConfig:Option[CrossAssignmentConfig],
//  @(JsonScalaEnumeration @field)(classOf[NormalizationMethodRef])
//  normalizationMethod: Option[NormalizationMethod.Value] = None,
//  @(JsonScalaEnumeration @field)(classOf[DetectionMethodRef])
//  detectionMethodName: DetectionMethod.Value,
//  detectionParams: Option[DetectionParams] = None,
//  useLastPeakelDetection: Boolean = false,
//  @(JsonScalaEnumeration @field)(classOf[MqPepIonAbundanceSummarizingMethodRef])
//  pepIonSummarizingMethdd: Option[MqPepIonAbundanceSummarizingMethod.Value] = None
// ) extends ILabelFreeQuantConfig {
//  override val mozCalibrationSmoothingMethod: MozCalibrationSmoothing.Value = MozCalibrationSmoothing.LOESS
//}

case class LabelFreeQuantConfig(
 override val configVersion: String = "3.0",
 extractionParams: ExtractionParams,
 clusteringParams: ClusteringParams,
 alignmentConfig: Option[AlignmentConfig],
 @(JsonScalaEnumeration @field)(classOf[MozCalibrationSmoothingRef])
 mozCalibrationSmoothingMethod : MozCalibrationSmoothing.Value, //New Value
 mozCalibrationSmoothingParams: Option[AlnSmoothingParams] = None, //New Value
 crossAssignmentConfig: Option[CrossAssignmentConfig],
 @(JsonScalaEnumeration@field)(classOf[NormalizationMethodRef])
 normalizationMethod: Option[NormalizationMethod.Value] = None,
 @(JsonScalaEnumeration@field)(classOf[DetectionMethodRef])
 detectionMethodName: DetectionMethod.Value,
 detectionParams: Option[DetectionParams] = None,
 useLastPeakelDetection: Boolean = false,
 @(JsonScalaEnumeration@field)(classOf[MqPepIonAbundanceSummarizingMethodRef])
 pepIonSummarizingMethod: Option[MqPepIonAbundanceSummarizingMethod.Value] = None
) extends ILabelFreeQuantConfig