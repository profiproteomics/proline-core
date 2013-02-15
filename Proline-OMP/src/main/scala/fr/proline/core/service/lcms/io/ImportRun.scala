package fr.proline.core.service.lcms.io

import fr.proline.core.om.model.lcms.PeakPickingSoftware
import fr.profi.jdbc.SQLQueryExecution
import fr.proline.api.service.IService
import java.io.File
import fr.proline.core.om.model.lcms.LcmsScan
import scala.io.Source._
import scala.collection.mutable.ArrayBuffer
import fr.proline.core.om.model.lcms.LcmsRun
import fr.proline.core.om.model.lcms.Instrument
import fr.proline.core.om.model.lcms.RawFile
import fr.proline.core.om.storer.lcms.impl.SQLRunStorer

trait String2FileConverter {
  implicit def string2File(filename:String) = new File(filename)
}

object ImportRun extends String2FileConverter {
  def parseMsScans(file: File) : Seq[LcmsScan] = {
    val it = fromFile(file).getLines()
    val header = it.next().split("\t") //skip the first header line
  
    it.map{ s => 
      		var value = header.zip(s.split("\t")).toMap
      		new LcmsScan(value("id").toInt,
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
  
  def buildLcmsRun( file: File, pps : PeakPickingSoftware, rawFile: RawFile): LcmsRun = {    
    this.buildLcmsRun( this.parseMsScans(file), pps, rawFile )
  }
  
  def buildLcmsRun( scans: Seq[LcmsScan], pps : PeakPickingSoftware, rawFile: RawFile): LcmsRun = {
    
    val ms1Count = scans.filter(_.msLevel == 1).length
    val ms2Count = scans.length - ms1Count
    
    this.buildLcmsRun( scans, pps, rawFile, ms1Count,ms2Count,0.,0.0)
  }
  
  def buildLcmsRun(scans: Seq[LcmsScan], pps : PeakPickingSoftware, rawFile: RawFile,
                   ms1Count: Int, ms2Count: Int,
                   minIntensity: Double = 0.0, maxIntensity: Double = 0.0
                   ): LcmsRun = {
    
    LcmsRun(  id = LcmsRun.generateNewId(),
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



class ImportRun(lcmsDb : SQLQueryExecution, lcmsRun: LcmsRun, var instrument: Instrument = null ) extends IService {
  
  def this(lcmsDb : SQLQueryExecution, scans: Seq[LcmsScan], pps : PeakPickingSoftware, rawfile: RawFile) {
    this(lcmsDb, ImportRun.buildLcmsRun(scans, pps , rawfile) )
  }
  
  def this(lcmsDb : SQLQueryExecution, file: File, pps : PeakPickingSoftware, rawfile: RawFile) {
    this(lcmsDb, ImportRun.buildLcmsRun(file,pps,rawfile))
  }
  //var scans = ImportRun.readRunFile(file)
  
  def runService(): Boolean = {
    
    val storer = new SQLRunStorer(lcmsDb)
    if (instrument == null) {
      logger.warn("Empty Instrument, building an empty one...\n")
      instrument = new Instrument(id = 0, name = "", source = "")
    }
    storer.storeLcmsRun(lcmsRun, instrument)
    true
  }
  
}