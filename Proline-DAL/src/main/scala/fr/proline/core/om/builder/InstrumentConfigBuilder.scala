package fr.proline.core.om.builder

import scala.collection.mutable.HashMap
import fr.profi.util.primitives._
import fr.profi.util.serialization._
import fr.proline.core.dal.tables.uds._
import fr.proline.core.om.model.msi._
import scala.collection.mutable.ArrayBuffer

/**
 * @author David Bouyssie
 *
 */
object InstrumentConfigBuilder {
  
  protected val instCols = UdsDbInstrumentColumns
  protected val instConfigCols = UdsDbInstrumentConfigColumns
  protected val fragRuleSetCols = UdsDbFragmentationRuleSetColumns
  protected val fragSerieCols = UdsDbFragmentationSeriesColumns
  
  def buildInstrumentConfigs(
    eachInstConfigRecord: (IValueContainer => InstrumentConfig) => Seq[InstrumentConfig],
    eachInstrumentRecordSelector: Array[Long] => ( (IValueContainer => Instrument) => Seq[Instrument] )
  ): Array[InstrumentConfig] = {
    
    val instIdByInstConfigId = new HashMap[Long, Long]
    val instConfigs = eachInstConfigRecord { r =>
      val instConfigId = r.getLong(instConfigCols.ID)
      val instId = r.getLong(instConfigCols.INSTRUMENT_ID)
      instIdByInstConfigId += instConfigId -> instId

      buildInstrumentConfig(r)
    }

    val instIds = instIdByInstConfigId.values.toArray.distinct
    val instruments = this.buildInstruments( eachInstrumentRecordSelector(instIds) )
    val instById = Map() ++ instruments.map(i => i.id -> i)

    for (instConfig <- instConfigs)
      instConfig.instrument = instById(instIdByInstConfigId(instConfig.id))

//    for (instConfig <- instConfigs) {
//      val fragRules = new ArrayBuffer[FragmentationRule]()
//      eachFragmentationSeriesSelector(Array(instConfig.id)) { r => {
//        val serie = new FragmentIonType(r.getString(fragSerieCols.NAME))
//        fragRules += new FragmentIonRequirement(serie.ionSeries.toString(), serie)
//        serie
//      }}
//      instConfig.fragmentationRules = Some(fragRules.toArray)
//    }
    instConfigs.toArray
  }

  def buildFragmentationRuleSets(
    eachFragmentationRuleSetRecord: (IValueContainer => FragmentationRuleSet) => Seq[FragmentationRuleSet],
    eachFragmentationSeriesSelector: Array[Long] => ( (IValueContainer => FragmentIonType) => Seq[FragmentIonType] )

   ) : Array[FragmentationRuleSet] = {

    val fragRuleSets = eachFragmentationRuleSetRecord { r =>
      new FragmentationRuleSet(
        id= r.getLong(fragRuleSetCols.ID),
        name = r.getString(fragRuleSetCols.NAME)
      )
    }

    for (fragRuleSet <- fragRuleSets) {
      val fragRules = new ArrayBuffer[FragmentationRule]()
      eachFragmentationSeriesSelector(Array(fragRuleSet.id)) { r => {
        val serie = new FragmentIonType(r.getString(fragSerieCols.NAME))
        fragRules += new FragmentIonRequirement(serie.ionSeries.toString(), serie)
        serie
      }}
      fragRuleSet.fragmentationRules = fragRules.toArray
    }
    fragRuleSets.toArray
  }

    def buildInstrumentConfig( record: IValueContainer ): InstrumentConfig = {
    
    val r = record

    new InstrumentConfig(
      id = r.getLong(instConfigCols.ID),
      name = r.getString(instConfigCols.NAME),
      instrument = null,
      ms1Analyzer = r.getString(instConfigCols.MS1_ANALYZER),
      msnAnalyzer = r.getString(instConfigCols.MSN_ANALYZER),
      activationType = r.getString(instConfigCols.ACTIVATION_TYPE),
      properties = r.getStringOption(instConfigCols.SERIALIZED_PROPERTIES).map(ProfiJson.deserialize[InstrumentConfigProperties](_))
    )
  }

  def buildInstruments( eachRecord: (IValueContainer => Instrument) => Seq[Instrument] ): Array[Instrument] = {    
    eachRecord( buildInstrument ).toArray
  }
  
  def buildInstrument( record: IValueContainer ): Instrument = {
    
    val r = record
    
    new Instrument(
      id = r.getLong(instCols.ID),
      name = r.getString(instCols.NAME),
      source = r.getStringOrElse(instCols.SOURCE, null),
      properties = r.getStringOption(instCols.SERIALIZED_PROPERTIES).map(ProfiJson.deserialize[InstrumentProperties](_))
    )
  }

}