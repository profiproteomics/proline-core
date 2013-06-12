package fr.proline.core.om.provider.msi.impl

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import com.codahale.jerkson.Json.parse
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.{SelectQueryBuilder1,SelectQueryBuilder2,SelectQueryBuilder4}
import fr.proline.core.dal.tables.msi._
import fr.proline.core.dal.tables.uds.UdsDbInstrumentColumns
import fr.proline.core.dal.tables.uds.UdsDbInstrumentConfigTable
import fr.proline.core.dal.tables.uds.UdsDbInstrumentTable
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.om.model.msi._
import fr.proline.core.om.provider.msi.IMSISearchProvider
import fr.proline.util.primitives._
import fr.proline.util.sql.StringOrBoolAsBool._

class SQLMsiSearchProvider(val udsSqlCtx: DatabaseConnectionContext, val msiSqlCtx: DatabaseConnectionContext, val psSqlCtx: DatabaseConnectionContext) extends IMSISearchProvider {

  protected lazy val ptmProvider = new SQLPTMProvider(psSqlCtx)

  protected val msiSearchCols = MsiDbMsiSearchColumns
  protected val peaklistCols = MsiDbPeaklistColumns
  protected val pklSoftCols = MsiDbPeaklistSoftwareColumns
  protected val ssCols = MsiDbSearchSettingsColumns
  //protected val instConfigCols = MsiDbInstrumentConfigColumns
  protected val ionSearchCols = MsiDbIonSearchColumns
  protected val msmsSearchCols = MsiDbMsmsSearchColumns
  protected val seqDbCols = MsiDbSeqDatabaseColumns
  protected val ssSeqDbMapCols = MsiDbSearchSettingsSeqDatabaseMapColumns
  protected val usedEnzCols = MsiDbUsedEnzymeColumns
  protected val enzCols = MsiDbEnzymeColumns

  protected val instCols = UdsDbInstrumentColumns
  protected val instConfigCols = UdsDbInstrumentConfigTable.columns

