package fr.proline.core.om.builder

import scala.collection.mutable.HashMap
import fr.profi.util.primitives._
import fr.profi.util.serialization._
import fr.proline.core.dal.tables.msi._
import fr.proline.core.om.model.msi._

/**
 * @author David Bouyssie
 *
 */
object PeaklistBuilder {
  
  protected val peaklistCols = MsiDbPeaklistColumns

  def buildPeaklists(
    eachPeaklistRecord: (IValueContainer => Peaklist) => Seq[Peaklist],
    eachPklSoftRecordSelector: Array[Long] => ( (IValueContainer => PeaklistSoftware) => Seq[PeaklistSoftware] )
  ): Array[Peaklist] = {
    
    val pklSoftIdByPklId = new HashMap[Long, Long]

    val peaklists = eachPeaklistRecord { r =>
    
      pklSoftIdByPklId += (r.getLong(peaklistCols.ID) -> r.getLong(peaklistCols.PEAKLIST_SOFTWARE_ID))
      
      buildPeaklist( r )
    }

    val pklSofts = PeaklistSoftwareBuilder.buildPeaklistSoftwareList( eachPklSoftRecordSelector(pklSoftIdByPklId.values.toArray.distinct) )
    val pklSoftById = Map() ++ pklSofts.map(ps => ps.id -> ps)

    for (pkl <- peaklists) {
      pkl.peaklistSoftware = pklSoftById(pklSoftIdByPklId(pkl.id))
    }

    peaklists.toArray

  }
  
  def buildPeaklist( record: IValueContainer ): Peaklist = {
    
    val r = record
    
    val propsOpt = r.getStringOption(peaklistCols.SERIALIZED_PROPERTIES).map( propStr =>
      ProfiJson.deserialize[PeaklistProperties](propStr)
    )
    
    new Peaklist(
      id = r.getLong(peaklistCols.ID),
      fileType = r.getString(peaklistCols.TYPE),
      path = r.getString(peaklistCols.PATH),
      rawFileIdentifier = r.getStringOrElse(peaklistCols.RAW_FILE_IDENTIFIER, ""),
      msLevel = r.getInt(peaklistCols.MS_LEVEL),
      properties = propsOpt
    )
  }

}