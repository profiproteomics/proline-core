package fr.proline.core.om.provider.msi.impl

import scala.collection.mutable.ArrayBuffer
import fr.profi.jdbc.SQLQueryExecution
import fr.proline.core.dal.SQLContext
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.SelectQueryBuilder4
import fr.proline.core.dal.tables.msi._
import fr.proline.core.om.model.msi.Enzyme
import fr.proline.core.om.model.msi.InstrumentConfig
import fr.proline.core.om.model.msi.MSISearch
import fr.proline.core.om.model.msi.MSMSSearchSettings
import fr.proline.core.om.model.msi.PMFSearchSettings
import fr.proline.core.om.model.msi.Peaklist
import fr.proline.core.om.model.msi.PeaklistSoftware
import fr.proline.core.om.model.msi.SearchSettings
import fr.proline.core.om.model.msi.SeqDatabase
import fr.proline.core.om.provider.msi.IMSISearchProvider
import fr.proline.context.DatabaseConnectionContext
import fr.proline.util.primitives.DoubleOrFloatAsFloat._
import fr.proline.util.primitives.LongOrIntAsInt._
import fr.proline.util.sql.StringOrBoolAsBool._
import fr.proline.core.om.model.msi.Instrument
import fr.proline.core.dal.tables.uds.UdsDbInstrumentColumns
import fr.proline.core.dal.tables.uds.UdsDbInstrumentConfigColumns
import scala.collection.mutable.HashMap

class SQLMsiSearchProvider( val udsSqlCtx: SQLContext, val msiSqlCtx: SQLContext, val psSqlCtx: SQLContext ) extends IMSISearchProvider {
  
  val udsDbCtx = udsSqlCtx.dbContext
  protected val udsSqlExec = udsSqlCtx.ezDBC
  
  val msiDbCtx = msiSqlCtx.dbContext
  protected val msiSqlExec = msiSqlCtx.ezDBC
  
  val psDbCtx = psSqlCtx.dbContext
  protected val psSqlExec = psSqlCtx.ezDBC
  
  protected lazy val ptmProvider = new SQLPTMProvider( psDbCtx, psSqlExec )
  
  protected val msiSearchCols = MsiDbMsiSearchColumns  
  protected val peaklistCols = MsiDbPeaklistColumns
  protected val pklSoftCols = MsiDbPeaklistSoftwareColumns
  protected val ssCols = MsiDbSearchSettingsColumns
  //protected val instConfigCols = MsiDbInstrumentConfigColumns
  protected val ionSearchCols = MsiDbIonSearchColumns
  protected val msmsSearchCols = MsiDbMsmsSearchColumns
  protected val seqDbCols = MsiDbSeqDatabaseColumns
  protected val usedEnzCols = MsiDbUsedEnzymeColumns
  protected val enzCols = MsiDbEnzymeColumns
  
  protected val instCols = UdsDbInstrumentColumns
  protected val instConfigCols = UdsDbInstrumentConfigColumns
  
  def getMSISearches( msiSearchIds: Seq[Int] ): Array[MSISearch] = {
    
    val searchSettingsIdByMsiSearchId = new HashMap[Int,Int]
    val peaklistIdByMsiSearchId = new HashMap[Int,Int]
    
    val msiSearches = msiSqlExec.select( "SELECT * FROM msi_search WHERE id IN ("+ msiSearchIds.mkString(",") +")" ) { r =>
      
      val msiSearchId: Int = r.getAnyVal( msiSearchCols.ID )
      
      searchSettingsIdByMsiSearchId += (msiSearchId -> r.getInt( msiSearchCols.SEARCH_SETTINGS_ID ) )
      peaklistIdByMsiSearchId += (msiSearchId -> r.getInt( msiSearchCols.PEAKLIST_ID ) )
      
      new MSISearch(
        id = msiSearchId,
        resultFileName = r.getString( msiSearchCols.RESULT_FILE_NAME ),
        submittedQueriesCount = r.getInt( msiSearchCols.SUBMITTED_QUERIES_COUNT ),
        searchSettings = null,
        peakList = null,
        date = r.getDate( msiSearchCols.DATE ),
        title = r.getStringOrElse( msiSearchCols.TITLE, "" ),
        resultFileDirectory = r.getStringOrElse( msiSearchCols.RESULT_FILE_DIRECTORY, "" ),
        jobNumber = r.getIntOrElse( msiSearchCols.JOB_NUMBER, 0 ),
        userName = r.getStringOrElse( msiSearchCols.USER_NAME, "" ),
        userEmail = r.getStringOrElse( msiSearchCols.USER_EMAIL, "" ),
        queriesCount = r.getIntOrElse( msiSearchCols.QUERIES_COUNT, 0 ),
        searchedSequencesCount = r.getIntOrElse( msiSearchCols.SEARCHED_SEQUENCES_COUNT, 0 )
      )

    }
    
    // Retrieve the peaklists
    val peaklists = getPeaklists( peaklistIdByMsiSearchId.values.toArray.distinct )
    val pklById = Map() ++ peaklists.map( pkl => pkl.id -> pkl )
    
    // Retrieve the search settings
    val searchSettingsList = getSearchSettingsList( searchSettingsIdByMsiSearchId.values.toArray.distinct )
    val ssById = Map() ++ searchSettingsList.map( ss => ss.id -> ss )
    
    for( msiSearch <- msiSearches ) {
      msiSearch.peakList = pklById( peaklistIdByMsiSearchId( msiSearch.id ) )
      msiSearch.searchSettings = ssById( searchSettingsIdByMsiSearchId( msiSearch.id) )
    }
    
    msiSearches.toArray
  }
  
