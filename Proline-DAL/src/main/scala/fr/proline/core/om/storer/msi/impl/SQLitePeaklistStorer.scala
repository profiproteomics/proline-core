package fr.proline.core.om.storer.msi.impl

import com.weiglewilczek.slf4s.Logging
import net.noerd.prequel.ReusableStatement
import net.noerd.prequel.SQLFormatterImplicits._
import fr.proline.core.dal.SQLFormatterImplicits._
import fr.proline.core.dal.MsiDb
import fr.proline.core.dal.{MsiDbPeaklistTable,MsiDbSpectrumTable}
import fr.proline.core.om.model.msi.{IPeaklistContainer,Peaklist,Spectrum}
import fr.proline.core.utils.sql._
import fr.proline.core.utils.lzma.EasyLzma
import fr.proline.core.om.storer.msi.IPeaklistStorer

private[msi] class SQLitePeaklistStorer( msiDb: MsiDb ) extends IPeaklistStorer with Logging {
  
  def storePeaklist( peaklist: Peaklist, peaklistContainer: IPeaklistContainer ): Map[String,Int] = {
    
    logger.info( "storing peaklist..." )
    
    // Insert the peaklist in the MSIdb
    this._insertPeaklist( peaklist )
    
    this.storeSpectra( peaklist, peaklistContainer )
    
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
  
  def storeSpectra( peaklist: Peaklist, peaklistContainer: IPeaklistContainer ): Map[String,Int] = {
    
    logger.info( "storing spectra..." )
 
    val spectrumColsList = MsiDbSpectrumTable.getColumnsAsStrList().filter { _ != "id" }
    val spectrumInsertQuery = MsiDbSpectrumTable.buildInsertQuery( spectrumColsList )
    
    // Insert corresponding spectra
    this.msiDb.getOrCreateTransaction.executeBatch( spectrumInsertQuery ) { stmt =>      
      peaklistContainer.eachSpectrum { spectrum => this._insertSpectrum( stmt, spectrum, peaklist.id ) } 
    }
    
    null
  }
  
  private def _insertSpectrum( stmt: ReusableStatement, spectrum: Spectrum, peaklistId: Int ): Unit = {
    
    val bytes = "1 2 3".getBytes()
    
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

