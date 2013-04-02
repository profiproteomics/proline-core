package fr.proline.core.service.lcms.io

import java.io.File

import scala.io.Source._

import fr.proline.api.service.IService
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.om.model.lcms.LcMsRun
import fr.proline.core.om.model.lcms.LcMsScan
import fr.proline.core.om.model.lcms.PeakPickingSoftware
import fr.proline.core.om.model.lcms.RawFile
import fr.proline.core.om.model.msi.Instrument
import fr.proline.core.om.storer.lcms.impl.SQLRunStorer

/*trait String2FileConverter {
  implicit def string2File(filename:String) = new File(filename)
}*/

object ImportRun { // extends String2FileConverter

  def parseMsScans(file: File): Seq[LcMsScan] = {
    val it = fromFile(file).getLines()
    val header = it.next().split("\t") //skip the first header line

    it.map { s =>
      var value = header.zip(s.split("\t")).toMap
      new LcMsScan(value("id").toInt,
        value("initial_id").toInt,
        value("cycle").toInt,
        value("time").toFloat,
        value("ms_level").toInt,
        value("tic").toDouble,
        value("base_peak_moz").toDouble,
        value("base_peak_intensity").toDouble,
        value("run_id").toInt,
        value("precursor_moz").toDouble,
        value("precursor_charge").toInt)
    }.toSeq

  }

  def buildLcMsRun(file: File, pps: PeakPickingSoftware, rawFile: RawFile): LcMsRun = {
    this.buildLcMsRun(this.parseMsScans(file), pps, rawFile)
  }

  def buildLcMsRun(scans: Seq[LcMsScan], pps: PeakPickingSoftware, rawFile: RawFile): LcMsRun = {

    val ms1Count = scans.filter(_.msLevel == 1).length
    val ms2Count = scans.length - ms1Count

    this.buildLcMsRun(scans, pps, rawFile, ms1Count, ms2Count, 0., 0.0)
  }

  def buildLcMsRun(
    scans: Seq[LcMsScan],
    pps: PeakPickingSoftware,
    rawFile: RawFile,
    ms1Count: Int,
    ms2Count: Int,
    minIntensity: Double = 0.0,
    maxIntensity: Double = 0.0): LcMsRun = {

    LcMsRun(
      id = LcMsRun.generateNewId(),
      rawFileName = rawFile.name,
      //instrumentName = rawfile.instrument.name,
      minIntensity = 0.,
      maxIntensity = 0.,
      ms1ScanCount = ms1Count,
      ms2ScanCount = ms2Count,
      rawFile = rawFile,
      scans = scans.toArray
    )
  }
}

class ImportRun(lcmsDbCtx: DatabaseConnectionContext, lcmsRun: LcMsRun, var instrument: Instrument = null) extends IService {

  def this(lcmsDbCtx: DatabaseConnectionContext, scans: Seq[LcMsScan], pps: PeakPickingSoftware, rawfile: RawFile) {
    this(lcmsDbCtx, ImportRun.buildLcMsRun(scans, pps, rawfile))
  }

  def this(lcmsDbCtx: DatabaseConnectionContext, file: File, pps: PeakPickingSoftware, rawfile: RawFile) {
    this(lcmsDbCtx, ImportRun.buildLcMsRun(file, pps, rawfile))
  }
  //var scans = ImportRun.readRunFile(file)

  def runService(): Boolean = {
    
    val storer = new SQLRunStorer(lcmsDbCtx)
    if (instrument == null) {
      logger.warn("Empty Instrument, building an empty one...\n")
      instrument = new Instrument(id = 0, name = "", source = "")
    }
    storer.storeLcmsRun(lcmsRun, instrument)
    
    true
  }

}