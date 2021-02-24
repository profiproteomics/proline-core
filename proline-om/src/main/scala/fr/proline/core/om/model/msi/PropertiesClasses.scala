package fr.proline.core.om.model.msi

import scala.beans.BeanProperty

case class FilterDescriptor (
  @BeanProperty var parameter: String,
  @BeanProperty var description: Option[String] = None,
  @BeanProperty var properties: Option[Map[String,Any]] = None
)

case class ValidatorDescriptor (
    @BeanProperty var name: String,
    @BeanProperty var description: Option[String] = None,
    @BeanProperty var properties: Option[Map[String,Any]] = None
)