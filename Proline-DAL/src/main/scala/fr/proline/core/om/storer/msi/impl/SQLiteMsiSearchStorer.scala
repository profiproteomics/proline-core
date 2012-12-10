package fr.proline.core.om.storer.msi.impl

import com.weiglewilczek.slf4s.Logging
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap

import fr.profi.jdbc.easy._
import fr.profi.jdbc.PreparedStatementWrapper
import fr.proline.core.dal.SQLQueryHelper
import fr.proline.core.dal.{MsiDbMsiSearchTable,MsiDbMsQueryTable,MsiDbSearchSettingsTable,MsiDbSeqDatabaseTable}
import fr.proline.core.dal.{MsiDbPtmSpecificityTable,MsiDbUsedPtmTable}
import fr.proline.util.sql._
import fr.proline.core.om.model.msi._
import fr.proline.core.om.storer.msi.IMsiSearchStorer

class SQLiteMsiSearchStorer( msiDb: SQLQueryHelper ) extends IMsiSearchStorer with Logging {
  
  val ezDBC = msiDb.ezDBC
  
  def storeMsiSearch( msiSearch: MSISearch, context: StorerContext ): Int = {
    
    val ss = msiSearch.searchSettings
    
    // Insert sequence databases
    // TODO : If seqDb does not exist in PDI do not create it !!
    val seqDbIdByTmpIdBuilder = collection.immutable.Map.newBuilder[Int,Int]
    ss.seqDatabases.foreach { seqDb =>
      val tmpSeqDbId = seqDb.id
      this._insertSeqDatabase( seqDb )
      seqDbIdByTmpIdBuilder += ( tmpSeqDbId -> seqDb.id )
    }    
    context.seqDbIdByTmpId = seqDbIdByTmpIdBuilder.result()
    
    // Insert search settings
    this._insertSearchSettings( ss )
    
    // Insert used PTMs
    val ssId = ss.id
    for( ptmDef <- ss.fixedPtmDefs ) this._insertUsedPTM( ssId, ptmDef, true )
    for( ptmDef <- ss.variablePtmDefs ) this._insertUsedPTM( ssId, ptmDef, false )
    
    // Insert MSI search
    this._insertMsiSearch( msiSearch )
    
    // Store MS queries
    //this.storeMsQueries( msiSearch, msQueries, context )
    
    msiSearch.id
  }
  
  def storeMsQueries( msiSearchId: Int, msQueries: Seq[MsQuery], context : StorerContext ): StorerContext = {
    
    val msQueryColsList = MsiDbMsQueryTable.getColumnsAsStrList().filter { _ != "id" }
    val msQueryInsertQuery = MsiDbMsQueryTable.makeInsertQuery( msQueryColsList )
    
    context.msiDB.executePrepared( msQueryInsertQuery, true ) { stmt =>
      
      for( msQuery <- msQueries ) {
        
        //val tmpMsQueryId = msQuery.id
        
        msQuery.msLevel match {
          case 1 => this._insertMsQuery( stmt, msQuery.asInstanceOf[Ms1Query], msiSearchId, Option.empty[Int], context )
          case 2 => {
            val ms2Query = msQuery.asInstanceOf[Ms2Query]
            // FIXME: it should not be null
            var spectrumId = Option.empty[Int]
            if( context.spectrumIdByTitle != null ) {
              ms2Query.spectrumId = context.spectrumIdByTitle(ms2Query.spectrumTitle)
              spectrumId = Some(ms2Query.spectrumId)
            }
            this._insertMsQuery( stmt, msQuery, msiSearchId, spectrumId, context )
          }
        }
        
        //msQueryIdByTmpId += ( tmpMsQueryId -> msQuery.id )
        
      }
      
    }
    
    context
  }
  
  private def _insertMsQuery( stmt: PreparedStatementWrapper, msQuery: MsQuery, msiSearchId: Int, spectrumId: Option[Int], context : StorerContext ): Unit = {
    
    import com.codahale.jerkson.Json.generate
    // Retrieve some vars
    //val spectrumId = ms2Query.spectrumId
    //if( spectrumId <= 0 )
      //throw new Exception("spectrum must first be persisted")
    
    val msqPropsAsJSON = if( msQuery.properties != None ) Some(generate(msQuery.properties.get)) else None
    
    stmt.executeWith(
          msQuery.initialId,
          msQuery.charge,
          msQuery.moz,
          msqPropsAsJSON,
          spectrumId,
          msiSearchId
          )

    msQuery.id = stmt.generatedInt
  }
  
