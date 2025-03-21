package fr.proline.core.om.storer.msi.impl


import com.typesafe.scalalogging.LazyLogging
import fr.profi.jdbc.easy._
import fr.profi.jdbc.PreparedStatementWrapper
import fr.profi.util.serialization.ProfiJson
import fr.proline.core.dal.tables.msi._
import fr.proline.core.dal._
import fr.proline.core.om.model.msi._
import fr.proline.core.om.storer.msi.IMsiSearchWriter
import fr.profi.util.primitives._
import fr.profi.chemistry.model.Enzyme
import fr.profi.jdbc.Nullable
import fr.profi.jdbc.LongFormattable

object SQLMsiSearchWriter extends AbstractSQLMsiSearchWriter

abstract class AbstractSQLMsiSearchWriter extends IMsiSearchWriter with LazyLogging {

  def insertMsiSearch(msiSearch: MSISearch, context: StorerContext): Long = {

    val ss = msiSearch.searchSettings

    DoJDBCWork.withEzDBC(context.getMSIDbConnectionContext) { msiEzDBC =>

      // Insert sequence databases
      // TODO : If seqDb does not exist in PDI do not create it !!
      val seqDbIdByTmpId = new collection.mutable.LongMap[Long]()
      ss.seqDatabases.foreach { seqDb =>
        val tmpSeqDbId = seqDb.id
        _insertSeqDatabase(seqDb, msiEzDBC)
        seqDbIdByTmpId += (tmpSeqDbId -> seqDb.id)
      }
      context.seqDbIdByTmpId = seqDbIdByTmpId

      // Insert search settings
      _insertSearchSettings(ss, msiEzDBC)

      // Insert MSI search
      _insertMsiSearch(msiSearch, msiEzDBC)

      // Store MS queries
      //this.storeMsQueries( msiSearch, msQueries, context )

    }

    msiSearch.id
  }

  def insertMsQueries(msiSearchId: Long, msQueries: Seq[MsQuery], context: StorerContext): StorerContext = {
    
    val msQueryInsertQuery = MsiDbMsQueryTable.mkInsertQuery( (col,colsList) => colsList.filter(_ != col.ID) )
    
    DoJDBCWork.withEzDBC(context.getMSIDbConnectionContext) { msiEzDBC =>

      msiEzDBC.executePrepared(msQueryInsertQuery, true) { stmt =>

        for (msQuery <- msQueries) {

          //val tmpMsQueryId = msQuery.id

          msQuery.msLevel match {
            case 1 => _insertMsQuery(stmt, msQuery.asInstanceOf[Ms1Query], msiSearchId, Option.empty[Long], context)
            case 2 => {
              val ms2Query = msQuery.asInstanceOf[Ms2Query]
              // FIXME: it should not be null
              var spectrumId = Option.empty[Long]
              val spectrumIdByTitle = context.spectrumIdByTitle
              if ( spectrumIdByTitle != null && spectrumIdByTitle.contains(ms2Query.spectrumTitle) ) {
                ms2Query.spectrumId = context.spectrumIdByTitle(ms2Query.spectrumTitle)
                spectrumId = Some(ms2Query.spectrumId)
              }
              _insertMsQuery(stmt, msQuery, msiSearchId, spectrumId, context)
            }
          }

          //msQueryIdByTmpId += ( tmpMsQueryId -> msQuery.id )

        }
      }
    }

    context
  }

  private def _insertMsQuery(stmt: PreparedStatementWrapper, msQuery: MsQuery, msiSearchId: Long, spectrumId: Option[Long], context: StorerContext): Unit = {

    // Retrieve some vars
    //val spectrumId = ms2Query.spectrumId
    //if( spectrumId <= 0 )
    //throw new Exception("spectrum must first be persisted")

    stmt.executeWith(
      msQuery.initialId,
      msQuery.charge,
      msQuery.moz,
      msQuery.properties.map(ProfiJson.serialize(_)),
      spectrumId,
      msiSearchId
    )

    msQuery.id = stmt.generatedLong
  }
  
