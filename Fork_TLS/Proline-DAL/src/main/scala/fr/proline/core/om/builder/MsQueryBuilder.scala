package fr.proline.core.om.builder

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import fr.profi.util.primitives._
import fr.profi.util.serialization._
import fr.proline.core.dal.tables.msi._
import fr.proline.core.dal.tables.uds._
import fr.proline.core.om.model.msi._
import fr.proline.core.om.provider.msi.impl.SQLPTMProvider

/**
 * @author David Bouyssie
 *
 */

object MsQueryBuilder {
  
  private val MsQueryCols = MsiDbMsQueryColumns

  def buildMs1Query( record: IValueContainer ): MsQuery = {
    
    val r = record
    
    new Ms1Query(
      id = r.getLong(MsQueryCols.ID),
      initialId = r.getInt(MsQueryCols.INITIAL_ID),
      moz = r.getDouble(MsQueryCols.MOZ),
      charge = r.getInt(MsQueryCols.CHARGE),
      properties = r.getStringOption(MsQueryCols.SERIALIZED_PROPERTIES).map(ProfiJson.deserialize[MsQueryProperties](_))
    )
  }
  
  def buildMs2Query( record: IValueContainer, spectrumTitleById: Map[Long,String] ): MsQuery = {
    
    val r = record
    val spectrumId = r.getLong(MsQueryCols.SPECTRUM_ID)
    val spectrumTitle = spectrumTitleById(spectrumId)
      
    new Ms2Query(
      id = r.getLong(MsQueryCols.ID),
      initialId = r.getInt(MsQueryCols.INITIAL_ID),
      moz = r.getDouble(MsQueryCols.MOZ),
      charge = r.getInt(MsQueryCols.CHARGE),
      spectrumTitle = spectrumTitle,
      spectrumId = spectrumId,
      properties = r.getStringOption(MsQueryCols.SERIALIZED_PROPERTIES).map(ProfiJson.deserialize[MsQueryProperties](_))
    )
  }
  
}