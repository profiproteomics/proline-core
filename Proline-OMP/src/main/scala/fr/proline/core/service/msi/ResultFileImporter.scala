package fr.proline.core.service.msi

import java.io.File
import com.weiglewilczek.slf4s.Logging
import fr.proline.core.dal.MsiDb
import fr.proline.core.dal.UdsDb
import fr.proline.core.om.model.msi.Instrument
import fr.proline.core.om.model.msi.InstrumentConfig
import fr.proline.core.om.model.msi.IResultFile
import fr.proline.core.om.model.msi.IResultFileProvider
import fr.proline.core.om.model.msi.ResultFileProviderRegistry
import fr.proline.core.om.storer.msi.MsiSearchStorer
import fr.proline.core.om.storer.msi.PeaklistStorer
import fr.proline.core.om.storer.msi.RsStorer
import fr.proline.core.service.IService

class ResultFileImporter( projectId: Int,
                          fileLocation: File,
                          fileType: String,
                          providerKey: String,
                          instrumentConfigId: Int,
                          storeResultSet: Boolean = true ) extends IService with Logging {
  
  private var targetResultSetId: Int = 0
  private val msiDb = new MsiDb( MsiDb.getDefaultConfig ) // TODO: retrieve from UDS-DB
  private val udsDb = new UdsDb( UdsDb.getDefaultConfig ) // TODO: retrieve from UDS-DB
  
  def getTargetResultSetId = targetResultSetId
  
  def runService(): Boolean = {
    
    // Check that a file is provided
    if (fileLocation == null)
      throw new IllegalArgumentException("ResultFileImporter service: No file specified.")
    
    // Retrieve the instrument configuration
    val instrumentConfig = this.getInstrumentConfig( instrumentConfigId )

    logger.info(" Run service " + fileType + " ResultFileImporter on " + fileLocation.getAbsoluteFile())
    
    // Get Right ResultFile provider
    val rfProvider: Option[IResultFileProvider] = ResultFileProviderRegistry.get( fileType )
    if (rfProvider == None)
      throw new IllegalArgumentException("No ResultFileProvider for specified identification file format")

    // Open the result file
    val resultFile = rfProvider.get.getResultFile( fileLocation, providerKey )
        
    // Configure result file before parsing
    resultFile.instrumentConfig = instrumentConfig
    
    // Retrieve MSISearch and related MS queries
    val msiSearch = resultFile.msiSearch
    val msQueryByInitialId = resultFile.msQueryByInitialId
    var msQueries: List[fr.proline.core.om.model.msi.MsQuery] = null
    if( msQueryByInitialId != null ) {
      msQueries = msQueryByInitialId.map { _._2 }.toList.sort { (a,b) => a.initialId < b.initialId }
    }
    
    // Store the peaklist
    val peaklistStorer = new PeaklistStorer( msiDb )
    val spectrumIdByTitle = peaklistStorer.storePeaklist( msiSearch.peakList, resultFile )
    
    // Store the MSI search with related search settings and MS queries
    val msiSearchStorer = new MsiSearchStorer( msiDb )
    val seqDbIdByTmpId = msiSearchStorer.storeMsiSearch( msiSearch, msQueries, spectrumIdByTitle )
    
    ////logger.info("Parsing file " + fileLocation.getAbsoluteFile() + " using " + resultFile.getClass().getName() + " failed !")
    
    val targetRs = resultFile.getResultSet(false)
    targetRs.name = msiSearch.title
    
    val rsStorer = RsStorer( msiDb )
    rsStorer.storeResultSet(targetRs, seqDbIdByTmpId )
    
    /*val targetRs = resultFile.getResultSet(false)  
    val decoyRs = resultFile.getResultSet(true)  
    
    val rsStorer = RsStorer( msiDb )
    
    if(decoyRs == null)
    	targetRs.decoyResultSet = None
  	else {
  		targetRs.decoyResultSet = Some(decoyRs)
  		
      rsStorer.storeResultSet(targetRs)
      readResultSetId = targetRs.id
  	}*/

    this.msiDb.commitTransaction()
    
    return true
  }
  
  private def getInstrumentConfig( instrumentConfigId: Int ): InstrumentConfig = {
        
    val sqlQuery = "SELECT * FROM instrument_config WHERE id=" + instrumentConfigId
    
    var udsInstConfigColNames: Seq[String] = null
    var udsInstConfigRecord: Map[String,Any] = null
    
    // Load the instrument configuration record
    udsDb.getOrCreateTransaction.selectAndProcess( "SELECT * FROM instrument_config WHERE id=" + instrumentConfigId ) { r => 
      if( udsInstConfigColNames == null ) { udsInstConfigColNames = r.columnNames }      
      udsInstConfigRecord = udsInstConfigColNames.map( colName => ( colName -> r.nextObject.get ) ).toMap      
    }
    
    val instrumentId = udsInstConfigRecord("instrument_id").asInstanceOf[Int]    
    var instrument: Instrument = null
    
    // Load the corresponding instrument
    udsDb.getOrCreateTransaction.selectAndProcess( "SELECT * FROM instrument WHERE id=" + instrumentId ) { r => 
      
      instrument = new Instrument( id = r.nextInt.get,
                                   name = r.nextString.get,
                                   source = r.nextString.get
                                  )
    }
    
    buildInstrumentConfig( udsInstConfigRecord, instrument )

  }
  
  private def buildInstrumentConfig( instConfigRecord: Map[String,Any], instrument: Instrument ): InstrumentConfig = {
    
    new InstrumentConfig(
         id = instConfigRecord("id").asInstanceOf[Int],
         name = instConfigRecord("name").asInstanceOf[String],
         instrument = instrument,
         ms1Analyzer = instConfigRecord("ms1_analyzer").asInstanceOf[String],
         msnAnalyzer = instConfigRecord("msn_analyzer").asInstanceOf[String],
         activationType = instConfigRecord("activation_type").asInstanceOf[String]
         )

  }
   
}