package fr.proline.core.algo.lcms

import fr.proline.core.om.model.lcms.LcMsRun

trait IMsQuantConfig {
  val extractionParams: ExtractionParams
}

trait ILcMsQuantConfig extends IMsQuantConfig {
  val mapSetName: String
  val lcMsRuns: Seq[LcMsRun]
  val clusteringParams: ClusteringParams
  val alnMethodName: String
  val alnParams: AlignmentParams
  val ftFilter: fr.proline.core.algo.lcms.filtering.Filter
  val ftMappingParams: FeatureMappingParams
  val normalizationMethod: Option[String]
}

trait ILabelFreeQuantConfig extends ILcMsQuantConfig

trait IMzTolerant {
  val mozTol: Double
  val mozTolUnit: String
  
  def calcMozTolInDalton( moz: Double ): Double = {
    fr.proline.util.ms.calcMozTolInDalton( moz, mozTol, mozTolUnit )
  }
}

case class AlignmentParams(
  massInterval: Int,
  smoothingMethodName: String,
  smoothingParams: AlnSmoothingParams,
  ftMappingParams: FeatureMappingParams,
  maxIterations: Int = 3
)

case class AlnSmoothingParams( windowSize: Int, windowOverlap: Int, minWindowLandmarks: Int = 0 )

case class ClusteringParams(
  mozTol: Double,
  mozTolUnit: String,
  timeTol: Float,
  intensityComputation: String,
  timeComputation: String
) extends IMzTolerant

case class ExtractionParams( mozTolPPM: Float )

case class FeatureMappingParams( mozTol: Double, mozTolUnit: String, timeTol: Float ) extends IMzTolerant

case class LabelFreeQuantConfig(
  mapSetName: String,
  lcMsRuns: Seq[LcMsRun],
  extractionParams: ExtractionParams,
  clusteringParams: ClusteringParams,
  alnMethodName: String,
  alnParams: AlignmentParams,
  ftFilter: fr.proline.core.algo.lcms.filtering.Filter,
  ftMappingParams: FeatureMappingParams,
  normalizationMethod: Option[String]
) extends ILabelFreeQuantConfig
