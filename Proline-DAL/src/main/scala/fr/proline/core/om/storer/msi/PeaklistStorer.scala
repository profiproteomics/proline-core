package fr.proline.core.om.storer.msi

import scala.Array.canBuildFrom

import com.weiglewilczek.slf4s.Logging

import fr.proline.core.dal.SQLFormatterImplicits.{someFloat2Formattable,someInt2Formattable,someNull2Formattable}
import fr.proline.core.dal.MsiDb
import fr.proline.core.om.model.msi.{IPeaklistContainer,Peaklist,Spectrum}
import fr.proline.core.utils.sql.{BoolToSQLStr,newDecimalFormat}
import net.noerd.prequel.SQLFormatterImplicits.{double2Formattable,float2Formattable,int2Formattable,string2Formattable}
import fr.proline.core.utils.lzma.EasyLzma

/** A factory object for implementations of the IRsStorer trait */
object PeaklistStorer {
  def apply( msiDb: MsiDb ) = new PeaklistStorer( msiDb )
}

class PeaklistStorer( msiDb: MsiDb ) extends Logging {
  
  import net.noerd.prequel.ReusableStatement
  import net.noerd.prequel.SQLFormatterImplicits._
  import fr.proline.core.dal.SQLFormatterImplicits._
  import fr.proline.core.utils.sql.{BoolToSQLStr,newDecimalFormat}
  import fr.proline.core.om.model.msi._
  
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
    
    // Insert the peaklist in the MSIdb
    this._insertPeaklist( peaklist )
    
    val msiDbTx = this.msiDb.getOrCreateTransaction()
    
    // Insert corresponding spectra    
    msiDbTx.executeBatch( "INSERT INTO spectrum VALUES ("+ "?,"*17 +"?)" ) { stmt =>      
      peaklistContainer.eachSpectrum { spectrum => this._insertSpectrum( stmt, spectrum, peaklist.id ) } 
    }
    
    null
  }
  
  private def _insertPeaklist( peaklist: Peaklist ): Unit = {
    
    val msiDbTx = this.msiDb.getOrCreateTransaction()
    msiDbTx.executeBatch( "INSERT INTO peaklist VALUES ("+ "?,"*7 +"?)", true ) { stmt =>
      stmt.executeWith(
            Option(null),
            peaklist.fileType,
            peaklist.path,
            peaklist.rawFileName,
            peaklist.msLevel,
            "lzma", // none | lzma TODO: create an enumeration near to the Peaklist class
            Option(null),
            1 // peaklist.peaklistSoftware.id
          )
          
      peaklist.id = this.msiDb.extractGeneratedInt( stmt.wrapped )
    }   
    
  }
  
  private def _insertSpectrum( stmt: ReusableStatement, spectrum: Spectrum, peaklistId: Int ): Unit = {
    
    // Define some vars
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
      Option(null) <<
      spectrum.title <<
      spectrum.precursorMoz <<
      spectrum.precursorIntensity <<
      spectrum.precursorCharge <<
      BoolToSQLStr( spectrum.isSummed ) <<         
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
    stmt.wrapped.setBytes(13,compressedMozList)
    stmt.wrapped.setBytes(14,compressedIntList)
    
    // Execute statement
    stmt.execute()

    spectrum.id = this.msiDb.extractGeneratedInt( stmt.wrapped )
    
    ()
  }
  
}