  def insertInstrumentConfig(instrumentConfig: InstrumentConfig, context: StorerContext): Unit = {

    require(instrumentConfig.id > 0, "instrument configuration must have a strictly positive identifier")

    DoJDBCWork.withEzDBC(context.getMSIDbConnectionContext) { msiEzDBC =>
      
      // Check if the instrument config exists in the MSIdb
      val count = msiEzDBC.selectInt("SELECT count(*) FROM instrument_config WHERE id=" + instrumentConfig.id)

      // If the instrument config doesn't exist in the MSIdb
      if (count == 0) {
        msiEzDBC.executePrepared("INSERT INTO instrument_config VALUES (?,?,?,?,?)") { stmt =>
          stmt.executeWith(instrumentConfig.id,
            instrumentConfig.name,
            instrumentConfig.ms1Analyzer,
            Option(instrumentConfig.msnAnalyzer),
            Option.empty[String])
        }
      }

    }

  }

  private def _insertSeqDatabase(seqDatabase: SeqDatabase, msiEzDBC: EasyDBC): Unit = {

    val fasta_path = seqDatabase.filePath
    val seqDbIds = msiEzDBC.select("SELECT id FROM seq_database WHERE fasta_file_path='" + fasta_path + "'") { v => toLong(v.nextAny) }

    // If the sequence database doesn't exist in the MSIdb
    if (seqDbIds.length == 0) {

      val seqDbInsertQuery = MsiDbSeqDatabaseTable.mkInsertQuery { (c,colsList) => 
                               colsList.filter( _ != c.ID)
                             }
      
      msiEzDBC.executePrepared( seqDbInsertQuery, true ) { stmt =>
      
        stmt.executeWith(
          seqDatabase.name,
          seqDatabase.filePath,
          seqDatabase.version,
          seqDatabase.releaseDate,
          seqDatabase.sequencesCount,
          Option.empty[String])

        seqDatabase.id = stmt.generatedLong
      }

    } else {
      seqDatabase.id = seqDbIds(0)
    }

  }

  private def _insertSearchSettings(searchSettings: SearchSettings, msiEzDBC: EasyDBC): Unit = {

    // Retrieve some vars
    val instrumentConfigId = searchSettings.instrumentConfig.id
    require(instrumentConfigId > 0, "instrument configuration must first be persisted")

    val searchSettingsInsertQuery = MsiDbSearchSettingsTable.mkInsertQuery { (c,colsList) => colsList.filter( _ != c.ID) }
    val frsIdOpt : Option[Long] = if(searchSettings.fragmentationRuleSet.isEmpty)  Option.empty[Long] else Some(searchSettings.fragmentationRuleSet.get.id)
    msiEzDBC.executePrepared( searchSettingsInsertQuery, true ) { stmt =>
      stmt.executeWith(
        searchSettings.softwareName,
        searchSettings.softwareVersion,
        searchSettings.taxonomy,
        searchSettings.maxMissedCleavages,
        searchSettings.ms1ChargeStates,
        searchSettings.ms1ErrorTol,
        searchSettings.ms1ErrorTolUnit,
        searchSettings.isDecoy,
        searchSettings.properties.map(ProfiJson.serialize(_)),
        searchSettings.instrumentConfig.id,
        frsIdOpt
      )

      searchSettings.id = stmt.generatedLong
    }
    
    val ssId = searchSettings.id
    
    // If PMF search => insert PMF settings
    if( searchSettings.pmfSearchSettings.isDefined ) {
      
      val pmfSettings = searchSettings.pmfSearchSettings.get

      msiEzDBC.executePrepared( MsiDbIonSearchTable.mkInsertQuery ) { stmt =>
        stmt.executeWith(
          ssId,
          pmfSettings.maxProteinMass,
          pmfSettings.minProteinMass,
          pmfSettings.proteinPI
        )
      }
    }
    // Else if MS/MS search => insert MS/MS settings
    else if ( searchSettings.msmsSearchSettings.isDefined ) {
      
      val msmsSettings = searchSettings.msmsSearchSettings.get
      
      msiEzDBC.executePrepared( MsiDbMsmsSearchTable.mkInsertQuery ) { stmt =>
        stmt.executeWith(
          ssId,
          msmsSettings.ms2ChargeStates,
          msmsSettings.ms2ErrorTol,
          msmsSettings.ms2ErrorTolUnit
        )
      }
    }
    
    // Insert used enzymes
    val usedEnzymes = searchSettings.usedEnzymes
    usedEnzymes.foreach( this._insertUsedEnzyme(ssId, _, msiEzDBC) )
    
    // Link search settings to used enzyme
    msiEzDBC.executePrepared( MsiDbUsedEnzymeTable.mkInsertQuery ) { stmt =>
      usedEnzymes.foreach( e => stmt.executeWith( ssId, e.id ) )
    }
    
    val fixedPtmSet = searchSettings.fixedPtmDefs.toSet
    val varPtmSet = searchSettings.variablePtmDefs.toSet
    
    // Insert used PTMs
    for (ptmDef <- searchSettings.fixedPtmDefs; if varPtmSet.contains(ptmDef) == false) {
      _insertUsedPTM(ssId, ptmDef, true, 1, msiEzDBC)
    }
    for (ptmDef <- searchSettings.variablePtmDefs) {
      _insertUsedPTM(ssId, ptmDef, false, 1, msiEzDBC)
    }
    
    // Store Ptms used as fixed AND variable modes in search round #2
    // TODO: update the Object Model to associate the search round to PTM definitions
    for (ptmDef <- searchSettings.fixedPtmDefs; if varPtmSet.contains(ptmDef)) {
      _insertUsedPTM(ssId, ptmDef, true, 2, msiEzDBC)
    }
    
    // Link search settings to sequence databases
    msiEzDBC.executePrepared("INSERT INTO search_settings_seq_database_map VALUES (?,?,?,?)") { stmt =>
      searchSettings.seqDatabases.foreach { seqDb =>
        assert(seqDb.id > 0, "sequence database must first be persisted")

        stmt.executeWith(
          searchSettings.id,
          seqDb.id,
          seqDb.sequencesCount,
          seqDb.searchProperties.map(ProfiJson.serialize(_))
        )
      }
    }

  }
  
