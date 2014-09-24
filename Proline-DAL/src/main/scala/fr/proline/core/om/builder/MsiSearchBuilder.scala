package fr.proline.core.om.builder

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import fr.profi.util.primitives._
import fr.profi.util.serialization._
import fr.proline.core.dal.tables.msi._
import fr.proline.core.dal.tables.uds._
import fr.proline.core.om.model.msi._
import fr.proline.core.om.provider.msi.IPTMProvider

/**
 * @author David Bouyssie
 *
 */
object MsiSearchBuilder {

  protected val msiSearchCols = MsiDbMsiSearchColumns
  protected val ssCols = MsiDbSearchSettingsColumns
  protected val ionSearchCols = MsiDbIonSearchColumns
  protected val msmsSearchCols = MsiDbMsmsSearchColumns
  protected val seqDbCols = MsiDbSeqDatabaseColumns
  protected val seqDbTblName = MsiDbSeqDatabaseTable.name
  protected val ssSeqDbMapCols = MsiDbSearchSettingsSeqDatabaseMapColumns
  protected val ssSeqDbMapTblName = MsiDbSearchSettingsSeqDatabaseMapTable.name
  protected val usedPtmCols = MsiDbUsedPtmColumns  
  protected val usedEnzCols = MsiDbUsedEnzymeColumns
  
  def selectRecords[T]( recordIds: Seq[Long], recordById: Map[Long,IValueContainer] ): ( (IValueContainer => Unit) => Unit ) = {
    unitMapper: (IValueContainer => Unit) => recordIds.withFilter(recordById.contains(_)).map( id => recordById(id) ).foreach _
  }
  
  def selectGroupedRecords[T]( recordIds: Seq[Long], recordsById: Map[Long,Seq[IValueContainer]] ): ( (IValueContainer => Unit) => Unit ) = {
    unitMapper: (IValueContainer => Unit) => recordIds.withFilter(recordsById.contains(_)).flatMap( id => recordsById(id) ).foreach _
  }
  
  def selectAndMapRecords[T]( recordIds: Seq[Long], recordById: Map[Long,IValueContainer] ): ( (IValueContainer => T) => Seq[T] ) = {
    mapper: (IValueContainer => T) => recordIds.withFilter(recordById.contains(_)).map( id => mapper(recordById(id)) )
  }
  
  def selectAndMapGroupedRecords[T]( recordIds: Seq[Long], recordsById: Map[Long,Seq[IValueContainer]] ): ( (IValueContainer => T) => Seq[T] ) = {
    mapper: (IValueContainer => T) => recordIds.withFilter(recordsById.contains(_)).flatMap( id => recordsById(id).map(mapper(_) ) )
  }
  
  def buildMsiSearches(
    msiSearchRecords: Seq[IValueContainer],
    peaklistRecordById: Map[Long,IValueContainer],
    pklSoftRecordById: Map[Long,IValueContainer],
    searchSettingsRecordById: Map[Long,IValueContainer],
    pmfSearchSettingsRecordById: Map[Long,IValueContainer],
    msmsSearchSettingsRecordById: Map[Long,IValueContainer],
    searchedSeqDbRecordsBySSId: Map[Long,Seq[IValueContainer]],
    usedEnzymeRecordsBySSId: Map[Long,Seq[IValueContainer]],
    usedPtmRecordsBySSId: Map[Long,Seq[IValueContainer]],
    enzymeRecordById: Map[Long,IValueContainer],
    enzymeCleavageRecordsByEnzymeId: Map[Long,IValueContainer],
    instConfigRecordById: Map[Long,IValueContainer],
    instrumentRecordById: Map[Long,IValueContainer],
    fragmentationSerieById: Map[Long,IValueContainer],
    ptmProvider: IPTMProvider
  ): Array[MSISearch] = {
    
    this.buildMsiSearches(
      { msiSearchBuilder: (IValueContainer => MSISearch) => msiSearchRecords.map( msiSearchBuilder(_) ) },
      { pklIds: Array[Long] => selectAndMapRecords(pklIds,peaklistRecordById) },
      { pklSoftIds: Array[Long] => selectAndMapRecords(pklSoftIds,pklSoftRecordById) },
      { ssIds: Array[Long] => selectAndMapRecords(ssIds,searchSettingsRecordById) },
      { ssIds: Array[Long] => selectRecords(ssIds,pmfSearchSettingsRecordById) },
      { ssIds: Array[Long] => selectRecords(ssIds,msmsSearchSettingsRecordById) },
      { ssId: Long => selectAndMapGroupedRecords( Array(ssId), searchedSeqDbRecordsBySSId ) },
      { ssIds: Array[Long] => selectGroupedRecords( ssIds, usedEnzymeRecordsBySSId ) },
      { ssIds: Array[Long] => selectGroupedRecords( ssIds, usedPtmRecordsBySSId ) },
      { enzymeIds: Array[Long] => selectAndMapRecords( enzymeIds, enzymeRecordById ) },
      { enzymeId: Long => selectAndMapRecords( Array(enzymeId), enzymeCleavageRecordsByEnzymeId ) },
      { instConfigIds: Array[Long] => selectAndMapRecords( instConfigIds, instConfigRecordById ) },
      { instIds: Array[Long] => selectAndMapRecords( instIds, instrumentRecordById ) },
      { fragSeriesIds: Array[Long] => selectAndMapRecords( fragSeriesIds, fragmentationSerieById ) },
      ptmProvider
    )
    
  }