  /*
  
  def storeMsQueries( msiSearch: MSISearch,
                      msQueries: Seq[MsQuery],
                      spectrumIdByTitle: Map[String,Int] ): Unit = {
    
    val msiSearchId = msiSearch.id    
    //val msQueryIdByTmpId = new HashMap[Int,Int]()
        
    val msQueryColsList = MsiDbMsQueryTable.getColumnsAsStrList().filter { _ != "id" }
    val msQueryInsertQuery = MsiDbMsQueryTable.makeInsertQuery( msQueryColsList )
    
    val msiDbTx = this.msiDb.getOrCreateTransaction()
    msiDbTx.executeBatch( msQueryInsertQuery, true ) { stmt =>
      
      for( msQuery <- msQueries ) {
        
        //val tmpMsQueryId = msQuery.id
        
        msQuery.msLevel match {
          case 1 => this._insertMsQuery( stmt, msQuery.asInstanceOf[Ms1Query], msiSearchId, Option.empty[Int] )
          case 2 => {
            val ms2Query = msQuery.asInstanceOf[Ms2Query]
            // FIXME: it should not be null
            var spectrumId = Option.empty[Int]
            if( spectrumIdByTitle != null ) {
              ms2Query.spectrumId = spectrumIdByTitle(ms2Query.spectrumTitle)
              spectrumId = Some(ms2Query.spectrumId)
            }
            this._insertMsQuery( stmt, msQuery, msiSearchId, spectrumId )
          }
        }
        
        //msQueryIdByTmpId += ( tmpMsQueryId -> msQuery.id )
        
      }
      
    }
    
  }

  private def _insertMsQuery( stmt: ReusableStatement, msQuery: MsQuery, msiSearchId: Int, spectrumId: Option[Int] ): Unit = {
    
    import com.codahale.jerkson.Json.generate
    // Retrieve some vars
    //val spectrumId = ms2Query.spectrumId
    //if( spectrumId <= 0 )
      //throw new Exception("spectrum must first be persisted")
    
    val msqPropsAsJSON = if( msQuery.properties != None ) Some(generate(msQuery.properties.get)) else None
    
    stmt.executeWith(
          msQuery.initialId,
          msQuery.charge,
          msQuery.moz,
          msqPropsAsJSON,
          spectrumId,
          msiSearchId
          )

    msQuery.id = this.msiDb.extractGeneratedInt( stmt.wrapped )
  }
  
  */
  
  def insertInstrumentConfig( instrumentConfig: InstrumentConfig, context: StorerContext ): Unit = {
    
    require( instrumentConfig.id > 0, "instrument configuration must have a strictly positive identifier" )
    
    // Check if the instrument config exists in the MSIdb
    val count = context.msiDB.selectInt( "SELECT count(*) FROM instrument_config WHERE id=" + instrumentConfig.id )
    
    // If the instrument config doesn't exist in the MSIdb
    if( count == 0 ) {
      context.msiDB.executePrepared("INSERT INTO instrument_config VALUES (?,?,?,?,?)") { stmt =>
        stmt.executeWith( instrumentConfig.id,
                          instrumentConfig.name,
                          instrumentConfig.ms1Analyzer,
                          Option(instrumentConfig.msnAnalyzer),
                          Option.empty[String]
                         )
      }
    }   
  }
  
  private def _insertSeqDatabase( seqDatabase: SeqDatabase ): Unit = {    
    
    val fasta_path = seqDatabase.filePath
    val seqDbIds = ezDBC.select( "SELECT id FROM seq_database WHERE fasta_file_path='" + fasta_path+"'" ) { _.nextInt }
    
    // If the sequence database doesn't exist in the MSIdb
    if( seqDbIds.length == 0 ) {
      
      val seqDbColsList = MsiDbSeqDatabaseTable.getColumnsAsStrList().filter { _ != "id" }
      val seqDbInsertQuery = MsiDbSeqDatabaseTable.makeInsertQuery( seqDbColsList )
      
      ezDBC.executePrepared( seqDbInsertQuery, true ) { stmt =>
      
        stmt.executeWith(
              seqDatabase.name,
              seqDatabase.filePath,
              seqDatabase.version,
              new java.util.Date,// TODO: upgrade to date seqDatabase.releaseDate,
              seqDatabase.sequencesCount,
              Option.empty[String]
            )
          
        seqDatabase.id = stmt.generatedInt
      }
      
    } else {
      seqDatabase.id = seqDbIds(0)
    }
    
  }
  
