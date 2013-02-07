package fr.proline.core.om.model.msi

import scala.reflect.BeanProperty
import com.codahale.jerkson.JsonSnakeCase
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include


@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class FilterProperties(
    @BeanProperty var name : String ,
    @BeanProperty var propeties : Option[Map[String,Any]] = None
)