  def buildMsiSearches(
    eachMsiSearchRecord: (IValueContainer => MSISearch) => Seq[MSISearch],
    eachPeaklistRecordSelector: Array[Long] => ( (IValueContainer => Peaklist) => Seq[Peaklist] ),
    eachPklSoftRecordSelector: Array[Long] => ( (IValueContainer => PeaklistSoftware) => Seq[PeaklistSoftware] ),
    eachSearchSettingsRecordSelector: Array[Long] => ( (IValueContainer => SearchSettings) => Seq[SearchSettings] ),
    eachPMFSearchSettingsRecordSelector: Array[Long] => ( (IValueContainer => Unit) => Unit ),
    eachMSMSSearchSettingsRecordSelector: Array[Long] => ( (IValueContainer => Unit) => Unit ),
    eachSearchedSeqDbRecordSelector: Long => ( (IValueContainer => SeqDatabase) => Seq[SeqDatabase] ),
    eachUsedEnzymeRecordSelector: Array[Long] => ( (IValueContainer => Unit) => Unit ),
    eachUsedPtmRecordSelector: Array[Long] => ( (IValueContainer => Unit) => Unit ),
    eachEnzymeRecordSelector: Array[Long] => ( (IValueContainer => Enzyme) => Seq[Enzyme] ),
    eachEnzymeCleavageRecordSelector: Long => ( (IValueContainer => EnzymeCleavage) => Seq[EnzymeCleavage] ),
    eachInstConfigRecordSelector: Array[Long] => ( (IValueContainer => InstrumentConfig) => Seq[InstrumentConfig] ),
    eachInstrumentRecordSelector: Array[Long] => ( (IValueContainer => Instrument) => Seq[Instrument] ),
    eachFragSeriesRecordSelector: Array[Long] => ( (IValueContainer => FragmentIonType) => Seq[FragmentIonType] ),
    ptmProvider: IPTMProvider
  ): Array[MSISearch] = {
    
    val searchSettingsIdByMsiSearchId = new HashMap[Long, Long]
    val peaklistIdByMsiSearchId = new HashMap[Long, Long]

    val msiSearches = eachMsiSearchRecord { r =>
      val msiSearchId = r.getLong(msiSearchCols.ID)
      
      searchSettingsIdByMsiSearchId += (msiSearchId -> r.getLong(msiSearchCols.SEARCH_SETTINGS_ID))
      peaklistIdByMsiSearchId += (msiSearchId -> r.getLong(msiSearchCols.PEAKLIST_ID))
      
      this.buildMsiSearch( r )
    }
  
    // Retrieve the peaklists
    val peaklists = PeaklistBuilder.buildPeaklists( 
      eachPeaklistRecordSelector(peaklistIdByMsiSearchId.values.toArray.distinct),
      eachPklSoftRecordSelector
    )
    val pklById = Map() ++ peaklists.map(pkl => pkl.id -> pkl)

    // Retrieve the search settings
    val distinctSearchSettingsIds = searchSettingsIdByMsiSearchId.values.toArray.distinct
    val searchSettingsList = buildSearchSettingsList(
      eachSearchSettingsRecordSelector(distinctSearchSettingsIds),
      eachPMFSearchSettingsRecordSelector(distinctSearchSettingsIds),
      eachMSMSSearchSettingsRecordSelector(distinctSearchSettingsIds),
      eachUsedEnzymeRecordSelector(distinctSearchSettingsIds),
      eachUsedPtmRecordSelector(distinctSearchSettingsIds),
      eachEnzymeRecordSelector,
      eachEnzymeCleavageRecordSelector,
      eachInstConfigRecordSelector,
      eachInstrumentRecordSelector,
      eachFragSeriesRecordSelector,
      eachSearchedSeqDbRecordSelector,
      ptmProvider
    )
    
    val ssById = Map() ++ searchSettingsList.map(ss => ss.id -> ss)

    for (msiSearch <- msiSearches) {
      msiSearch.peakList = pklById(peaklistIdByMsiSearchId(msiSearch.id))
      msiSearch.searchSettings = ssById(searchSettingsIdByMsiSearchId(msiSearch.id))
    }

    msiSearches.toArray
  }
  
