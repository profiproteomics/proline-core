package fr.proline.core.algo.msq.config

import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.module.scala.JsonScalaEnumeration

import scala.annotation.meta.field

class AbundanceComputationMethodRef extends TypeReference[AbundanceComputationMethod.type]

object AbundanceComputationMethod extends Enumeration {
  val INTENSITY_SUM = Value("INTENSITY_SUM")
  val MOST_INTENSE = Value("MOST_INTENSE")
}

case class AggregationQuantConfig (
  quantitationIds: Array[Long] = Array.empty[Long], // Array of quantitation dataset ids that must be merged
  quantChannelsMapping: Array[QuantChannelMapping],
  @(JsonScalaEnumeration @field)(classOf[AbundanceComputationMethodRef])
  intensityComputationMethodName: AbundanceComputationMethod.Value = AbundanceComputationMethod.INTENSITY_SUM
) extends IQuantConfig

/*
 * Map a parent QuantChannel with a set of "child" QuantChannel (one for each child dataset). Child QuantChannel are indexed
 * by their MasterQuantChannel id
 */
case class QuantChannelMapping(
  quantChannelNumber: Int,
  // Map child QuantChannel ids by MasterQuantChannel ids they belongs to.
  @JsonDeserialize( keyAs = classOf[java.lang.Long], contentAs = classOf[java.lang.Long] )
  quantChannelsMatching: Map[Long, Long]
)