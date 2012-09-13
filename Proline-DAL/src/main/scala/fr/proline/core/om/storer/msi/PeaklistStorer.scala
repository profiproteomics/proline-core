package fr.proline.core.om.storer.msi

import com.weiglewilczek.slf4s.Logging
import fr.proline.core.dal.MsiDb
import fr.proline.core.om.model.msi.{Peaklist,IPeaklistContainer}

/**
 * Provides methods to write PeakList information and associated data 
 * in persitence repository :
 *  - PeakList & PeakList Software
 *  - Spectra 
 * 
 */
trait IPeaklistStorer extends Logging {
  
  import fr.proline.core.utils.sql.newDecimalFormat
  
  object TrailingZerosStripper {
    
    private val decimalParser = """(\d+\.\d*?)0*$""".r
    
    def apply( decimalAsStr: String ): String = {
      val decimalParser(compactDecimal) = decimalAsStr
      compactDecimal
    }
  }
  
  protected val doubleFormatter = newDecimalFormat("0.000000")
  protected val floatFormatter = newDecimalFormat("0.00")
  
  /**
   * Store PeakList and associated information in repository :
   *  - peaklist & peaklist software 
   *  - spectra
   * 
   */  
  def storePeaklist( peaklist: Peaklist, peaklistContainer: IPeaklistContainer ): Map[String,Int]
  def storeSpectra( peaklist: Peaklist, peaklistContainer: IPeaklistContainer ): Map[String,Int]
  
}

/** A factory object for implementations of the IPeaklistStorer trait */
object PeaklistStorer {
  
  //import fr.proline.core.om.storer.msi.impl.GenericRsStorer
  import fr.proline.core.om.storer.msi.impl.PgPeaklistStorer
  import fr.proline.core.om.storer.msi.impl.SQLitePeaklistStorer

  def apply(msiDb: MsiDb ): IPeaklistStorer = { msiDb.config.driver match {
    case "org.postgresql.Driver" => new PgPeaklistStorer( msiDb )
    case "org.sqlite.JDBC" => new SQLitePeaklistStorer( msiDb )
    //case _ => 
    }
  }
}
