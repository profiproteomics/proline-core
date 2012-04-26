package fr.proline.core.om.storer.msi

import fr.proline.core._
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
  import fr.proline.core.SQLFormatterImplicits._
  import fr.proline.core.utils.sql.BoolToSQLStr
  import fr.proline.core.om.model.msi._
  
  def storePeaklist( peaklist: Peaklist, peaklistContainer: IPeaklistContainer ): Map[String,Int] = {
    
    // Insert the peaklist in the MSIdb
    this._insertPeaklist( peaklist )
    
    // Insert corresponding spectra
    peaklistContainer.eachSpectrum { s =>
      
      // TODO: insert spectrum
      this._insertSpectrum( s, peaklist.id )
    }
    
    null
  }
  
  private def _insertPeaklist( peaklist: Peaklist ): Unit = {
        
    val msiDbConn = this.msiDb.getOrCreateConnection()
    val stmt = msiDbConn.prepareStatement( "INSERT INTO peaklist VALUES ("+ "?,"*7 +"?)",
                                           java.sql.Statement.RETURN_GENERATED_KEYS ) 
    
    new ReusableStatement( stmt, msiDb.config.sqlFormatter ) <<
      Some(null) <<
      peaklist.fileType <<
      peaklist.path <<
      peaklist.rawFileName <<
      peaklist.msLevel <<
      "none" << // TODO: create an enumeration near to the Peaklist class
      Some(null) <<
      1 // peaklist.peaklistSoftware.id

    stmt.execute()
    peaklist.id = this.msiDb.extractGeneratedInt( stmt )
    
  }
  
  private def _insertSpectrum( spectrum: Spectrum, peaklistId: Int ): Unit = {
    
    val msiDbConn = this.msiDb.getOrCreateConnection()
    
    ()
  }
  
}
