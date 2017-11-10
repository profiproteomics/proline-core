package fr.proline.core.algo.lcms

import fr.proline.core.algo.msq.config.IMzTolerant

trait IMzTimeTolerant extends IMzTolerant {
  val timeTol: Float
}

object AlnMethod extends Enumeration {
  val EXHAUSTIVE = Value("EXHAUSTIVE")
  val ITERATIVE = Value("ITERATIVE")
}

case class AlignmentParams(
  massInterval: Int,
  smoothingMethodName: String,
  smoothingParams: AlnSmoothingParams,
  ftMappingMethodName: Option[String],
  ftMappingParams: FeatureMappingParams,
  maxIterations: Int = 3,
  removeOutliers: Option[Boolean] = None
) {
  def getFeatureMappingMethod(): FeatureMappingMethod.Value = {
    if (ftMappingMethodName.isEmpty) FeatureMappingMethod.FEATURE_COORDINATES
    else FeatureMappingMethod.withName(ftMappingMethodName.get)
  }
}

object AlnSmoothing extends Enumeration {
  val LANDMARK_RANGE = Value("LANDMARK_RANGE")
  val LOESS = Value("LOESS")
  val TIME_WINDOW = Value("TIME_WINDOW")
}

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

object FeatureMappingMethod extends Enumeration {
  val FEATURE_COORDINATES = Value("FEATURE_COORDINATES")
  val PEPTIDE_IDENTITY = Value("PEPTIDE_IDENTITY")
}

case class FeatureMappingParams( mozTol: Double, mozTolUnit: String, timeTol: Float ) extends IMzTimeTolerant
