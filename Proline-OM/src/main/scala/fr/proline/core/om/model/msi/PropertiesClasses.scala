package fr.proline.core.om.model.msi

import scala.reflect.BeanProperty
import com.codahale.jerkson.Json._
import com.codahale.jerkson.JsonSnakeCase

@JsonSnakeCase
case class MsQueryDbSearchProperties( @BeanProperty var candidatePeptidesCount: Int,
                                      @BeanProperty var mascotIdentityThreshold: Option[Float] = None,
                                      @BeanProperty var mascotHomologyThreshold: Option[Float] = None
                                     )
                                     
@JsonSnakeCase
case class MsQueryProperties( @BeanProperty var targetDbSearch: Option[MsQueryDbSearchProperties] = None,
                              @BeanProperty var decoyDbSearch: Option[MsQueryDbSearchProperties] = None
                            )

@JsonSnakeCase            
case class PeptideMatchProperties (
  @BeanProperty var mascotProperties: Option[PeptideMatchMascotProperties] = None
)

@JsonSnakeCase
case class PeptideMatchMascotProperties (
  @BeanProperty var expectationValue: Double,
  @BeanProperty var readableVarMods: Option[String] = None,
  @BeanProperty var varModsPositions: Option[String] = None,
  @BeanProperty var ambiguityString: Option[String] = None
)

@JsonSnakeCase
case class PeptideInstancePeptideMatchMapProperties (
  @BeanProperty var mascotScoreOffset: Option[Float] = None,
  @BeanProperty var mascotAdjustedExpectationValue: Option[Double] = None
)

@JsonSnakeCase
case class PeaklistProperties (
  @BeanProperty var spectrumDataCompressionLevel: Option[Int] = None,
  @BeanProperty var putativePrecursorCharges: Option[Seq[Int]] = None,
  @BeanProperty var polarity: Option[Char] = None // +/-
)

@JsonSnakeCase            
case class SequenceMatchProperties (
)

@JsonSnakeCase            
case class ProteinMatchProperties (
)