  def getMSISearchesAsOptions( msiSearchIds: Seq[Int] ): Array[Option[MSISearch]] = {
    val msiSearches = this.getMSISearches( msiSearchIds )
    val msiSearchById = msiSearches.map { s => s.id -> s } toMap
    
    msiSearchIds.map { msiSearchById.get( _ ) } toArray
  }

  def getPeaklists(peaklistIds: Seq[Int]): Array[Peaklist] = {
    
    val pklSoftIdByPklId = new HashMap[Int,Int]
    
    val peaklists = msiSqlExec.select( "SELECT * FROM peaklist WHERE id IN ("+ peaklistIds.mkString(",") +")" ) { r =>
      
      val pklId: Int = r.getAnyVal(peaklistCols.ID)
      pklSoftIdByPklId += ( pklId -> r.getInt(peaklistCols.PEAKLIST_SOFTWARE_ID) )
      
      // TODO: load properties
      new Peaklist(
        id = pklId,
        fileType = r.getString(peaklistCols.TYPE),
        path = r.getString(peaklistCols.PATH),
        rawFileName = r.getString(peaklistCols.RAW_FILE_NAME),
        msLevel = r.getInt(peaklistCols.MS_LEVEL)
      )
      
    }
    
    val pklSofts = getPeaklistSoftwareList( pklSoftIdByPklId.values.toArray.distinct )
    val pklSoftById = Map() ++ pklSofts.map( ps => ps.id -> ps )
    
    for( pkl <- peaklists ) {
      pkl.peaklistSoftware = pklSoftById(pklSoftIdByPklId( pkl.id ))
    }
    
    peaklists.toArray

  }
  
