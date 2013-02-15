package fr.proline.core.om.model.lcms

import scala.reflect.BeanProperty
import com.codahale.jerkson.JsonSnakeCase
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class FeatureProperties (
  @BeanProperty var peakelsCount: Option[Int] = None,
  @BeanProperty var peakelsRatios: Option[Array[Float]] = None,
  @BeanProperty var overlapCorrelation: Option[Float] = None,
  @BeanProperty var overlapFactor: Option[Float] = None
  //@BeanProperty var maPRopDuJour2: Option[Float] = None
)

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class LcmsRunProperties


@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class LcmsScanProperties 