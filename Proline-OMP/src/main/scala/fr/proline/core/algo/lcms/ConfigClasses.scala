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
}

trait ILabelFreeQuantConfig extends ILcMsQuantConfig

trait IMzTolerant {
  val mozTol: Double
  val mozTolUnit: String
  
  def calcMozTolInDalton( moz: Double ): Double = {
    fr.proline.util.ms.calcMozTolInDalton( moz, mozTol, mozTolUnit )
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

case class ClusteringParams(
  mozTol: Double,
  mozTolUnit: String,
  timeTol: Float,
  intensityComputation: String,
  timeComputation: String
) extends IMzTimeTolerant {
  
  // TODO: remove me when the Jackson Scala Module will use the primary constructor by default
  @JsonCreator
  def this( props: Map[String,AnyRef] ) = {
    this(
      props("moz_tol").asInstanceOf[Double],
      props("moz_tol_unit").asInstanceOf[String],
      props("time_tol").asInstanceOf[Double].toFloat,
      props("intensity_computation").asInstanceOf[String],
      props("time_computation").asInstanceOf[String]
    )
  }
  
  def this( mzTimeTol: IMzTimeTolerant, intensityComputation: String,timeComputation: String) = {
    this(mzTimeTol.mozTol,mzTimeTol.mozTolUnit,mzTimeTol.timeTol,intensityComputation,timeComputation)
  }
  
}

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
  normalizationMethod: Option[String]
) extends ILabelFreeQuantConfig
