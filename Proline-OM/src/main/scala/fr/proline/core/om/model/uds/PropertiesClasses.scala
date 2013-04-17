package fr.proline.core.om.model.uds

import scala.reflect.BeanProperty
import com.codahale.jerkson.JsonSnakeCase
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class ExternalDbProperties(  
  @BeanProperty var jdbcDriverClassName: Option[String] = None, // TODO: remove ???
  var driverType: Option[String] = None,
  @BeanProperty var hibernateDialect: Option[String] = None
) {

  // Small hack
  // TODO: replace by @BeanProperty
  def getDriverType(): Option[String] = {
    if( driverType != None ) driverType
	else jdbcDriverClassName
  }
  
  def setDriverType( driverType: Option[String] ) {
    this.driverType = driverType
  }
}

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class RawFileProperties(
  @BeanProperty var mzdbFilePath: Option[String] = None
)