  def buildMsiSearch( record: IValueContainer ): MSISearch = {
    
    val r = record
    
    val propsOpt = r.getStringOption(msiSearchCols.SERIALIZED_PROPERTIES).map( propStr =>
      ProfiJson.deserialize[MSISearchProperties](propStr)
    )

    new MSISearch(
      id = r.getLong(msiSearchCols.ID),
      resultFileName = r.getString(msiSearchCols.RESULT_FILE_NAME),
      submittedQueriesCount = r.getInt(msiSearchCols.SUBMITTED_QUERIES_COUNT),
      searchSettings = null,
      peakList = null,
      date = r.getDate(msiSearchCols.DATE),
      title = r.getStringOrElse(msiSearchCols.TITLE, ""),
      resultFileDirectory = r.getStringOrElse(msiSearchCols.RESULT_FILE_DIRECTORY, ""),
      jobNumber = r.getIntOrElse(msiSearchCols.JOB_NUMBER, 0),
      userName = r.getStringOrElse(msiSearchCols.USER_NAME, ""),
      userEmail = r.getStringOrElse(msiSearchCols.USER_EMAIL, ""),
      queriesCount = r.getIntOrElse(msiSearchCols.QUERIES_COUNT, 0),
      searchedSequencesCount = r.getIntOrElse(msiSearchCols.SEARCHED_SEQUENCES_COUNT, 0),
      properties = propsOpt
    )

  }

