package fr.proline.core.om.builder

import fr.profi.util.primitives._
import fr.profi.util.serialization._
import fr.proline.core.dal.tables.msi.MsiDbPeaklistSoftwareColumns
import fr.proline.core.om.model.msi._

/**
 * @author David Bouyssie
 *
 */
object PeaklistSoftwareBuilder {
  
  protected val pklSoftCols = MsiDbPeaklistSoftwareColumns
  
  def buildPeaklistSoftwareList(eachRecord: (IValueContainer => PeaklistSoftware) => Seq[PeaklistSoftware]): Array[PeaklistSoftware] = {
    eachRecord( buildPeaklistSoftware ).toArray
  }
  
  def buildPeaklistSoftware(record: IValueContainer): PeaklistSoftware = {
    
    val r = record
    
    new PeaklistSoftware(
      id = r.getLong(pklSoftCols.ID),
      name = r.getString(pklSoftCols.NAME),
      version = r.getString(pklSoftCols.VERSION),
      properties = r.getStringOption(pklSoftCols.SERIALIZED_PROPERTIES).map( ProfiJson.deserialize[PeaklistSoftwareProperties](_) )
    )

  }

}