  private def _insertSearchSettings( searchSettings: SearchSettings ): Unit = {
    
    // Retrieve some vars
    val instrumentConfigId = searchSettings.instrumentConfig.id
    require( instrumentConfigId > 0, "instrument configuration must first be persisted" )
    
    val searchSettingsColsList = MsiDbSearchSettingsTable.getColumnsAsStrList().filter { _ != "id" }
    val searchSettingsInsertQuery = MsiDbSearchSettingsTable.makeInsertQuery( searchSettingsColsList )
    
    ezDBC.executePrepared( searchSettingsInsertQuery, true ) { stmt =>
      stmt.executeWith(
            searchSettings.softwareName,
            searchSettings.softwareVersion,
            searchSettings.taxonomy,
            searchSettings.maxMissedCleavages,
            searchSettings.ms1ChargeStates,
            searchSettings.ms1ErrorTol,
            searchSettings.ms1ErrorTolUnit,
            searchSettings.quantitation,
            searchSettings.isDecoy,
            Option.empty[String],
            searchSettings.instrumentConfig.id
            )
            
      searchSettings.id = stmt.generatedInt
    }
    
    // Link search settings to sequence databases
    ezDBC.executePrepared( "INSERT INTO search_settings_seq_database_map VALUES (?,?,?,?)" ) { stmt =>
      searchSettings.seqDatabases.foreach { seqDb =>
        assert( seqDb.id > 0, "sequence database must first be persisted" )
        
        stmt.executeWith( searchSettings.id, seqDb.id, seqDb.sequencesCount, Option.empty[String] )
      }
    }
    
  }

  protected def _insertUsedPTM( ssId: Int, ptmDef: PtmDefinition, isFixed: Boolean ): Unit = {
    
    // Check if the PTM specificity exists in the MSIdb
    val count = ezDBC.selectInt( "SELECT count(*) FROM ptm_specificity WHERE id =" + ptmDef.id )
    
    // Insert PTM specificity if it doesn't exist in the MSIdb
    if( count == 0 ) {
      val ptmSpecifColsList = MsiDbPtmSpecificityTable.getColumnsAsStrList()
      val ptmSpecifInsertQuery = MsiDbPtmSpecificityTable.makeInsertQuery( ptmSpecifColsList )      
      val residueAsStr = if(ptmDef.residue == '\0') "" else ptmDef.residue.toString
      
      ezDBC.executePrepared( ptmSpecifInsertQuery, false ) { stmt =>
        stmt.executeWith(
              ptmDef.id,
              ptmDef.location,
              residueAsStr,
              Option.empty[String]
              )
      }
    }
    
    // Link used PTMs to search settings
    val usedPtmColsList = MsiDbUsedPtmTable.getColumnsAsStrList()
    val usedPtmInsertQuery = MsiDbUsedPtmTable.makeInsertQuery( usedPtmColsList )
    ezDBC.executePrepared( usedPtmInsertQuery ) { stmt =>
      stmt.executeWith(
        ssId,
        ptmDef.id,
        ptmDef.names.shortName,
        isFixed,
        Option.empty[String]
      )   
    }
    
  }
  
  private def _insertMsiSearch( msiSearch: MSISearch ): Unit = {
    
    // Retrieve some vars
    val searchSettingsId = msiSearch.searchSettings.id
    require( searchSettingsId > 0, "search settings must first be persisted" )
    
    val peaklistId = msiSearch.peakList.id
    require( peaklistId > 0, "peaklist must first be persisted" )
    
    val msiSearchColsList = MsiDbMsiSearchTable.getColumnsAsStrList().filter { _ != "id" }
    val msiSearchInsertQuery = MsiDbMsiSearchTable.makeInsertQuery( msiSearchColsList )
    
    ezDBC.executePrepared( msiSearchInsertQuery, true ) { stmt =>
      stmt.executeWith(
        msiSearch.title,
        msiSearch.date, // msiDb.stringifyDate( msiSearch.date ),
        msiSearch.resultFileName,
        msiSearch.resultFileDirectory,
        msiSearch.jobNumber,
        msiSearch.userName,
        msiSearch.userEmail,
        msiSearch.queriesCount,
        msiSearch.submittedQueriesCount,
        msiSearch.searchedSequencesCount,
        Option.empty[String],
        searchSettingsId,
        peaklistId
      )
          
      msiSearch.id = stmt.generatedInt
    }
    
  }
  
}


