package fr.proline.core.om.storer.msi

import fr.proline.core._
import fr.proline.core.dal.MsiDb
import com.weiglewilczek.slf4s.Logging
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap

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
  
  private val doubleFormatter = newDecimalFormat(".000000")
  private val floatFormatter = newDecimalFormat(".00")
  
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
            "none", // TODO: create an enumeration near to the Peaklist class
            Option(null),
            1 // peaklist.peaklistSoftware.id
          )
          
      peaklist.id = this.msiDb.extractGeneratedInt( stmt.wrapped )
    }   
    
  }
  
  private def _insertSpectrum( stmt: ReusableStatement, spectrum: Spectrum, peaklistId: Int ): Unit = {
    
    stmt.executeWith(
          Option(null),
          spectrum.title,
          spectrum.precursorMoz,
          spectrum.precursorIntensity,
          spectrum.precursorCharge,
          BoolToSQLStr( spectrum.isSummed ),          
          if( spectrum.firstCycle > 0 ) Some(spectrum.firstCycle) else Option.empty[Int],
          if( spectrum.lastCycle > 0 ) Some(spectrum.lastCycle) else Option.empty[Int],
          if( spectrum.firstScan > 0 ) Some(spectrum.firstScan) else Option.empty[Int],
          if( spectrum.lastScan > 0 ) Some(spectrum.lastScan) else Option.empty[Int],
          if( spectrum.firstTime > 0 ) Some(spectrum.firstTime) else Option.empty[Float],
          if( spectrum.lastTime > 0 ) Some(spectrum.lastTime) else Option.empty[Float],
          
          // moz and intensity lists are formatted as numbers separated by spaces
          spectrum.mozList.getOrElse(Array.empty[Double]).map { this.doubleFormatter.format( _ ) } mkString(" "),
          spectrum.intensityList.getOrElse(Array.empty[Float]).map { this.floatFormatter.format( _ ) } mkString(" "),
          spectrum.peaksCount,
          Option(null),
          peaklistId,
          spectrum.instrumentConfigId
          )

    spectrum.id = this.msiDb.extractGeneratedInt( stmt.wrapped )
    
    ()
  }
  
}
