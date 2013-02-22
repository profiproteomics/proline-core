package fr.proline.core.om.model.msi

import scala.reflect.BeanProperty
import com.codahale.jerkson.JsonSnakeCase
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include


@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class FilterDescriptor (
  @BeanProperty protected var parameter: String,
  @BeanProperty protected var description: Option[String] = None,
  @BeanProperty protected var properties: Option[Map[String,Any]] = None
)

