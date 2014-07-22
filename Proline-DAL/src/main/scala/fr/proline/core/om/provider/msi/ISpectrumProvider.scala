package fr.proline.core.om.provider.msi

import fr.proline.core.om.model.msi.Spectrum

trait ISpectrumProvider {

  def getSpectra( spectrumIds: Seq[Long] ): Array[Spectrum]
  
  def getSpectrum( spectrumId: Long ): Spectrum = { getSpectra( Array(spectrumId) )(0) }

}