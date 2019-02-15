package fr.proline.core.om.provider.msi

import fr.proline.core.om.model.msi.Spectrum

trait ISpectrumProvider {

  def getSpectra( spectrumIds: Seq[Long], loadPeaks: Boolean ): Array[Spectrum]
  
  def getSpectrum( spectrumId: Long, loadPeaks: Boolean = true ): Spectrum = { getSpectra( Array(spectrumId), loadPeaks )(0) }

}