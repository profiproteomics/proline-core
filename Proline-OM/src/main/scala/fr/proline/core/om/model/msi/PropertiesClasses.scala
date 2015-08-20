package fr.proline.core.om.model.msi

import scala.beans.BeanProperty

case class FilterDescriptor (
  @BeanProperty var parameter: String,
  @BeanProperty var description: Option[String] = None,
  // FIXME: replace by Option[Map[String,Any]] when Jacks supports scala.Any deserialization
  @BeanProperty var properties: Option[Map[String,AnyRef]] = None
)
