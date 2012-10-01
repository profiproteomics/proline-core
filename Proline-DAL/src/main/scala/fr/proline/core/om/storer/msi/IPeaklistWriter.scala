package fr.proline.core.om.storer.msi

import com.weiglewilczek.slf4s.Logging
import fr.proline.core.dal.MsiDb
import fr.proline.core.om.model.msi.{Peaklist,IPeaklistContainer}
import fr.proline.core.om.storer.msi.impl.StorerContext
import fr.proline.core.om.storer.msi.impl.SQLPeaklistWriter
import fr.proline.core.om.storer.msi.impl.PgSQLSpectraWriter

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
   def storePeaklist(peaklist: Peaklist, context : StorerContext):Int
  
  /**
   * Specific implementation of IRsStorer storeSpectra method
   */
  def storeSpectra( peaklistId: Int, peaklistContainer: IPeaklistContainer, context : StorerContext ): StorerContext
  
  def rollBackInfo(peaklistId: Int, context : StorerContext)
   
}

/** A factory object for implementations of the IPeaklistStorer trait */
object IPeaklistWriter {
  
  //import fr.proline.core.om.storer.msi.impl.GenericRsStorer

  def apply( jdbcDriverClassName: String ): IPeaklistWriter = {
    jdbcDriverClassName match {
      case "org.postgresql.Driver" => new PgSQLSpectraWriter()
      case _ => new SQLPeaklistWriter()
    }
  }
}
