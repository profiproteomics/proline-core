package fr.proline.core.om.model.uds

import scala.reflect.BeanProperty
import com.codahale.jerkson.JsonSnakeCase
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class ExternalDbProperties(
  @BeanProperty var jdbcDriverClassName: Option[String] = None,
  @BeanProperty var hibernateDialect: Option[String] = None
)