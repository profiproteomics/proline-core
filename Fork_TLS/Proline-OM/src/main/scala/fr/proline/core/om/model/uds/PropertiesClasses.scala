package fr.proline.core.om.model.uds

import scala.beans.BeanProperty

case class ExternalDbProperties(
  @BeanProperty var driverType: Option[String] = None,
  @BeanProperty var hibernateDialect: Option[String] = None
)