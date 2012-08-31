package fr.proline.core.om.model.msi

import scala.reflect.BeanProperty
import com.codahale.jerkson.JsonSnakeCase
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class MsQueryDbSearchProperties( @BeanProperty var candidatePeptidesCount: Int,
                                      @BeanProperty var mascotIdentityThreshold: Option[Float] = None,
                                      @BeanProperty var mascotHomologyThreshold: Option[Float] = None
                                     )
                                     
@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class MsQueryProperties( @BeanProperty var targetDbSearch: Option[MsQueryDbSearchProperties] = None,
                              @BeanProperty var decoyDbSearch: Option[MsQueryDbSearchProperties] = None
                            )

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class PeptideMatchProperties (
  @BeanProperty var mascotProperties: Option[PeptideMatchMascotProperties] = None
)

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class PeptideMatchMascotProperties (
  @BeanProperty var expectationValue: Double,
  @BeanProperty var readableVarMods: Option[String] = None,
  @BeanProperty var varModsPositions: Option[String] = None,
  @BeanProperty var ambiguityString: Option[String] = None
)

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class PeptideMatchValidationProperties (
  @BeanProperty var mascotScoreOffset: Option[Float] = None,
  @BeanProperty var mascotAdjustedExpectationValue: Option[Double] = None
)

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class PeaklistProperties (
  @BeanProperty var spectrumDataCompressionLevel: Option[Int] = None,
  @BeanProperty var putativePrecursorCharges: Option[Seq[Int]] = None,
  @BeanProperty var polarity: Option[Char] = None // +/-
)

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class SequenceMatchProperties (
)

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class ProteinMatchProperties (
)

///////////////////////////////////////////////////////////////
////////////////// RESULT SUMMARY PROPERTIES //////////////////
///////////////////////////////////////////////////////////////

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class RsmPepMatchValidationParamsProperties (
  @BeanProperty var expectedFdr: Option[Float] = None,
  @BeanProperty var scoreThreshold: Option[Float] = None
)

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class RsmProtSetValidationParamsProperties (
  @BeanProperty var methodName: String,
  @BeanProperty var expectedFdr: Option[Float] = None  
)

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class RsmValidationParamsProperties (
  @BeanProperty var peptideParams: Option[RsmPepMatchValidationParamsProperties] = None,
  @BeanProperty var proteinParams: Option[RsmProtSetValidationParamsProperties] = None
)

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class RsmPepMatchValidationResultsProperties (
  @BeanProperty var pValueThreshold: Float,
  @BeanProperty var targetMatchesCount: Int,
  @BeanProperty var decoyMatchesCount: Option[Int] = None,
  @BeanProperty var fdr: Option[Float] = None
)

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class RsmProtSetValidationResultsProperties (
  //@BeanProperty var results: Option[RsmValidationProperties] = None
  // TODO: expectedRocPoint and RocPoints model
)

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class RsmValidationResultsProperties (
  @BeanProperty var peptideResults: Option[RsmPepMatchValidationResultsProperties] = None,
  @BeanProperty var proteinResults: Option[RsmProtSetValidationResultsProperties] = None
)

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class RsmValidationProperties (
  @BeanProperty var params: RsmValidationParamsProperties,
  @BeanProperty var results: RsmValidationResultsProperties
)

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class ResultSummaryProperties (
  @BeanProperty var validationProperties: Option[RsmValidationProperties] = None
)
