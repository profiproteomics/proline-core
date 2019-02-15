package fr.proline.core.algo.msq.config

case class LabelParams(
  tagId: Long,
  ptmSpecificityIds: Array[Long]
)

case class ResidueLabelingQuantConfig(
  tags: Array[LabelParams],
  labelFreeQuantConfig: LabelFreeQuantConfig
) extends IQuantConfig  {

}