  protected def _insertUsedEnzyme(ssId: Long, enzyme: Enzyme, msiEzDBC: EasyDBC): Unit = {

    // Check if the enzyme exists in the MSIdb
    val count = msiEzDBC.selectInt("SELECT count(*) FROM enzyme WHERE id =" + enzyme.id)

    // Insert enzyme if it doesn't exist in the MSIdb
    if (count == 0) {
      msiEzDBC.executePrepared( MsiDbEnzymeTable.mkInsertQuery ) { stmt =>
        stmt.executeWith(
          enzyme.id,
          enzyme.name,
          enzyme.cleavageRegexp,
          enzyme.isIndependant,
          enzyme.isSemiSpecific,
          enzyme.properties.map( ProfiJson.serialize(_) )
        )
      }
    }
    
  }

  protected def _insertUsedPTM(ssId: Long, ptmDef: PtmDefinition, isFixed: Boolean, searchRound: Int, msiEzDBC: EasyDBC): Unit = {

    // Check if the PTM specificity exists in the MSIdb
    require(ptmDef.id > 0, s"The PTM definition should be first persisted (ID=${ptmDef.id}, name=${ptmDef.names.shortName})")
    
    // Link search settings to used PTMs
    msiEzDBC.executePrepared( MsiDbUsedPtmTable.mkInsertQuery ) { stmt =>
      stmt.executeWith(
        ssId,
        ptmDef.id,
        searchRound,
        ptmDef.names.shortName,
        isFixed
      )
    }

  }

  private def _insertMsiSearch(msiSearch: MSISearch, msiEzDBC: EasyDBC): Unit = {

    // Retrieve some vars
    val searchSettingsId = msiSearch.searchSettings.id
    require(searchSettingsId > 0, "search settings must first be persisted")

    val peaklistId = msiSearch.peakList.id
    require(peaklistId > 0, "peaklist must first be persisted")

    val msiSearchInsertQuery = MsiDbMsiSearchTable.mkInsertQuery { (c,colsList) => 
      colsList.filter( _ != c.ID)
    }
    
    msiEzDBC.executePrepared( msiSearchInsertQuery, true ) { stmt =>
      stmt.executeWith(
        msiSearch.title,
        msiSearch.date, // msiDb.stringifyDate( msiSearch.date ),
        msiSearch.resultFileName,
        msiSearch.resultFileDirectory,
        msiSearch.jobNumber,
        msiSearch.userName,
        msiSearch.userEmail,
        msiSearch.queriesCount,
        msiSearch.searchedSequencesCount,
        Option.empty[String],
        searchSettingsId,
        peaklistId)

      msiSearch.id = stmt.generatedLong
    }

  }

}