  def getPeaklistSoftwareList(pklSoftIds: Seq[Int]): Array[PeaklistSoftware] = {
    
    msiSqlExec.select( "SELECT * FROM peaklist_software WHERE id IN ("+ pklSoftIds.mkString(",") +")" ) { r =>
      new PeaklistSoftware(
        id = r.getAnyVal(pklSoftCols.ID),
        name = r.getString(pklSoftCols.NAME),
        version = r.getString(pklSoftCols.VERSION)
      )
    } toArray

  }

  
  def getSearchSettingsList(searchSettingsIds: Seq[Int]): Array[SearchSettings] = {
    
    // TODO: remove the instrument config JOIN
    val sqb4 = new SelectQueryBuilder4( MsiDbSearchSettingsTable, MsiDbIonSearchTable, MsiDbMsmsSearchTable, MsiDbInstrumentConfigTable )
    
    // TODO: find why JOIN doesn't work with H2
    val ssIdsStr = searchSettingsIds.mkString(",")
    val whereClause = sqb4.mkClause( (t1,t2,t3,t4) =>
      //" LEFT OUTER JOIN " ~ t2.$tableName ~ " ON " ~ t1.ID ~ " = "  ~ t2.ID ~
      //" LEFT OUTER JOIN " ~ t3.$tableName ~ " ON " ~ t1.ID ~ " = "  ~ t3.ID ~
      " WHERE " ~  t1.INSTRUMENT_CONFIG_ID ~ " = "  ~ t4.ID ~
      " AND " ~  t1.ID ~ " IN ("  ~ ssIdsStr ~ ")" )
    
    val sqlQuery = sqb4.mkSelectQuery( (t1,c1,t2,c2,t3,c3,t4,c4) => 
      //List(t1.*) ++ c2.filter( _ != t2.ID ) ++ c3.filter( _ != t3.ID ) ++ c4.filter( _ != t4.ID ),
      List(t1.*) ++ c4.filter( _ != t4.ID ),
      Some(whereClause),
      Some(Set(MsiDbIonSearchTable.name,MsiDbMsmsSearchTable.name)) // exclude some table because of the OUTER JOIN
    )
    
    val instConfigIdBySSId = new HashMap[Int,Int]
    val searchSettingsList = msiSqlExec.select( sqlQuery ) { r =>
      
      val ssId: Int = r.getAnyVal(ssCols.ID)      
      
      //val ms2ErrorTolOpt = r.getDoubleOption(msmsSearchCols.FRAGMENT_MASS_ERROR_TOLERANCE)
      
      var msmsSearchSettings: MSMSSearchSettings = null
      var pmfSearchSettings: PMFSearchSettings = null
      /*if( ms2ErrorTolOpt != None ) {
        msmsSearchSettings = new MSMSSearchSettings(
          ms2ChargeStates = r.getString(msmsSearchCols.FRAGMENT_CHARGE_STATES),
          ms2ErrorTol = r.getDouble(msmsSearchCols.FRAGMENT_MASS_ERROR_TOLERANCE),
          ms2ErrorTolUnit = r.getString(msmsSearchCols.FRAGMENT_MASS_ERROR_TOLERANCE_UNIT)
        )
      } else {
        pmfSearchSettings = new PMFSearchSettings(
          maxProteinMass = r.getDoubleOption(ionSearchCols.MAX_PROTEIN_MASS),
          minProteinMass = r.getDoubleOption(ionSearchCols.MIN_PROTEIN_MASS),
          proteinPI = r.getDoubleOption(ionSearchCols.PROTEIN_PI).map( _.toFloat )
        )
      }*/
      
      instConfigIdBySSId += ssId -> r.getInt( ssCols.INSTRUMENT_CONFIG_ID )
      
      // TODO: load properties
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
        isDecoy = r.getAnyVal(ssCols.IS_DECOY),
        usedEnzymes = null,
        variablePtmDefs = null,
        fixedPtmDefs = null,
        seqDatabases = null,
        instrumentConfig = null,        
        msmsSearchSettings = Option(msmsSearchSettings),
        pmfSearchSettings = Option(pmfSearchSettings)
      )
    }
    
    val seqDbIdsBySSId = new HashMap[Int,ArrayBuffer[Int]]
    val seqDbIds = new ArrayBuffer[Int]
    
    msiSqlExec.selectAndProcess(
      "SELECT * FROM search_settings_seq_database_map" +
      " WHERE " + MsiDbSearchSettingsSeqDatabaseMapColumns.SEARCH_SETTINGS_ID + " IN (" + ssIdsStr + ")"
    ) { r =>
      val( ssId, seqDbId) = (r.nextInt, r.nextInt )
      seqDbIdsBySSId.getOrElseUpdate(ssId, new ArrayBuffer[Int] ) += seqDbId
      seqDbIds += seqDbId
    }
    
    val enzymeIdsBySSId = new HashMap[Int,ArrayBuffer[Int]]
    val enzIds = new ArrayBuffer[Int]
    
    msiSqlExec.selectAndProcess(
      "SELECT * FROM used_enzyme" +
      " WHERE " + usedEnzCols.SEARCH_SETTINGS_ID + " IN (" + ssIdsStr + ")"
    ) { r =>
      val( ssId, enzId) = (r.nextInt, r.nextInt )
      enzymeIdsBySSId.getOrElseUpdate(ssId, new ArrayBuffer[Int] ) += enzId
      enzIds += enzId
    }
    
    val ptmSpecIdsBySSId = new HashMap[Int,ArrayBuffer[Int]]
    val ptmSpecIds = new ArrayBuffer[Int]
    val fixedPtmById = new HashMap[Int,Boolean]
    
    msiSqlExec.selectAndProcess(
      "SELECT * FROM used_ptm" +
      " WHERE " + MsiDbUsedPtmColumns.SEARCH_SETTINGS_ID + " IN (" + ssIdsStr + ")"
    ) { r =>
      val( ssId, ptmSpecId,shortName) = (r.nextInt, r.nextInt,r.nextString )
      val isFixed: Boolean = r.nextAnyVal
      
      ptmSpecIdsBySSId.getOrElseUpdate(ssId, new ArrayBuffer[Int] ) += ptmSpecId
      ptmSpecIds += ptmSpecId
      fixedPtmById += ptmSpecId -> isFixed
    }
    
