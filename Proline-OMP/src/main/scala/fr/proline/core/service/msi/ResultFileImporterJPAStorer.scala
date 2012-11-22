package fr.proline.core.service.msi

import java.io.File
import com.weiglewilczek.slf4s.Logging
import org.apache.commons.lang3.StringUtils

import fr.proline.core.algo.msi.TargetDecoyResultSetSplitter
import fr.proline.core.dal.{DatabaseManagement,MsiDb,UdsDb}
import fr.proline.core.om.model.msi.{IResultFile,IResultFileProvider,Instrument,InstrumentConfig,PeaklistSoftware,ResultSet}
import fr.proline.core.om.model.msi.ResultFileProviderRegistry
import fr.proline.core.om.storer.msi.{IRsStorer,MsiSearchStorer,RsStorer}
import fr.proline.core.om.storer.msi.impl.JPARsStorer
import fr.proline.core.om.storer.msi.impl.StorerContext
import fr.proline.core.service.IService

class ResultFileImporterJPAStorer( dbMgnt: DatabaseManagement,
                          projectId: Int,
                          resultIdentFile: File,
                          fileType: String,
                          providerKey: String,
                          instrumentConfigId: Int,
                          importerProperties: Map[String, Any],
                          acDecoyRegex: Option[util.matching.Regex] = None ) extends IService with Logging {
  
  private var targetResultSetId = 0
  
  private val msiDbConnector = dbMgnt.getMSIDatabaseConnector(projectId, false)
  private val udsDb = new UdsDb( UdsDb.buildConfigFromDatabaseManagement(dbMgnt) )
  private var stContext: StorerContext = null
  
  override protected def beforeInterruption = {
    // Release database connections
    this.logger.info("releasing database connections before service interruption...")
    this.udsDb.closeConnection()
    this.stContext.closeOpenedEM()
  }
  
  def getTargetResultSetId = targetResultSetId
  
  def runService(): Boolean = {
    
    var serviceResultOK: Boolean = true
    
    // Check that a file is provided
    require(resultIdentFile != null, "ResultFileImporter service: No file specified.")
    
    // Retrieve the instrument configuration
    val instrumentConfig = this._getInstrumentConfig( instrumentConfigId )

    this.stContext = new StorerContext(dbMgnt, msiDbConnector)
    logger.info(" Run service " + fileType + " ResultFileImporter on " + resultIdentFile.getAbsoluteFile())
    >>>
    
    // Get Right ResultFile provider
    val rfProvider: Option[IResultFileProvider] = ResultFileProviderRegistry.get( fileType )
    require( rfProvider != None, "No ResultFileProvider for specified identification file format")
    
    // Open the result file
    val resultFile = rfProvider.get.getResultFile( resultIdentFile, providerKey, importerProperties )
    >>>
    
    // Instantiate RsStorer   
    val rsStorer: IRsStorer = new JPARsStorer(dbMgnt, msiDbConnector) //<> SQLStorer
 
    
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
    	rsStorer.insertInstrumentConfig( instrumentConfig, this.stContext)               
      stContext.msiDB.commitTransaction()// Attente partage transaction TODO  ??
      
      // Retrieve MSISearch and related MS queries
      val msiSearch = resultFile.msiSearch
      val msQueryByInitialId = resultFile.msQueryByInitialId
      var msQueries: List[fr.proline.core.om.model.msi.MsQuery] = null
      if( msQueryByInitialId != null ) {
        msQueries = msQueryByInitialId.map { _._2 }.toList.sort { (a,b) => a.initialId < b.initialId }
      }
      
      //Done by RsStorer <> SQLStorer
        //Store the MSI search with related search settings and MS queries ...
        // Store the peaklist  ... 
      	    
		  // Load target result set
		  var targetRs = resultFile.getResultSet(false)
		  if(StringUtils.isEmpty(targetRs.name))
		    targetRs.name = msiSearch.title
		  
		  if(targetRs.msiSearch != null && targetRs.msiSearch.peakList.peaklistSoftware ==null){
		    //TODO : Define how to get this information !
		    targetRs.msiSearch.peakList.peaklistSoftware = _getPeaklistSoftware("Default_PL","0.1" )		  	
		  }
		    	    
	    //-- VDS TODO: Identify decoy mode to get decoy RS from parser or to create it from target RS.
	
		  def storeDecoyRs( decoyRs: ResultSet ) {
        if(StringUtils.isEmpty(decoyRs.name))
          decoyRs.name = msiSearch.title
                
        //VDTESTif( msiDbConnector.getDriverType().getDriverClassName == "org.sqlite.JDBC" )
//VDTEST          rsStorer.storeResultSet(decoyRs,this.stContext) //Comportement différent des implémentation RsStorer ?! 
        //VDTESTelse
          targetRs.decoyResultSet = Some(decoyRs)          
		  }
		  
	    // Load and store decoy result set if it exists
		  if( resultFile.hasDecoyResultSet ) {
		    storeDecoyRs( resultFile.getResultSet(true) )
		    >>>
  	  }
		  // Else if a regex has been passed to detect decoy protein matches		  
      else if( acDecoyRegex != None ) {
        // Then split the result set into a target and a decoy one
        val( tRs, dRs ) = TargetDecoyResultSetSplitter.split(targetRs,acDecoyRegex.get)
        targetRs = tRs
        
        storeDecoyRs(dRs)
        >>>
      }
      else 
      	targetRs.decoyResultSet = None

      //  Store target result set
   this.targetResultSetId = rsStorer.storeResultSet(targetRs, msQueries, resultFile, this.stContext)
      >>>


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
    
    this.beforeInterruption()
    true 
//    msiTransacOk   
  }

  private def _getPeaklistSoftware( plName: String, plRevision: String ): PeaklistSoftware = {

    var plSoftware: PeaklistSoftware = null
    
    val prepStmt = this.udsDb.getOrCreateConnection.prepareStatement("SELECT * FROM peaklist_software WHERE name= ? and version= ? ")
    prepStmt.setString(1, plName)
    prepStmt.setString(2, plRevision)
    
    val result = prepStmt.executeQuery ()  
    if(result.next){
      plSoftware = new PeaklistSoftware( id = result.getInt("id"),
        name =result.getString("name"),
        version =result.getString("version"))
    }
       
    if ( plSoftware == null )
      plSoftware = new PeaklistSoftware( id=  PeaklistSoftware.generateNewId,
        name = "Default",
        version = "0.1" )

    plSoftware
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