  def buildSearchSettingsList(
    eachSearchSettingsRecord: (IValueContainer => SearchSettings) => Seq[SearchSettings],
    eachPMFSearchSettingsRecord: (IValueContainer => Unit) => Unit,
    eachMSMSSearchSettingsRecord: (IValueContainer => Unit) => Unit,
    eachUsedEnzymeRecord: (IValueContainer => Unit) => Unit,
    eachUsedPtmRecord: (IValueContainer => Unit) => Unit,
    eachEnzymeRecordSelector: Array[Long] => ( (IValueContainer => Enzyme) => Seq[Enzyme] ),
    eachEnzymeCleavageRecordSelector: Long => ( (IValueContainer => EnzymeCleavage) => Seq[EnzymeCleavage] ),
    eachInstConfigRecordSelector: Array[Long] => ( (IValueContainer => InstrumentConfig) => Seq[InstrumentConfig] ),
    eachInstrumentRecordSelector: Array[Long] => ( (IValueContainer => Instrument) => Seq[Instrument] ),
    eachFragmentationSeriesSelector: Array[Long] => ( (IValueContainer => FragmentIonType) => Seq[FragmentIonType] ),
    eachSearchedSeqDbRecordSelector: Long => ( (IValueContainer => SeqDatabase) => Seq[SeqDatabase] ),
    ptmProvider: IPTMProvider
  ): Array[SearchSettings] = {
    
    // Retrieve PMF search settings
    val pmfSettingsById = new HashMap[Long,PMFSearchSettings]
    eachPMFSearchSettingsRecord { r =>
      pmfSettingsById += r.getLong(ionSearchCols.ID) -> new PMFSearchSettings(
        maxProteinMass = r.getDoubleOption(ionSearchCols.MAX_PROTEIN_MASS),
        minProteinMass = r.getDoubleOption(ionSearchCols.MIN_PROTEIN_MASS),
        proteinPI = r.getDoubleOption(ionSearchCols.PROTEIN_PI).map( _.toFloat )
      )
    }
    
    // Retrieve MS/MS search settings
    val msmsSettingsById = new HashMap[Long,MSMSSearchSettings]
    eachMSMSSearchSettingsRecord { r =>
      msmsSettingsById += r.getLong(msmsSearchCols.ID) -> new MSMSSearchSettings(
        ms2ChargeStates = r.getString(msmsSearchCols.FRAGMENT_CHARGE_STATES),
        ms2ErrorTol = r.getDouble(msmsSearchCols.FRAGMENT_MASS_ERROR_TOLERANCE),
        ms2ErrorTolUnit = r.getString(msmsSearchCols.FRAGMENT_MASS_ERROR_TOLERANCE_UNIT)
      )
    }
    
    val instConfigIdBySSId = new HashMap[Long, Long]
    val searchSettingsList = eachSearchSettingsRecord { r =>

      val ssId = r.getLong(ssCols.ID)
      instConfigIdBySSId += ssId -> r.getLong(ssCols.INSTRUMENT_CONFIG_ID)

      buildSearchSettings( r, pmfSettingsById.get(ssId), msmsSettingsById.get(ssId) )
    }
    
    val enzymeIdsBySSId = new HashMap[Long, ArrayBuffer[Long]]
    val enzIds = new ArrayBuffer[Long]
    
    eachUsedEnzymeRecord { r =>
      val (ssId, enzId) = ( r.getLong(usedEnzCols.SEARCH_SETTINGS_ID), r.getLong(usedEnzCols.ENZYME_ID) )
      enzymeIdsBySSId.getOrElseUpdate(ssId, new ArrayBuffer[Long]) += enzId
      enzIds += enzId
    }

    val ptmSpecIdsBySSId = new HashMap[Long, ArrayBuffer[Long]]
    val ptmSpecIds = new ArrayBuffer[Long]
    val fixedPtmById = new HashMap[Long, Boolean]
    
    eachUsedPtmRecord { r =>
      val (ssId, ptmSpecId, shortName, isFixed) = (
        r.getLong(usedPtmCols.SEARCH_SETTINGS_ID),
        r.getLong(usedPtmCols.PTM_SPECIFICITY_ID),
        r.getString(usedPtmCols.SHORT_NAME),
        r.getBoolean(usedPtmCols.IS_FIXED)
      )

      ptmSpecIdsBySSId.getOrElseUpdate(ssId, new ArrayBuffer[Long]) += ptmSpecId
      ptmSpecIds += ptmSpecId
      fixedPtmById += ptmSpecId -> isFixed
    }

    //val seqDbById = Map() ++ this.getSeqDatabases(seqDbIds.distinct).map(s => s.id -> s)
    val enzById = EnzymeBuilder.buildEnzymes(
      eachEnzymeRecordSelector(enzIds.distinct.toArray),
      eachEnzymeCleavageRecordSelector
    ).map(s => s.id -> s).toMap
    
    // TODO: override the short name with the MsiDb one
    val ptmSpecById = Map() ++ ptmProvider.getPtmDefinitions(ptmSpecIds.distinct).map(s => s.id -> s)
    val instConfById = Map() ++ InstrumentConfigBuilder.buildInstrumentConfigs(
      eachInstConfigRecordSelector(instConfigIdBySSId.values.toArray.distinct),
      eachInstrumentRecordSelector,
      eachFragmentationSeriesSelector
    ).map(i => i.id -> i)
    
    for (ss <- searchSettingsList) {
      val ssId = ss.id
      //ss.seqDatabases = seqDbIdsBySSId(ssId).map(seqDbById(_)).toArray      
      ss.seqDatabases = this.buildSearchedSeqDatabases( eachSearchedSeqDbRecordSelector(ssId) )
      
      // TODO: enzyme should be always defined
      ss.usedEnzymes = enzymeIdsBySSId.getOrElse(ssId, ArrayBuffer.empty[Long]).map(enzById(_)).toArray

      val ptms = ptmSpecIdsBySSId.getOrElse(ssId, ArrayBuffer.empty[Long]).map(ptmSpecById(_)).toArray
      val (fixedPtms, varPtms) = ptms.partition(p => fixedPtmById(p.id))
      ss.fixedPtmDefs = fixedPtms
      ss.variablePtmDefs = varPtms

      ss.instrumentConfig = instConfById(instConfigIdBySSId(ss.id))
    }

    searchSettingsList.toArray
  }
  
