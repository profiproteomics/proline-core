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
import fr.proline.core.service.IService
import fr.proline.core.om.storer.msi.PeaklistStorer
import fr.proline.core.om.storer.msi.MsiSearchStorer
import fr.proline.core.om.storer.msi.IRsStorer
import fr.proline.core.om.storer.msi.impl.JPARsStorer
import fr.proline.core.om.storer.msi.impl.StorerContext
import org.apache.commons.lang3.StringUtils

class ResultFileImporter( dbMgnt: DatabaseManagement,
                          projectId: Int,
                          resultIdentFile: File,
                          fileType: String,
                          providerKey: String,
                          instrumentConfigId: Int,
                          importerProperties: Map[String, Any]) extends IService with Logging {
  
  private var targetResultSetId: Int = 0
  
  private val msiDbConnector = dbMgnt.getMSIDatabaseConnector(projectId, false)
//  private val msiDb = new MsiDb( MsiDb.buildConfigFromDatabaseConnector(msiDbConnector) ) 
  private val udsDb = new UdsDb( UdsDb.buildConfigFromDatabaseManagement(dbMgnt) )
  private var stContext : StorerContext=null
  
  override protected def beforeInterruption = {
    // Release database connections
    this.logger.info("releasing database connections before service interruption...")
//    this.msiDb.closeConnection()
    this.udsDb.closeConnection()
    this.stContext.closeOpenedEM()
  }
  
  def getTargetResultSetId = targetResultSetId
  
  def runService(): Boolean = {
    
    var serviceResultOK: Boolean = true
    
    // Check that a file is provided
    if (resultIdentFile == null)
      throw new IllegalArgumentException("ResultFileImporter service: No file specified.")
    
    // Retrieve the instrument configuration
    val instrumentConfig = this._getInstrumentConfig( instrumentConfigId )

    this.stContext = new StorerContext(dbMgnt, msiDbConnector)
    logger.info(" Run service " + fileType + " ResultFileImporter on " + resultIdentFile.getAbsoluteFile())
    >>>
    
    // Get Right ResultFile provider
    val rfProvider: Option[IResultFileProvider] = ResultFileProviderRegistry.get( fileType )
    if (rfProvider == None)
      throw new IllegalArgumentException("No ResultFileProvider for specified identification file format")

    // Open the result file
    val resultFile = rfProvider.get.getResultFile( resultIdentFile, providerKey, importerProperties )
    >>>
    
    // Instantiate RsStorer
//    val rsStorer = RsStorer(dbMgnt, msiDb )//  VD Pour SQLStorer Only  TODO A RERENDRE AVEC SQLRsStorer 
    val rsStorer: IRsStorer = new JPARsStorer(dbMgnt, msiDbConnector) //VD Pour ORMStorer Only
        
	 // Configure result file before parsing
    resultFile.instrumentConfig = instrumentConfig
    
    val msiTransaction = stContext.msiEm.getTransaction
    var msiTransacOk: Boolean = false
    
    logger.debug("Starting Msi Db transaction")
    
    //Start MSI Transaction and ResultSets store
    try {
      msiTransaction.begin()
      msiTransacOk = false
      
      // Insert instrument config in the MSIdb         
      rsStorer.insertInstrumentConfig( instrumentConfig ,  this.stContext)
    
	  // Retrieve MSISearch and related MS queries
	  val msiSearch = resultFile.msiSearch
	  val msQueryByInitialId = resultFile.msQueryByInitialId
	  var msQueries: List[fr.proline.core.om.model.msi.MsQuery] = null
	  if( msQueryByInitialId != null ) {
		msQueries = msQueryByInitialId.map { _._2 }.toList.sort { (a,b) => a.initialId < b.initialId }
	  }
	      
      //  // Store the peaklist    
      //  val spectrumIdByTitle = peaklistStorer.storePeaklist( msiSearch.peakList, resultFile )//  VD Pour SQLStorer Only  TODO A RERENDRE AVEC SQLRsStorer 
      //  >>>
	    
	  // Store the MSI search with related search settings and MS queries    
      //  val seqDbIdByTmpId = msiSearchStorer.storeMsiSearch( msiSearch, msQueries, spectrumIdByTitle ) // VD Pour SQLStorer Only TODO A RERENDRE AVEC SQLRsStorer 
	  >>>
	    	  
	    
	  // Load target result set
	  val targetRs = resultFile.getResultSet(false)
	  if(StringUtils.isEmpty(targetRs.name))
	      targetRs.name = msiSearch.title 
	    	    
	  //-- VDS TODO: Identify decoy mode to get decoy RS from parser or to create it from target RS.
	
	  // Load and store decoy result set if it exists
	  if( resultFile.hasDecoyResultSet ) {
	  	 val decoyRs = resultFile.getResultSet(true)
	  	 if(StringUtils.isEmpty(decoyRs.name))
	  		 decoyRs.name = msiSearch.title
	  		 
//  	  rsStorer.storeResultSet(decoyRs,seqDbIdByTmpId) // VD Pour SQLStorer Only TODO A RERENDRE AVEC SQLRsStorer 	
	     targetRs.decoyResultSet = Some(decoyRs)
	     >>>
  	  }
       else targetRs.decoyResultSet = None

     //  Store target result set
//    rsStorer.storeResultSet(targetRs,seqDbIdByTmpId)// VD Pour SQLStorer Only TODO A RERENDRE AVEC SQLRsStorer 
      this.targetResultSetId = rsStorer.storeResultSet(targetRs, msQueries, resultFile,  this.stContext)// VD Pour ORMStorer Only
      >>>
    
//    this.msiDb.commitTransaction()// VD Pour SQLStorer Only
      msiTransaction.commit()
      msiTransacOk = true
    } finally {
      /* Check msiTransaction integrity */
      if ((msiTransaction != null) && !msiTransacOk) {
        try {
          msiTransaction.rollback()
        } catch {
          case ex => logger.error("Error rollbacking Msi Db transaction", ex)
        }
      }
    }
    
    this.beforeInterruption()    
    msiTransacOk
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
   
}