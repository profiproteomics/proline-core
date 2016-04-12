package fr.proline.core.algo.msq.config

import fr.proline.core.algo.msq.config._
import fr.proline.core.om.model.lcms.LcMsRun

case class IsobaricTaggingQuantConfig(
  extractionParams: ExtractionParams,
  labelFreeQuantConfig: Option[LabelFreeQuantConfig]
) extends IQuantConfig