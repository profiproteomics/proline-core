package fr.proline.core.service.msi

import java.io.File

import com.weiglewilczek.slf4s.Logging

import fr.proline.core.dal.DatabaseManagement
import fr.proline.core.dal.MsiDb
import fr.proline.core.dal.UdsDb
import fr.proline.core.om.model.msi.IResultFile
import fr.proline.core.om.model.msi.IResultFileProvider
import fr.proline.core.om.model.msi.Instrument
import fr.proline.core.om.model.msi.InstrumentConfig
import fr.proline.core.om.model.msi.ResultFileProviderRegistry
import fr.proline.core.om.storer.msi.MsiSearchStorer
import fr.proline.core.om.storer.msi.PeaklistStorer
import fr.proline.core.om.storer.msi.RsStorer
import fr.proline.core.service.IService
//import scala.collection.mutable.Map

class ResultFileImporter( dbMgnt: DatabaseManagement,
                          projectId: Int,
                          resultIdentFile: File,
                          fileType: String,
                          providerKey: String,
                          instrumentConfigId: Int,
                          storeResultSet: Boolean = true ) extends IService with Logging {
  
  private var targetResultSetId: Int = 0
  
  private val msiDbConnector = dbMgnt.getMSIDatabaseConnector(projectId, false)
  private val msiDb = new MsiDb( MsiDb.buildConfigFromDatabaseConnector(msiDbConnector) ) 
  private val udsDb = new UdsDb( UdsDb.buildConfigFromDatabaseManagement(dbMgnt) )
  
  def getTargetResultSetId = targetResultSetId
  
  def runService(): Boolean = {
    
    // Check that a file is provided
    if (resultIdentFile == null)
      throw new IllegalArgumentException("ResultFileImporter service: No file specified.")
    
    // Retrieve the instrument configuration
    val instrumentConfig = this._getInstrumentConfig( instrumentConfigId )

    logger.info(" Run service " + fileType + " ResultFileImporter on " + resultIdentFile.getAbsoluteFile())
    
    // Get Right ResultFile provider
    val rfProvider: Option[IResultFileProvider] = ResultFileProviderRegistry.get( fileType )
    if (rfProvider == None)
      throw new IllegalArgumentException("No ResultFileProvider for specified identification file format")

    // Open the result file
    val resultFile = rfProvider.get.getResultFile( resultIdentFile, providerKey )
    
    // Instantiate some storers
    val msiSearchStorer = MsiSearchStorer( msiDb )
    val peaklistStorer = PeaklistStorer( msiDb )
    val rsStorer = RsStorer(dbMgnt, msiDb )
        
    // Configure result file before parsing
    resultFile.instrumentConfig = instrumentConfig
    
    // Insert instrument config in the MSIdb
    msiSearchStorer.insertInstrumentConfig( instrumentConfig )
    
    // Retrieve MSISearch and related MS queries
    val msiSearch = resultFile.msiSearch
    val msQueryByInitialId = resultFile.msQueryByInitialId
    var msQueries: List[fr.proline.core.om.model.msi.MsQuery] = null
    if( msQueryByInitialId != null ) {
      msQueries = msQueryByInitialId.map { _._2 }.toList.sort { (a,b) => a.initialId < b.initialId }
    }
    
    // Store the peaklist    
    val spectrumIdByTitle = peaklistStorer.storePeaklist( msiSearch.peakList, resultFile )
    
    // Store the MSI search with related search settings and MS queries    
    val seqDbIdByTmpId = msiSearchStorer.storeMsiSearch( msiSearch, msQueries, spectrumIdByTitle )
    
    ////logger.info("Parsing file " + fileLocation.getAbsoluteFile() + " using " + resultFile.getClass().getName() + " failed !")
    
    // Load target result set
    val targetRs = resultFile.getResultSet(false)
    targetRs.name = msiSearch.title    
    
    // Load and store decoy result set if it exists
    if( resultFile.hasDecoyResultSet ) {
  	  val decoyRs = resultFile.getResultSet(true)
  	  decoyRs.name = msiSearch.title
  	  
  	  rsStorer.storeResultSet(decoyRs,seqDbIdByTmpId)
      targetRs.decoyResultSet = Some(decoyRs)
  	}
    else targetRs.decoyResultSet = None

    // Store target result set
    rsStorer.storeResultSet(targetRs,seqDbIdByTmpId)
    this.targetResultSetId = targetRs.id

    this.msiDb.commitTransaction()
    this.msiDb.closeConnection()
    this.udsDb.closeConnection()
    
    true
  }
  
  private def _getInstrumentConfig( instrumentConfigId: Int ): InstrumentConfig = {
    
    import fr.proline.core.utils.primitives.LongOrIntAsInt._
    
    var udsInstConfigColNames: Seq[String] = null
    var udsInstConfigRecord: Map[String,Any] = null
    
    // Load the instrument configuration record
    udsDb.getOrCreateTransaction.selectAndProcess( "SELECT * FROM instrument_config WHERE id=" + instrumentConfigId ) { r => 
      if( udsInstConfigColNames == null ) { udsInstConfigColNames = r.columnNames }      
      udsInstConfigRecord = udsInstConfigColNames.map { colName => ( colName -> r.nextObject.getOrElse(null) ) } toMap      
    }
    
    val instrumentId: Int = udsInstConfigRecord("instrument_id").asInstanceOf[AnyVal]    
    var instrument: Instrument = null
    
    // Load the corresponding instrument
    udsDb.getOrCreateTransaction.selectAndProcess( "SELECT * FROM instrument WHERE id=" + instrumentId ) { r => 
      
      instrument = new Instrument( id = r.nextInt.get,
                                   name = r.nextString.get,
                                   source = r.nextString.get
                                  )
    }
    
    this._buildInstrumentConfig( udsInstConfigRecord, instrument )

  }
  
  private def _buildInstrumentConfig( instConfigRecord: Map[String,Any], instrument: Instrument ): InstrumentConfig = {
    
    import fr.proline.core.utils.primitives.LongOrIntAsInt._
    
    new InstrumentConfig(
         id = instConfigRecord("id").asInstanceOf[AnyVal],
         name = instConfigRecord("name").asInstanceOf[String],
         instrument = instrument,
         ms1Analyzer = instConfigRecord("ms1_analyzer").asInstanceOf[String],
         msnAnalyzer = instConfigRecord("msn_analyzer").asInstanceOf[String],
         activationType = instConfigRecord("activation_type").asInstanceOf[String]
         )

  }
  
  /*private def _insertInstrumentConfig( instrumentConfig: InstrumentConfig ): Unit = {
    
    import net.noerd.prequel.SQLFormatterImplicits._
    import fr.proline.core.dal.SQLFormatterImplicits._

    this.msiDb.getOrCreateTransaction().executeBatch("INSERT INTO instrument_config VALUES ("+"?," *4 +"?)") { stmt =>
          
      // Store new protein
      stmt.executeWith(
              instrumentConfig.id,
              instrumentConfig.name,
              instrumentConfig.ms1Analyzer,
              instrumentConfig.msnAnalyzer,
              Option(null)
            )

    }
    
  }*/
   
}