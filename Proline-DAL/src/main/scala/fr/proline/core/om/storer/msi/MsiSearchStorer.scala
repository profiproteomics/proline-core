package fr.proline.core.om.storer.msi

import fr.proline.core._
import fr.proline.core.dal._
import com.weiglewilczek.slf4s.Logging
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap

import fr.proline.core.om.model.msi.Ms1Query
import fr.proline.core.om.model.msi.Ms2Query

/** A factory object for implementations of the IRsStorer trait */
object MsiSearchStorer {
  def apply( msiDb: MsiDb ) = new MsiSearchStorer( msiDb )  
}

class MsiSearchStorer( msiDb: MsiDb ) extends Logging {
  
  import net.noerd.prequel.ReusableStatement
  import net.noerd.prequel.SQLFormatterImplicits._
  import fr.proline.core.dal.SQLFormatterImplicits._
  import fr.proline.core.utils.sql.BoolToSQLStr
  import fr.proline.core.om.model.msi._
  
  def storeMsiSearch( msiSearch: MSISearch,
                      msQueries: Seq[MsQuery],
                      spectrumIdByTitle: Map[String,Int] ): Map[Int,Int] = {
    
    this._insertInstrumentConfig( msiSearch.searchSettings.instrumentConfig )
    
    // Insert sequence databases
    val seqDbIdByTmpId = new HashMap[Int,Int]()
    msiSearch.searchSettings.seqDatabases.foreach { seqDb =>
      val tmpSeqDbId = seqDb.id
      this._insertSeqDatabase( seqDb )
      seqDbIdByTmpId += ( tmpSeqDbId -> seqDb.id )
    }
    
    // Insert search settings
    this._insertSearchSettings( msiSearch.searchSettings )
    
    // Insert MSI search
    this._insertMsiSearch( msiSearch )
    
    val msiSearchId = msiSearch.id    
    //val msQueryIdByTmpId = new HashMap[Int,Int]()
    
    for( msQuery <- msQueries ) {
      
      //val tmpMsQueryId = msQuery.id
      
      msQuery.msLevel match {
        case 1 => this._insertMs1Query( msQuery.asInstanceOf[Ms1Query], msiSearchId )
        case 2 => {
          val ms2Query = msQuery.asInstanceOf[Ms2Query]
          // FIXME: it should not be null
          if( spectrumIdByTitle != null ) {
            ms2Query.spectrumId = spectrumIdByTitle(ms2Query.spectrumTitle)
          }
          this._insertMs2Query( ms2Query, msiSearchId )
        }
      }
      
      //msQueryIdByTmpId += ( tmpMsQueryId -> msQuery.id )
      
    }
    
    Map() ++ seqDbIdByTmpId
  }
  
  private def _insertInstrumentConfig( instrumentConfig: InstrumentConfig ): Unit = {
    
    if( instrumentConfig.id <= 0 ) throw new Exception("instrument configuration must have a strictly positive identifier")
    
    // Check if the instrument config exists in the MSIdb
    val count = msiDb.getOrCreateTransaction.selectInt( "SELECT count(*) FROM instrument_config WHERE id=" + instrumentConfig.id )
    
    // If the instrument config doesn't exist in the MSIdb
    if( count == 0 ) {
      msiDb.getOrCreateTransaction.executeBatch("INSERT INTO instrument_config VALUES (?,?,?,?,?)") { stmt =>
        stmt.executeWith( instrumentConfig.id,
                          instrumentConfig.name,
                          instrumentConfig.ms1Analyzer,
                          instrumentConfig.msnAnalyzer,
                          Some(null)
                         )
      }
    }
    
  }
  
  private def _insertSeqDatabase( seqDatabase: SeqDatabase ): Unit = {
    
    val fasta_path = seqDatabase.filePath
    val seqDbIds = msiDb.getOrCreateTransaction.select( "SELECT id FROM seq_database WHERE fasta_file_path='" + fasta_path+"'" ) { _.nextInt.get }
    
    // If the sequence database doesn't exist in the MSIdb
    if( seqDbIds.length == 0 ) {
      
      val msiDbConn = this.msiDb.getOrCreateConnection()
      val stmt = msiDbConn.prepareStatement( "INSERT INTO seq_database VALUES ("+ "?,"*6 +"?)",
                                             java.sql.Statement.RETURN_GENERATED_KEYS ) 
      
      new ReusableStatement( stmt, msiDb.config.sqlFormatter ) <<
        Some(null) <<
        seqDatabase.name <<
        seqDatabase.filePath <<
        seqDatabase.version <<
        seqDatabase.releaseDate <<
        seqDatabase.sequencesCount <<
        Some(null)
  
      stmt.execute()
      seqDatabase.id = this.msiDb.extractGeneratedInt( stmt )
      
    } else {
      seqDatabase.id = seqDbIds(0)
    }
    
  }
  
