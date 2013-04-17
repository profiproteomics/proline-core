package fr.proline.core.service.lcms.io

import java.io.File

import scala.io.Source._

import fr.proline.api.service.IService
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.om.model.lcms.LcMsScan
import fr.proline.core.om.model.lcms.LcMsScanSequence
import fr.proline.core.om.model.lcms.PeakPickingSoftware
import fr.proline.core.om.model.lcms.RawFile
import fr.proline.core.om.model.msi.Instrument
import fr.proline.core.om.storer.lcms.impl.SQLScanSequenceStorer

/*trait String2FileConverter {
  implicit def string2File(filename:String) = new File(filename)
}*/

object ImportScanSequence { // extends String2FileConverter

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

  def buildScanSequence(file: File, pps: PeakPickingSoftware, rawFile: RawFile): LcMsScanSequence = {
    this.buildScanSequence(this.parseMsScans(file), pps, rawFile)
  }

  def buildScanSequence(scans: Seq[LcMsScan], pps: PeakPickingSoftware, rawFile: RawFile): LcMsScanSequence = {

    val ms1Count = scans.filter(_.msLevel == 1).length
    val ms2Count = scans.length - ms1Count

    this.buildScanSequence(scans, pps, rawFile, ms1Count, ms2Count, 0., 0.0)
  }

  def buildScanSequence(
    scans: Seq[LcMsScan],
    pps: PeakPickingSoftware,
    rawFile: RawFile,
    ms1Count: Int,
    ms2Count: Int,
    minIntensity: Double = 0.0,
    maxIntensity: Double = 0.0): LcMsScanSequence = {

    LcMsScanSequence(
      id = LcMsScanSequence.generateNewId(),
      rawFileName = rawFile.name,
      //instrumentName = rawfile.instrument.name,
      minIntensity = 0.,
      maxIntensity = 0.,
      ms1ScansCount = ms1Count,
      ms2ScansCount = ms2Count,
      instrumentId = rawFile.instrument.map( _.id ),
      scans = scans.toArray
    )
  }
}

class ImportScanSequence(lcmsDbCtx: DatabaseConnectionContext, lcmsScanSeq: LcMsScanSequence) extends IService {

  def this(lcmsDbCtx: DatabaseConnectionContext, scans: Seq[LcMsScan], pps: PeakPickingSoftware, rawfile: RawFile) {
    this(lcmsDbCtx, ImportScanSequence.buildScanSequence(scans, pps, rawfile))
  }

  def this(lcmsDbCtx: DatabaseConnectionContext, file: File, pps: PeakPickingSoftware, rawfile: RawFile) {
    this(lcmsDbCtx, ImportScanSequence.buildScanSequence(file, pps, rawfile))
  }

  def runService(): Boolean = {
    
    val storer = new SQLScanSequenceStorer(lcmsDbCtx)
    storer.storeScanSequence(lcmsScanSeq)
    
    true
  }

}