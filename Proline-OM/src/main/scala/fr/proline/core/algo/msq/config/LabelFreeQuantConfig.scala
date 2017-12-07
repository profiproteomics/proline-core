package fr.proline.core.algo.msq.config

import fr.proline.core.algo.lcms._
import fr.proline.core.om.model.lcms.LcMsRun

trait ILcMsQuantConfig extends IMsQuantConfig {
  val clusteringParams: ClusteringParams
  val alnMethodName: String
  val alnParams: AlignmentParams
  val ftFilter: fr.proline.core.algo.lcms.filtering.Filter
  val ftMappingParams: FeatureMappingParams
  val normalizationMethod: Option[String]
  // TODO: use an algorithm enumeration instead of boolean values
  val detectFeatures: Boolean
  val detectPeakels: Boolean
  val startFromValidatedPeptides: Boolean
  val useLastPeakelDetection: Boolean
  val performCrossAssignmentInsideGroupsOnly: Boolean
  val restrainCrossAssignmentToReliableFeatures: Boolean
}

trait ILabelFreeQuantConfig extends ILcMsQuantConfig

case class LabelFreeQuantConfig(
  extractionParams: ExtractionParams,
  clusteringParams: ClusteringParams,
  alnMethodName: String,
  alnParams: AlignmentParams,
  ftFilter: fr.proline.core.algo.lcms.filtering.Filter,
  ftMappingParams: FeatureMappingParams,
  normalizationMethod: Option[String],
  // TODO: use an algorithm enumeration instead of boolean values
  detectFeatures: Boolean = false, // false implies feature extraction instead of detection
  detectPeakels: Boolean = false, // false implies feature extraction instead of detection
  startFromValidatedPeptides: Boolean = true, // only checked if detectFeatures is false
  useLastPeakelDetection: Boolean = false,
  performCrossAssignmentInsideGroupsOnly: Boolean = false, 
  restrainCrossAssignmentToReliableFeatures: Boolean = false
) extends ILabelFreeQuantConfig
