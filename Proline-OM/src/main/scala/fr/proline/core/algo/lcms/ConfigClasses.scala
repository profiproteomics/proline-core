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
  ftMappingParams: FeatureMappingParams,
  maxIterations: Int = 3,
  removeOutliers: Option[Boolean] = None
)

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


case class FeatureMappingParams( mozTol: Double, mozTolUnit: String, timeTol: Float ) extends IMzTimeTolerant
