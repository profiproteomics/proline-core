package fr.proline.core.om.provider.msi.impl

import fr.profi.jdbc.easy._
import fr.profi.util.primitives._
import fr.proline.context.UdsDbConnectionContext
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.SelectQueryBuilder3
import fr.proline.core.dal.tables.uds._
import fr.proline.core.om.builder.InstrumentConfigBuilder
import fr.proline.core.om.model.msi._
import fr.proline.core.om.provider.msi.IInstrumentConfigProvider

class SQLInstrumentConfigProvider(val udsDbCtx: UdsDbConnectionContext) extends IInstrumentConfigProvider {
  
  import SQLInstrumentConfigProvider._
  
  def getInstrumentConfigsAsOptions( instConfigIds: Seq[Long] ): Array[Option[InstrumentConfig]] = {
    val instConfigById = Map() ++ this.getInstrumentConfigs(instConfigIds).map( ic => ic.id -> ic )
    instConfigIds.toArray.map( instConfigById.get(_) )
  }
  
  def getInstrumentConfigs(instConfigIds: Seq[Long]): Array[InstrumentConfig] = {
    if( instConfigIds.isEmpty ) return Array()
    
    DoJDBCReturningWork.withEzDBC(udsDbCtx) { udsEzDBC =>

      InstrumentConfigBuilder.buildInstrumentConfigs(
        selectInstConfigRecords(udsEzDBC,instConfigIds),
        instIds => selectInstrumentRecords(udsEzDBC,instIds)//,
       // instIds => selectFragmentationSeriesRecords(udsEzDBC,instIds)
      )
      
    }
  }
  
  def getInstruments(instIds: Seq[Long]): Array[Instrument] = {
    if( instIds.isEmpty ) return Array()
    
    DoJDBCReturningWork.withEzDBC(udsDbCtx) { udsEzDBC =>
      InstrumentConfigBuilder.buildInstruments( selectInstrumentRecords(udsEzDBC,instIds) )    
    }
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
    require( instConfigIds.nonEmpty, "instConfigIds is empty" )
    
    val instConfigQuery = new SelectQueryBuilder1(UdsDbInstrumentConfigTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.ID ~" IN("~ instConfigIds.mkString(",") ~")"
    )
    
    udsEzDBC.select[InstrumentConfig](instConfigQuery)
  }
  
  def selectInstrumentRecords(udsEzDBC: EasyDBC, instIds: Seq[Long]): (IValueContainer => Instrument) => Seq[Instrument] = {
    require( instIds.nonEmpty, "instIds is empty" )
    
    val instQuery = new SelectQueryBuilder1(UdsDbInstrumentTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.ID ~" IN("~ instIds.mkString(",") ~")"
    )
    
    udsEzDBC.select[Instrument](instQuery)
  }

  def selectFragRuleSetsRecords(udsEzDBC: EasyDBC, fragRuleSetsIds: Seq[Long]): (IValueContainer => FragmentationRuleSet) => Seq[FragmentationRuleSet] = {
    require( fragRuleSetsIds.nonEmpty, "fragRuleSetsIds is empty" )

    val fragRuleSetQuery = new SelectQueryBuilder1(UdsDbFragmentationRuleSetTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.ID ~" IN("~ fragRuleSetsIds.mkString(",") ~")"
    )

    udsEzDBC.select[FragmentationRuleSet](fragRuleSetQuery)
  }

  def selectFragRuleSetsRecordsWithName(udsEzDBC: EasyDBC, name: String): (IValueContainer => FragmentationRuleSet) => Seq[FragmentationRuleSet] = {
    require( name!=null, "name is empty" )

    val fragRuleSetQuery = new SelectQueryBuilder1(UdsDbFragmentationRuleSetTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.NAME ~" = "~ name ~""
    )

    udsEzDBC.select[FragmentationRuleSet](fragRuleSetQuery)
  }

  def selectFragmentationSeriesRecords(udsEzDBC: EasyDBC, fragRuleSetsIds: Seq[Long]): (IValueContainer => FragmentIonType) => Seq[FragmentIonType] = {
    require( fragRuleSetsIds.nonEmpty, "fragmentationRuleSetsIds is empty" )

    val fragmentationSeriesQuery = new SelectQueryBuilder3(UdsDbFragmentationSeriesTable, UdsDbFragmentationRuleSetMapTable, UdsDbFragmentationRuleTable).mkSelectQuery( (t1,c1,t2,c2,t3,c3) =>
      List(t1.*) -> "WHERE "~ t3.FRAGMENT_SERIES_ID ~"="~ t1.ID~" AND "~ t2.FRAGMENTATION_RULE_ID ~"="~ t3.ID~" AND "~ t2.FRAGMENTATION_RULE_SET_ID ~" IN("~ fragRuleSetsIds.mkString(",") ~")"
    )
    
    udsEzDBC.select[FragmentIonType](fragmentationSeriesQuery)
  }
  
}