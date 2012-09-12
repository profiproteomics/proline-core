package fr.proline.core.om.storer.msi.impl

import com.weiglewilczek.slf4s.Logging
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import net.noerd.prequel.ReusableStatement
import net.noerd.prequel.SQLFormatterImplicits._
import fr.proline.core.dal.SQLFormatterImplicits._
import fr.proline.core.dal.MsiDb
import fr.proline.core.dal.{MsiDbMsiSearchTable,MsiDbMsQueryTable,MsiDbSearchSettingsTable,MsiDbSeqDatabaseTable}
import fr.proline.core.dal.{MsiDbPtmSpecificityTable,MsiDbUsedPtmTable}
import fr.proline.core.utils.sql._
import fr.proline.core.om.model.msi._
import fr.proline.core.om.storer.msi.IMsiSearchStorer

class SQLiteMsiSearchStorer( msiDb: MsiDb ) extends IMsiSearchStorer with Logging {
  
  def storeMsiSearch( msiSearch: MSISearch,
                      msQueries: Seq[MsQuery],
                      spectrumIdByTitle: Map[String,Int] ): Map[Int,Int] = {
    
    val ss = msiSearch.searchSettings
    
    // Insert sequence databases
    val seqDbIdByTmpId = new HashMap[Int,Int]()
    ss.seqDatabases.foreach { seqDb =>
      val tmpSeqDbId = seqDb.id
      this._insertSeqDatabase( seqDb )
      seqDbIdByTmpId += ( tmpSeqDbId -> seqDb.id )
    }
    
    // Insert search settings    
    this._insertSearchSettings( ss )
    
    // Insert used PTMs
    val ssId = ss.id
    for( ptmDef <- ss.fixedPtmDefs ) this._insertUsedPTM( ssId, ptmDef, true )
    for( ptmDef <- ss.variablePtmDefs ) this._insertUsedPTM( ssId, ptmDef, false )
    
    // Insert MSI search
    this._insertMsiSearch( msiSearch )
    
    // Store MS queries
    this.storeMsQueries( msiSearch, msQueries, spectrumIdByTitle )
    
    Map() ++ seqDbIdByTmpId
  }
  
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
  
  def insertInstrumentConfig( instrumentConfig: InstrumentConfig ): Unit = {
    
    require( instrumentConfig.id > 0, "instrument configuration must have a strictly positive identifier" )
    
    // Check if the instrument config exists in the MSIdb
    val count = msiDb.getOrCreateTransaction.selectInt( "SELECT count(*) FROM instrument_config WHERE id=" + instrumentConfig.id )
    
    // If the instrument config doesn't exist in the MSIdb
    if( count == 0 ) {
      
      msiDb.getOrCreateTransaction.executeBatch("INSERT INTO instrument_config VALUES (?,?,?,?,?)") { stmt =>
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
    val seqDbIds = msiDb.getOrCreateTransaction.select( "SELECT id FROM seq_database WHERE fasta_file_path='" + fasta_path+"'" ) { _.nextInt.get }
    
    // If the sequence database doesn't exist in the MSIdb
    if( seqDbIds.length == 0 ) {
      
      val seqDbColsList = MsiDbSeqDatabaseTable.getColumnsAsStrList().filter { _ != "id" }
      val seqDbInsertQuery = MsiDbSeqDatabaseTable.makeInsertQuery( seqDbColsList )
      
      val msiDbTx = this.msiDb.getOrCreateTransaction()
      msiDbTx.executeBatch( seqDbInsertQuery, true ) { stmt =>
      
        stmt.executeWith(
              seqDatabase.name,
              seqDatabase.filePath,
              seqDatabase.version,
              new java.util.Date,// TODO: upgrade to date seqDatabase.releaseDate,
              seqDatabase.sequencesCount,
              Option.empty[String]
            )
          
        seqDatabase.id = this.msiDb.extractGeneratedInt( stmt.wrapped )
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
    
    val msiDbTx = this.msiDb.getOrCreateTransaction()
    msiDbTx.executeBatch( searchSettingsInsertQuery, true ) { stmt =>
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
            
      searchSettings.id = this.msiDb.extractGeneratedInt( stmt.wrapped )
    }
    
    // Link search settings to sequence databases
    msiDb.getOrCreateTransaction.executeBatch( "INSERT INTO search_settings_seq_database_map VALUES (?,?,?,?)" ) { stmt =>
      searchSettings.seqDatabases.foreach { seqDb =>
        assert( seqDb.id > 0, "sequence database must first be persisted" )
        
        stmt.executeWith( searchSettings.id, seqDb.id, seqDb.sequencesCount, Option.empty[String] )
      }
    }
    
  }

  private def _insertUsedPTM( ssId: Int, ptmDef: PtmDefinition, isFixed: Boolean ): Unit = {
    
    // Check if the PTM specificity exists in the MSIdb
    val msiDbTx = this.msiDb.getOrCreateTransaction()
    val count = msiDbTx.selectInt( "SELECT count(*) FROM ptm_specificity WHERE id =" + ptmDef.id )
    
    // Insert PTM specificity if it doesn't exist in the MSIdb
    if( count == 0 ) {
      val ptmSpecifColsList = MsiDbPtmSpecificityTable.getColumnsAsStrList()
      val ptmSpecifInsertQuery = MsiDbPtmSpecificityTable.makeInsertQuery( ptmSpecifColsList )      
      
      msiDbTx.executeBatch( ptmSpecifInsertQuery, false ) { stmt =>
        stmt.executeWith(
              ptmDef.id,
              ptmDef.location,
              ptmDef.residue,
              Option.empty[String]
              )
      }
    }
    
    // Link used PTMs to search settings
    val usedPtmColsList = MsiDbUsedPtmTable.getColumnsAsStrList()
    val usedPtmInsertQuery = MsiDbUsedPtmTable.makeInsertQuery( usedPtmColsList )
    msiDbTx.executeBatch( usedPtmInsertQuery, false ) { stmt =>
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
    
    val msiDbTx = this.msiDb.getOrCreateTransaction()
    msiDbTx.executeBatch( msiSearchInsertQuery, true ) { stmt =>
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
          
      msiSearch.id = this.msiDb.extractGeneratedInt( stmt.wrapped )
    }
    
  }
  
  /*private def _insertMs1Query( ms1Query: Ms1Query, msiSearchId: Int ): Unit = {
    
    val msiDbConn = this.msiDb.getOrCreateConnection()
    val stmt = msiDbConn.prepareStatement( "INSERT INTO ms_query VALUES ("+ "?,"*6 +"?)",
                                           java.sql.Statement.RETURN_GENERATED_KEYS ) 
    
    new ReusableStatement( stmt, msiDb.config.sqlFormatter ) <<
      Some(null) <<
      ms1Query.initialId <<
      ms1Query.charge <<
      ms1Query.moz <<
      Some(null) <<
      Some(null) <<
      msiSearchId

    stmt.execute()
    ms1Query.id = this.msiDb.extractGeneratedInt( stmt )
    
  }*/
  
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
  
}


