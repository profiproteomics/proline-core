package fr.proline.core.algo.lcms

import fr.proline.core.om.model.lcms.LcMsRun
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include

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

@JsonInclude(Include.NON_NULL)
case class AlignmentParams(
  massInterval: Int,
  smoothingMethodName: String,
  smoothingParams: AlnSmoothingParams,
  ftMappingParams: FeatureMappingParams,
  maxIterations: Int = 3
)

@JsonInclude(Include.NON_NULL)
case class AlnSmoothingParams( windowSize: Int, windowOverlap: Int, minWindowLandmarks: Int = 0 )

@JsonInclude(Include.NON_NULL)
case class ClusteringParams(
  mozTol: Double,
  mozTolUnit: String,
  timeTol: Float,
  intensityComputation: String,
  timeComputation: String
) extends IMzTimeTolerant {
  
  def this( mzTimeTol: IMzTimeTolerant, intensityComputation: String,timeComputation: String) = {
    this(mzTimeTol.mozTol,mzTimeTol.mozTolUnit,mzTimeTol.timeTol,intensityComputation,timeComputation)
  }
  
}

@JsonInclude(Include.NON_NULL)
case class ExtractionParams( mozTol: Double, mozTolUnit: String ) extends IMzTolerant

@JsonInclude(Include.NON_NULL)
case class FeatureMappingParams( mozTol: Double, mozTolUnit: String, timeTol: Float ) extends IMzTimeTolerant

@JsonInclude( Include.NON_NULL )
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
