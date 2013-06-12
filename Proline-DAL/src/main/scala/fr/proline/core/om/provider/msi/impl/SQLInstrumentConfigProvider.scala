package fr.proline.core.om.provider.msi.impl

import com.codahale.jerkson.Json.parse
import fr.profi.jdbc.easy._
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.om.model.msi.{Instrument,InstrumentConfig,InstrumentProperties,InstrumentConfigProperties}
import fr.proline.core.om.provider.msi.IInstrumentConfigProvider
import fr.proline.util.StringUtils
import fr.proline.util.primitives._
    
class SQLInstrumentConfigProvider(val udsDbCtx: DatabaseConnectionContext) extends IInstrumentConfigProvider {
  
  def getInstrumentConfigsAsOptions( instConfigIds: Seq[Long] ): Array[Option[InstrumentConfig]] = {
    val instConfigById = Map() ++ this.getInstrumentConfigs(instConfigIds).map( ic => ic.id -> ic )
    instConfigIds.toArray.map( instConfigById.get(_) )
  }
  
  def getInstrumentConfigs( instConfigIds: Seq[Long] ): Array[InstrumentConfig] = {
    
    DoJDBCReturningWork.withEzDBC(udsDbCtx, { udsEzDBC =>
      
      // TODO: use static enumerations
      
      val sqlQuery = "SELECT instrument.*,instrument_config.* FROM instrument,instrument_config " +
      "WHERE instrument.id = instrument_config.instrument_id AND instrument_config.id IN(" + instConfigIds.mkString(",") +")"
  
      // Load the instrument configuration record
      udsEzDBC.select( sqlQuery ) { r =>

        val instrument = new Instrument(id = toLong(r.nextAny), name = r, source = r)
        for (instPropStr <- r.nextStringOption) {
          if (StringUtils.isEmpty(instPropStr) == false)
            instrument.properties = Some(parse[InstrumentProperties](instPropStr))
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
            instrumentConfig.properties = Some(parse[InstrumentConfigProperties](instConfPropStr))
        }

        instrumentConfig
        
      } toArray
    })
    
  }

}
