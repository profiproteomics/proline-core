package fr.proline.core.om.model.msq

import fr.profi.util.collection._
import fr.profi.util.lang.EnhancedEnum

object AbundanceUnit extends EnhancedEnum {
  val FEATURE_INTENSITY = Value("feature_intensity")
  val REPORTER_ION_INTENSITY = Value("reporter_ion_intensity")
  val SPECTRAL_COUNTS = Value("spectral_counts")
}

object QuantMethodType extends EnhancedEnum {
  val ATOM_LABELING = Value("atom_labeling")
  val ISOBARIC_TAGGING = Value("isobaric_tagging")
  val LABEL_FREE = Value("label_free")
  val RESIDUE_LABELING = Value("residue_labeling")
}

trait IQuantMethod {
  val methodType: QuantMethodType.Value
  val abundanceUnit: AbundanceUnit.Value
}

trait ILabelingQuantMethod extends IQuantMethod {
  val quantLabels: List[IQuantLabel]
}

object LabelFreeQuantMethod extends IQuantMethod {
  val methodType = QuantMethodType.LABEL_FREE
  val abundanceUnit = AbundanceUnit.FEATURE_INTENSITY
}

case class IsobaricTaggingQuantMethod( quantLabels: List[IsobaricTag] ) extends ILabelingQuantMethod {
  val methodType = QuantMethodType.ISOBARIC_TAGGING
  val abundanceUnit = AbundanceUnit.REPORTER_ION_INTENSITY
  
  lazy val tagById = quantLabels.mapByLong(_.id)
}

case class ResidueLabelingQuantMethod( quantLabels: List[ResidueTag] ) extends ILabelingQuantMethod {
  val methodType = QuantMethodType.RESIDUE_LABELING
  val abundanceUnit = AbundanceUnit.FEATURE_INTENSITY

  lazy val tagById = quantLabels.mapByLong(_.id)
}


