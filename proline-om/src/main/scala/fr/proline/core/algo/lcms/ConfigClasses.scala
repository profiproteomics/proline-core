package fr.proline.core.algo.lcms

import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.module.scala.JsonScalaEnumeration
import fr.proline.core.algo.lcms.filtering.Filter
import fr.proline.core.algo.msq.config.{IMzTimeTolerant, MzToleranceParams}

import scala.annotation.meta.field

class CrossAssignMethodRef extends TypeReference[CrossAssignMethod.type]

object CrossAssignMethod extends Enumeration {
  val BETWEEN_ALL_RULS = Value("BETWEEN_ALL_RUNS")
  val WITHIN_GROUPS_ONLY = Value("WITHIN_GROUPS_ONLY")
}

class AlnMethodRef extends TypeReference[AlnMethod.type]

object AlnMethod extends Enumeration {
  val EXHAUSTIVE = Value("EXHAUSTIVE")
  val ITERATIVE = Value("ITERATIVE")
}

class MqPepIonAbundanceSummarizingMethodRef extends TypeReference[MqPepIonAbundanceSummarizingMethod.type]

object MqPepIonAbundanceSummarizingMethod extends Enumeration {
  val BEST_ION = Value
  val SUM = Value
}

class MqReporterIonAbundanceSummarizingMethodRef extends TypeReference[MqReporterIonAbundanceSummarizingMethod.type]

object MqReporterIonAbundanceSummarizingMethod extends Enumeration {
  val MEDIAN = Value
  val SUM = Value
}

class AlnSmoothingRef extends TypeReference[AlnSmoothing.type]

object AlnSmoothing extends Enumeration {
  val LANDMARK_RANGE = Value("LANDMARK_RANGE")
  val LOESS = Value("LOESS")
  val TIME_WINDOW = Value("TIME_WINDOW")
}

class MozCalibrationSmoothingRef extends TypeReference[MozCalibrationSmoothing.type]

object MozCalibrationSmoothing extends Enumeration{
  val LANDMARK_RANGE = Value("LANDMARK_RANGE")
  val LOESS = Value("LOESS")
  val TIME_WINDOW = Value("TIME_WINDOW")
  val MEAN = Value("MEAN") //To be defined !
}

class FeatureMappingMethodRef extends TypeReference[FeatureMappingMethod.type]

object FeatureMappingMethod extends Enumeration {
  val FEATURE_COORDINATES = Value("FEATURE_COORDINATES")
  val PEPTIDE_IDENTITY = Value("PEPTIDE_IDENTITY")
}

class DetectionMethodRef extends TypeReference[DetectionMethod.type]

object DetectionMethod extends Enumeration {
  val DETECT_PEAKELS = Value("DETECT_PEAKELS")
  val DETECT_FEATURES = Value("DETECT_FEATURES")
  val EXTRACT_IONS = Value("EXTRACT_IONS")
}

class NormalizationMethodRef extends TypeReference[NormalizationMethod.type]

object NormalizationMethod extends Enumeration {
  val MEDIAN_RATIO = Value("MEDIAN_RATIO")
  val INTENSITY_SUM = Value("INTENSITY_SUM")
  val MEDIAN_INTENSITY = Value("MEDIAN_INTENSITY")
}


class PeakelsSummarizingMethodRef extends TypeReference[PeakelsSummarizingMethod.type]

object PeakelsSummarizingMethod extends Enumeration {
  val APEX_INTENSITY = Value("APEX_INTENSITY")
  val AREA = Value("AREA")

}

case class AlignmentParams(
  @JsonDeserialize(contentAs = classOf[java.lang.Integer])
  massInterval: Option[Int] = None, // default for iterative method => Some(20000),
  @JsonDeserialize(contentAs = classOf[java.lang.Integer])
  maxIterations: Option[Int] = None // default for iterative method =>  Some(3),
  )

case class AlnSmoothingParams(
  windowSize: Int,
  windowOverlap: Int,
  @JsonDeserialize(contentAs = classOf[java.lang.Integer])
  minWindowLandmarks: Option[Int] = None
)

case class AlignmentConfig(
  @(JsonScalaEnumeration @field)(classOf[AlnMethodRef])
  methodName : AlnMethod.Value,
  methodParams : Option[AlignmentParams],
  @(JsonScalaEnumeration @field)(classOf[AlnSmoothingRef])
  smoothingMethodName: AlnSmoothing.Value,
  smoothingMethodParams: Option[AlnSmoothingParams] = None,
  @(JsonScalaEnumeration @field)(classOf[FeatureMappingMethodRef])
  ftMappingMethodName: FeatureMappingMethod.Value,
  ftMappingMethodParams: FeatureMappingParams,
  @JsonDeserialize(contentAs = classOf[java.lang.Boolean])
  removeOutliers: Option[Boolean] = None, // getOrElse(false)
  @JsonDeserialize(contentAs = classOf[java.lang.Boolean])
  ignoreErrors: Option[Boolean] = None    // getOrElse(false)
  ) {
  require(ftMappingMethodParams.timeTol.isDefined, "Fixed time tolerance should be specified !")
}

case class CrossAssignmentConfig(
  @(JsonScalaEnumeration @field)(classOf[CrossAssignMethodRef])
  methodName : CrossAssignMethod.Value,
  ftMappingParams: FeatureMappingParams,
  restrainToReliableFeatures: Boolean = true,
  ftFilter: Option[Filter] = None
)

// Default value to be confirmed
case class FeatureMappingParams(
 @JsonDeserialize(contentAs = classOf[java.lang.Double])
 mozTol: Option[Double] = None,
 mozTolUnit: Option[String] = None,
 @JsonDeserialize(contentAs = classOf[java.lang.Float])
 timeTol: Option[Float],
 useMozCalibration: Boolean = true,
 useAutomaticTimeTol: Boolean = false,
 @JsonDeserialize(contentAs = classOf[java.lang.Double])
 maxAutoTimeTol: Option[Float] = None,
 @JsonDeserialize(contentAs = classOf[java.lang.Double])
 minAutoTimeTol: Option[Float] = None
) {
  if(useAutomaticTimeTol){
    require(maxAutoTimeTol.isDefined, "Max time tolerance should be specified is Automatic time tolerance mdoe")
    require(minAutoTimeTol.isDefined, "Min time tolerance should be specified is Automatic time tolerance mdoe")
  } else {
    require(timeTol.isDefined,  "time tolerance should be specified in None Automatic time tolerance mode")
  }

}

case class DetectionParams(
  startFromValidatedPeptides: Option[Boolean] = None,
  psmMatchingParams: Option[MzToleranceParams] = None,
  ftMappingParams: Option[FeatureMappingParams] = None,
  isotopeMatchingParams: Option[MzToleranceParams] = None
) {
  if(ftMappingParams.isDefined){
    require(ftMappingParams.get.timeTol.isDefined, "Fixed time tolerance should be specified !")
  }
}

case class ClusteringParams(
  mozTol: Double,
  mozTolUnit: String,
  timeTol: Float,
  intensityComputation: String,
  timeComputation: String
) extends IMzTimeTolerant