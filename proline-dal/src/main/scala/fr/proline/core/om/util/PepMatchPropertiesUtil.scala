package fr.proline.core.om.util

import fr.proline.context.{MsiDbConnectionContext, UdsDbConnectionContext}
import fr.proline.core.dal.helper.MsiDbHelper
import fr.proline.core.om.model.msi.{PeaklistSoftware, PeptideMatch, PeptideMatchProperties, ResultSummary, Spectrum}
import fr.proline.core.om.provider.msi.impl.{SQLMsiSearchProvider, SQLPeaklistProvider, SQLSpectrumProvider}
import fr.proline.core.om.storer.msi.PeptideWriter

import scala.collection.mutable
import scala.collection.mutable.{ArrayBuffer, LongMap}


object PepMatchPropertiesUtil {


  /**
   * Read PIF Value from Spectrum Title and save it in PeptideMatch Properties
   * This is done for all PeptideMap in the Map which should belong to specified peaklist
   * Parsing rule to extract PIF value from spectrum title is read in Peaklist software
   *
   * @param peptdeMatchesBySpecId : All PeptideMatch, referenced by their spectrum Id, to get PIF value for
   * @param peaklistId peaklist to which all spectrum should belong to
   * @param peaklistSoftware peaklist Software to get PIF parsing rule regExp.
   * @param msiDbCtx connection to MSI db to read and save info to.
   * @return number of PeptideMatch which have been updated
   */
  def readPIFValuesForPeaklist(peptdeMatchesBySpecId:  mutable.Map[Long, List[PeptideMatch]], peaklistId: Long, peaklistSoftware: PeaklistSoftware, msiDbCtx: MsiDbConnectionContext): Int = {

    val spectrumProvider = new SQLSpectrumProvider(msiDbCtx)
    val peptideMatchToSave = new ArrayBuffer[PeptideMatch]()
    spectrumProvider.foreachPeaklistSpectrum(peaklistId, loadPeaks = false) { spectrum =>
      val pepMatches = readSinglePIFValue(spectrum, peptdeMatchesBySpecId, peaklistSoftware)
      if(pepMatches.nonEmpty)
        peptideMatchToSave ++= pepMatches
    }
    if (!peptideMatchToSave.isEmpty) {
      val pepProvider = PeptideWriter.apply(msiDbCtx.getDriverType)
      pepProvider.updatePeptideMatchProperties(peptideMatchToSave, msiDbCtx)
    }
    peptideMatchToSave.size
  }


  /**
   * Read PIF Value from Spectrum Title and save it in PeptideMatch Properties
   * This is done for all PeptideMap in the Map which should belong to resultsummary peaklist (or resultsummary's child peaklist)
   * Parsing rule to extract PIF value from spectrum title is read in Peaklist software associated to peaklist
   *
   * @param peptdeMatchesBySpecId : All PeptideMatch, referenced by their spectrum Id, to get PIF value for
   * @param resultSummary  ResultSummary to search peaklist and associated spectrum to read
   * @param udsDbCtx  connection to UDS db to read info from.
   * @param msiDbCtx  connection to MSI db to read and save info to.
   * @return number of PeptideMatch which have been updated
   */
  def readPIFValuesForResultSummary(peptdeMatchesBySpecId: mutable.Map[Long, List[PeptideMatch]], resultSummary: ResultSummary, udsDbCtx: UdsDbConnectionContext, msiDbCtx: MsiDbConnectionContext): Int = {

    val msiDbHelper = new MsiDbHelper(msiDbCtx)
    val allMsiSearchesIds = msiDbHelper.getResultSetsMsiSearchIds(Seq(resultSummary.getResultSetId))
    val peakListIds: Seq[Long] = if (allMsiSearchesIds.isEmpty) Seq.empty[Long] else {
      val msiSearchProvider = new SQLMsiSearchProvider(udsDbCtx, msiDbCtx)
      val allMsiSearches = msiSearchProvider.getMSISearches(allMsiSearchesIds)
      allMsiSearches.map(msiSearch => msiSearch.peakList.id)
    }

    val peaklistSoftwareByPklId: mutable.HashMap[Long, PeaklistSoftware] = new mutable.HashMap[Long, PeaklistSoftware]()
    if (peakListIds.nonEmpty) {
      val pklistProvider = new SQLPeaklistProvider(msiDbCtx)
      val pklList = pklistProvider.getPeaklists(peakListIds)
      if (!pklList.isEmpty) {
         pklList.foreach(pl => {
           peaklistSoftwareByPklId += pl.id -> pl.peaklistSoftware
         })
      }
    }

    val spectrumProvider = new SQLSpectrumProvider(msiDbCtx)
    val peptideMatchToSave = new ArrayBuffer[PeptideMatch]()
    spectrumProvider.foreachPeaklistsSpectrum(peakListIds, loadPeaks = false) { spectrum =>
      val pepMatches = readSinglePIFValue(spectrum, peptdeMatchesBySpecId, peaklistSoftwareByPklId(spectrum.peaklistId))
      if (pepMatches.nonEmpty) {
        peptideMatchToSave ++= pepMatches

      }
    }
    if (!peptideMatchToSave.isEmpty) {
      val pepProvider = PeptideWriter.apply(msiDbCtx.getDriverType)
      pepProvider.updatePeptideMatchProperties(peptideMatchToSave, msiDbCtx)
    }

    peptideMatchToSave.size
  }

  def readSinglePIFValue(spectrum: Spectrum, peptdeMatchesBySpecId: mutable.Map[Long, List[PeptideMatch]], peaklistSoftware: PeaklistSoftware): List[PeptideMatch] = {
    val pifValue = peaklistSoftware.parsePIFValue(spectrum.title)
    if (!pifValue.isNaN) {
      val peptideMatches = peptdeMatchesBySpecId.getOrElse(spectrum.id, Nil)
      peptideMatches.foreach { pm =>
        val pmProp = pm.properties.getOrElse(new PeptideMatchProperties())
        pmProp.precursorIntensityFraction = Some(pifValue)
        pm.properties = Some(pmProp)
      }
      peptideMatches
    } else {
      Nil
    }
  }
}