  def getMSISearches(msiSearchIds: Seq[Long]): Array[MSISearch] = {
    
    DoJDBCReturningWork.withEzDBC(msiSqlCtx, { msiEzDBC =>

      val searchSettingsIdByMsiSearchId = new HashMap[Long, Long]
      val peaklistIdByMsiSearchId = new HashMap[Long, Long]
      
      val msiSearchQuery = new SelectQueryBuilder1(MsiDbMsiSearchTable).mkSelectQuery( (t,c) =>
        List(t.*) -> "WHERE "~ t.ID ~" IN("~ msiSearchIds.mkString(",") ~")"
      )
  
      val msiSearches = msiEzDBC.select(msiSearchQuery) { r =>
  
      val msiSearchId: Long = toLong(r.getAny(msiSearchCols.ID))
  
        searchSettingsIdByMsiSearchId += (msiSearchId -> toLong(r.getAny(msiSearchCols.SEARCH_SETTINGS_ID)))
        peaklistIdByMsiSearchId += (msiSearchId -> toLong(r.getAny(msiSearchCols.PEAKLIST_ID)))
        
        val propsOpt = r.getStringOption(msiSearchCols.SERIALIZED_PROPERTIES).map( propStr =>
          parse[MSISearchProperties](propStr)
        )
  
        new MSISearch(
          id = msiSearchId,
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
  
      // Retrieve the peaklists
      val peaklists = getPeaklists(peaklistIdByMsiSearchId.values.toArray.distinct)
      val pklById = Map() ++ peaklists.map(pkl => pkl.id -> pkl)
  
      // Retrieve the search settings
      val searchSettingsList = getSearchSettingsList(searchSettingsIdByMsiSearchId.values.toArray.distinct)
      val ssById = Map() ++ searchSettingsList.map(ss => ss.id -> ss)
  
      for (msiSearch <- msiSearches) {
        msiSearch.peakList = pklById(peaklistIdByMsiSearchId(msiSearch.id))
        msiSearch.searchSettings = ssById(searchSettingsIdByMsiSearchId(msiSearch.id))
      }
  
      msiSearches.toArray
      
    })
  }

  def getMSISearchesAsOptions(msiSearchIds: Seq[Long]): Array[Option[MSISearch]] = {
    val msiSearches = this.getMSISearches(msiSearchIds)
    val msiSearchById = msiSearches.map { s => s.id -> s } toMap

    msiSearchIds.map { msiSearchById.get(_) } toArray
  }

  def getPeaklists(peaklistIds: Seq[Long]): Array[Peaklist] = {
    
    DoJDBCReturningWork.withEzDBC(msiSqlCtx, { msiEzDBC =>

      val pklSoftIdByPklId = new HashMap[Long, Long]
      
      val pklQuery = new SelectQueryBuilder1(MsiDbPeaklistTable).mkSelectQuery( (t,c) =>
        List(t.*) -> "WHERE "~ t.ID ~" IN("~ peaklistIds.mkString(",") ~")"
      )
  
      val peaklists = msiEzDBC.select(pklQuery) { r =>
      
        val pklId: Long = toLong(r.getAny(peaklistCols.ID))
        pklSoftIdByPklId += (pklId -> toLong(r.getAny(peaklistCols.PEAKLIST_SOFTWARE_ID)))
        
        val propsOpt = r.getStringOption(peaklistCols.SERIALIZED_PROPERTIES).map( propStr =>
          parse[PeaklistProperties](propStr)
        )
        
        new Peaklist(
          id = pklId,
          fileType = r.getString(peaklistCols.TYPE),
          path = r.getString(peaklistCols.PATH),
          rawFileName = r.getString(peaklistCols.RAW_FILE_NAME),
          msLevel = r.getInt(peaklistCols.MS_LEVEL),
          properties = propsOpt
        )
      }
  
      val pklSofts = getPeaklistSoftwareList(pklSoftIdByPklId.values.toArray.distinct)
      val pklSoftById = Map() ++ pklSofts.map(ps => ps.id -> ps)
  
      for (pkl <- peaklists) {
        pkl.peaklistSoftware = pklSoftById(pklSoftIdByPklId(pkl.id))
      }
  
      peaklists.toArray
      
    })

  }

  def getPeaklistSoftwareList(pklSoftIds: Seq[Long]): Array[PeaklistSoftware] = {
    
    DoJDBCReturningWork.withEzDBC(msiSqlCtx, { msiEzDBC =>
    
      val pklSoftQuery = new SelectQueryBuilder1(MsiDbPeaklistSoftwareTable).mkSelectQuery( (t,c) =>
        List(t.*) -> "WHERE "~ t.ID ~" IN("~ pklSoftIds.mkString(",") ~")"
      )
  
      msiEzDBC.select(pklSoftQuery) { r =>
        new PeaklistSoftware(
          id = toLong(r.getAny(pklSoftCols.ID)),
          name = r.getString(pklSoftCols.NAME),
          version = r.getString(pklSoftCols.VERSION),
          properties = r.getStringOption(pklSoftCols.SERIALIZED_PROPERTIES).map( parse[PeaklistSoftwareProperties](_) )
        )
      } toArray
    
    })

  }

  def getSearchSettingsList(searchSettingsIds: Seq[Long]): Array[SearchSettings] = {
    
    DoJDBCReturningWork.withEzDBC(msiSqlCtx, { msiEzDBC =>

      val ssIdsStr = searchSettingsIds.mkString(",")
      
      // Retrieve PMF search settings
      val pmfSearchQuery = new SelectQueryBuilder1( MsiDbIonSearchTable ).mkSelectQuery( (t,c) =>
        List(t.*) -> " WHERE " ~ t.ID ~ " IN (" ~ ssIdsStr ~ ")"
      )
      val pmfSettingsById = new HashMap[Long,PMFSearchSettings]
      msiEzDBC.selectAndProcess(pmfSearchQuery) { r =>
        pmfSettingsById += toLong(r.nextAny) -> new PMFSearchSettings(
          maxProteinMass = r.getDoubleOption(ionSearchCols.MAX_PROTEIN_MASS),
          minProteinMass = r.getDoubleOption(ionSearchCols.MIN_PROTEIN_MASS),
          proteinPI = r.getDoubleOption(ionSearchCols.PROTEIN_PI).map( _.toFloat )
        )
      }
      
      // Retrieve MS/MS search settings
      val msmsSearchQuery = new SelectQueryBuilder1( MsiDbMsmsSearchTable ).mkSelectQuery( (t,c) =>
        List(t.*) -> " WHERE " ~ t.ID ~ " IN (" ~ ssIdsStr ~ ")"
      )
      val msmsSettingsById = new HashMap[Long,MSMSSearchSettings]
      msiEzDBC.selectAndProcess(msmsSearchQuery) { r =>
        msmsSettingsById += toLong(r.nextAny) -> new MSMSSearchSettings(
          ms2ChargeStates = r.getString(msmsSearchCols.FRAGMENT_CHARGE_STATES),
          ms2ErrorTol = r.getDouble(msmsSearchCols.FRAGMENT_MASS_ERROR_TOLERANCE),
          ms2ErrorTolUnit = r.getString(msmsSearchCols.FRAGMENT_MASS_ERROR_TOLERANCE_UNIT)
        )
      }
      
      val ssQuery = new SelectQueryBuilder1(MsiDbSearchSettingsTable).mkSelectQuery( (t,c) =>
        List(t.*) -> " WHERE " ~ t.ID ~ " IN (" ~ ssIdsStr ~ ")"
      )
  
      val instConfigIdBySSId = new HashMap[Long, Long]
      val searchSettingsList = msiEzDBC.select(ssQuery) { r =>
  
        val ssId: Long = toLong(r.getAny(ssCols.ID))
        instConfigIdBySSId += ssId -> toLong(r.getAny(ssCols.INSTRUMENT_CONFIG_ID))
        
        new SearchSettings(
          // Required fields
          id = ssId,
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
          msmsSearchSettings = msmsSettingsById.get(ssId),
          pmfSearchSettings = pmfSettingsById.get(ssId),
          properties = r.getStringOption(ssCols.SERIALIZED_PROPERTIES).map( parse[SearchSettingsProperties](_) )
        )
      }
  
      /*val seqDbIdsBySSId = new HashMap[Int, ArrayBuffer[Int]]
      val seqDbIds = new ArrayBuffer[Int]
  
      msiDbCtx.ezDBC.selectAndProcess(
        "SELECT * FROM search_settings_seq_database_map" +
        " WHERE " + ssSeqDbMapCols.SEARCH_SETTINGS_ID + " IN (" + ssIdsStr + ")") { r =>
        val (ssId, seqDbId) = (r.nextInt, r.nextInt)
        seqDbIdsBySSId.getOrElseUpdate(ssId, new ArrayBuffer[Int]) += seqDbId
        seqDbIds += seqDbId
      }*/
  
      val enzymeIdsBySSId = new HashMap[Long, ArrayBuffer[Long]]
      val enzIds = new ArrayBuffer[Long]
      
      val usedEnzQuery = new SelectQueryBuilder1(MsiDbUsedEnzymeTable).mkSelectQuery( (t,c) =>
        List(t.*) -> "WHERE "~ t.SEARCH_SETTINGS_ID ~" IN("~ ssIdsStr ~")"
      )
  
      msiEzDBC.selectAndProcess(usedEnzQuery) { r =>
        val (ssId, enzId) = (toLong(r.nextAny), toLong(r.nextAny))
        enzymeIdsBySSId.getOrElseUpdate(ssId, new ArrayBuffer[Long]) += enzId
        enzIds += enzId
      }
  
      val ptmSpecIdsBySSId = new HashMap[Long, ArrayBuffer[Long]]
      val ptmSpecIds = new ArrayBuffer[Long]
      val fixedPtmById = new HashMap[Long, Boolean]
      
      val usedPtmQuery = new SelectQueryBuilder1(MsiDbUsedPtmTable).mkSelectQuery( (t,c) =>
        List(t.*) -> "WHERE "~ t.SEARCH_SETTINGS_ID ~" IN("~ ssIdsStr ~")"
      )
  
      msiEzDBC.selectAndProcess(usedPtmQuery) { r =>
        val (ssId, ptmSpecId, shortName) = (toLong(r.nextAny), toLong(r.nextAny), r.nextString)
        val isFixed = toBoolean(r.nextAny)
  
        ptmSpecIdsBySSId.getOrElseUpdate(ssId, new ArrayBuffer[Long]) += ptmSpecId
        ptmSpecIds += ptmSpecId
        fixedPtmById += ptmSpecId -> isFixed
      }
  
      //val seqDbById = Map() ++ this.getSeqDatabases(seqDbIds.distinct).map(s => s.id -> s)
      val enzById = Map() ++ this.getEnzymes(enzIds.distinct).map(s => s.id -> s)
      // TODO: override the short name with the MsiDb one
      val ptmSpecById = Map() ++ ptmProvider.getPtmDefinitions(ptmSpecIds.distinct).map(s => s.id -> s)
      val instConfById = Map() ++ this.getInstrumentConfigs(instConfigIdBySSId.values.toArray.distinct).map(i => i.id -> i)
      
      for (ss <- searchSettingsList) {
        val ssId = ss.id
        //ss.seqDatabases = seqDbIdsBySSId(ssId).map(seqDbById(_)).toArray      
        ss.seqDatabases = this.getSearchedSeqDatabases(ssId)
        
        // TODO: enzyme should be always defined
        ss.usedEnzymes = enzymeIdsBySSId.getOrElse(ssId, ArrayBuffer.empty[Long]).map(enzById(_)).toArray
  
        val ptms = ptmSpecIdsBySSId.getOrElse(ssId, ArrayBuffer.empty[Long]).map(ptmSpecById(_)).toArray
        val (fixedPtms, varPtms) = ptms.partition(p => fixedPtmById(p.id))
        ss.fixedPtmDefs = fixedPtms
        ss.variablePtmDefs = varPtms
  
        ss.instrumentConfig = instConfById(instConfigIdBySSId(ss.id))
      }
  
      searchSettingsList.toArray
      
    })
  }

  def getSeqDatabases(seqDbIds: Seq[Long]): Array[SeqDatabase] = {
    
    DoJDBCReturningWork.withEzDBC(msiSqlCtx, { msiEzDBC =>
    
      val seqDbQuery = new SelectQueryBuilder1(MsiDbSeqDatabaseTable).mkSelectQuery( (t,c) =>
        List(t.*) -> "WHERE "~ t.ID ~" IN("~ seqDbIds.mkString(",") ~")"
      )
      
      msiEzDBC.select(seqDbQuery) { r =>
        new SeqDatabase(
          id = toLong(r.getAny(seqDbCols.ID)),
          name = r.getString(seqDbCols.NAME),
          filePath = r.getString(seqDbCols.FASTA_FILE_PATH),
          sequencesCount = r.getIntOrElse(seqDbCols.SEQUENCE_COUNT, 0),
          releaseDate = r.getDate(seqDbCols.RELEASE_DATE),
          version = r.getStringOrElse(seqDbCols.FASTA_FILE_PATH, ""),
          properties = r.getStringOption(seqDbCols.SERIALIZED_PROPERTIES).map(parse[SeqDatabaseProperties](_))
        )
      } toArray
    
    })
  }
  
  def getSearchedSeqDatabases(searchSettingsId: Long): Array[SeqDatabase] = {
    
    DoJDBCReturningWork.withEzDBC(msiSqlCtx, { msiEzDBC =>
    
      val seqDbTblName = MsiDbSeqDatabaseTable.name
      val ssSeqDbMapTblName = MsiDbSearchSettingsSeqDatabaseMapTable.name
      val sqb2 = new SelectQueryBuilder2(MsiDbSeqDatabaseTable, MsiDbSearchSettingsSeqDatabaseMapTable)
      
      val sqlQuery = sqb2.mkSelectQuery( (t1, c1, t2, c2) =>
        List(t1.*,t2.SEARCHED_SEQUENCES_COUNT,t2.SERIALIZED_PROPERTIES) ->
        " WHERE " ~ t1.ID ~ " = " ~ t2.SEQ_DATABASE_ID ~
        " AND " ~ t2.SEARCH_SETTINGS_ID ~ " = " ~ searchSettingsId
      )
      
      msiEzDBC.select(sqlQuery) { r =>
        new SeqDatabase(
          id = toLong(r.getAny(seqDbCols.ID)),
          name = r.getString(seqDbCols.NAME),
          filePath = r.getString(seqDbCols.FASTA_FILE_PATH),
          sequencesCount = r.getIntOrElse(seqDbCols.SEQUENCE_COUNT, 0),
          releaseDate = r.getDate(seqDbCols.RELEASE_DATE),
          version = r.getStringOrElse(seqDbCols.FASTA_FILE_PATH, ""),
          searchedSequencesCount = r.getInt(ssSeqDbMapCols.SEARCHED_SEQUENCES_COUNT),
          properties = r.getStringOption(seqDbTblName+"_"+seqDbCols.SERIALIZED_PROPERTIES).map(parse[SeqDatabaseProperties](_)),
          searchProperties = r.getStringOption(ssSeqDbMapTblName+"_"+ssSeqDbMapCols.SERIALIZED_PROPERTIES).map(parse[SeqDatabaseSearchProperties](_))
        )
      } toArray
      
    })
  }

  def getEnzymes(enzymeIds: Seq[Long]): Array[Enzyme] = {
    
    DoJDBCReturningWork.withEzDBC(msiSqlCtx, { msiEzDBC =>
    
      val enzQuery = new SelectQueryBuilder1(MsiDbEnzymeTable).mkSelectQuery( (t,c) =>
        List(t.*) -> "WHERE "~ t.ID ~" IN("~ enzymeIds.mkString(",") ~")"
      )
      
      msiEzDBC.select(enzQuery) { r =>
        new Enzyme(
          id = toLong(r.getAny(enzCols.ID)),
          name = r.getString(enzCols.NAME),
          cleavageRegexp = r.getStringOption(enzCols.CLEAVAGE_REGEXP),
          isIndependant = r.getBooleanOrElse(enzCols.IS_INDEPENDANT, false),
          isSemiSpecific = r.getBooleanOrElse(enzCols.IS_SEMI_SPECIFIC, false),
          properties = r.getStringOption(enzCols.SERIALIZED_PROPERTIES).map( parse[EnzymeProperties](_))
        )
      } toArray
    
    })
  }

  // TODO: put in a dedicated provider
  def getInstrumentConfigs(instConfigIds: Seq[Long]): Array[InstrumentConfig] = {
    
    DoJDBCReturningWork.withEzDBC(udsSqlCtx, { udsEzDBC =>
    
      val instConfigQuery = new SelectQueryBuilder1(UdsDbInstrumentConfigTable).mkSelectQuery( (t,c) =>
        List(t.*) -> "WHERE "~ t.ID ~" IN("~ instConfigIds.mkString(",") ~")"
      )
  
      val instIdByInstConfigId = new HashMap[Long, Long]
      val instConfigs = udsEzDBC.select(instConfigQuery) { r =>
        
        val instConfigId: Long = toLong(r.getAny(instConfigCols.ID))
        val instId = toLong(r.getAny(instConfigCols.INSTRUMENT_ID))
        instIdByInstConfigId += instConfigId -> instId
  
        new InstrumentConfig(
          id = instConfigId,
          name = r.getString(instConfigCols.NAME),
          instrument = null,
          ms1Analyzer = r.getString(instConfigCols.MS1_ANALYZER),
          msnAnalyzer = r.getString(instConfigCols.MSN_ANALYZER),
          activationType = r.getString(instConfigCols.ACTIVATION_TYPE),
          properties = r.getStringOption(instConfigCols.SERIALIZED_PROPERTIES).map(parse[InstrumentConfigProperties](_))
        )
      }
  
      val instIds = instIdByInstConfigId.values.toArray.distinct
      val instruments = this.getInstruments(instIds)
      val instById = Map() ++ instruments.map(i => i.id -> i)
  
      for (instConfig <- instConfigs)
        instConfig.instrument = instById(instIdByInstConfigId(instConfig.id))
  
      instConfigs.toArray
      
    })
  }

  // TODO: put in a dedicated provider
  def getInstruments(instIds: Seq[Long]): Array[Instrument] = {
    
    DoJDBCReturningWork.withEzDBC(udsSqlCtx, { udsEzDBC =>
    
      val instQuery = new SelectQueryBuilder1(UdsDbInstrumentTable).mkSelectQuery( (t,c) =>
        List(t.*) -> "WHERE "~ t.ID ~" IN("~ instIds.mkString(",") ~")"
      )
      
      udsEzDBC.select(instQuery) { r =>
        new Instrument(
          id = toLong(r.getAny(instCols.ID)),
          name = r.getString(instCols.NAME),
          source = r.getStringOrElse(instCols.SOURCE, null),
          properties = r.getStringOption(instCols.SERIALIZED_PROPERTIES).map(parse[InstrumentProperties](_))
        )
      } toArray
    
    })

  }

}