package fr.proline.core.algo.lcms

import fr.proline.core.om.model.lcms.LcMsRun
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

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
  // TODO: use an algorithm enumeration instead of boolean values
  val detectFeatures: Boolean
  val detectPeakels: Boolean
  val startFromValidatedPeptides: Boolean
  val useLastPeakelDetection: Boolean = false
}

trait ILabelFreeQuantConfig extends ILcMsQuantConfig

trait IMzTolerant {
  val mozTol: Double
  val mozTolUnit: String
  
  def calcMozTolInDalton( moz: Double ): Double = {
    fr.profi.util.ms.calcMozTolInDalton( moz, mozTol, mozTolUnit )
  }
}

trait IMzTimeTolerant extends IMzTolerant {
  val timeTol: Float
}

case class AlignmentParams(
  massInterval: Int,
  smoothingMethodName: String,
  smoothingParams: AlnSmoothingParams,
  ftMappingParams: FeatureMappingParams,
  maxIterations: Int = 3
)

case class AlnSmoothingParams( windowSize: Int, windowOverlap: Int, minWindowLandmarks: Int = 0 )

object ClusteringParams {
  def apply(mzTimeTol: IMzTimeTolerant, intensityComputation: String,timeComputation: String) = {
    new ClusteringParams(mzTimeTol.mozTol,mzTimeTol.mozTolUnit,mzTimeTol.timeTol,intensityComputation,timeComputation)
  }
}

case class ClusteringParams(
  mozTol: Double,
  mozTolUnit: String,
  timeTol: Float,
  intensityComputation: String,
  timeComputation: String
) extends IMzTimeTolerant

case class ExtractionParams( mozTol: Double, mozTolUnit: String ) extends IMzTolerant

case class FeatureMappingParams( mozTol: Double, mozTolUnit: String, timeTol: Float ) extends IMzTimeTolerant

case class LabelFreeQuantConfig(
  mapSetName: String,
  lcMsRuns: Seq[LcMsRun],
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
  parentRsmId : Option[Long] = None, //ID of the rsm to use as merged reference RSM
  parentDsId : Option[Long] = None // ID of the DS containing merged reference RSM.
) extends ILabelFreeQuantConfig
