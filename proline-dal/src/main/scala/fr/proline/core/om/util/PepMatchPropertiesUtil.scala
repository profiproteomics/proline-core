package fr.proline.core.om.util

import fr.proline.context.MsiDbConnectionContext
import fr.proline.core.om.model.msi.{PeaklistSoftware, PeptideMatch, PeptideMatchProperties, Spectrum}
import fr.proline.core.om.provider.msi.impl.SQLSpectrumProvider
import fr.proline.core.om.storer.msi.PeptideWriter

import scala.collection.mutable.{ArrayBuffer, LongMap}


object PepMatchPropertiesUtil {


  def readPIFValues(peptdeMatchBySpecId:  LongMap[PeptideMatch], peaklistId: Long, peaklistSoftware: PeaklistSoftware, msiDbCtx: MsiDbConnectionContext): Unit = {

    val spectrumProvider = new SQLSpectrumProvider(msiDbCtx)
    val peptideMatchToSave = new ArrayBuffer[PeptideMatch]()
    spectrumProvider.foreachPeaklistSpectrum(peaklistId, loadPeaks = false) { spectrum =>
      val pepMatchOpt = readSinglePIFValue(spectrum, peptdeMatchBySpecId, peaklistSoftware)
      if(pepMatchOpt.isDefined)
        peptideMatchToSave += pepMatchOpt.get
    }
    if (!peptideMatchToSave.isEmpty) {
      val pepProvider = PeptideWriter.apply(msiDbCtx.getDriverType)
      pepProvider.updatePeptideMatchProperties(peptideMatchToSave, msiDbCtx)
    }

  }

  def readSinglePIFValue(spectrum: Spectrum, peptdeMatchBySpecId: LongMap[PeptideMatch], peaklistSoftware: PeaklistSoftware): Option[PeptideMatch] = {
    var peptideMatchToSave: Option[PeptideMatch] = None
    val pifValue = peaklistSoftware.parsePIFValue(spectrum.title)
    if (!pifValue.isNaN) {
      val peptideMatchOpt = peptdeMatchBySpecId.get(spectrum.id)
      if (peptideMatchOpt.isDefined) {
        peptideMatchToSave = peptideMatchOpt
        val pmProp = peptideMatchOpt.get.properties.getOrElse(new PeptideMatchProperties())
        pmProp.precursorIntensityFraction = Some(pifValue)
        peptideMatchOpt.get.properties = Some(pmProp)
      }
    }
    peptideMatchToSave
  }
}
