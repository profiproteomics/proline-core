package fr.proline.core.algo.msq.config

import scala.annotation.meta.field

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.module.scala.JsonScalaEnumeration

import fr.profi.util.lang.EnhancedEnum
import fr.proline.core.algo.msq.config._
import fr.proline.core.om.model.lcms.LcMsRun

case class IsobaricTaggingQuantConfig(
  extractionParams: ExtractionParams,
  @(JsonScalaEnumeration @field)(classOf[ReporterIonDataSourceTypeRef])
  reporterIonDataSource: ReporterIonDataSource.Value,
  labelFreeQuantConfig: Option[LabelFreeQuantConfig]
) extends IQuantConfig

object ReporterIonDataSource extends EnhancedEnum {
  val PROLINE_SPECTRUM, MZDB_MS2_SPECTRUM, MZDB_MS3_SPECTRUM = Value
}
// Required by the Scala-Jackson-Module to handle Scala enumerations
class ReporterIonDataSourceTypeRef extends TypeReference[ReporterIonDataSource.type]