    val seqDbById = Map() ++ this.getSeqDatabases( seqDbIds.distinct ).map( s => s.id -> s )
    val enzById = Map() ++ this.getEnzymes( enzIds.distinct ).map( s => s.id -> s )
    // TODO: override the short name with the MsiDb one
    val ptmSpecById = Map() ++ ptmProvider.getPtmDefinitions( ptmSpecIds.distinct ).map( s => s.id -> s )
    val instConfById = Map() ++ this.getInstrumentConfigs(instConfigIdBySSId.values.toArray.distinct).map( i => i.id -> i )
    
    for( ss <- searchSettingsList ) {
      val ssId = ss.id
      ss.seqDatabases = seqDbIdsBySSId(ssId).map( seqDbById(_) ).toArray
      // TODO: enzyme should be always defined
      ss.usedEnzymes = enzymeIdsBySSId.getOrElse(ssId,ArrayBuffer.empty[Int]).map( enzById(_) ).toArray
      
      val ptms = ptmSpecIdsBySSId.getOrElse(ssId,ArrayBuffer.empty[Int]).map( ptmSpecById(_) ).toArray
      val( fixedPtms, varPtms ) = ptms.partition(p => fixedPtmById(p.id) )
      ss.fixedPtmDefs = fixedPtms
      ss.variablePtmDefs = varPtms
      
      ss.instrumentConfig = instConfById( instConfigIdBySSId(ss.id) )
    }

    searchSettingsList.toArray
  }
  
  def getSeqDatabases(seqDbIds: Seq[Int]): Array[SeqDatabase] = {
    msiSqlExec.select( "SELECT * FROM seq_database WHERE id IN ("+ seqDbIds.mkString(",") +")" ) { r =>
      new SeqDatabase(
        id = r.getAnyVal(seqDbCols.ID),
        name = r.getString(seqDbCols.NAME),
        filePath = r.getString(seqDbCols.FASTA_FILE_PATH),
        sequencesCount = r.getIntOrElse(seqDbCols.SEQUENCE_COUNT, 0),
        releaseDate = r.getDate(seqDbCols.RELEASE_DATE),
        version = r.getStringOrElse(seqDbCols.FASTA_FILE_PATH,"")
      )
    } toArray
  }
  
  def getEnzymes(enzymeIds: Seq[Int]): Array[Enzyme] = {
    msiSqlExec.select( "SELECT * FROM enzyme WHERE id IN ("+ enzymeIds.mkString(",") +")" ) { r =>
      new Enzyme(
        id = r.getAnyVal(enzCols.ID),
        name = r.getString(enzCols.NAME),
        cleavageRegexp = r.getStringOption(enzCols.CLEAVAGE_REGEXP),
        isIndependant = r.getBooleanOrElse(enzCols.IS_INDEPENDANT,false),
        isSemiSpecific = r.getBooleanOrElse(enzCols.IS_SEMI_SPECIFIC,false)
      )
    } toArray
  }
  
  
  // TODO: put in a dedicated provided
  def getInstrumentConfigs( instConfigIds: Seq[Int] ): Array[InstrumentConfig] = {
    
    val instIdByInstConfigId = new HashMap[Int,Int]
    
    // TODO: parse properties
    val instConfigs = udsSqlExec.select( "SELECT * FROM instrument_config WHERE id IN ("+ instConfigIds.mkString(",") +")" ) { r =>
      val instConfigId: Int = r.getAnyVal(instConfigCols.ID)
      val instId = r.getInt(instConfigCols.INSTRUMENT_ID)
      instIdByInstConfigId += instConfigId ->instId
      
      new InstrumentConfig(
        id = instConfigId,
        name = r.getString(instConfigCols.NAME),
        instrument = null,
        ms1Analyzer = r.getString(instConfigCols.MS1_ANALYZER),
        msnAnalyzer = r.getString(instConfigCols.MSN_ANALYZER),
        activationType = r.getString(instConfigCols.ACTIVATION_TYPE)
      )
    }
    
    val instIds = instIdByInstConfigId.values.toArray.distinct
    val instruments = this.getInstruments(instIds)
    val instById = Map() ++ instruments.map(i => i.id -> i )
    
    for( instConfig <- instConfigs )
      instConfig.instrument = instById(instIdByInstConfigId(instConfig.id))
    
    instConfigs.toArray
  }
  
  // TODO: put in a dedicated provided
  def getInstruments( instIds: Seq[Int] ): Array[Instrument] = {
    
    // TODO: parse properties
    udsSqlExec.select( "SELECT * FROM instrument WHERE id IN ("+ instIds.mkString(",") +")" ) { r =>
      new Instrument(
        id = r.getAnyVal(instCols.ID),
        name = r.getString(instCols.NAME),
        source = r.getStringOrElse(instCols.SOURCE,null)
      )
    } toArray
    
  }
  
}