  private def _insertSearchSettings( searchSettings: SearchSettings ): Unit = {
    
    // Retrieve some vars
    val instrumentConfigId = searchSettings.instrumentConfig.id
    if( instrumentConfigId <= 0 )
      throw new Exception("instrument configuration must first be persisted")
    
    val msiDbConn = this.msiDb.getOrCreateConnection()
    val stmt = msiDbConn.prepareStatement( "INSERT INTO search_settings VALUES ("+ "?,"*11 +"?)",
                                           java.sql.Statement.RETURN_GENERATED_KEYS ) 
    
    new ReusableStatement( stmt, msiDb.config.sqlFormatter ) <<
      Some(null) <<
      searchSettings.softwareName <<
      searchSettings.softwareVersion <<
      searchSettings.taxonomy <<
      searchSettings.maxMissedCleavages <<
      searchSettings.ms1ChargeStates <<
      searchSettings.ms1ErrorTol <<
      searchSettings.ms1ErrorTolUnit <<
      searchSettings.quantitation <<
      BoolToSQLStr( searchSettings.isDecoy ) <<
      Some(null) <<
      searchSettings.instrumentConfig.id

    stmt.execute()
    searchSettings.id = this.msiDb.extractGeneratedInt( stmt )
    
    // Link search settings to sequence databases
    msiDb.getOrCreateTransaction.executeBatch( "INSERT INTO search_settings_seq_database_map VALUES (?,?,?,?)" ) { stmt =>
      searchSettings.seqDatabases.foreach { seqDb =>
        if( seqDb.id <= 0 ) throw new Exception("sequence database must first be persisted")
        
        stmt.executeWith( searchSettings.id, seqDb.id, seqDb.sequencesCount, Some(null) )
      }
    }
    
  }

  
  private def _insertMsiSearch( msiSearch: MSISearch ): Unit = {
    
    // Retrieve some vars
    val searchSettingsId = msiSearch.searchSettings.id
    if( searchSettingsId <= 0 )
      throw new Exception("search settings must first be persisted")
    
    val peaklistId = msiSearch.peakList.id
    if( peaklistId <= 0 )
      throw new Exception("peaklist must first be persisted")
    
    val msiDbConn = this.msiDb.getOrCreateConnection()
    val stmt = msiDbConn.prepareStatement( "INSERT INTO msi_search VALUES ("+ "?,"*13 +"?)",
                                           java.sql.Statement.RETURN_GENERATED_KEYS ) 
    
    new ReusableStatement( stmt, msiDb.config.sqlFormatter ) <<
      Some(null) <<
      msiSearch.title <<
      msiDb.stringifyDate( msiSearch.date ) <<
      msiSearch.resultFileName <<
      msiSearch.resultFileDirectory <<
      msiSearch.jobNumber <<
      msiSearch.userName <<
      msiSearch.userEmail <<
      msiSearch.queriesCount <<
      msiSearch.submittedQueriesCount <<
      msiSearch.searchedSequencesCount <<
      Some(null) <<
      searchSettingsId <<
      peaklistId

    stmt.execute()
    msiSearch.id = this.msiDb.extractGeneratedInt( stmt )
    
  }
  
  private def _insertMs1Query( ms1Query: Ms1Query, msiSearchId: Int ): Unit = {
    
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
    
  }
  
  private def _insertMs2Query( ms2Query: Ms2Query, msiSearchId: Int ): Unit = {
    
    // Retrieve some vars
    val spectrumId = ms2Query.spectrumId
    //if( spectrumId <= 0 )
      //throw new Exception("spectrum must first be persisted")
    
    val msiDbConn = this.msiDb.getOrCreateConnection()
    val stmt = msiDbConn.prepareStatement( "INSERT INTO ms_query VALUES ("+ "?,"*6 +"?)",
                                           java.sql.Statement.RETURN_GENERATED_KEYS ) 
    
    new ReusableStatement( stmt, msiDb.config.sqlFormatter ) <<
      Some(null) <<
      ms2Query.initialId <<
      ms2Query.charge <<
      ms2Query.moz <<
      Some(null) <<
      Some(null) <<
      msiSearchId

    stmt.execute()
    ms2Query.id = this.msiDb.extractGeneratedInt( stmt )
  }
  
  
  
}
