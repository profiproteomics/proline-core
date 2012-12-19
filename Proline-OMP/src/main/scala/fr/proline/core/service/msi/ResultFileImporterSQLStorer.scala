package fr.proline.core.service.msi

import java.io.File
import com.weiglewilczek.slf4s.Logging
import com.codahale.jerkson.Json.parse

import fr.profi.jdbc.easy._
import fr.proline.api.service.IService
import fr.proline.core.algo.msi.TargetDecoyResultSetSplitter
import fr.proline.core.dal.SQLQueryHelper
import fr.proline.core.om.model.msi.{IResultFile,IResultFileProvider,Instrument,InstrumentConfig,PeaklistSoftware,ResultSet}
import fr.proline.core.om.model.msi.ResultFileProviderRegistry
import fr.proline.core.om.storer.msi.{IRsStorer,MsiSearchStorer,RsStorer}
import fr.proline.core.om.storer.msi.impl.JPARsStorer
import fr.proline.core.om.storer.msi.impl.StorerContext
import fr.proline.core.orm.util.DatabaseManager
import fr.proline.repository.IDatabaseConnector
import fr.proline.repository.DriverType
import fr.proline.util.StringUtils

class ResultFileImporterSQLStorer(
        storerContext: StorerContext,
        resultIdentFile: File,
        fileType: String,
        providerKey: String,
        instrumentConfigId: Int,
        peaklistSoftwareId: Int,
        importerProperties: Map[String, Any],
        acDecoyRegex: Option[util.matching.Regex] = None ) extends IService with Logging {
  
  private var _hasInitiatedStorerContext: Boolean = false
  
  // Secondary constructor
  def this( dbManager: DatabaseManager,
            msiDbConnector: IDatabaseConnector,
            resultIdentFile: File,
            fileType: String,
            providerKey: String,
            instrumentConfigId: Int,
            peaklistSoftwareId: Int,
            importerProperties: Map[String, Any],
            acDecoyRegex: Option[util.matching.Regex] = None ) {
    this(
      new StorerContext( dbManager, msiDbConnector ),
      resultIdentFile,
      fileType,
      providerKey,
      instrumentConfigId,
      peaklistSoftwareId,
      importerProperties,
      acDecoyRegex
    )
    _hasInitiatedStorerContext = true
  }
  
  //val wasMsiConnectionOpened = storerContext.isMsiDbConnectionOpened
  //val wasPsConnectionOpened = storerContext.isPsConnectionOpened
  
  private var targetResultSetId = 0
  
  private val udsSqlHelper = new SQLQueryHelper(storerContext.dbManager.getUdsDbConnector)
  
  override protected def beforeInterruption = {
    // Release database connections
    this.logger.info("releasing database connections before service interruption...")
//    this.msiDb.closeConnection()
    this.udsSqlHelper.closeConnection()
    storerContext.closeOpenedEMs()
    
    //if( !wasMsiConnectionOpened ) storerContext.msiDbConnection.close()
    //if( !wasPsConnectionOpened ) storerContext.psDbConnection.close()
    if( _hasInitiatedStorerContext ) storerContext.closeConnections()
  }
  
  def getTargetResultSetId = targetResultSetId
  
  def runService(): Boolean = {
    
    var serviceResultOK: Boolean = true
    
    // Check that a file is provided
    require(resultIdentFile != null, "ResultFileImporter service: No file specified.")
    logger.info(" Run service " + fileType + " ResultFileImporter on " + resultIdentFile.getAbsoluteFile())
    >>>
    
    // Check if a transaction is already initiated
    val wasInTransaction = storerContext.msiEzDBC.isInTransaction 
    if( !wasInTransaction ) storerContext.msiEzDBC.beginTransaction()
    
    // Retrieve the instrument configuration
    val instrumentConfig = this._getInstrumentConfig( instrumentConfigId )
    
    // Get Right ResultFile provider
    val rfProvider: Option[IResultFileProvider] = ResultFileProviderRegistry.get( fileType )
    require( rfProvider != None, "No ResultFileProvider for specified identification file format")
    
    // Open the result file
    val resultFile = rfProvider.get.getResultFile( resultIdentFile, providerKey, importerProperties )
    >>>
    
    // Instantiate RsStorer   
    val rsStorer: IRsStorer = RsStorer( storerContext )
     
	  // Configure result file before parsing
    resultFile.instrumentConfig = instrumentConfig
    
    //Fait par le Storer: Attente partage transaction TODO    
//    val msiTransaction = stContext.msiEm.getTransaction
//    var msiTransacOk: Boolean = false
    
    logger.debug("Starting Msi Db transaction")
    
    //Start MSI Transaction and ResultSets store
    try {
      //Fait par le Storer: Attente partage transaction TODO  
//      msiTransaction.begin()
//      msiTransacOk = false
      
      // Insert instrument config in the MSIdb         
      rsStorer.insertInstrumentConfig( instrumentConfig, this.storerContext)
      
      // Retrieve MSISearch and related MS queries
      val msiSearch = resultFile.msiSearch
      val msQueryByInitialId = resultFile.msQueryByInitialId
      var msQueries: List[fr.proline.core.om.model.msi.MsQuery] = null
      if( msQueryByInitialId != null ) {
        msQueries = msQueryByInitialId.map { _._2 }.toList.sort { (a,b) => a.initialId < b.initialId }
      }
      
      // Load the peaklist software from the MSidb if it is only an in memory object
      if( msiSearch.peakList.peaklistSoftware == null || msiSearch.peakList.peaklistSoftware.id <= 0 )
        msiSearch.peakList.peaklistSoftware = this._getOrCreatePeaklistSoftware( peaklistSoftwareId, this.storerContext )
      
      /*if(msiSearch != null && msiSearch.peakList.peaklistSoftware == null ){
        //TODO : Define how to get this information !
        msiSearch.peakList.peaklistSoftware = _getPeaklistSoftware("Default_PL","0.1" )
      } */ 
      
      //val driverType = msiDbConnector.getDriverType()
      //if( driverType.getDriverClassName == "org.sqlite.JDBC" ) {
        
        // Store the peaklist    
        val spectrumIdByTitle = rsStorer.storePeaklist( msiSearch.peakList, this.storerContext )
        rsStorer.storeSpectra( msiSearch.peakList.id, resultFile, this.storerContext )
        >>>
        
    	  //Store the MSI search with related search settings and MS queries    
        rsStorer.storeMsiSearch( msiSearch, this.storerContext ) 
        rsStorer.storeMsQueries( msiSearch.id, msQueries, this.storerContext )        
        >>>
     // }
	    
		  // Load target result set
		  var targetRs = resultFile.getResultSet(false)
		  if( StringUtils.isEmpty(targetRs.name) ) targetRs.name = msiSearch.title
		    	    
	    //-- VDS TODO: Identify decoy mode to get decoy RS from parser or to create it from target RS.
	
		  def storeDecoyRs( decoyRs: ResultSet ): ResultSet = {
        if(StringUtils.isEmpty(decoyRs.name))
          decoyRs.name = msiSearch.title
         
        //if( driverType.getDriverClassName == "org.sqlite.JDBC" )
          rsStorer.storeResultSet(decoyRs,this.storerContext) 
        //else
        //  targetRs.decoyResultSet = Some(decoyRs)
          decoyRs
		  }
		  
	    // Load and store decoy result set if it exists
		  //var decoyRsId = Option.empty[Int]
		  if( resultFile.hasDecoyResultSet ) {
		    val dRs = resultFile.getResultSet(true)
		    
		    targetRs.decoyResultSet = Some( storeDecoyRs( dRs ) )
		    //decoyRsId = Some(dRs.id)
		    >>>
  	  }
		  // Else if a regex has been passed to detect decoy protein matches		  
      else if( acDecoyRegex != None ) {
        // Then split the result set into a target and a decoy one
        val( tRs, dRs ) = TargetDecoyResultSetSplitter.split(targetRs,acDecoyRegex.get)
        targetRs = tRs
        
        targetRs.decoyResultSet = Some( storeDecoyRs(dRs) )
        //decoyRsId = Some(dRs.id)
        >>>
      }
      else targetRs.decoyResultSet = None

     //  Store target result set
  		this.targetResultSetId = rsStorer.storeResultSet(targetRs,this.storerContext)       
      >>>
    
//    this.msiDb.commitTransaction()// VD Pour SQLStorer Only
//      msiTransaction.commit()
//      msiTransacOk = true
    } finally {
     //Fait par le Storer: Attente partage transaction TODO  
//      /* Check msiTransaction integrity */
//      if ((msiTransaction != null) && !msiTransacOk) {
//        try {
//          if(stContext.msiDB.isInTransaction)
//          	 stContext.msiDB.rollbackTransaction
//          msiTransaction.rollback()
//        } catch {
//          case ex => logger.error("Error rollbacking Msi Db transaction", ex)
//        }
//      } else 
//         if(stContext.msiDB.isInTransaction)
//        	 stContext.msiDB.rollbackTransaction
    }
    
    if( !wasInTransaction ) storerContext.msiEzDBC.commitTransaction
    
    this.beforeInterruption()
    true 
//    msiTransacOk   
  }

  private def _getPeaklistSoftware( plName: String, plRevision: String ): PeaklistSoftware = {

    udsSqlHelper.ezDBC.selectHeadOrElse(
    "SELECT * FROM peaklist_software WHERE name= ? and version= ? ",plName,plRevision) ( r =>
      new PeaklistSoftware( id = r, name = r, version = r )
      ,
      new PeaklistSoftware( id = PeaklistSoftware.generateNewId, name = "Default", version = "0.1" )
    )
  }
  
  private def _getOrCreatePeaklistSoftware( peaklistSoftwareId: Int, stContext: StorerContext ): PeaklistSoftware = {
    
    import fr.proline.util.primitives.LongOrIntAsInt._
    import fr.proline.core.dal.tables.msi.MsiDbPeaklistSoftwareTable
    
    def getPeaklistSoftware( ezDBC: EasyDBC ): PeaklistSoftware = {

      var peaklistSoftware: PeaklistSoftware = null
      ezDBC.selectAndProcess( "SELECT name, version FROM peaklist_software WHERE id=" + peaklistSoftwareId ) { r => 
        peaklistSoftware = new PeaklistSoftware( id = peaklistSoftwareId, name = r, version = r.nextStringOrElse("") )
      }
      
      peaklistSoftware
    }
    
    val msiDb = storerContext.msiEzDBC  
    
    // Try to retrieve peaklist software from the MSidb
    var peaklistSoftware = getPeaklistSoftware( msiDb )
    if( peaklistSoftware == null ) {
      
      // If it doesn't exist => retrieve from the UDSdb
      peaklistSoftware = getPeaklistSoftware( udsSqlHelper.ezDBC )
      
      // Then insert it in the current MSIdb
      val peaklistInsertQuery = MsiDbPeaklistSoftwareTable.makeInsertQuery( c => List(c.id,c.name,c.version) )      
      msiDb.execute(peaklistInsertQuery,peaklistSoftware.id,peaklistSoftware.name,peaklistSoftware.version)
    }

    peaklistSoftware

  }
  
  private def _getInstrumentConfig( instrumentConfigId: Int ): InstrumentConfig = {
    
    import fr.proline.util.primitives.LongOrIntAsInt._
    import fr.proline.core.om.model.msi.{InstrumentProperties,InstrumentConfigProperties}
    
    // Load the instrument configuration record
    udsSqlHelper.ezDBC.selectHead(
    "SELECT instrument.*,instrument_config.* FROM instrument,instrument_config "+
    "WHERE instrument.id = instrument_config.instrument_id AND instrument_config.id =" + instrumentConfigId ) { r =>
      
      val instrument = new Instrument( id = r.nextObject.asInstanceOf[AnyVal], name = r, source = r )
      for( instPropStr <- r.nextStringOption ) {
        if( StringUtils.isEmpty(instPropStr) == false )
          instrument.properties = Some( parse[InstrumentProperties]( instPropStr ) )
      }
      
      // Skip instrument_config.id field
      r.nextObject
      
      val instrumentConfig = new InstrumentConfig(
        id = instrumentConfigId,
        name = r.nextString,
        instrument = instrument,
        ms1Analyzer = r.nextString,
        msnAnalyzer = r.nextString,
        activationType = ""
      )
      for( instConfPropStr <- r.nextStringOption ) {
        if( StringUtils.isEmpty(instConfPropStr) == false )
          instrumentConfig.properties = Some( parse[InstrumentConfigProperties]( instConfPropStr ) )
      }
      
      // Skip instrument_config.instrument_id field
      r.nextObject
      
      // Update activation type
      instrumentConfig.activationType = r.nextString
      
      instrumentConfig
    }

  }
   
}