  def buildSearchSettings(
    record: IValueContainer,
    pmfSearchSettings: Option[PMFSearchSettings],
    msmsSearchSettings: Option[MSMSSearchSettings]    
  ): SearchSettings = {
    
    val r = record
    
    new SearchSettings(
      id = r.getLong(ssCols.ID),
      softwareName = r.getString(ssCols.SOFTWARE_NAME),
      softwareVersion = r.getString(ssCols.SOFTWARE_VERSION),
      taxonomy = r.getString(ssCols.TAXONOMY),
      maxMissedCleavages = r.getInt(ssCols.MAX_MISSED_CLEAVAGES),
      ms1ChargeStates = r.getString(ssCols.PEPTIDE_CHARGE_STATES),
      ms1ErrorTol = r.getDouble(ssCols.PEPTIDE_MASS_ERROR_TOLERANCE),
      ms1ErrorTolUnit = r.getString(ssCols.PEPTIDE_MASS_ERROR_TOLERANCE_UNIT),
      isDecoy = toBoolean(r.getAny(ssCols.IS_DECOY)),
      usedEnzymes = null,
      variablePtmDefs = null,
      fixedPtmDefs = null,
      seqDatabases = null,
      instrumentConfig = null,
      msmsSearchSettings = msmsSearchSettings,
      pmfSearchSettings = pmfSearchSettings,
      properties = r.getStringOption(ssCols.SERIALIZED_PROPERTIES).map( ProfiJson.deserialize[SearchSettingsProperties](_) )
    )
  }
  
  def buildSeqDatabases( eachRecord: (IValueContainer => SeqDatabase) => Seq[SeqDatabase] ): Array[SeqDatabase] = {
    eachRecord( buildSearchedSeqDatabase ).toArray
  }
  
  def buildSeqDatabase( record: IValueContainer ): SeqDatabase = {
    
    val r = record
    
    new SeqDatabase(
      id = r.getLong(seqDbCols.ID),
      name = r.getString(seqDbCols.NAME),
      filePath = r.getString(seqDbCols.FASTA_FILE_PATH),
      sequencesCount = r.getIntOrElse(seqDbCols.SEQUENCE_COUNT, 0),
      releaseDate = r.getDate(seqDbCols.RELEASE_DATE),
      version = r.getStringOrElse(seqDbCols.FASTA_FILE_PATH, ""),
      properties = r.getStringOption(seqDbCols.SERIALIZED_PROPERTIES).map(ProfiJson.deserialize[SeqDatabaseProperties](_))
    )
  }
  
  def buildSearchedSeqDatabases( eachRecord: (IValueContainer => SeqDatabase) => Seq[SeqDatabase] ): Array[SeqDatabase] = {    
    eachRecord( buildSearchedSeqDatabase ).toArray
  }
  
  def buildSearchedSeqDatabase( record: IValueContainer ): SeqDatabase = {
    
    val r = record

    new SeqDatabase(
      id = r.getLong(seqDbCols.ID),
      name = r.getString(seqDbCols.NAME),
      filePath = r.getString(seqDbCols.FASTA_FILE_PATH),
      sequencesCount = r.getIntOrElse(seqDbCols.SEQUENCE_COUNT, 0),
      releaseDate = r.getDate(seqDbCols.RELEASE_DATE),
      version = r.getStringOrElse(seqDbCols.FASTA_FILE_PATH, ""),
      searchedSequencesCount = r.getIntOrElse(ssSeqDbMapCols.SEARCHED_SEQUENCES_COUNT,0),
      properties = r.getStringOption(seqDbTblName+"_"+seqDbCols.SERIALIZED_PROPERTIES).map(ProfiJson.deserialize[SeqDatabaseProperties](_)),
      searchProperties = r.getStringOption(ssSeqDbMapTblName+"_"+ssSeqDbMapCols.SERIALIZED_PROPERTIES).map(ProfiJson.deserialize[SeqDatabaseSearchProperties](_))
    )
  }
  
}