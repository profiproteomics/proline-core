package fr.proline.core.om.storer.msi

import scala.Array.canBuildFrom

import com.weiglewilczek.slf4s.Logging

import net.noerd.prequel.ReusableStatement
import net.noerd.prequel.SQLFormatterImplicits._

import fr.proline.core.dal.SQLFormatterImplicits._
import fr.proline.core.dal.MsiDb
import fr.proline.core.dal.{MsiDbPeaklistTable,MsiDbSpectrumTable}
import fr.proline.core.om.model.msi.{IPeaklistContainer,Peaklist,Spectrum}
import fr.proline.core.utils.sql._
import fr.proline.core.utils.lzma.EasyLzma

/** A factory object for implementations of the IRsStorer trait */
object PeaklistStorer {
  def apply( msiDb: MsiDb ) = new PeaklistStorer( msiDb )
}

class PeaklistStorer( msiDb: MsiDb ) extends Logging {
  
  object TrailingZerosStripper {
    
    private val decimalParser = """(\d+\.\d*?)0*$""".r
    
    def apply( decimalAsStr: String ): String = {
      val decimalParser(compactDecimal) = decimalAsStr
      compactDecimal
    }
  }
  
  private val doubleFormatter = newDecimalFormat("0.000000")
  private val floatFormatter = newDecimalFormat("0.00")
  
  def storePeaklist( peaklist: Peaklist, peaklistContainer: IPeaklistContainer ): Map[String,Int] = {
    
    logger.info( "storing peaklist and spectra..." )
    
    // Insert the peaklist in the MSIdb
    this._insertPeaklist( peaklist )
    
    val msiDbTx = this.msiDb.getOrCreateTransaction()
    
    val spectrumColsList = MsiDbSpectrumTable.getColumnsAsStrList().filter { _ != "id" }
    val spectrumInsertQuery = MsiDbSpectrumTable.buildInsertQuery( spectrumColsList )
    
    // Insert corresponding spectra
    msiDbTx.executeBatch( spectrumInsertQuery ) { stmt =>      
      peaklistContainer.eachSpectrum { spectrum => this._insertSpectrum( stmt, spectrum, peaklist.id ) } 
    }
    
    null
  }
  
  private def _insertPeaklist( peaklist: Peaklist ): Unit = {
    
    val peaklistColsList = MsiDbPeaklistTable.getColumnsAsStrList().filter { _ != "id" } 
    val peaklistInsertQuery = MsiDbPeaklistTable.buildInsertQuery( peaklistColsList )
    
    val msiDbTx = this.msiDb.getOrCreateTransaction()
    msiDbTx.executeBatch( peaklistInsertQuery, true ) { stmt =>
      stmt.executeWith(
            peaklist.fileType,
            peaklist.path,
            peaklist.rawFileName,
            peaklist.msLevel,
            "xz", // none | lzma | xz TODO: create an enumeration near to the Peaklist class
            Option(null),
            1 // peaklist.peaklistSoftware.id
          )
          
      peaklist.id = this.msiDb.extractGeneratedInt( stmt.wrapped )
    }   
    
  }
  
  private def _insertSpectrum( stmt: ReusableStatement, spectrum: Spectrum, peaklistId: Int ): Unit = {
    
    // Define some vars
    val precursorIntensity = if( !spectrum.precursorIntensity.isNaN ) Some(spectrum.precursorIntensity) else Option.empty[Float]
    val firstCycle = if( spectrum.firstCycle > 0 ) Some(spectrum.firstCycle) else Option.empty[Int]
    val lastCycle = if( spectrum.lastCycle > 0 ) Some(spectrum.lastCycle) else Option.empty[Int]
    val firstScan = if( spectrum.firstScan > 0 ) Some(spectrum.firstScan) else Option.empty[Int]
    val lastScan = if( spectrum.lastScan > 0 ) Some(spectrum.lastScan) else Option.empty[Int]
    val firstTime = if( spectrum.firstTime > 0 ) Some(spectrum.firstTime) else Option.empty[Float]
    val lastTime = if( spectrum.lastTime > 0 ) Some(spectrum.lastTime) else Option.empty[Float]
    
    // moz and intensity lists are formatted as numbers separated by spaces
    val tzs = TrailingZerosStripper
    val mozList = spectrum.mozList.getOrElse(Array.empty[Double]).map { m => tzs(this.doubleFormatter.format( m )) } mkString(" ")
    val intList = spectrum.intensityList.getOrElse(Array.empty[Float]).map { i => tzs(this.floatFormatter.format( i )) } mkString(" ")
    
    // Compress peaks
    val compressedMozList = EasyLzma.compress( mozList.getBytes )
    val compressedIntList = EasyLzma.compress( intList.getBytes )
    
    stmt <<
      spectrum.title <<
      spectrum.precursorMoz <<
      precursorIntensity <<
      spectrum.precursorCharge <<
      spectrum.isSummed <<
      firstCycle <<
      lastCycle <<
      firstScan <<
      lastScan <<
      firstTime <<
      lastTime <<
      Option(null) <<
      Option(null) <<
      spectrum.peaksCount <<
      Option(null) <<
      peaklistId <<
      spectrum.instrumentConfigId
    
    // Override BLOB values using JDBC
    stmt.wrapped.setBytes(12,compressedMozList)
    stmt.wrapped.setBytes(13,compressedIntList)
    
    // Execute statement
    stmt.execute()

    spectrum.id = this.msiDb.extractGeneratedInt( stmt.wrapped )
    
    ()
  }
  
}

/*
object PeaklistTable extends TableDefinition {
  
  val tableName = "peaklist"
  
  object columns extends Enumeration {
    //type column = Value
    val id = Value("id")
    val `type` = Value("type")
    val path = Value("path")
    val rawFileName = Value("raw_file_name")    
    val msLevel = Value("ms_level")
    val spectrumDataCompression = Value("spectrum_data_compression")
    val serializedProperties = Value("serialized_properties")
    val peaklistSoftwareId = Value("peaklist_software_id")
  }
  
  def getColumnsAsStrList( f: PeaklistTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[PeaklistTable.columns.type]( f )
  }
  
  def getInsertQuery( f: PeaklistTable.columns.type => List[Enumeration#Value] ): String = {
    this._getInsertQuery[PeaklistTable.columns.type]( f )
  }

}


object SpectrumTable extends TableDefinition {
  
  val tableName = "spectrum"
    
  object columns extends Enumeration {
    //type column = Value
    val id = Value("id")
    val title = Value("title")
    val precursorMoz = Value("precursor_moz")
    val precursorIntensity = Value("precursor_intensity")
    val precursorCharge = Value("precursor_charge")
    val isSummed = Value("is_summed")
    val firstCycle = Value("first_cycle")
    val lastCycle = Value("last_cycle")
    val firstScan = Value("first_scan")
    val lastScan = Value("last_scan")
    val firstTime = Value("first_time")
    val lastTime = Value("last_time")
    var mozList = Value("moz_list")
    var intensityList = Value("intensity_list")
    val peakCount = Value("peak_count")
    val serializedProperties = Value("serialized_properties")
    val peaklistId = Value("peaklist_id")
    val instrumentConfigId = Value("instrument_config_id")    
  }
  
  def getColumnsAsStrList( f: SpectrumTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[SpectrumTable.columns.type]( f )
  }
  
  def getInsertQuery( f: SpectrumTable.columns.type => List[Enumeration#Value] ): String = {
    this._getInsertQuery[SpectrumTable.columns.type]( f )
  }

}*/