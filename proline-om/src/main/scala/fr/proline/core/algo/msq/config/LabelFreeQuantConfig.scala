package fr.proline.core.algo.msq.config

import com.fasterxml.jackson.module.scala.JsonScalaEnumeration
import fr.proline.core.algo.lcms._

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
  val ionPeptideAggreagationMethod: Option[IonAbundanceSummarizerMethod.Value]
}

trait ILabelFreeQuantConfig extends ILcMsQuantConfig

object LabelFreeQuantConfigConverter {

  def convertFromV1(map: Map[String, Any]): Map[String, Any] = {
    val extractionParams = map.get("extraction_params").get.asInstanceOf[Map[String, Any]]
    val clusteringMap = map.get("clustering_params").get.asInstanceOf[Map[String, Any]]
    val normalizationMethod = map.get("normalization_method")
    val useLastPeakelDetection = map.get("use_last_peakel_detection")
    val peakelsDetection = map.get("detect_peakels").get.asInstanceOf[Boolean]
    val featuresDetection = map.get("detect_features").get.asInstanceOf[Boolean]
    val startFromValidatedPeptides = map.get("start_from_validated_peptides")
    val ftMapping = map.get("ft_mapping_params")
    val alnMethodName = map.get("aln_method_name")
    val alnParams = map.get("aln_params").get.asInstanceOf[Map[String, Any]]
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
      "normalization_method" -> normalizationMethod,
      "detection_method_name" -> detectionMethod.toString
    )

    var alignmentConfig = Map("method_name" -> alnMethodName.get)
    if (alnMethodName.get.equals(AlnMethod.ITERATIVE.toString)) {
      alignmentConfig += ("method_params" -> Map("mass_interval" -> alnMassInterval.get, "max_iterations" -> alnMaxIterations.get))
    }
    alignmentConfig += ("smoothing_method_name" -> alnSmoothingMethodName)
    if (alnSmoothingMethodName.isDefined && !alnSmoothingMethodName.get.equals(AlnSmoothing.LOESS.toString)) alignmentConfig += ("smoothing_method_params" -> alnSmoothingParams)
    alignmentConfig += ("ft_mapping_method_name" -> alnFtMappingMethodName)
    if (alnFtMappingMethodName.isDefined && !alnFtMappingMethodName.get.equals(FeatureMappingMethod.PEPTIDE_IDENTITY.toString)) {
      alignmentConfig += ("ft_mapping_method_params" -> alnFtMappingParams)
    } else {
      // for PEPTIDE_IDENTITY method remove mozXXX parameters
      alignmentConfig += ("ft_mapping_method_params" -> alnFtMappingParams.get.asInstanceOf[Map[String, Any]].filterKeys(!_.startsWith("moz")))
    }
    if (alnRemoveOutliers.isDefined) alignmentConfig += ("remove_outliers" -> alnRemoveOutliers)
    if (alnIgnoreErrors.isDefined) alignmentConfig += ("ignore_errors" -> alnIgnoreErrors)

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
    crossAssignmentConfig += ("ft_mapping_params" -> ftMapping.get)
    if (restrainToReliableFeatures.isDefined) crossAssignmentConfig += ("restrain_to_reliable_features" -> restrainToReliableFeatures.get)
    crossAssignmentConfig += ("ft_filter" -> ftFilter.get)
    mapV2 += ("cross_assignment_config" -> crossAssignmentConfig)

    if (useLastPeakelDetection.isDefined) mapV2 += ("use_last_peakel_detection" -> useLastPeakelDetection.get)
    var detectionParams = Map.empty[String, Any]
    if ((detectionMethod.equals(DetectionMethod.EXTRACT_IONS)) && startFromValidatedPeptides.isDefined)  detectionParams += ("start_from_validated_peptides" -> startFromValidatedPeptides)
    if (detectionMethod.equals(DetectionMethod.DETECT_PEAKELS)) {
      detectionParams += ("psm_matching_params" -> extractionParams)
      detectionParams += ("isotope_matching_params" -> extractionParams)

    } else {
      detectionParams += ("ft_mapping_params" -> ftMapping)
    }

    mapV2 += ("detection_params" -> detectionParams)
    mapV2
  }
}

case class LabelFreeQuantConfig(
  override val configVersion: String = "2.0",
  extractionParams: ExtractionParams,
  clusteringParams: ClusteringParams,
  alignmentConfig:Option[AlignmentConfig],
  crossAssignmentConfig:Option[CrossAssignmentConfig],
  @(JsonScalaEnumeration @field)(classOf[NormalizationMethodRef])
  normalizationMethod: Option[NormalizationMethod.Value] = None,
  @(JsonScalaEnumeration @field)(classOf[DetectionMethodRef])
  detectionMethodName: DetectionMethod.Value,
  detectionParams: Option[DetectionParams] = None,
  useLastPeakelDetection: Boolean = false,
  @(JsonScalaEnumeration @field)(classOf[IonAbundanceSummarizerMethodRef])
  ionPeptideAggreagationMethod: Option[IonAbundanceSummarizerMethod.Value] = None
 ) extends ILabelFreeQuantConfig