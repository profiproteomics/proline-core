package fr.proline.core.om.provider.uds.impl

import fr.profi.util.serialization.ProfiJson
import fr.profi.util.misc.MapIfNotNull
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.uds.UdsDbInstrumentTable
import fr.proline.core.om.model.msi.Instrument
import fr.proline.core.om.model.msi.InstrumentProperties
import fr.proline.core.om.provider.uds.IInstrumentProvider
import fr.profi.util.primitives._
import fr.profi.util.StringUtils
    
class SQLInstrumentProvider(val dbCtx: DatabaseConnectionContext) extends IInstrumentProvider {
  
  def getInstrumentsAsOptions( instrumentIds: Seq[Long] ): Array[Option[Instrument]] = {
    if( instrumentIds.isEmpty ) return Array()
    
    val instConfigById = Map() ++ this.getInstruments(instrumentIds).map( i => i.id -> i )
    instrumentIds.toArray.map( instConfigById.get(_) )
  }
  
  def getInstruments( instrumentIds: Seq[Long] ): Array[Instrument] = {
    if( instrumentIds.isEmpty ) return Array()
    
    DoJDBCReturningWork.withEzDBC(dbCtx, { udsEzDBC =>
      
      val instrumentQuery = new SelectQueryBuilder1(UdsDbInstrumentTable).mkSelectQuery( (t,c) =>
        List(t.*) -> "WHERE "~ t.ID ~" IN("~ instrumentIds.mkString(",") ~")"
      )
  
      // Load the instrument configuration record
      udsEzDBC.select( instrumentQuery ) { r =>

        val instrument = new Instrument(
          id = toLong(r.nextAny),
          name = r.nextString,
          source = r.nextString
        )
        
        for (instPropStr <- r.nextStringOption) {
          if (StringUtils.isEmpty(instPropStr) == false)
            instrument.properties = Some(ProfiJson.deserialize[InstrumentProperties](instPropStr))
        }

        instrument
        
      } toArray
    })
    
    
  }

}
