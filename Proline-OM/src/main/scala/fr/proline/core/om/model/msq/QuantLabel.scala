package fr.proline.core.om.model.msq

import scala.beans.BeanProperty
import fr.profi.util.lang.EnhancedEnum

object QuantLabelType extends EnhancedEnum {
  val ATOM_LABEL = Value("atom_label")
  val RESIDUE_LABEL = Value("residue_label")
  val ISOBARIC_TAG = Value("isobaric_tag")
}

trait IQuantLabel {
  val id: Long
  val labelType: QuantLabelType.Value
  val name: String
  val number: Int
}

case class IsobaricTag(
  id: Long,
  name: String,
  number: Int,
  properties: IsobaricTagProperties
) extends IQuantLabel {
  
  val labelType = QuantLabelType.ISOBARIC_TAG
  
  def reporterMz = properties.getReporterMz()
}

case class IsobaricTagProperties(
  // TODO: rename to reporterMass in BDD ???
  @BeanProperty reporterMz: Double
)

case class ResidueTag(
  id: Long,
  name: String,
  number: Int,
  properties: ResidueTagProperties
) extends IQuantLabel {
  val labelType = QuantLabelType.RESIDUE_LABEL
}

case class ResidueTagProperties(
)
