package fr.proline.core.algo.lcms.summarizing

import fr.profi.util.lang.EnhancedEnum
import fr.proline.core.om.model.lcms.Peakel

trait IPeakelPreProcessing {
  def applyFilter( peakel: Peakel ): Peakel
}

object PeakelPreProcessingMethod extends EnhancedEnum {
  val GAUSSIAN_FITTING = Value
  val POLYNOMIAL_FITTING = Value
  val SAVITZKY_GOLAY_SMOOTHING = Value
  val NONE = Value
}