package fr.proline.core.om.storer.msi

import com.typesafe.scalalogging.slf4j.Logging

import fr.proline.core.om.model.msi.{Peaklist,IPeaklistContainer}
import fr.proline.core.om.storer.msi.impl.StorerContext
import fr.proline.core.om.storer.msi.impl.SQLPeaklistWriter
import fr.proline.core.om.storer.msi.impl.PgPeaklistWriter

/**
 * Provides methods to write PeakList information and associated data 
 * in persitence repository :
 *  - PeakList & PeakList Software
 *  - Spectra 
 * 
 */
trait IPeaklistWriter extends Logging {
  
  /**
   * Specific implementation of IRsStorer storePeaklist method: 
   * Store PeakList & Peaklist software 
   *
   */  
   def insertPeaklist(peaklist: Peaklist, context : StorerContext): Long
  
  /**
   * Specific implementation of IRsStorer storeSpectra method
   */
  def insertSpectra( peaklistId: Long, peaklistContainer: IPeaklistContainer, context : StorerContext ): StorerContext
  
  // FIXME: what is this method ???
  //def rollBackInfo(peaklistId: Int, context : StorerContext): Unit
   
}

/** A factory object for implementations of the IPeaklistStorer trait */
object PeaklistWriter {
  
  import fr.proline.repository.DriverType

  def apply( driverType: DriverType ): IPeaklistWriter = {
    driverType match {
      case DriverType.POSTGRESQL => PgPeaklistWriter
      case _ => SQLPeaklistWriter
    }
  }
}
