package fr.proline.core.om.provider.msi.impl

import scala.collection.mutable.HashMap

import fr.profi.jdbc.easy._
import fr.profi.util.primitives._
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.uds._
import fr.proline.core.om.builder.InstrumentConfigBuilder
import fr.proline.core.om.model.msi._
import fr.proline.core.om.provider.msi.IInstrumentConfigProvider
import fr.proline.repository.ProlineDatabaseType

class SQLInstrumentConfigProvider(val udsDbCtx: DatabaseConnectionContext) extends IInstrumentConfigProvider {
  
  require( udsDbCtx.getProlineDatabaseType == ProlineDatabaseType.UDS, "UdsDb connection required")
  
  import SQLInstrumentConfigProvider._
  
  def getInstrumentConfigsAsOptions( instConfigIds: Seq[Long] ): Array[Option[InstrumentConfig]] = {
    val instConfigById = Map() ++ this.getInstrumentConfigs(instConfigIds).map( ic => ic.id -> ic )
    instConfigIds.toArray.map( instConfigById.get(_) )
  }
  
  def getInstrumentConfigs(instConfigIds: Seq[Long]): Array[InstrumentConfig] = {
    if( instConfigIds.isEmpty ) return Array()
    
    DoJDBCReturningWork.withEzDBC(udsDbCtx, { udsEzDBC =>
    
      val instIdByInstConfigId = new HashMap[Long, Long]
      InstrumentConfigBuilder.buildInstrumentConfigs(
        selectInstConfigRecords(udsEzDBC,instConfigIds),
        instIds => selectInstrumentRecords(udsEzDBC,instIds)
      )
      
    })
  }
  
  def getInstruments(instIds: Seq[Long]): Array[Instrument] = {
    if( instIds.isEmpty ) return Array()
    
    DoJDBCReturningWork.withEzDBC(udsDbCtx, { udsEzDBC =>
      InstrumentConfigBuilder.buildInstruments( selectInstrumentRecords(udsEzDBC,instIds) )    
    })
  }
  
  /*def getInstrumentConfigs( instConfigIds: Seq[Long] ): Array[InstrumentConfig] = {
    
    DoJDBCReturningWork.withEzDBC(udsDbCtx, { udsEzDBC =>
      
      // TODO: use static enumerations
      
      val sqlQuery = "SELECT instrument.*,instrument_config.* FROM instrument,instrument_config " +
      "WHERE instrument.id = instrument_config.instrument_id AND instrument_config.id IN(" + instConfigIds.mkString(",") +")"
  
      // Load the instrument configuration record
      udsEzDBC.select( sqlQuery ) { r =>

        val instrument = new Instrument(id = toLong(r.nextAny), name = r, source = r)
        for (instPropStr <- r.nextStringOption) {
          if (StringUtils.isEmpty(instPropStr) == false)
            instrument.properties = Some(ProfiJson.deserialize[InstrumentProperties](instPropStr))
        }

        val instrumentConfig = new InstrumentConfig(
          id = toLong(r.nextAny),
          name = r.nextString,
          instrument = instrument,
          ms1Analyzer = r.nextString,
          msnAnalyzer = r.nextString,
          activationType = r.getString("activation_type")
        )
        for (instConfPropStr <- r.nextStringOption) {
          if (StringUtils.isEmpty(instConfPropStr) == false)
            instrumentConfig.properties = Some(ProfiJson.deserialize[InstrumentConfigProperties](instConfPropStr))
        }

        instrumentConfig
        
      } toArray
    })
    
  }*/

}

object SQLInstrumentConfigProvider {
  
  def selectInstConfigRecords(udsEzDBC: EasyDBC, instConfigIds: Seq[Long]): (IValueContainer => InstrumentConfig) => Seq[InstrumentConfig] = {
    require( instConfigIds.isEmpty == false, "instConfigIds is empty" )
    
    val instConfigQuery = new SelectQueryBuilder1(UdsDbInstrumentConfigTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.ID ~" IN("~ instConfigIds.mkString(",") ~")"
    )
    
    udsEzDBC.select[InstrumentConfig](instConfigQuery)
  }
  
  def selectInstrumentRecords(udsEzDBC: EasyDBC, instIds: Seq[Long]): (IValueContainer => Instrument) => Seq[Instrument] = {
    require( instIds.isEmpty == false, "instIds is empty" )
    
    val instQuery = new SelectQueryBuilder1(UdsDbInstrumentTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.ID ~" IN("~ instIds.mkString(",") ~")"
    )
    
    udsEzDBC.select[Instrument](instQuery)
  }
  
}